plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.loisebastiano.claudelimits"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.loisebastiano.claudelimits"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
    }

    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM: Android's copy is only a stub in local unit tests.
    testImplementation("org.json:json:20240303")
}
