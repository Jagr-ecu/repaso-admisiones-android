package com.preparate.arquitectura

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Las reglas de módulos y de datos de CLAUDE.md, comprobadas sobre el código fuente (ADR-0017).
 * Si un test falla, el mensaje dice qué archivo rompe la regla y por qué.
 */
class ArquitecturaTest {
    private val raiz: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun modulo(vararg nombres: String): KoScope =
        nombres.map { Konsist.scopeFromModule(it) }.reduce { a, b -> a + b }

    /** Los archivos de [alcance] que importan algo que empieza por uno de [prefijos]. */
    private fun importan(alcance: KoScope, vararg prefijos: String): List<String> = alcance.files.flatMap { archivo ->
        archivo.imports
            .filter { i -> prefijos.any { i.name == it.removeSuffix(".") || i.name.startsWith(it) } }
            .map { "${archivo.relativo()}: import ${it.name}" }
    }

    private fun KoFileDeclaration.relativo(): String = File(path).relativeTo(raiz).invariantSeparatorsPath

    private fun sinInfracciones(regla: String, infracciones: List<String>) {
        if (infracciones.isNotEmpty()) fail("$regla\n" + infracciones.joinToString("\n") { "  - $it" })
    }

    // --- Módulos -------------------------------------------------------------------------------

    @Test
    fun dominioYTutorNoUsanAndroid() {
        sinInfracciones(
            ":dominio y :tutor son Kotlin puro: no pueden importar android.* ni androidx.* (ADR-0006)",
            importan(modulo("dominio", "tutor"), "android.", "androidx."),
        )
    }

    @Test
    fun dominioYTutorNoUsanFirebase() {
        sinInfracciones(
            ":dominio y :tutor no hablan con Firebase: eso es cosa de :sincro",
            importan(modulo("dominio", "tutor"), "com.google.firebase.", "com.google.android."),
        )
    }

    @Test
    fun dominioNoDependeDeOtrosModulos() {
        sinInfracciones(
            ":dominio no puede usar código de otros módulos del proyecto",
            importan(
                modulo("dominio"),
                "com.preparate.datos.",
                "com.preparate.tutor.",
                "com.preparate.sincro.",
                "com.preparate.app.",
            ),
        )
    }

    @Test
    fun nadieDependeDeApp() {
        sinInfracciones(
            "Ningún módulo puede usar código de :app",
            importan(modulo("dominio", "tutor", "datos", "sincro"), "com.preparate.app."),
        )
        val conApp = listOf("dominio", "tutor", "datos", "sincro", "arquitectura")
            .map { File(raiz, "$it/build.gradle.kts") }
            .filter { it.isFile && Regex("""project\(\s*":app"\s*\)""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(raiz).invariantSeparatorsPath }
        sinInfracciones("Ningún módulo puede depender de :app en Gradle", conApp)
    }

    // --- Datos de cada estudiante --------------------------------------------------------------

    @Test
    fun lasConsultasDeLosDaoFiltranPorUsuario() {
        val daos = modulo("datos").interfaces().filter { it.hasAnnotationWithName("Dao") }
        assertTrue("No se encontró ningún @Dao: ¿cambió el módulo :datos?", daos.isNotEmpty())

        val sinUsuario = daos.flatMap { dao ->
            dao.functions()
                .mapNotNull { f -> f.annotations.firstOrNull { it.name == "Query" }?.let { f to it.text } }
                .filterNot { (_, consulta) -> Regex("""\busuario_id\s*=\s*:""").containsMatchIn(consulta) }
                .map { (f, _) -> "${dao.name}.${f.name} (${f.containingFile.relativo()})" }
        }
        sinInfracciones(
            "Toda consulta @Query de un DAO filtra por usuario_id: varios estudiantes comparten el dispositivo",
            sinUsuario,
        )
    }

    // --- Conversaciones con Kiwi (regla: no se guardan nunca, ADR-0009) -------------------------

    @Test
    fun laPersistenciaNoUsaElTutor() {
        sinInfracciones(
            ":datos y :sincro no pueden usar :tutor: así ningún mensaje de Kiwi llega a Room ni a Firestore",
            importan(modulo("datos", "sincro"), "com.preparate.tutor."),
        )
    }

    @Test
    fun ningunaTablaGuardaMensajesDeKiwi() {
        val tablas = modulo("datos").classes().filter { it.hasAnnotationWithName("Entity") }
        assertTrue("No se encontró ninguna @Entity: ¿cambió el módulo :datos?", tablas.isNotEmpty())

        val prohibido = Regex("""(?i)mensaje|conversaci|historial|chat|turno|dicho""")
        val sospechosas = tablas.flatMap { tabla ->
            tabla.properties()
                .filter { p -> prohibido.containsMatchIn(p.name) || prohibido.containsMatchIn(p.type?.text.orEmpty()) }
                .map { "${tabla.name}.${it.name} (${tabla.containingFile.relativo()})" }
        }
        sinInfracciones("Ninguna tabla de Room guarda mensajes ni conversaciones con Kiwi", sospechosas)
    }

    @Test
    fun lasPantallasConKiwiNoEscribenEnRoomNiEnFirestore() {
        val tiposDeKiwi = Regex("""\b(MensajeKiwi|Turno|Dicho|Mensaje)\b""")
        val conKiwi = modulo("app").files.filter { tiposDeKiwi.containsMatchIn(it.text) }
        assertTrue("No se encontró el chat de Kiwi en :app: ¿cambió de nombre?", conKiwi.isNotEmpty())

        val infracciones = conKiwi.flatMap { archivo ->
            archivo.imports
                .filter { i ->
                    listOf("androidx.room.", "com.google.firebase.firestore.", "com.preparate.datos.local.")
                        .any { i.name.startsWith(it) }
                }
                .map { "${archivo.relativo()}: import ${it.name}" }
        }
        sinInfracciones(
            "Los archivos que manejan el chat de Kiwi no pueden usar Room ni Firestore directamente",
            infracciones,
        )
    }
}
