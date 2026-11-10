package com.preparate.tutor

import com.preparate.dominio.AyudaProhibida
import com.preparate.dominio.ContextoTutor
import com.preparate.dominio.CriterioRubrica
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Intento
import com.preparate.dominio.Plantilla
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.TipoVerificador
import com.preparate.dominio.Variante
import com.preparate.dominio.ayudaPermitida
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Un modelo falso: responde lo que se le diga y cuenta las llamadas. */
private class ModeloFalso(var respuesta: String = "¿Qué pasa con las unidades?", var encendido: Boolean = true) : Modelo {
    var llamadas = 0
    val recibidos = mutableListOf<List<Mensaje>>()

    override suspend fun modelos(): List<String>? = if (encendido) listOf("qwen") else null

    override suspend fun chat(mensajes: List<Mensaje>, esquema: JsonObject?, maxTokens: Int): String {
        llamadas++
        recibidos += mensajes
        if (!encendido) throw TutorNoDisponible("apagado")
        return respuesta
    }
}

class TutorTest {
    private val ahora = Instant.parse("2026-11-10T15:00:00Z")
    private val plantilla = Plantilla("mat.p", TipoEjercicio.ABIERTA, TipoVerificador.SYMPY)
    private val variante = Variante(
        plantilla, 1, "Calcula 25 + 17", "42",
        pistas = listOf("Suma primero las unidades.", "5 + 7 = 12: escribe 2 y llevas 1.", "Ejemplo: 18 + 15 = 33."),
    )
    private val fallado = Intento(
        "i1", "s1", "mat.p", "c", "v", 1, "h", ahora,
        respondido = ahora, respuesta = "32", estado = EstadoRespuesta.INCORRECTA, correcta = false,
    )

    private fun ctx(tipo: TipoSesion, intento: Intento = fallado, v: Variante = variante) =
        ContextoTutor(tipo, "Matemática", "Sumar llevando", "Material de la lección", v, intento)

    // --- Regla 3: la puerta de la ayuda, un test por cada tipo de sesión -------------------

    @Test
    fun enCadaTipoDeSesionLaAyudaSigueAAyudaPermitida() = runTest {
        for (tipo in TipoSesion.entries) {
            val modelo = ModeloFalso()
            val tutor = Tutor(modelo)
            if (ayudaPermitida(tipo)) {
                assertEquals(tipo.name, "¿Qué pasa con las unidades?", tutor.pista(ctx(tipo, fallado.copy(respondido = null)), 1, emptyList()))
                assertTrue(tipo.name, tutor.trasFallo(ctx(tipo), emptySet()) is Dicho.DeKiwi)
                assertTrue(tipo.name, tutor.conversar(ctx(tipo), emptyList(), "No entiendo", emptySet()) is Dicho.DeKiwi)
                assertEquals(3, modelo.llamadas)
            } else {
                assertThrows(tipo.name, AyudaProhibida::class.java) { runBlockingTest { tutor.pista(ctx(tipo), 1, emptyList()) } }
                assertThrows(tipo.name, AyudaProhibida::class.java) { runBlockingTest { tutor.trasFallo(ctx(tipo), emptySet()) } }
                assertThrows(tipo.name, AyudaProhibida::class.java) { runBlockingTest { tutor.conversar(ctx(tipo), emptyList(), "¿Cuál es?", emptySet()) } }
                // El modelo nunca se llama.
                assertEquals(tipo.name, 0, modelo.llamadas)
            }
        }
    }

    @Test
    fun hayTestParaLasCuatroSesiones() {
        assertEquals(
            setOf(TipoSesion.LECCION, TipoSesion.REPASO),
            TipoSesion.entries.filter { ayudaPermitida(it) }.toSet(),
        )
        assertEquals(4, TipoSesion.entries.size)
    }

    // --- Regla 2: la guardia ---------------------------------------------------------------

    @Test
    fun siKiwiIbaARevelarLaRespuestaSeUsaUnaPistaEscrita() = runTest {
        val tutor = Tutor(ModeloFalso("¡Casi! Es 42."))
        val vistas = setOf("Suma primero las unidades.")
        assertEquals(Dicho.PistaEscrita("5 + 7 = 12: escribe 2 y llevas 1."), tutor.trasFallo(ctx(TipoSesion.LECCION), vistas))
        assertEquals(Dicho.Bloqueado, tutor.conversar(ctx(TipoSesion.LECCION), emptyList(), "dime", variante.pistas.toSet()))
        // En una pista, lo que revela se descarta y la pantalla deja la pista de la lista.
        assertNull(tutor.pista(ctx(TipoSesion.LECCION, fallado.copy(respondido = null)), 1, emptyList()))
    }

