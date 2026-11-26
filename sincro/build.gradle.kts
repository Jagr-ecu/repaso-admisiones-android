plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.preparate.sincro"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":datos"))
    implementation(libs.work.runtime)
    api(project(":dominio"))
    implementation(libs.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    constraints {
        // Firestore 26.0.2 trae Guava 32.1.3 a través de gRPC: CVE-2026-102554 (GHSA-xxph-c9ww-hj94, alta).
        // Se puede quitar cuando el BOM de Firebase traiga 33.7.2 o superior.
        implementation(libs.guava) { because("CVE-2026-102554: se corrige en 33.7.2") }
    }
}
