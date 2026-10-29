pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FocusGuard"
include(":app")

// Apps falsas para pruebas de integración en emulador (no se compilan por defecto):
//   ./gradlew -PwithFixtures :fixtures:fakeapps:assembleDebug
if (providers.gradleProperty("withFixtures").isPresent) include(":fixtures:fakeapps")
