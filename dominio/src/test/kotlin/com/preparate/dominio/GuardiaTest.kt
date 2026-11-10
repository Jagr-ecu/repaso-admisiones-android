package com.preparate.dominio

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardiaTest {
    private val numerica = Plantilla("p", TipoEjercicio.ABIERTA, TipoVerificador.SYMPY)
    private val texto = Plantilla("t", TipoEjercicio.ABIERTA, TipoVerificador.TEXTO)

    @Test
    fun detectaLaRespuestaEnCualquierForma() {
        val v = Variante(numerica, 1, "Simplifica 6/27", "2/9")
        assertTrue(Guardia.revelaRespuesta(v, "La respuesta es 2/9."))
        assertTrue(Guardia.revelaRespuesta(v, "Prueba con \\frac{4}{18}"))
        assertTrue(Guardia.revelaRespuesta(v, "Son 4 / 18"))
        // Lo que ya está en el enunciado no la revela, aunque valga lo mismo.
        assertFalse(Guardia.revelaRespuesta(v, "Mira el 6/27 con calma."))
        assertFalse(Guardia.revelaRespuesta(v, "¿Qué número divide a 6 y a 27?"))
    }

    @Test
    fun repetirLaRespuestaDelEstudianteSoloCuentaSiEsLaCorrecta() {
        val v = Variante(numerica, 1, "Calcula 3 + 4", "7")
        assertFalse(Guardia.revelaRespuesta(v, "Escribiste 8. ¿Contaste bien?", aLaVista = listOf("8")))
        assertTrue(Guardia.revelaRespuesta(v, "Escribiste 7.", aLaVista = listOf("7")))
    }

    @Test
    fun cifrasDeOtrosAlfabetosTambienCuentan() {
        val v = Variante(numerica, 1, "Calcula 7 × 2", "14")
        assertTrue(Guardia.revelaRespuesta(v, "Es ١٤"))
    }

    @Test
    fun enTextoBuscaLaPalabraEntera() {
        val v = Variante(texto, 1, "¿Cuál es el sujeto?", "el perro")
        assertTrue(Guardia.revelaRespuesta(v, "Es «El Perro»."))
        assertFalse(Guardia.revelaRespuesta(v, "Piensa en el perrito."))
        assertFalse(Guardia.revelaRespuesta(Variante(texto, 1, "Escribe", null), "cualquier cosa"))
    }

    /** Los casos que genera Python con `exportar-referencia`: la guardia de Kotlin decide igual. */
    @Test
    fun coincideConLosCasosDeReferencia() {
        var total = 0
        for ((variante, caso) in Referencia.casos("guardia")) {
            val texto = caso.getValue("texto").jsonPrimitive.content
            val aLaVista = caso.getValue("a_la_vista").jsonArray.map { it.jsonPrimitive.content }
            val esperado = caso.getValue("revela").jsonPrimitive.boolean
            assertEquals(
                "${variante.plantilla.id} semilla ${variante.semilla}: «$texto» con $aLaVista",
                esperado, Guardia.revelaRespuesta(variante, texto, aLaVista),
            )
            total++
        }
        assertEquals(Referencia.leer("guardia").getValue("total").jsonPrimitive.content.toInt(), total)
    }
}
