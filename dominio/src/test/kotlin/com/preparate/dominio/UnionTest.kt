package com.preparate.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Duration
import java.time.Instant

class UnionTest {
    private val t0 = Instant.parse("2026-11-01T15:00:00Z")
    private fun min(n: Long) = t0.plus(Duration.ofMinutes(n))
    private fun dias(n: Long) = t0.plus(Duration.ofDays(n))

    private val sesion = Sesion("s1", TipoSesion.PRUEBA_SECCION, "admisiones", "mat", "sec1", inicio = t0)
    private val intento = Intento("i1", "s1", "mat.p1", "c1", "v1", 7, "h", t0)

    private fun respondido(i: Intento, en: Instant, bien: Boolean?, pistas: Int = 0) = i.copy(
        respondido = en, respuesta = "x", correcta = bien, pistasAlResponder = pistas, pistasUsadas = pistas,
        estado = when (bien) {
            true -> EstadoRespuesta.CORRECTA
            false -> EstadoRespuesta.INCORRECTA
            null -> EstadoRespuesta.PENDIENTE
        },
    )

    // --- Sesiones ----------------------------------------------------------------------

    @Test
    fun sinNadaLocalGanaLoRemoto() {
        assertSame(sesion, Union.sesion(null, sesion))
        assertSame(intento, Union.intento(null, intento))
    }

    @Test
    fun laSesionCerradaGanaALaAbierta() {
        val cerrada = sesion.copy(fin = min(10), nota = 8.0, aprobada = true)
        assertEquals(cerrada, Union.sesion(sesion, cerrada))
        assertEquals(cerrada, Union.sesion(cerrada, sesion))
    }

    @Test
    fun unaNotaNoSeBorraConUnValorVacio() {
        // Cerrada en los dos lados, pero un Writing se calificó solo en uno.
        val pendiente = sesion.copy(fin = min(10))
        val calificada = pendiente.copy(nota = 6.0, aprobada = false)
        assertEquals(calificada, Union.sesion(pendiente, calificada))
        assertEquals(calificada, Union.sesion(calificada, pendiente))
    }

    @Test
    fun siLasDosEstanCerradasSeQuedaLoLocal() {
        val a = sesion.copy(fin = min(10), nota = 8.0, aprobada = true)
        val b = sesion.copy(fin = min(12), nota = 3.0, aprobada = false)
        assertEquals(a, Union.sesion(a, b))
    }

    // --- Intentos ----------------------------------------------------------------------

    @Test
    fun elIntentoRespondidoGanaAlQueNoSeRespondio() {
        val r = respondido(intento, min(1), true)
        assertEquals(r, Union.intento(intento, r))
        assertEquals(r, Union.intento(r, intento))
    }

    @Test
    fun lasPistasNoSePierden() {
        val conPistas = intento.copy(pistasUsadas = 3)
        val r = respondido(intento, min(1), false, pistas = 1)
        assertEquals(3, Union.intento(conPistas, r).pistasUsadas)
        assertEquals(min(1), Union.intento(conPistas, r).respondido)
    }

    @Test
    fun elWritingCalificadoNoSeBorra() {
        val pendiente = respondido(intento, min(1), null)
        val calificado = Estudio.registrarEscritura(pendiente, 8.0)
        assertEquals(calificado, Union.intento(pendiente, calificado))
        assertEquals(calificado, Union.intento(calificado, pendiente))
    }

    // --- Memoria -----------------------------------------------------------------------

    @Test
    fun laMemoriaSeReconstruyeEnElOrdenEnQueSeRespondio() {
        val a = respondido(intento.copy(id = "a"), dias(0), true)
        val b = respondido(intento.copy(id = "b"), dias(3), false)
        val c = respondido(intento.copy(id = "c"), dias(4), true, pistas = 2)
        var esperada: Tarjeta? = null
        for (i in listOf(a, b, c)) {
            esperada = Memoria.repasar(esperada, Reglas.calificacionMemoria(i.correcta!!, i.pistasAlResponder!!), i.respondido!!)
        }
        // Llegan desordenados (de dos dispositivos): el resultado es el mismo.
        assertEquals(mapOf("c1" to esperada), Union.memoria(listOf(c, a, b)))
    }

    @Test
    fun laMemoriaIgnoraLoSinResponderYLosWritingPendientes() {
        val pendiente = respondido(intento.copy(id = "w", concepto = "c2"), min(1), null)
        assertEquals(emptyMap<String, Tarjeta>(), Union.memoria(listOf(intento, pendiente)))
    }

    @Test
    fun lasPistasPedidasDespuesDeResponderNoCuentanEnLaMemoria() {
        // Acertó sin pistas y luego miró la solución.
        val bien = respondido(intento, min(1), true).copy(pistasUsadas = 4)
        val sinMirar = respondido(intento, min(1), true)
        assertEquals(Union.memoria(listOf(sinMirar)), Union.memoria(listOf(bien)))
    }
}
