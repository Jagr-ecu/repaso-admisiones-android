package com.preparate.tutor

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Prueba el cliente de LM Studio contra un servidor HTTP local de verdad (`com.sun.net.httpserver`, del JDK),
 * sin dependencias nuevas. Así se comprueba la regla 8: si LM Studio falla, Kiwi descansa y nada se rompe.
 */
class ClienteKiwiTest {
    private data class Peticion(val metodo: String, val ruta: String, val cuerpo: String)

    private lateinit var servidor: HttpServer
    private val peticiones = CopyOnWriteArrayList<Peticion>()

    // Lo que responde el falso LM Studio. Cada test cambia lo que necesite.
    @Volatile private var codigoModelos = 200

    @Volatile private var cuerpoModelos = """{"data": [{"id": "text-embedding-nomic"}, {"id": "qwen3-8b"}]}"""

    @Volatile private var codigoChat = 200

    @Volatile private var cuerpoChat = respuestaChat("¿Qué pasa con las unidades?")

    private var ahora = 1_000_000L
    private val reloj = { ahora }

    @Before
    fun encender() {
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/v1/models") { responder(it, codigoModelos, cuerpoModelos) }
        servidor.createContext("/v1/chat/completions") { responder(it, codigoChat, cuerpoChat) }
        servidor.start()
    }

    @After
    fun apagar() = servidor.stop(0)

    private fun responder(intercambio: HttpExchange, codigo: Int, cuerpo: String) {
        val recibido = intercambio.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
        peticiones += Peticion(intercambio.requestMethod, intercambio.requestURI.path, recibido)
        val bytes = cuerpo.toByteArray(Charsets.UTF_8)
        intercambio.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        intercambio.sendResponseHeaders(codigo, bytes.size.toLong())
        intercambio.responseBody.use { it.write(bytes) }
    }

    private fun direccion() = "127.0.0.1:${servidor.address.port}"

    private fun cliente(direccion: String = direccion()) = ClienteKiwi(direccion, reloj)

    private fun chats() = peticiones.filter { it.ruta == "/v1/chat/completions" }

    private val pregunta = listOf(Mensaje("system", "Eres Kiwi."), Mensaje("user", "No entiendo"))

    // --- Dirección -----------------------------------------------------------------------------

    @Test
    fun normalizaLaDireccionComoLaWeb() {
        assertEquals("http://192.168.1.50:1234", ClienteKiwi.normalizar("192.168.1.50:1234"))
        assertEquals("http://192.168.1.50:1234", ClienteKiwi.normalizar(" http://192.168.1.50:1234/v1/ "))
        assertEquals("https://pc.local:1234", ClienteKiwi.normalizar("https://pc.local:1234/"))
        assertEquals("", ClienteKiwi.normalizar("   "))
    }

    @Test
    fun reconoceLosModelosDeEmbeddings() {
        assertTrue(ClienteKiwi.esDeEmbeddings("text-embedding-nomic-embed-text-v1.5"))
        assertTrue(ClienteKiwi.esDeEmbeddings("BGE-m3"))
        assertTrue(ClienteKiwi.esDeEmbeddings("multilingual-e5-large"))
        assertFalse(ClienteKiwi.esDeEmbeddings("qwen3-8b"))
    }

    @Test
    fun sinDireccionNoLlamaANadie() = runBlocking<Unit> {
        val kiwi = cliente("")
        assertFalse(kiwi.configurado)
        assertNull(kiwi.modelos())
        assertThrows(TutorNoDisponible::class.java) { runBlocking { kiwi.chat(pregunta) } }
        assertTrue(peticiones.isEmpty())
    }

    // --- Solo la red local (ADR-0018) ----------------------------------------------------------

