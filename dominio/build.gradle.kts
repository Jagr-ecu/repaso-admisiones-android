plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.serialization.json)
    testImplementation(libs.junit)
}

// Los tests leen el paquete de contenido del APK: si cambia, se vuelven a correr.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("app/src/main/assets/contenido")).withPropertyName("paqueteDeContenido")
}
