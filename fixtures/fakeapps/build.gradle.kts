// Apps de prueba que imitan la ESTRUCTURA de los visores de videos cortos (no su contenido ni
// su marca) para probar el servicio de accesibilidad de FocusGuard en un emulador sin cuentas.
// Usan los nombres de paquete reales para que FocusGuard las trate como esas apps: instalar solo
// en un emulador de pruebas.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.personal.fakeapps"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "test"
    }

    flavorDimensions += "app"
    productFlavors {
        create("instagram") {
            dimension = "app"
            applicationId = "com.instagram.android"
            resValue("string", "fake_name", "Fake IG (prueba)")
            resValue("string", "safe_tab", "Inicio")
        }
        create("tiktok") {
            dimension = "app"
            applicationId = "com.zhiliaoapp.musically"
            resValue("string", "fake_name", "Fake TT (prueba)")
            resValue("string", "safe_tab", "Bandeja de entrada")
        }
    }

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
