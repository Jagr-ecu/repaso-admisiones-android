plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.preparate.datos"
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
    api(project(":dominio"))
    testImplementation(libs.junit)
}

// Los tests leen el paquete de contenido del APK: si cambia, se vuelven a correr.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("app/src/main/assets/contenido")).withPropertyName("paqueteDeContenido")
}
