// Kotlin puro, sin Android: el cliente de LM Studio usa HttpURLConnection y los tests corren en la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":dominio"))
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
