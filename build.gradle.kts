// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
// Keep intermediate output outside the source/signing workspace when requested.
allprojects {
    providers.gradleProperty("buildRoot").orNull?.let { root ->
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
