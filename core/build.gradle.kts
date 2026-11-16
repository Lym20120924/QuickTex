plugins {
    id("com.android.library")
}

android {
    namespace = "com.formulastudio.core"
    compileSdk = 35

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
