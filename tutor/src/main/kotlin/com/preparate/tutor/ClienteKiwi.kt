package com.preparate.tutor

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Un mensaje para el modelo: `rol` es "system", "user" o "assistant". */
data class Mensaje(val rol: String, val texto: String)

/** LM Studio no responde, no tiene un modelo o devolvió algo inservible. La app sigue sin Kiwi. */
class TutorNoDisponible(mensaje: String) : Exception(mensaje)

/** El modelo que usa Kiwi. En los tests se reemplaza por uno falso. */
interface Modelo {
    /** Los modelos que ofrece, o null si no responde a tiempo. */
    suspend fun modelos(): List<String>?

    /** La respuesta del modelo, sin el razonamiento. Lanza [TutorNoDisponible]. */
    suspend fun chat(mensajes: List<Mensaje>, esquema: JsonObject? = null, maxTokens: Int = 300): String
}

/**
 * Cliente de LM Studio en el PC de la casa (API compatible con OpenAI). Puerto de app/tutor/cliente.py.
 *
 * Antes de cada llamada se comprueba `GET /v1/models` con 2 s de espera: si LM Studio está apagado,
 * Kiwi "descansa" y la app sigue sin él. La llamada en sí espera hasta 120 s, porque LM Studio
 * puede estar cargando el modelo. Tras una comprobación fallida, las llamadas no la repiten durante
 * 5 minutos (cada pista tardaría 2 s más); [modelos] sí comprueba siempre.
 */
class ClienteKiwi(direccion: String, private val reloj: () -> Long = System::currentTimeMillis) : Modelo {
    val base: String = normalizar(direccion)

    /** La dirección apunta a la red de la casa. Si no, Kiwi no se usa: el chat no sale sin cifrar (ADR-0018). */
    val deRedLocal: Boolean = esDeRedLocal(base)
    val configurado: Boolean get() = base.isNotEmpty() && deRedLocal

    @Volatile private var apagadoHasta = 0L

