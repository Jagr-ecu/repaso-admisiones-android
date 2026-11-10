package com.preparate.dominio

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.RoundingMode

/** Un criterio de la rúbrica ya calificado. */
@Serializable
data class CriterioCalificado(val criterio: String, val puntos: Double, val maximo: Double, val comentario: String)

/**
 * La calificación de un Writing: nota sobre 10 y detalle por criterio. Se guarda en el intento
 * (es el trabajo calificado, no una conversación) y sube a la nube con él.
 */
@Serializable
data class Revision(val puntaje: Double, val criterios: List<CriterioCalificado>, val comentario: String) {
    fun aJson(): String = formato.encodeToString(serializer(), this)

    companion object {
        private val formato = Json { ignoreUnknownKeys = true }

        fun desdeJson(texto: String): Revision = formato.decodeFromString(serializer(), texto)
    }
}

/** El modelo no devolvió una calificación que se pueda usar. El Writing sigue pendiente. */
class CalificacionInvalida(mensaje: String) : Exception(mensaje)

/**
 * Lee la calificación que da Kiwi con la rúbrica. Puerto de `calificar_escritura` en
 * app/tutor/servicio.py: el modelo pone los puntos de cada criterio y el código los recorta al
 * máximo, los suma y calcula la nota. La IA nunca decide la nota final por sí sola.
 */
object Escritura {
    /** La rúbrica para el prompt: "- Ideas (máximo 4 puntos): ...". */
    fun rubricaEnTexto(rubrica: List<CriterioRubrica>): String =
        rubrica.joinToString("\n") { "- ${it.criterio} (máximo ${numero(it.puntos)} puntos): ${it.descripcion}" }

    /** `{x:g}` de Python para los puntos de la rúbrica: 4.0 → "4", 2.5 → "2.5". */
    private fun numero(x: Double): String = if (x == Math.floor(x) && !x.isInfinite()) x.toLong().toString() else x.toString()

    fun revision(rubrica: List<CriterioRubrica>, respuestaDelModelo: String): Revision {
        require(rubrica.isNotEmpty()) { "un Writing necesita su rúbrica" }
        val datos = leerJson(respuestaDelModelo)
        val dados = runCatching { datos["criterios"]?.jsonArray.orEmpty() }.getOrDefault(emptyList())
            .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .associateBy { (it["criterio"] as? JsonPrimitive)?.contentOrNull }
        val criterios = rubrica.map { c ->
            val d = dados[c.criterio]
            val p = d?.get("puntos") as? JsonPrimitive
            val puntos = p?.takeIf { !it.isString }?.doubleOrNull
                ?: throw CalificacionInvalida("la calificación no trae el criterio '${c.criterio}'")
            CriterioCalificado(c.criterio, puntos.coerceIn(0.0, c.puntos), c.puntos, texto(d["comentario"]))
        }
        val maximo = criterios.sumOf { it.maximo }
        val puntaje = BigDecimal(Reglas.NOTA_MAXIMA.toDouble() * criterios.sumOf { it.puntos } / maximo)
            .setScale(1, RoundingMode.HALF_EVEN).toDouble()
        return Revision(puntaje, criterios, texto(datos["comentario"]))
    }

    private fun texto(e: kotlinx.serialization.json.JsonElement?): String = when (e) {
        null -> ""
        is JsonPrimitive -> e.contentOrNull.orEmpty()
        else -> e.toString()
    }.trim()

    /** El JSON de una respuesta con esquema; tolera un bloque ``` alrededor. */
    fun leerJson(texto: String): JsonObject {
        val limpio = texto.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
        val elemento = try {
            Json.parseToJsonElement(limpio)
        } catch (e: SerializationException) {
            throw CalificacionInvalida("el modelo no devolvió JSON válido: ${e.message}")
        }
        return elemento as? JsonObject ?: throw CalificacionInvalida("el modelo no devolvió un objeto JSON")
    }
}
