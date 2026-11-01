// Plugins go on the buildscript classpath so the Android plugin is only
// required when the :app module is included (see settings.gradle.kts).
buildscript {
    val kotlinVersion = "2.1.21"
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").exists()
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        if (hasAndroidSdk) {
            classpath("com.android.tools.build:gradle:8.10.1")
            classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.1.21-2.0.2")
        }
    }
}