    @Test
    fun repetirLaRespuestaEquivocadaDelEstudianteNoEsRevelar() = runTest {
        val tutor = Tutor(ModeloFalso("Escribiste 32. ¿Sumaste lo que llevabas?"))
        assertTrue(tutor.trasFallo(ctx(TipoSesion.LECCION), emptySet()) is Dicho.DeKiwi)
    }

    // --- Sin Kiwi ----------------------------------------------------------------------------

    @Test
    fun sinLmStudioNoHayKiwiYNadaFalla() = runTest {
        val tutor = Tutor(ModeloFalso(encendido = false))
        assertEquals(false, tutor.disponible())
        assertNull(tutor.pista(ctx(TipoSesion.LECCION, fallado.copy(respondido = null)), 1, emptyList()))
        assertNull(tutor.trasFallo(ctx(TipoSesion.REPASO), emptySet()))
    }

    @Test
    fun laSolucionNoLaEscribeKiwi() = runTest {
        val modelo = ModeloFalso()
        assertNull(Tutor(modelo).pista(ctx(TipoSesion.LECCION), 4, emptyList()))
        assertEquals(0, modelo.llamadas)
    }

    @Test
    fun trasUnAciertoKiwiNoAparece() {
        val acierto = fallado.copy(respuesta = "42", estado = EstadoRespuesta.CORRECTA, correcta = true)
        assertThrows(IllegalStateException::class.java) {
            runBlockingTest { Tutor(ModeloFalso()).trasFallo(ctx(TipoSesion.LECCION, acierto), emptySet()) }
        }
    }

    @Test
    fun elPromptLlevaElEjercicioYLasPistasPrevias() = runTest {
        val modelo = ModeloFalso()
        Tutor(modelo).pista(ctx(TipoSesion.LECCION, fallado.copy(respondido = null)), 2, listOf("Pista anterior de Kiwi"))
        val m = modelo.recibidos.single()
        assertTrue(m.first().texto.contains("EJERCICIO: Calcula 25 + 17"))
        assertTrue(m.first().texto.contains("NIVEL DE PISTA PEDIDO: 2"))
        assertTrue(m.first().texto.contains("RESPUESTA DEL ESTUDIANTE: todavía no responde"))
        assertEquals(listOf("system", "user", "assistant", "user"), m.map { it.rol })
        assertEquals("Pista anterior de Kiwi", m[2].texto)
    }

    // --- Writing -----------------------------------------------------------------------------

    @Test
    fun elWritingSeCalificaInclusoEnElExamen() = runTest {
        val writing = Plantilla(
            "ing.w", TipoEjercicio.ESCRITURA, TipoVerificador.TEXTO,
            rubrica = listOf(CriterioRubrica("Ideas", "Responde la consigna.", 5.0), CriterioRubrica("Grammar", "Presente simple.", 5.0)),
        )
        val v = Variante(writing, 1, "Write about your family.", null)
        val texto = fallado.copy(respuesta = "My family is big. I have two brothers.", estado = EstadoRespuesta.PENDIENTE, correcta = null)
        val modelo = ModeloFalso(
            """{"criterios": [{"criterio": "Ideas", "puntos": 4, "comentario": "Bien"},""" +
                """{"criterio": "Grammar", "puntos": 3.5, "comentario": "Ojo"}], "comentario": "¡Muy bien!"}""",
        )
        val r = Tutor(modelo).calificar(ctx(TipoSesion.EXAMEN_FINAL, texto, v))
        assertEquals(7.5, r.puntaje, 0.0)
        assertEquals("My family is big. I have two brothers.", modelo.recibidos.single().last().texto)

        modelo.respuesta = "no es JSON"
        assertThrows(TutorNoDisponible::class.java) { runBlockingTest { Tutor(modelo).calificar(ctx(TipoSesion.EXAMEN_FINAL, texto, v)) } }
    }
}

private fun runBlockingTest(bloque: suspend () -> Unit) = kotlinx.coroutines.runBlocking { bloque() }
