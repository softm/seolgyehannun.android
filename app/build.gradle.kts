plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.softm.seolgyehannun"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.softm.seolgyehannun"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        val appUrl = providers.gradleProperty("SEOLGYEHANNUN_WEB_URL")
            .orElse("https://seolgyehannun.designboard.net/")
            .get()
        buildConfigField("String", "WEB_URL", "\"$appUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.12.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
