import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}

// Formato (ADR-0015): ktlint lee el estilo de .editorconfig. `./gradlew spotlessApply` lo corrige.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val excluidos = listOf("**/build/**", "**/node_modules/**", "**/.gradle/**")
    kotlin {
        target("**/*.kt")
        targetExclude(excluidos)
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude(excluidos)
        ktlint(ktlintVersion)
    }
}

// Análisis estático (ADR-0015): una configuración común y un baseline por módulo para lo que ya existía.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        // Todos los conjuntos de fuentes: main, test y androidTest.
        source.setFrom(
            file("src").listFiles().orEmpty()
                .flatMap { conjunto -> listOf(File(conjunto, "kotlin"), File(conjunto, "java")) }
                .filter { it.isDirectory },
        )
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
    tasks.withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget = "17"
    }

    // detekt 1.23 está compilado con Kotlin 2.0: su clasificador no debe recibir el Kotlin del proyecto.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(io.gitlab.arturbosch.detekt.getSupportedKotlinVersion())
            }
        }
    }
}

// Cobertura (ADR-0016): cada módulo con Kover (hoy :dominio y :tutor) exige un mínimo de líneas cubiertas.
// El mínimo se cambia sin tocar el código: ./gradlew koverVerify -Pcobertura.minima=80
val coberturaMinima = providers.gradleProperty("cobertura.minima").map(String::toInt).orElse(70)

subprojects {
    plugins.withId("org.jetbrains.kotlinx.kover") {
        extensions.configure<KoverProjectExtension> {
            reports {
                verify {
                    rule("Cobertura de líneas de ${project.name}") {
                        bound {
                            minValue.set(coberturaMinima)
                            coverageUnits.set(CoverageUnit.LINE)
                            aggregationForGroup.set(AggregationType.COVERED_PERCENTAGE)
                        }
                    }
                }
            }
        }
        tasks.named("check") { dependsOn("koverVerify") }
    }
}
