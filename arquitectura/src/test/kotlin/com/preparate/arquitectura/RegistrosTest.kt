package com.preparate.arquitectura

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Ningún dato personal en los registros (ADR-0019). Los datos son de menores: un `Log` con un nombre, un Writing
 * o un mensaje de Kiwi acabaría en logcat, que cualquier app de depuración o un informe de errores puede leer.
 *
 * - En el código de producción no hay `println`, `print`, `printStackTrace` ni `System.out`/`System.err`.
 * - `Log.*` solo en los archivos de [PERMITIDOS], y sin variables con nombres de datos personales.
 */
class RegistrosTest {
    private val raiz: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val produccion = Konsist.scopeFromProduction().files.toList()

    private fun relativo(ruta: String) = File(ruta).relativeTo(raiz).invariantSeparatorsPath

    @Test
    fun hayCodigoQueRevisar() {
        assertTrue("No se encontró código de producción: ¿cambió la estructura?", produccion.size > 10)
    }

    @Test
    fun sinSalidaPorConsola() {
        val infracciones = produccion.flatMap { archivo ->
            Registros.salidaPorConsola(archivo.text).map { "${relativo(archivo.path)}: $it" }
        }
        if (infracciones.isNotEmpty()) {
            fail(
                "No se escribe en la consola en el código de producción:\n" +
                    infracciones.joinToString("\n") { "  - $it" },
            )
        }
    }

    @Test
    fun logSoloDondeSePermiteYSinDatosPersonales() {
        val infracciones = produccion.flatMap { archivo ->
            val ruta = relativo(archivo.path)
            Registros.llamadasALog(archivo.text).mapNotNull { llamada ->
                when {
                    ruta !in PERMITIDOS -> "$ruta: $llamada (este archivo no puede usar Log)"

                    Registros.datoPersonal(llamada) != null ->
                        "$ruta: $llamada (usa \"${Registros.datoPersonal(llamada)}\")"

                    else -> null
                }
            }
        }
        if (infracciones.isNotEmpty()) {
            fail(
                "Log solo en ${PERMITIDOS.joinToString()} y sin datos personales (ADR-0019):\n" +
                    infracciones.joinToString("\n") { "  - $it" },
            )
        }
    }

    // --- La propia regla, con ejemplos ---------------------------------------------------------

    @Test
    fun detectaUnLogConElTextoDeUnWriting() {
        val codigo = """
            fun guardar(intento: Intento) {
                Log.d("Estudio", "Writing: ${'$'}{intento.respuesta}")
            }
        """.trimIndent()
        val llamada = Registros.llamadasALog(codigo).single()
        assertEquals("respuesta", Registros.datoPersonal(llamada))
    }

    @Test
    fun detectaLlamadasEnVariasLineasYOtrosNombres() {
        val codigo = """
            Log.i(
                "Kiwi",
                textoDeKiwi,
            )
            Log.e("Cuentas", "falló", error)
            android.util.Log.w("Sesion", estudiante.nombre)
        """.trimIndent()
        val llamadas = Registros.llamadasALog(codigo)
        assertEquals(3, llamadas.size)
        assertEquals(listOf("texto", null, "nombre"), llamadas.map { Registros.datoPersonal(it) })
    }

    @Test
    fun detectaLaSalidaPorConsola() {
        val codigo = """
            println(usuario)
            System.err.println("x")
            e.printStackTrace()
            val impresora = imprimir(x) // no cuenta
        """.trimIndent()
        assertEquals(3, Registros.salidaPorConsola(codigo).size)
    }

    private companion object {
        /** El aviso de la consola del WebView de fórmulas, solo en depuración y sin su contenido. */
        val PERMITIDOS = setOf("app/src/main/java/com/preparate/app/ui/Formula.kt")
    }
}

/** Busca registros en el texto de un archivo de Kotlin. */
internal object Registros {
    private val LOG = Regex("""\bLog\.(v|d|i|w|e|wtf)\s*\(""")
    private val CONSOLA = Regex("""(?<![\w.])(println|print)\s*\(|\.printStackTrace\s*\(|\bSystem\.(out|err)\b""")

    /** Nombres que suelen llevar datos de un estudiante o de su conversación con Kiwi. */
    private val DATO_PERSONAL = Regex(
        """(?i)(nombre|apellido|usuario|correo|email|clave|password|contrase|respuesta|texto|mensaje|""" +
            """writing|escritura|redaccion|conversacion|historial|kiwi|uid|token)""",
    )

    /** Cada llamada a `Log.x(...)`, completa aunque ocupe varias líneas. */
    fun llamadasALog(codigo: String): List<String> = LOG.findAll(codigo).map { m ->
        var nivel = 0
        var fin = m.range.last
        for (i in m.range.last until codigo.length) {
            when (codigo[i]) {
                '(' -> nivel++
                ')' -> nivel--
            }
            if (nivel == 0) {
                fin = i
                break
            }
        }
        codigo.substring(m.range.first, fin + 1).replace(Regex("""\s+"""), " ")
    }.toList()

    /**
     * El primer nombre sospechoso en los argumentos, sin contar la etiqueta (el primer argumento) ni el texto fijo
     * entre comillas: dentro de una cadena solo cuentan las variables que se insertan con el signo de dólar.
     */
    fun datoPersonal(llamada: String): String? {
        val argumentos = llamada.substringAfter('(').substringBeforeLast(')')
        val sinEtiqueta = argumentos.substringAfter(',', "")
        val codigoVariable = Regex("\"(?:[^\"\\\\]|\\\\.)*\"").replace(sinEtiqueta) { cadena ->
            Regex("""\$\{([^}]*)}|\$(\w+)""").findAll(cadena.value).joinToString(" ") { it.value }
        }
        return DATO_PERSONAL.find(codigoVariable)?.value?.lowercase()
    }

    fun salidaPorConsola(codigo: String): List<String> =
        codigo.lines().filter { CONSOLA.containsMatchIn(it.substringBefore("//")) }.map { it.trim() }
}
