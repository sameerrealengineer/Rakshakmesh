rootProject.name = "RakshakMesh"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core")

// The Android app needs the Android SDK. Without it (e.g. a plain JVM box),
// only :core is built so its unit tests can still run.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
if (hasAndroidSdk) include(":app")
