// M0.0 — root build config.
// Plugins are declared here (apply false) and applied in module build files.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
