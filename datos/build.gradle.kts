plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.preparate.datos"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Los tests en el dispositivo usan el mismo paquete de contenido que la app.
    sourceSets {
        named("androidTest") {
            assets.directories.add(rootProject.file("app/src/main/assets").path)
            // Los casos de referencia de Python: el verificador se prueba también con el motor de Android.
            assets.directories.add(rootProject.file("dominio/src/test/resources").path)
            // Los esquemas exportados: con ellos se prueban las migraciones.
            assets.directories.add("$projectDir/schemas")
        }
    }
}

// El esquema de cada versión de la base va a git: con él se escriben y prueban las migraciones.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":dominio"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}

// Los tests leen el paquete de contenido del APK: si cambia, se vuelven a correr.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("app/src/main/assets/contenido")).withPropertyName("paqueteDeContenido")
}
