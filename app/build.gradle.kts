import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.formulastudio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.formulastudio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.7"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propFile = rootProject.file("keystore.properties")
            if (propFile.exists()) {
                propFile.inputStream().use { props.load(it) }
            }
            storeFile = rootProject.file(
                props.getProperty("storeFile") ?: "keystore/quicktex-release.jks"
            )
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")


    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
