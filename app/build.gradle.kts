import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * All configuration comes from environment variables, with a gitignored `.env` file at the
 * repository root as the local source for them. A real environment variable always wins over
 * the file, which is how CI supplies the same settings from repository secrets with no `.env`
 * present. See `.env.example` for the full list.
 */
val dotenv: Map<String, String> = providers
    .fileContents(layout.settingsDirectory.file(".env"))
    .asText.orNull
    ?.lineSequence()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
    ?.associate { line ->
        val (key, value) = line.split('=', limit = 2)
        key.trim() to value.trim().trim('"', '\'')
    }
    .orEmpty()

fun env(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: dotenv[name]?.takeIf { it.isNotBlank() }

// CI passes the workflow run number, which only ever increases. Local builds fall back to 1
// and a "-dev" name so a sideloaded debug build never looks newer than a real release.
val appVersionCode = (env("APP_VERSION_CODE") ?: "1").toInt()
val appVersionName = env("APP_VERSION_NAME") ?: "0.0.$appVersionCode-dev"

val githubOwner = env("APP_GITHUB_OWNER") ?: "augustinavicius"
val githubRepo = env("APP_GITHUB_REPO") ?: "nutrition"

// Release signing. The keystore itself never enters the repository; only its path does.
// A relative path is resolved against the repository root, not this module — `.env` sits at
// the root, so "release.jks" there means the one next to it.
val keystoreFile = env("APP_KEYSTORE_FILE")?.let { path ->
    val candidate = File(path)
    if (candidate.isAbsolute) candidate else layout.settingsDirectory.file(path).asFile
}
val keystorePassword = env("APP_KEYSTORE_PASSWORD")
val keystoreKeyAlias = env("APP_KEY_ALIAS")
val keystoreKeyPassword = env("APP_KEY_PASSWORD")

android {
    namespace = "io.github.augustinavicius.nutrition"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.augustinavicius.nutrition"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    signingConfigs {
        if (keystoreFile != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
                // v3 as well as v2, so the signing key can be rotated later without
                // stranding installed builds.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to debug signing so `assembleRelease` works on a fresh clone. That
            // fallback is dangerous to ship — an update only installs when the signature
            // matches — so say so loudly rather than producing a quietly useless APK.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug").also {
                logger.warn(
                    buildString {
                        append("\n**** Release builds will be DEBUG-SIGNED ****\n")
                        if (keystoreFile == null) {
                            append("APP_KEYSTORE_FILE is not set.\n")
                        } else {
                            append("APP_KEYSTORE_FILE points at ")
                            append(keystoreFile.absolutePath)
                            append(", which does not exist.\n")
                        }
                        append("Such an APK cannot update an installed release. ")
                        append("Run ./scripts/setup-release.sh.\n")
                    }
                )
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
    // Exported schemas are what migrations are written against, and what proves a migration
    // produced exactly the shape Room expects.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)

    implementation(libs.work.runtime.ktx)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}
