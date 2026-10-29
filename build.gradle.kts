plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 incluye Kotlin integrado; declarar KGP aquí solo fija su versión.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
