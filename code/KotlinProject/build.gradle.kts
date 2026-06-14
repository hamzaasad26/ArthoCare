// This is the correct content for the top-level build file.
// It only defines plugins and does not apply them.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
}
