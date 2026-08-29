#!/usr/bin/env bash
# One-time setup for publishing releases.
#
#   1. Creates the release signing keystore.
#   2. Records its details in .env so local release builds work.
#   3. Uploads the same values to GitHub Actions as repository secrets.
#   4. Optionally records the OAuth client id the app needs to sign in to GitHub.
#
# Every release has to be signed with the same key: Android refuses to install an update
# whose signature differs from the installed app, so rotating this key would strand every
# device on its current build. Run this once, then keep the generated .jks somewhere safe.
#
# The GitHub CLI is optional. Without it the script prints exactly what to paste into
# Settings -> Secrets and variables -> Actions on github.com.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
KEYSTORE_PATH="${KEYSTORE_PATH:-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-nutrition}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10950}" # 30 years

# --- helpers ------------------------------------------------------------------------
# keytool ships with any JDK, but this machine may not have one on PATH.
find_keytool() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    echo "$JAVA_HOME/bin/keytool"; return
  fi
  if command -v keytool >/dev/null 2>&1; then
    command -v keytool; return
  fi
  for candidate in "$HOME/.local/toolchain/jdk/bin/keytool" /usr/lib/jvm/*/bin/keytool; do
    [ -x "$candidate" ] && { echo "$candidate"; return; }
  done
  return 1
}

# Sets KEY=VALUE in .env, replacing any existing line for that key.
set_env_var() {
  local key="$1" value="$2" tmp
  if [ ! -f "$ENV_FILE" ]; then
    if [ -f .env.example ]; then cp .env.example "$ENV_FILE"; else : > "$ENV_FILE"; fi
  fi
  chmod 600 "$ENV_FILE"
  tmp="$(mktemp)"
  if grep -q "^${key}=" "$ENV_FILE"; then
    awk -v k="$key" -v v="$value" 'index($0, k "=") == 1 { print k "=" v; next } { print }' \
      "$ENV_FILE" > "$tmp"
    mv "$tmp" "$ENV_FILE"
  else
    rm -f "$tmp"
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
  chmod 600 "$ENV_FILE"
}

# gh cannot work out the repository when the remote uses an SSH host alias, e.g.
# git@github-work:owner/repo.git, so derive owner/repo from the URL and pass it through.
repo_slug() {
  local url
  url="$(git remote get-url "${GIT_REMOTE:-origin}" 2>/dev/null)" || return 1
  printf '%s\n' "${url%.git}" | tr ':' '/' | awk -F/ 'NF>=2 {print $(NF-1)"/"$NF}'
}

if ! KEYTOOL="$(find_keytool)"; then
  cat >&2 <<'MSG'
keytool not found. It comes with any JDK — install one, or point JAVA_HOME at an
existing JDK and run this again:

    JAVA_HOME=/path/to/jdk ./scripts/setup-release.sh
MSG
  exit 1
fi

# --- 1. create or reuse the keystore ------------------------------------------------
if [ -f "$KEYSTORE_PATH" ]; then
  echo "Using the existing keystore at $KEYSTORE_PATH."
  echo "(This is the keystore password from when it was created — not your OAuth client id.)"
  read -r -s -p "Keystore password: " KEYSTORE_PASSWORD; echo
  read -r -s -p "Key password (blank to reuse the keystore password): " KEY_PASSWORD; echo
  KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

  # Check it before going any further. Without this a mistyped password is written
  # straight into .env and the repository secrets, and only surfaces as a CI failure.
  if ! "$KEYTOOL" -list -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" \
        -storepass "$KEYSTORE_PASSWORD" >/dev/null 2>&1; then
    echo >&2
    echo "That password does not open $KEYSTORE_PATH (alias '$KEY_ALIAS')." >&2
    echo "Nothing has been changed. Re-run with the right password, or delete the" >&2
    echo "keystore to generate a fresh one — safe as long as no release has shipped" >&2
    echo "with the old key." >&2
    exit 1
  fi
  echo "Password accepted."
else
  echo "Creating a new keystore at $KEYSTORE_PATH."
  KEYSTORE_PASSWORD="$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)"
  KEY_PASSWORD="$KEYSTORE_PASSWORD"
  "$KEYTOOL" -genkeypair \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 4096 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=Nutrition, OU=Personal, O=Nutrition, L=, ST=, C=" \
    -storetype PKCS12
  echo
  echo "Generated keystore password (store this in your password manager — it cannot"
  echo "be recovered from the keystore):"
  echo
  echo "    $KEYSTORE_PASSWORD"
fi

echo
echo "Certificate fingerprint (this must never change between releases):"
"$KEYTOOL" -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass "$KEYSTORE_PASSWORD" \
  > "$PWD/.keytool-out" 2>&1 || { cat "$PWD/.keytool-out" >&2; rm -f "$PWD/.keytool-out"; exit 1; }
grep -E "SHA256:" "$PWD/.keytool-out" || true
rm -f "$PWD/.keytool-out"

# --- 2. record it in .env for local release builds ----------------------------------
set_env_var APP_KEYSTORE_FILE "$KEYSTORE_PATH"
set_env_var APP_KEYSTORE_PASSWORD "$KEYSTORE_PASSWORD"
set_env_var APP_KEY_ALIAS "$KEY_ALIAS"
set_env_var APP_KEY_PASSWORD "$KEY_PASSWORD"
echo
echo "Wrote the signing settings to $ENV_FILE (mode 600, gitignored)."

# --- 3. the OAuth client id the app signs in with -----------------------------------
CLIENT_ID="${APP_GITHUB_OAUTH_CLIENT_ID:-}"
if [ -z "$CLIENT_ID" ]; then
  cat <<'MSG'

Signing in with GitHub is the only way the app can read a private repository's releases,
and that needs a client id. See the README for the two options: a GitHub App with
Contents: Read-only installed on this repository alone (recommended), or an OAuth app,
which is quicker to set up but whose token can reach every repository you can.
Either way, enable device flow and paste the client id below.
The client id is not a secret — it is compiled into the APK.

MSG
  read -r -p "OAuth client id (leave blank to add it later): " CLIENT_ID
fi
if [ -n "$CLIENT_ID" ]; then
  set_env_var APP_GITHUB_OAUTH_CLIENT_ID "$CLIENT_ID"
fi

# --- 4. hand the values to GitHub Actions -------------------------------------------
KEYSTORE_BASE64="$(base64 -w0 "$KEYSTORE_PATH")"
REPO_SLUG="${GH_REPO:-$(repo_slug || true)}"

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  if [ -z "$REPO_SLUG" ]; then
    echo "Could not work out owner/repo from the git remote." >&2
    echo "Re-run with it set explicitly: GH_REPO=owner/repo $0" >&2
    exit 1
  fi
  echo
  echo "Uploading secrets to $REPO_SLUG…"
  printf '%s' "$KEYSTORE_BASE64"   | gh secret set APP_KEYSTORE_BASE64   --repo "$REPO_SLUG"
  printf '%s' "$KEYSTORE_PASSWORD" | gh secret set APP_KEYSTORE_PASSWORD --repo "$REPO_SLUG"
  printf '%s' "$KEY_ALIAS"         | gh secret set APP_KEY_ALIAS         --repo "$REPO_SLUG"
  printf '%s' "$KEY_PASSWORD"      | gh secret set APP_KEY_PASSWORD      --repo "$REPO_SLUG"
  if [ -n "$CLIENT_ID" ]; then
    printf '%s' "$CLIENT_ID" | gh secret set APP_GITHUB_OAUTH_CLIENT_ID --repo "$REPO_SLUG"
  else
    echo "No client id given — set it before pushing, or the release build will fail:"
    echo "  gh secret set APP_GITHUB_OAUTH_CLIENT_ID --repo $REPO_SLUG"
  fi
  echo "Done."
else
  OUT_DIR="$(mktemp -d)"
  printf '%s' "$KEYSTORE_BASE64" > "$OUT_DIR/APP_KEYSTORE_BASE64.txt"
  cat <<MSG

The GitHub CLI is not available (or not signed in), so set these secrets by hand at
    Settings -> Secrets and variables -> Actions -> New repository secret

  APP_KEYSTORE_BASE64         contents of $OUT_DIR/APP_KEYSTORE_BASE64.txt
  APP_KEYSTORE_PASSWORD       the keystore password shown above
  APP_KEY_ALIAS               $KEY_ALIAS
  APP_KEY_PASSWORD            the same password (unless you set a separate key password)
  APP_GITHUB_OAUTH_CLIENT_ID  ${CLIENT_ID:-<your OAuth app client id>}

Delete $OUT_DIR/APP_KEYSTORE_BASE64.txt once the secret is saved.
To do it with the CLI instead: run 'gh auth login', then re-run this script.
MSG
fi

cat <<MSG

Keep $KEYSTORE_PATH and its password safe — losing them means no installed copy of the
app can ever be updated again, only uninstalled and replaced. .gitignore excludes *.jks.
MSG