    @Test
    fun aceptaLasDireccionesDeLaRedDeCasa() {
        val locales = listOf(
            "192.168.1.50:1234", "10.0.0.5:1234", "172.16.0.1:1234", "172.31.255.254:1234", "169.254.3.4:1234",
            "127.0.0.1:1234", "localhost:1234", "http://pc-casa.local:1234", "pc.lan:1234", "pc.home.arpa:1234",
            "pc-casa:1234", "http://[fe80::1]:1234", "http://[fd12:3456::1]:1234", "http://[::1]:1234",
        )
        for (d in locales) assertTrue(d, ClienteKiwi(d).deRedLocal)
    }

    @Test
    fun rechazaLasDireccionesDeInternet() {
        val deInternet = listOf(
            "8.8.8.8:1234", "172.32.0.1:1234", "172.15.0.1:1234", "192.169.1.1:1234", "11.0.0.1:1234",
            "999.168.1.1:1234", "mi-servidor.com:1234", "https://api.ejemplo.org", "pc.local.ejemplo.com:1234",
            "localhost.ejemplo.com:1234", "http://[2001:db8::1]:1234", "",
        )
        for (d in deInternet) assertFalse(d, ClienteKiwi(d).deRedLocal)
    }

    @Test
    fun conUnaDireccionDeInternetNoLlamaANadie() = runBlocking<Unit> {
        val kiwi = cliente("8.8.8.8:1234")
        assertFalse(kiwi.configurado)
        assertNull(kiwi.modelos())
        assertThrows(TutorNoDisponible::class.java) { runBlocking { kiwi.chat(pregunta) } }
    }

