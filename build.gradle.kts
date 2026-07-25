plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Déclaration manuelle et propre sans passer par le catalogue défaillant
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}