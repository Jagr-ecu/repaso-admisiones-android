// Solo tests de arquitectura (ADR-0017): Konsist lee el código fuente de todos los módulos y comprueba las reglas
// de CLAUDE.md. No depende de ningún módulo ni lo usa nadie.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // Los tests leen archivos de otros módulos: si cambian, se vuelven a correr.
    listOf("dominio", "tutor", "datos", "sincro", "app").forEach { modulo ->
        inputs.dir(rootProject.file("$modulo/src")).withPropertyName("fuentes-$modulo").optional()
        inputs.file(rootProject.file("$modulo/build.gradle.kts")).withPropertyName("build-$modulo").optional()
    }
}
