package com.preparate.datos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.preparate.dominio.ArchivoPlantilla
import com.preparate.dominio.Guardia
import com.preparate.dominio.Variante
import com.preparate.dominio.VarianteJson
import com.preparate.dominio.Verificador
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los casos de referencia de Python, corridos en el dispositivo. Android usa otro motor de
 * expresiones regulares (ICU) que la JVM de los tests de `:dominio`, y una diferencia entre los
 * dos rompería la corrección solo en la tablet.
 */
@RunWith(AndroidJUnit4::class)
class VerificadorEnDispositivoTest {
    private val json = Json { ignoreUnknownKeys = false }

    /** Cada caso de una parte de la referencia, con su variante. */
    private fun casos(parte: String): Sequence<Pair<Variante, JsonObject>> = sequence {
        val texto = InstrumentationRegistry.getInstrumentation().context.assets
            .open("referencia/referencia-$parte.json").bufferedReader().use { it.readText() }
        for (p in json.parseToJsonElement(texto).jsonObject.getValue("plantillas").jsonArray) {
            val datos = p.jsonObject
            val plantilla = json.decodeFromJsonElement<ArchivoPlantilla>(
                JsonObject(datos + ("variantes" to JsonArray(emptyList()))),
            ).plantilla()
            for (v in datos.getValue("variantes").jsonArray) {
                val variante = json.decodeFromJsonElement<VarianteJson>(
                    JsonObject(v.jsonObject - "casos"),
                ).aDominio(plantilla)
                for (c in v.jsonObject.getValue("casos").jsonArray) yield(variante to c.jsonObject)
            }
        }
    }

    /** La guardia de Kiwi (regla 2) también usa expresiones regulares: se prueba con el motor de Android. */
    @Test
    fun laGuardiaDecideIgualQuePythonConElMotorDeAndroid() {
        var total = 0
        for ((variante, c) in casos("guardia")) {
            val texto = c.getValue("texto").jsonPrimitive.content
            val aLaVista = c.getValue("a_la_vista").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(
                "${variante.plantilla.id} semilla ${variante.semilla}: «$texto»",
                c.getValue("revela").jsonPrimitive.boolean,
                Guardia.revelaRespuesta(variante, texto, aLaVista),
            )
            total++
        }
        assertTrue(total > 7_000)
    }

    @Test
    fun corrigeIgualQuePythonConElMotorDeAndroid() {
        var total = 0
        for ((variante, c) in casos("verificador")) {
            val e = c.getValue("entrada")
            val r = if (e is JsonArray) {
                Verificador.verificar(variante, e.map { it.jsonPrimitive.content })
            } else {
                Verificador.verificar(variante, e.jsonPrimitive.content)
            }
            val mensaje = "${variante.plantilla.id} semilla ${variante.semilla}, entrada $e"
            fun texto(clave: String) = c.getValue(clave).let { if (it is JsonNull) null else it.jsonPrimitive.content }
            assertEquals(mensaje, texto("estado"), r.estado.valor)
            assertEquals(mensaje, texto("error_comun"), r.errorComun)
            assertEquals(mensaje, texto("grupo_mal")?.toInt(), r.grupoMal)
            total++
        }
        assertTrue(total > 19_000)
    }
}
