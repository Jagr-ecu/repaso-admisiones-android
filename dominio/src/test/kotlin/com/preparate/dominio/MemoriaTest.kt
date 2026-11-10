package com.preparate.dominio

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime

class MemoriaTest {
    private val inicio = Instant.parse("2026-03-01T10:00:00Z")

    @Test
    fun unConceptoNuevoSeRepasaProntoYLuegoMasLejos() {
        val t1 = Memoria.repasar(null, Calificacion.BIEN, inicio)
        assertEquals(EstadoTarjeta.APRENDIENDO, t1.estado)
        assertEquals(inicio.plusSeconds(600), t1.vence)
        val t2 = Memoria.repasar(t1, Calificacion.BIEN, t1.vence)
        assertEquals(EstadoTarjeta.REPASO, t2.estado)
        assertTrue(t2.vence > t2.ultimoRepaso!!.plusSeconds(86_400))
        val t3 = Memoria.repasar(t2, Calificacion.OTRA_VEZ, t2.vence)
        assertEquals(EstadoTarjeta.REAPRENDIENDO, t3.estado)
    }

    /** Secuencias de repasos calculadas con py-fsrs: estado, paso y fecha iguales; los números, casi iguales. */
    @Test
    fun coincideConPyFsrs() {
        var pasos = 0
        for (secuencia in Referencia.leer("memoria").getValue("secuencias").jsonArray) {
            var tarjeta: Tarjeta? = null
            for (p in secuencia.jsonArray.map { it.jsonObject }) {
                val fecha = OffsetDateTime.parse(p.texto("fecha")!!).toInstant()
                val calificacion = Calificacion.entries.first { it.valor == p.getValue("calificacion").jsonPrimitive.int }
                tarjeta = Memoria.repasar(tarjeta, calificacion, fecha)
                val mensaje = "$secuencia paso $pasos"
                assertEquals(mensaje, p.getValue("estado").jsonPrimitive.int, tarjeta.estado.valor)
                assertEquals(mensaje, p.getValue("paso").let { if (it is JsonNull) null else it.jsonPrimitive.int }, tarjeta.paso)
                assertEquals(mensaje, p.getValue("estabilidad").jsonPrimitive.double, tarjeta.estabilidad!!, 1e-9)
                assertEquals(mensaje, p.getValue("dificultad").jsonPrimitive.double, tarjeta.dificultad!!, 1e-9)
                assertEquals(mensaje, OffsetDateTime.parse(p.texto("vence")!!).toInstant(), tarjeta.vence)
                pasos++
            }
        }
        assertTrue(pasos > 1000)
    }
}
