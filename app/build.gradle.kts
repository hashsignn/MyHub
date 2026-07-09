// M0.0 — app module build config.
// Dependencies are added milestone-by-milestone: Phase 0 only needs core UI + test libs.
// Room/DataStore (M1.2), WorkManager (M1.4), etc. are added when their milestone lands.
import java.io.FileInputStream
import java.util.Properties

// Release signing is driven by a gitignored keystore.properties at the repo root (see RELEASE.md).
// When it's absent (fresh clone, CI without secrets) the project still configures and builds — the
// release APK is simply produced unsigned instead of failing the build.
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasReleaseKeystore) FileInputStream(keystorePropsFile).use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.contentreg.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.contentreg.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink + obfuscate for a smaller, harder-to-reverse release APK. Keep rules live
            // in proguard-rules.pro. Test the release build once after changing keep rules — a
            // missing rule surfaces as a runtime crash, not a compile error.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed only when keystore.properties is present; otherwise the APK builds unsigned.
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        // BuildConfig.DEBUG gates sensitive detail logging (see PrivacyLog); must be generated.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // JVM unit tests touch android.util.Log indirectly (via classes under test); returning
        // defaults for un-mocked framework calls keeps those tests from throwing "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)

    // M1.0 — lifecycle-aware coroutine scopes (UI observing the sensing layer) + coroutines.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // M1.2 — persistence: Room (durable budget state) + DataStore (settings).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
