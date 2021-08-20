pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        google()
        jcenter()
    }
}
rootProject.name = "plugin-android-nfc-example"

// Fix resolution of dependencies with dynamic version in order to use SNAPSHOT first when available.
// See explanation here : https://docs.gradle.org/6.8.3/userguide/single_versions.html
enableFeaturePreview("VERSION_ORDERING_V2")