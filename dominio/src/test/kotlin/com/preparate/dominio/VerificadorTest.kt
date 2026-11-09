package com.preparate.dominio

import org.json.JSONArray
import org.json.JSONObject
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

    /** Casos generados por Python con herramientas/exportar_prueba.py: Kotlin debe corregir igual. */
    @Test
    fun coincideConLosCasosDeReferencia() {
        val casos = JSONArray(File(javaClass.getResource("/referencia-verificador.json")!!.toURI()).readText())
        val variantes = File("../app/src/main/assets/contenido/variantes").listFiles()!!.associate { archivo ->
            val json = JSONObject(archivo.readText())
            val plantilla = Plantilla(
                id = json.getString("id"),
                tipo = TipoEjercicio.desde(json.getString("tipo")),
                verificador = TipoVerificador.desde(json.getString("verificador")),
                formaIrreducible = json.getBoolean("forma_irreducible"),
                ignorarTildes = json.getBoolean("ignorar_tildes"),
            )
            val lista = json.getJSONArray("variantes")
            plantilla.id to (0 until lista.length()).associate { i ->
                val v = lista.getJSONObject(i)
                val e = v.getJSONArray("errores")
                val errores = (0 until e.length()).map {
                    ErrorEsperado(e.getJSONObject(it).getString("valor"), e.getJSONObject(it).getString("explicacion"))
                }
                v.getInt("semilla") to Variante(
                    plantilla, v.getInt("semilla"), v.getString("enunciado"), v.getString("respuesta"), errores = errores,
                )
            }
        }
        assertTrue(casos.length() > 100)
        for (i in 0 until casos.length()) {
            val caso = casos.getJSONObject(i)
            val variante = variantes.getValue(caso.getString("plantilla")).getValue(caso.getInt("semilla"))
            val entrada = caso.getString("entrada")
            val r = Verificador.verificar(variante, entrada)
            val mensaje = "${variante.plantilla.id} semilla ${variante.semilla}, entrada '$entrada'"
            assertEquals(mensaje, caso.getString("estado"), r.estado.valor)
            val esperado = if (caso.isNull("error_comun")) null else caso.getString("error_comun")
            assertEquals(mensaje, esperado, r.errorComun)
        }
    }
}
