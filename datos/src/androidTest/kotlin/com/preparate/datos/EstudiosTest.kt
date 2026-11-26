package com.preparate.datos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.preparate.datos.local.BaseDatos
import com.preparate.datos.local.SesionFila
import com.preparate.dominio.AyudaProhibida
import com.preparate.dominio.EstadoNodo
import com.preparate.dominio.EstadoPrueba
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.ReglaError
import com.preparate.dominio.Reglas
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.TipoVerificador
import com.preparate.dominio.Variante
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** El estudio sobre Room real, en el dispositivo. Puerto de los tests de la web que usan la base. */
@RunWith(AndroidJUnit4::class)
class EstudiosTest {
    private val curso = "admisiones"
    private val tema = "mat.fracciones.equivalentes"
    private lateinit var db: BaseDatos
    private lateinit var contenido: LectorContenido
    private var ahora = Instant.parse("2026-03-01T15:00:00Z")

    @Before
    fun abrir() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        db = BaseDatos.enMemoria(instrumentacion.targetContext)
        contenido = LectorContenido(instrumentacion.context.assets)
    }

    @After
    fun cerrarBase() = db.close()

    private fun estudios(usuario: String = "ana", semilla: Int = 1) =
        Estudios(db, contenido, usuario, reloj = { ahora }, azar = Random(semilla))

    private fun bien(v: Variante): Any = when {
        v.cuenta != null -> v.cuenta!!.esperadas
        v.plantilla.tipo == TipoEjercicio.ORDENAR -> v.respuesta!!.split(" ")
        else -> v.respuesta!!
    }

    private fun mal(v: Variante): Any = if (v.plantilla.verificador == TipoVerificador.SYMPY) "123456" else "zzzz qqqq"

    private suspend fun Estudios.contestar(e: Ejercicio, correcta: Boolean) {
        val r = when (val respuesta = if (correcta) bien(e.variante) else mal(e.variante)) {
            is List<*> -> responder(e.intento.id, respuesta.map { it.toString() })
            else -> responder(e.intento.id, respuesta.toString())
        }
        assertEquals(e.variante.plantilla.id, correcta, r.correcta)
    }

    /** Juega un tema entero: `aciertos` dice cómo responde cada ejercicio que va saliendo. */
    private suspend fun Estudios.jugar(sesionId: String, vararg aciertos: Boolean) {
        for (ok in aciertos) contestar(checkNotNull(siguiente(sesionId)) { "no hay más ejercicios" }, ok)
    }

    @Test
    fun completarUnTemaDaPuntosUnaSolaVezYAbreElSiguiente() = runTest {
        val e = estudios()
        val s = e.empezarTema(curso, tema)
        e.jugar(s.id, true, true, true, true, true)
        assertNull(e.siguiente(s.id))
        val cierre = e.cerrar(s.id)
        assertTrue(cierre.completada)
        assertEquals(Reglas.PUNTOS_LECCION, cierre.puntos)
        assertEquals(EstadoNodo.COMPLETADO, e.progreso(curso, "matematica").leccion(tema)!!.estado)
        assertEquals(Reglas.PUNTOS_LECCION, e.observarPuntos(curso).first())

        val otra = e.empezarTema(curso, tema)
        e.jugar(otra.id, true, true, true, true, true)
        assertEquals(0, e.cerrar(otra.id).puntos)
        assertEquals(Reglas.PUNTOS_LECCION, e.observarPuntos(curso).first())
    }

    @Test
    fun loFalladoVuelveTalCualAlFinalYElCuartoErrorCortaElTema() = runTest {
        val e = estudios()
        val s = e.empezarTema(curso, tema)
        val primero = e.siguiente(s.id)!!
        e.contestar(primero, false)
        e.jugar(s.id, true, true, true, true)
        val repeticion = e.siguiente(s.id)!!
        assertEquals(primero.intento.semilla, repeticion.intento.semilla)
        assertEquals(primero.intento.id, repeticion.intento.repite)

        val otra = e.reiniciarTema(curso, tema)
        e.jugar(otra.id, false, false, false)
        assertEquals(3, e.estado(otra.id).errores)
        e.jugar(otra.id, false)
        assertNull(e.siguiente(otra.id))
        assertFalse(e.cerrar(otra.id).completada)
        assertEquals(0, e.observarPuntos(curso).first())
    }

    @Test
    fun unTemaAMediasSeRetomaConElMismoEjercicio() = runTest {
        val e = estudios()
        val s = e.empezarTema(curso, tema)
        val pendiente = e.siguiente(s.id)!!
        // Se cierra la app: otra instancia de Estudios, la misma base.
        val despues = estudios(semilla = 99)
        assertEquals(s.id, despues.empezarTema(curso, tema).id)
        assertEquals(pendiente.intento.id, despues.siguiente(s.id)!!.intento.id)
    }

    @Test
    fun unaRespuestaIlegibleNoSeGuarda() = runTest {
        val e = estudios()
        val s = e.empezarTema(curso, tema)
        val ej = e.siguiente(s.id)!!
        assertEquals(EstadoRespuesta.FORMATO_INVALIDO, e.responder(ej.intento.id, "cinco novenos").estado)
        assertEquals(0, e.estado(s.id).respondidas)
        assertEquals(ej.intento.id, e.siguiente(s.id)!!.intento.id)
    }

    @Test
    fun dosEstudiantesEnLaMismaTabletNoSeMezclan() = runTest {
        val ana = estudios("ana")
        val beto = estudios("beto")
        val s = ana.empezarTema(curso, tema)
        ana.jugar(s.id, true, true, true, true, true)
        ana.cerrar(s.id)
        assertEquals(EstadoNodo.COMPLETADO, ana.progreso(curso, "matematica").leccion(tema)!!.estado)
        assertEquals(EstadoNodo.DISPONIBLE, beto.progreso(curso, "matematica").leccion(tema)!!.estado)
        assertEquals(0, beto.observarPuntos(curso).first())
        try {
            beto.siguiente(s.id)
            fail("beto no debe ver la sesión de ana")
        } catch (e: IllegalStateException) {
            // esperado
        }
    }

    /** Marca como hechos todos los temas de una sección, como si se hubieran jugado. */
    private suspend fun temasHechos(usuario: String, seccion: String) {
        val s = contenido.curso(curso).materias.single { it.id == "matematica" }.secciones.single { it.id == seccion }
        for (l in s.lecciones) {
            db.estudio().insertarSesion(
                SesionFila(
                    "hecho-$usuario-${l.id}", usuario, curso, "matematica", TipoSesion.LECCION.valor, seccion, l.id, "",
                    ahora.toEpochMilli(), ahora.toEpochMilli(), null, true,
                ),
            )
        }
    }

    @Test
    fun pruebaDeSeccionDe10PreguntasSinPistasConNotaYResultado() = runTest {
        val e = estudios()
        try {
            e.empezarPrueba(curso, "matematica", "fracciones")
            fail("la prueba debe estar bloqueada")
        } catch (_: ReglaError) {
        }
        temasHechos("ana", "fracciones")
        val prueba = e.empezarPrueba(curso, "matematica", "fracciones")
        assertEquals(prueba.id, e.empezarPrueba(curso, "matematica", "fracciones").id) // se retoma
        assertEquals(Reglas.PREGUNTAS_PRUEBA_SECCION, e.estado(prueba.id).total)

        repeat(Reglas.PREGUNTAS_PRUEBA_SECCION) { n ->
            val ej = e.siguiente(prueba.id)!!
            if (n == 0) {
                try {
                    e.pista(ej.intento.id)
                    fail("en la prueba no hay pistas")
                } catch (_: AyudaProhibida) {
                }
            }
            e.contestar(ej, correcta = n < 6)
        }
        assertNull(e.siguiente(prueba.id))
        val cierre = e.cerrar(prueba.id)
        assertEquals(6.0, cierre.nota!!, 0.0)
        assertEquals(false, cierre.aprobada)

        val resultado = e.resultado(prueba.id)
        assertEquals(10, resultado.preguntas.size)
        assertTrue(resultado.repasar.isNotEmpty())
        val progreso = e.progreso(curso, "matematica")
        assertEquals(EstadoPrueba.REPASO_REQUERIDO, progreso.seccion("fracciones")!!.prueba)

        // Tras reprobar: el repaso de los temas fallados habilita el reintento.
        val repaso = e.empezarRepaso(curso, "matematica", "fracciones")
        ahora = ahora.plusSeconds(60)
        repeat(Reglas.EJERCICIOS_REPASO) { e.contestar(e.siguiente(repaso.id)!!, true) }
        assertTrue(e.cerrar(repaso.id).completada)
        assertEquals(EstadoPrueba.DISPONIBLE, e.progreso(curso, "matematica").seccion("fracciones")!!.prueba)
    }

    @Test
    fun elExamenSeCierraSoloALos20Minutos() = runTest {
        val e = estudios()
        val secciones = contenido.curso(curso).materias.single { it.id == "matematica" }.secciones
        for (s in secciones) {
            temasHechos("ana", s.id)
            db.estudio().insertarSesion(
                SesionFila(
                    "prueba-${s.id}", "ana", curso, "matematica", TipoSesion.PRUEBA_SECCION.valor, s.id, null, "",
                    ahora.toEpochMilli(), ahora.toEpochMilli(), 9.0, true,
                ),
            )
        }
        val examen = e.empezarExamen(curso, "matematica")
        assertEquals(Reglas.PREGUNTAS_EXAMEN_FINAL, e.estado(examen.id).total)
        repeat(3) { e.contestar(e.siguiente(examen.id)!!, true) }
        ahora = ahora.plus(Duration.ofMinutes(21))
        assertNull(e.siguiente(examen.id))
        val estado = e.estado(examen.id)
        assertNotNull(estado.sesion.fin)
        assertEquals(2.0, estado.sesion.nota!!, 0.0) // 3 de 15
        assertEquals(0L, estado.segundosRestantes)
    }

    @Test
    fun responderActualizaElRepasoEspaciadoDelConcepto() = runTest {
        val e = estudios()
        val s = e.empezarTema(curso, tema)
        e.jugar(s.id, true)
        assertTrue(e.conceptosVencidos(curso, "matematica").isEmpty())
        ahora = ahora.plus(Duration.ofDays(30))
        assertEquals(listOf(tema), e.conceptosVencidos(curso, "matematica"))
        val repaso = e.empezarRepaso(curso, "matematica")
        assertEquals(listOf(tema), repaso.conceptos)
        assertEquals(TipoSesion.REPASO, repaso.tipo)
    }
}
