package com.preparate.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerificadorTest {
    private val numerica = Plantilla("p", TipoEjercicio.ABIERTA, TipoVerificador.SYMPY, formaIrreducible = true)

    @Test
    fun leeNumerosComoPython() {
        assertEquals(Racional.de(1, 2), Verificador.leerNumero("2/4"))
        assertEquals(Racional.de(1, 2), Verificador.leerNumero("0,5"))
        assertEquals(Racional.de(1, 2), Verificador.leerNumero(" 0.5 "))
        assertEquals(Racional.de(3, 2), Verificador.leerNumero("1 1/2"))
        assertEquals(Racional.de(-3, 4), Verificador.leerNumero("−3/4"))
        assertEquals(null, Verificador.leerNumero("3/0"))
        assertEquals(null, Verificador.leerNumero("tres"))
    }

    @Test
    fun exigeLaFormaIrreducible() {
        val v = Variante(numerica, 1, "", "2/3")
        assertEquals(EstadoRespuesta.CORRECTA, Verificador.verificar(v, "2/3").estado)
        assertEquals(EstadoRespuesta.FORMA_INCORRECTA, Verificador.verificar(v, "4/6").estado)
        assertFalse(Verificador.irreducible("0,5"))
        assertTrue(Verificador.irreducible("7"))
    }

    @Test
    fun normalizaTextoComoPython() {
        assertEquals("ca-mi-sa", Verificador.normalizar(" Ca - mi - sa. "))
        assertEquals("cancion", Verificador.normalizar("¿Canción?", ignorarTildes = true))
        assertEquals("niño", Verificador.normalizar("Niño", ignorarTildes = true))
        assertEquals("i'm", Verificador.normalizar("I’m"))
    }

    /**
     * Casos que genera Python con `exportar-referencia` para todas las plantillas: Kotlin debe corregir igual.
     * Escritura y cuaderno se portan en la fase 3; hasta entonces sus casos se cuentan aparte.
     */
    @Test
    fun coincideConLosCasosDeReferencia() {
        var revisados = 0
        val sinPortar = mutableMapOf<TipoEjercicio, Int>()
        for ((variante, caso) in Referencia.casos("verificador")) {
            val tipo = variante.plantilla.tipo
            if (tipo == TipoEjercicio.ESCRITURA || tipo == TipoEjercicio.CUADERNO) {
                sinPortar.merge(tipo, 1, Int::plus)
                continue
            }
            // Python une con espacios la lista de palabras de `ordenar`.
            val entrada = when (val e = caso.getValue("entrada")) {
                is JsonArray -> e.joinToString(" ") { it.jsonPrimitive.content }
                else -> e.jsonPrimitive.content
            }
            val r = Verificador.verificar(variante, entrada)
            val mensaje = "${variante.plantilla.id} semilla ${variante.semilla}, entrada '$entrada'"
            assertEquals(mensaje, caso.texto("estado"), r.estado.valor)
            assertEquals(mensaje, caso.texto("error_comun"), r.errorComun)
            revisados++
        }
        assertTrue("solo $revisados casos", revisados > 10_000)
        assertTrue(sinPortar.isNotEmpty())
    }
}

/** Lee los archivos `referencia-<parte>.json` de los recursos de test. */
object Referencia {
    private val json = Json { ignoreUnknownKeys = false }

    fun leer(parte: String): JsonObject {
        val recurso = checkNotNull(javaClass.getResource("/referencia/referencia-$parte.json")) {
            "falta referencia-$parte.json: genéralo con exportar-referencia"
        }
        return json.parseToJsonElement(File(recurso.toURI()).readText(Charsets.UTF_8)).jsonObject
    }

    /** Cada caso con su variante, para las partes que tienen la forma plantillas → variantes → casos. */
    fun casos(parte: String): List<Pair<Variante, JsonObject>> =
        leer(parte).getValue("plantillas").jsonArray.flatMap { p ->
            val datos = p.jsonObject
            val variantes = datos.getValue("variantes").jsonArray
            val archivo = json.decodeFromJsonElement<ArchivoPlantilla>(
                JsonObject(datos + ("variantes" to JsonArray(emptyList()))),
            )
            val plantilla = archivo.plantilla()
            variantes.flatMap { v ->
                val sinCasos = JsonObject(v.jsonObject - "casos")
                val variante = json.decodeFromJsonElement<VarianteJson>(sinCasos).aDominio(plantilla)
                v.jsonObject.getValue("casos").jsonArray.map { variante to it.jsonObject }
            }
        }
}

fun JsonObject.texto(clave: String): String? =
    getValue(clave).let { if (it is JsonNull) null else it.jsonPrimitive.content }
