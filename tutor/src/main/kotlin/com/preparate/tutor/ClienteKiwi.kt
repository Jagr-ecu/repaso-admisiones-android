package com.preparate.tutor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de LM Studio en el PC de la casa (API compatible con OpenAI).
 *
 * Fase 1: solo comprueba la conexión y pide un saludo. Todavía NO se usa en los
 * ejercicios: antes hay que portar la guardia (regla 2) en la fase 6.
 */
class ClienteKiwi(direccion: String) {
    val base: String = normalizar(direccion)

    /** Modelos que ofrece LM Studio, o null si no responde en 2 s. */
    suspend fun modelos(): List<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val datos = JSONObject(pedir("GET", "/v1/models", null, TIMEOUT_COMPROBACION_MS))
            val lista = datos.getJSONArray("data")
            (0 until lista.length()).map { lista.getJSONObject(it).getString("id") }
        }.getOrNull()
    }

    /** Un saludo corto de Kiwi. Lanza una excepción si LM Studio no responde. */
    suspend fun saludar(modelo: String): String = withContext(Dispatchers.IO) {
        val cuerpo = JSONObject()
            .put("model", modelo)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SISTEMA_SALUDO))
                    .put(JSONObject().put("role", "user").put("content", "Hola, Kiwi. ¿Me escuchas?")),
            )
            .put("max_tokens", 120)
            // Mismo muestreo que la web (app/tutor/cliente.py): Qwen sin razonamiento.
            .put("temperature", 0.7)
            .put("top_p", 0.8)
            .put("presence_penalty", 1.5)
            .put("top_k", 20)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
        val datos = JSONObject(pedir("POST", "/v1/chat/completions", cuerpo.toString(), TIMEOUT_RESPUESTA_MS))
        val texto = datos.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        PENSAMIENTO.replace(texto, "").trim()
    }

    private fun pedir(metodo: String, ruta: String, cuerpo: String?, timeoutMs: Int): String {
        val conexion = URL(base + ruta).openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = metodo
            conexion.connectTimeout = minOf(timeoutMs, TIMEOUT_COMPROBACION_MS)
            conexion.readTimeout = timeoutMs
            if (cuerpo != null) {
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = conexion.responseCode
            check(codigo in 200..299) { "LM Studio respondió $codigo" }
            return conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }

    companion object {
        const val TIMEOUT_COMPROBACION_MS = 2_000
        const val TIMEOUT_RESPUESTA_MS = 120_000
        private val PENSAMIENTO = Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL)
        private const val SISTEMA_SALUDO =
            "Eres Kiwi, un tutor amable para niños de 11 años. Responde en español, " +
                "con una o dos frases cortas y sencillas."

        /** "192.168.1.50:1234", "http://192.168.1.50:1234/v1/" → "http://192.168.1.50:1234". */
        fun normalizar(direccion: String): String {
            var d = direccion.trim().trimEnd('/')
            if (!d.startsWith("http://") && !d.startsWith("https://")) d = "http://$d"
            return d.removeSuffix("/v1").trimEnd('/')
        }
    }
}
