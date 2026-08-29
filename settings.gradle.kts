/**
 * Bootstraps the Android SDK location from `.env` so a fresh clone needs no other file.
 *
 * AGP finds the SDK via `local.properties` or the ANDROID_HOME/ANDROID_SDK_ROOT environment
 * variables, none of which read `.env` — so if `.env` names one and `local.properties` is
 * absent, write it once. Deliberately only when the file is missing: an existing
 * `local.properties` is the developer's own choice and is never overwritten.
 */
run {
    val localProperties = File(rootDir, "local.properties")
    if (localProperties.exists()) return@run
    if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) return@run

    val dotenv = File(rootDir, ".env").takeIf { it.isFile }?.readLines().orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val (key, value) = line.split('=', limit = 2)
            key.trim() to value.trim().trim('"', '\'')
        }

    val sdkDir = (dotenv["ANDROID_HOME"] ?: dotenv["ANDROID_SDK_ROOT"])?.takeIf { it.isNotBlank() }
    if (sdkDir != null) {
        localProperties.writeText("# Generated from .env by settings.gradle.kts\nsdk.dir=$sdkDir\n")
    }
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Nutrition"
include(":app")