    @Test
    fun noSigueRedireccionesFueraDeLaRedLocal() = runBlocking<Unit> {
        servidor.removeContext("/v1/models")
        servidor.createContext("/v1/models") {
            it.responseHeaders.add("Location", "http://8.8.8.8:1234/v1/models")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        assertNull(cliente().modelos())
    }

    // --- Modelos -------------------------------------------------------------------------------

    @Test
    fun listaLosModelosDeLmStudio() = runBlocking<Unit> {
        assertEquals(listOf("text-embedding-nomic", "qwen3-8b"), cliente().modelos())
        assertEquals(Peticion("GET", "/v1/models", ""), peticiones.single())
    }

    @Test
    fun siLosModelosFallanDevuelveNull() = runBlocking<Unit> {
        codigoModelos = 500
        assertNull(cliente().modelos())
        codigoModelos = 200
        cuerpoModelos = "esto no es JSON"
        assertNull(cliente().modelos())
    }

    // --- Chat ----------------------------------------------------------------------------------

    @Test
    fun enviaLaConversacionAlPrimerModeloQueNoEsDeEmbeddings() = runBlocking<Unit> {
        val texto = cliente().chat(pregunta, maxTokens = 200)

        assertEquals("¿Qué pasa con las unidades?", texto)
        val cuerpo = Json.parseToJsonElement(chats().single().cuerpo).jsonObject
        assertEquals("POST", chats().single().metodo)
        assertEquals("qwen3-8b", cuerpo.getValue("model").jsonPrimitive.content)
        assertEquals(200, cuerpo.getValue("max_tokens").jsonPrimitive.int)
        val mensajes = cuerpo.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), mensajes.map { it.getValue("role").jsonPrimitive.content })
        assertEquals("No entiendo", mensajes[1].getValue("content").jsonPrimitive.content)
        assertFalse("sin esquema no se pide JSON", "response_format" in cuerpo)
    }

    @Test
    fun conEsquemaPideUnaRespuestaJsonEstricta() = runBlocking<Unit> {
        val esquema: JsonObject = buildJsonObject { put("type", "object") }
        cliente().chat(pregunta, esquema = esquema)

        val formato = Json.parseToJsonElement(chats().single().cuerpo).jsonObject.getValue("response_format").jsonObject
        assertEquals("json_schema", formato.getValue("type").jsonPrimitive.content)
        val definicion = formato.getValue("json_schema").jsonObject
        assertEquals("true", definicion.getValue("strict").jsonPrimitive.content)
        assertEquals(esquema, definicion.getValue("schema"))
    }

    @Test
    fun quitaElRazonamientoDelModelo() = runBlocking<Unit> {
        cuerpoChat = respuestaChat("<think>la respuesta es 12</think>  Piensa en cuántos grupos hay.")
        assertEquals("Piensa en cuántos grupos hay.", cliente().chat(pregunta))

        cuerpoChat = respuestaChat("<think>sin cerrar, la respuesta es 12")
        assertThrows(TutorNoDisponible::class.java) { runBlocking { cliente().chat(pregunta) } }
    }

    @Test
    fun siSoloHayModelosDeEmbeddingsNoLlama() = runBlocking<Unit> {
        cuerpoModelos = """{"data": [{"id": "text-embedding-nomic"}]}"""
        val error = assertThrows(TutorNoDisponible::class.java) { runBlocking { cliente().chat(pregunta) } }
        assertTrue(error.message!!.contains("modelo adecuado"))
        assertTrue(chats().isEmpty())
    }

    @Test
    fun unErrorDeLmStudioEnElChatEsTutorNoDisponible() = runBlocking<Unit> {
        codigoChat = 500
        val error = assertThrows(TutorNoDisponible::class.java) { runBlocking { cliente().chat(pregunta) } }
        assertTrue(error.message!!.contains("500"))
    }

    @Test
    fun unaRespuestaQueNoSeEntiendeOVaciaEsTutorNoDisponible() = runBlocking<Unit> {
        for (cuerpo in listOf("no es JSON", """{"choices": []}""", respuestaChat("   "))) {
            cuerpoChat = cuerpo
            assertThrows(cuerpo, TutorNoDisponible::class.java) { runBlocking { cliente().chat(pregunta) } }
        }
    }

    @Test
    fun saludarPideUnaRespuestaCorta() = runBlocking<Unit> {
        cuerpoChat = respuestaChat("¡Hola! Sí, te escucho.")
        assertEquals("¡Hola! Sí, te escucho.", cliente().saludar())
        val cuerpo = Json.parseToJsonElement(chats().single().cuerpo).jsonObject
        assertEquals(120, cuerpo.getValue("max_tokens").jsonPrimitive.int)
    }

    // --- LM Studio apagado (regla 8) -----------------------------------------------------------

    @Test
    fun conLmStudioApagadoKiwiDescansaYNoLoReintentaDuranteCincoMinutos() = runBlocking<Unit> {
        val kiwi = cliente("127.0.0.1:${puertoLibre()}")

        assertNull(kiwi.modelos())
        val error = assertThrows(TutorNoDisponible::class.java) { runBlocking { kiwi.chat(pregunta) } }
        assertTrue("no debe esperar otra comprobación", error.message!!.contains("hace un momento"))

        // Pasados los 5 minutos vuelve a comprobar (y sigue apagado).
        ahora += ClienteKiwi.RECUERDA_APAGADO_MS
        val otra = assertThrows(TutorNoDisponible::class.java) { runBlocking { kiwi.chat(pregunta) } }
        assertTrue(otra.message!!.contains("no está encendido"))
    }

    @Test
    fun cuandoLmStudioVuelveSeOlvidaDelApagado() = runBlocking<Unit> {
        codigoModelos = 503
        val kiwi = cliente()
        assertNull(kiwi.modelos())
        assertThrows(TutorNoDisponible::class.java) { runBlocking { kiwi.chat(pregunta) } }

        // modelos() siempre comprueba: si responde, el chat ya no espera los 5 minutos.
        codigoModelos = 200
        assertEquals(2, kiwi.modelos()!!.size)
        assertEquals("¿Qué pasa con las unidades?", kiwi.chat(pregunta))
    }

    private companion object {
        fun respuestaChat(contenido: String): String = buildJsonObject {
            put("choices", Json.parseToJsonElement("""[{"message": {"content": ${Json.encodeToString(contenido)}}}]"""))
        }.toString()

        /** Un puerto donde no escucha nadie: la conexión se rechaza al momento. */
        fun puertoLibre(): Int = ServerSocket(0).use { it.localPort }
    }
}
