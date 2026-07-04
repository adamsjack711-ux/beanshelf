// Top-level build file. Declare plugins here (apply false); apply them in :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // No org.jetbrains.kotlin.android — AGP 9.x built-in Kotlin handles compilation.
}
