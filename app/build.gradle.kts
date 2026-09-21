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
        versionCode = 6
        versionName = "1.4.2"
        resourceConfigurations += setOf("en")
    }

    signingConfigs {
        create("release") {
            // Demo release key that ships with the project so the APK can be built and
            // installed locally. Replace it with your own keystore before publishing.
            storeFile = file("../texthub-release.jks")
            storePassword = "texthub"
            keyAlias = "texthub"
            keyPassword = "texthub"
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
            signingConfig = signingConfigs.getByName("release")
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
