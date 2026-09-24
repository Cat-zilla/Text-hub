import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// R8 shrinking is opt-in so the project also builds on machines with limited RAM
// (R8 needs roughly 1.5-2 GB). Enable it with:  ./gradlew assembleRelease -PenableR8=true
val enableR8: Boolean = (providers.gradleProperty("enableR8").orNull ?: "false").toBoolean()

android {
    namespace = "com.texthub.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.texthub.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 17
        versionName = "1.6.8"
        resourceConfigurations += setOf("en")
    }

    // ---------------------------------------------------------------------------------------
    // Release signing
    //
    // **No password is written in this file, and none belongs in a source checkout.** The keystore
    // of the published build is kept with the release, not with the source (see docs/SIGNING.md).
    // When the material is available, the release variant is signed; when it is not - which is what
    // a plain `git clone` looks like - the release variant is built unsigned instead of failing, so
    // the source archive stays buildable for everybody.
    //
    // Provide the credentials with any one of:
    //   * Gradle properties:  ./gradlew assembleRelease -PtexthubStorePassword=… -PtexthubKeyPassword=…
    //   * the environment:    TEXTHUB_STORE_PASSWORD / TEXTHUB_KEY_PASSWORD
    //   * a local file:       keystore.properties next to this file (kept out of Git and out of
    //                         the published source archive)
    val signingProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }
    fun signingValue(property: String, key: String, env: String): String? =
        (providers.gradleProperty(property).orNull ?: signingProperties.getProperty(key) ?: System.getenv(env))
            ?.takeIf { it.isNotBlank() }

    val storeFilePath = signingValue("texthubStoreFile", "storeFile", "TEXTHUB_STORE_FILE") ?: "texthub-release.jks"
    val storePasswordValue = signingValue("texthubStorePassword", "storePassword", "TEXTHUB_STORE_PASSWORD")
    val keyAliasValue = signingValue("texthubKeyAlias", "keyAlias", "TEXTHUB_KEY_ALIAS") ?: "texthub"
    val keyPasswordValue = signingValue("texthubKeyPassword", "keyPassword", "TEXTHUB_KEY_PASSWORD")
    val signingReady = storePasswordValue != null && keyPasswordValue != null &&
        rootProject.file(storeFilePath).isFile

    signingConfigs {
        create("release") {
            if (signingReady) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = enableR8
            isShrinkResources = enableR8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only set when the credentials were provided; otherwise the variant stays unsigned.
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkDependencies = false
        disable += listOf("UnusedMaterial3ScaffoldPaddingParameter", "MissingTranslation")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-window-size-class")

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
