package com.preparate.dominio

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelTest {
    private val curso = PaqueteDePrueba.curso
    private val zona = ZoneId.of("America/Guayaquil")
    private val ahora = Instant.parse("2026-11-10T20:00:00Z") // 15:00 en Ecuador
    private fun antes(minutos: Long) = ahora.minus(Duration.ofMinutes(minutos))

    private val mat = PaqueteDePrueba.materia("matematica")
    private val seccion = mat.secciones.first { it.lecciones.isNotEmpty() }
    private val leccion = seccion.lecciones.first()

    private fun intento(
        id: String,
        sesion: String,
        en: Instant,
        bien: Boolean?,
        pistas: Int = 0,
        concepto: String = leccion.concepto,
    ) = Intento(
        id, sesion, "p", concepto, "v", 1, "h", en.minusSeconds(30),
        respondido = en, respuesta = "r$id", correcta = bien, pistasUsadas = pistas,
        estado = when (bien) {
            true -> EstadoRespuesta.CORRECTA
            false -> EstadoRespuesta.INCORRECTA
            null -> EstadoRespuesta.PENDIENTE
        },
    )

    private fun resumen(sesiones: List<Sesion>, intentos: List<Intento>) =
        Panel.resumen(listOf(curso), sesiones, intentos, ahora, zona) { _, i -> "enunciado de ${i.id}" }

    @Test
    fun sinEstudiarTodoEnCero() {
        val r = resumen(emptyList(), emptyList())
        assertNull(r.ultimaActividad)
        assertEquals(7, r.dias.size)
        assertEquals("2026-11-10", r.dias.last().fecha)
        assertEquals(0, r.minutosTotal)
        assertEquals(curso.materias.size, r.materias.size)
        assertEquals(0, r.materias.sumOf { it.temasHechos })
    }

    @Test
    fun cuentaMinutosTemasYNotas() {
        val tema =
            Sesion(
                "s1", TipoSesion.LECCION, curso.id, "matematica", seccion.id, leccion.id,
                inicio = antes(
                    60,
                ),
                fin = antes(48), aprobada = true,
            )
        // Abierta y olvidada: cuenta hasta su última respuesta.
        val olvidada = Sesion("s2", TipoSesion.REPASO, curso.id, "matematica", inicio = antes(30))
        // Ayer en Ecuador.
        val prueba = Sesion(
            "s3", TipoSesion.PRUEBA_SECCION, curso.id, "matematica", seccion.id,
            inicio = ahora.minus(
                Duration.ofHours(20),
            ),
            fin = ahora.minus(Duration.ofHours(20)).plusSeconds(600), nota = 6.0, aprobada = false,
        )
        val intentos = listOf(
            intento("a", "s1", antes(55), false, pistas = 2),
            intento("b", "s1", antes(50), true),
            intento("c", "s2", antes(25), true),
            intento("d", "s3", ahora.minus(Duration.ofHours(20)).plusSeconds(60), false),
        )
        val r = resumen(listOf(tema, olvidada, prueba), intentos)

        assertEquals(antes(25).toEpochMilli(), r.ultimaActividad)
        assertEquals(12 + 5 + 10, r.minutosTotal)
        assertEquals(17, r.dias.last().minutos)
        assertEquals(10, r.dias[5].minutos)
        assertEquals(27, r.minutosSemana)

        val m = r.materias.single { it.materiaId == "matematica" }
        assertEquals(27, m.minutos)
        assertEquals(1, m.temasHechos)
        assertEquals(1, m.notas.size)
        assertEquals(6.0, m.notas.single().nota!!, 0.0)
        assertEquals(seccion.nombre, m.notas.single().seccion)
        val t = m.temas.single()
        assertEquals(leccion.titulo, t.titulo)
        assertEquals(4, t.respondidos)
        assertEquals(2, t.fallos)
        assertEquals(1, t.conPista)
        assertEquals(2, t.pistas)
    }

    @Test
    fun unaSesionNoSumaMasDe90Minutos() {
        val s = Sesion("s", TipoSesion.LECCION, curso.id, "matematica", inicio = antes(600), fin = ahora)
        assertEquals(90, Panel.minutos(s, emptyList()))
        assertNull(Panel.minutos(s.copy(fin = null), emptyList()))
    }

    @Test
    fun losWritingPendientesSalenConSuTexto() {
        val examen = Sesion("e", TipoSesion.EXAMEN_FINAL, curso.id, "ingles", inicio = antes(20), fin = antes(5))
        val r = resumen(listOf(examen), listOf(intento("w", "e", antes(10), null)))
        val p = r.pendientes.single()
        assertEquals("rw", p.respuesta)
        assertEquals("enunciado de w", p.enunciado)
        assertEquals(TipoSesion.EXAMEN_FINAL.valor, p.tipoSesion)
        // El examen con un Writing sin nota queda pendiente, y el Writing no cuenta como fallo.
        val ing = r.materias.single { it.materiaId == "ingles" }
        assertEquals(0, ing.temas.size)
        assertNull(ing.notas.single().nota)
    }

    @Test
    fun elJsonVaYVuelve() {
        val r = resumen(emptyList(), emptyList())
        assertEquals(r, ResumenPanel.desdeJson(r.aJson()))
    }
}