    override suspend fun modelos(): List<String>? {
        if (!configurado) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val datos = json.parseToJsonElement(
                    pedir("GET", "/v1/models", null, TIMEOUT_COMPROBACION_MS),
                ).jsonObject
                datos.getValue("data").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            }.getOrNull()
        }.also { apagadoHasta = if (it == null) reloj() + RECUERDA_APAGADO_MS else 0L }
    }

    override suspend fun chat(mensajes: List<Mensaje>, esquema: JsonObject?, maxTokens: Int): String {
        if (reloj() < apagadoHasta) throw TutorNoDisponible("LM Studio no estaba encendido hace un momento")
        val lista = modelos() ?: throw TutorNoDisponible("LM Studio no está encendido")
        val modelo =
            lista.firstOrNull { !esDeEmbeddings(it) }
                ?: throw TutorNoDisponible("LM Studio no tiene un modelo adecuado")
        val cuerpo = buildJsonObject {
            put("model", modelo)
            putJsonArray("messages") {
                for (m in mensajes) {
                    addJsonObject {
                        put("role", m.rol)
                        put("content", m.texto)
                    }
                }
            }
            put("max_tokens", maxTokens)
            // Qwen en modo instruct (sin razonamiento), como en la web.
            put("temperature", 0.7)
            put("top_p", 0.8)
            put("presence_penalty", 1.5)
            put("top_k", 20)
            putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
            if (esquema != null) {
                putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", "respuesta")
                        put("strict", true)
                        put("schema", esquema)
                    }
                }
            }
        }
        val respuesta = withContext(Dispatchers.IO) {
            try {
                pedir("POST", "/v1/chat/completions", cuerpo.toString(), TIMEOUT_RESPUESTA_MS)
            } catch (e: Exception) {
                throw TutorNoDisponible("LM Studio no respondió: ${e.message}")
            }
        }
        val texto = runCatching {
            json.parseToJsonElement(respuesta).jsonObject.getValue("choices").jsonArray[0].jsonObject
                .getValue("message").jsonObject["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }.getOrElse { throw TutorNoDisponible("LM Studio devolvió algo que no se entiende") }
        return PENSAMIENTO.replace(texto, "").trim().ifEmpty {
            throw TutorNoDisponible("el modelo devolvió una respuesta vacía")
        }
    }

    /** Un saludo corto, para probar a Kiwi desde los ajustes. */
    suspend fun saludar(): String = chat(
        listOf(Mensaje("system", SISTEMA_SALUDO), Mensaje("user", "Hola, Kiwi. ¿Me escuchas?")),
        maxTokens = 120,
    )

    private fun pedir(metodo: String, ruta: String, cuerpo: String?, timeoutMs: Int): String {
        val conexion = URL(base + ruta).openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = metodo
            // Una redirección podría llevar el chat fuera de la red local.
            conexion.instanceFollowRedirects = false
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
        const val RECUERDA_APAGADO_MS = 300_000L
        private val json = Json { ignoreUnknownKeys = true }
        private val PENSAMIENTO = Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL)
        private const val SISTEMA_SALUDO =
            "Eres Kiwi, un tutor amable para niños de 11 años. Responde en español, " +
                "con una o dos frases cortas y sencillas."

        fun esDeEmbeddings(id: String): Boolean = listOf("embed", "bge", "e5-", "nomic").any { it in id.lowercase() }

        /** "192.168.1.50:1234", "http://192.168.1.50:1234/v1/" → "http://192.168.1.50:1234". Vacío si no hay. */
        fun normalizar(direccion: String): String {
            var d = direccion.trim().trimEnd('/')
            if (d.isEmpty()) return ""
            if (!d.startsWith("http://") && !d.startsWith("https://")) d = "http://$d"
            return d.removeSuffix("/v1").trimEnd('/')
        }

        /**
         * La dirección (ya normalizada) es de la red local: IP privada (10/8, 172.16/12, 192.168/16), de enlace
         * local (169.254/16, fe80::/10), local de IPv6 (fc00::/7), el propio dispositivo (127/8, ::1, localhost),
         * un nombre `.local`, `.lan` o `.home.arpa`, o un nombre sin punto. No consulta el DNS: solo mira el texto.
         */
        fun esDeRedLocal(base: String): Boolean {
            val host = runCatching { URI(base).host }.getOrNull()?.lowercase()?.removeSurrounding("[", "]")
            return when {
                host.isNullOrEmpty() -> false
                host == "localhost" -> true
                ':' in host -> esIpv6Local(host)
                Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host) -> esIpv4Local(host)
                SUFIJOS_LOCALES.any { host.endsWith(it) } -> true
                else -> '.' !in host
            }
        }

        private val SUFIJOS_LOCALES = listOf(".local", ".lan", ".home.arpa")

        /** Privadas (RFC 1918), el propio dispositivo y enlace local. */
        private val REDES_IPV4_LOCALES =
            listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16")

        /** Locales únicas (fc00::/7) y enlace local (fe80::/10). El propio dispositivo (::1) se compara aparte. */
        private val PREFIJOS_IPV6_LOCALES = listOf("fc", "fd", "fe8", "fe9", "fea", "feb")

        private const val BITS_POR_OCTETO = 8
        private const val MAX_OCTETO = 255
        private const val BITS_IPV4 = 32

        private fun ipv4ANumero(ip: String): Long? {
            val octetos = ip.split('.').map { it.toInt() }
            if (octetos.any { it > MAX_OCTETO }) return null
            return octetos.fold(0L) { acc, o -> (acc shl BITS_POR_OCTETO) or o.toLong() }
        }

        private fun esIpv4Local(ip: String): Boolean {
            val numero = ipv4ANumero(ip) ?: return false
            return REDES_IPV4_LOCALES.any { red ->
                val (inicio, bits) = red.split('/')
                val desplazamiento = BITS_IPV4 - bits.toInt()
                (numero shr desplazamiento) == (ipv4ANumero(inicio)!! shr desplazamiento)
            }
        }

        private fun esIpv6Local(ip: String): Boolean = ip == "::1" || PREFIJOS_IPV6_LOCALES.any { ip.startsWith(it) }
    }
}
