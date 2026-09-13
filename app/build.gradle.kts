import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.play.publisher)
}

// Upload key for Play. keystore.properties is gitignored; without it the release build is debug-signed
// so local perf testing keeps working on any machine.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasUploadKey = keystoreProps.containsKey("storeFile")

android {
    namespace = "com.ahmedgeek.quicklaunch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ahmedgeek.quicklaunch"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
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
        buildConfig = false
        viewBinding = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "kotlin/**", "META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
}

// Play Console upload. Drop the service-account JSON at play/service-account.json (gitignored) after
// granting it access in Play Console > Setup > API access. Then:
//   ./gradlew publishBundle            upload the AAB to the internal track
//   ./gradlew publishListing publishImages   push store text, icon, feature graphic, screenshots
//   ./gradlew promoteArtifact --from-track internal --promote-track production
val playCredentials = rootProject.file("play/service-account.json")
play {
    enabled.set(playCredentials.exists())
    if (playCredentials.exists()) serviceAccountCredentials.set(playCredentials)
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)
}
