package com.preparate.datos

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.preparate.datos.local.BaseDatos
import com.preparate.dominio.ContextoTutor
import com.preparate.dominio.CriterioCalificado
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Revision
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.Variante
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** Lo que Kiwi necesita de Room: el contexto del ejercicio y la nota del Writing. */
@RunWith(AndroidJUnit4::class)
class EscrituraEnRoomTest {
    private val curso = "admisiones"
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: BaseDatos
    private lateinit var contenido: LectorContenido
    private var ahora = Instant.parse("2026-03-01T15:00:00Z")

    @get:Rule
    val migraciones = MigrationTestHelper(instrumentacion, BaseDatos::class.java)

    @Before
    fun abrir() {
        db = BaseDatos.enMemoria(instrumentacion.targetContext)
        contenido = LectorContenido(instrumentacion.context.assets)
    }

    @After
    fun cerrar() = db.close()

    private fun estudios() = Estudios(db, contenido, "ana", reloj = { ahora }, azar = Random(3))

    private fun bien(v: Variante): Any = when {
        v.cuenta != null -> v.cuenta!!.esperadas
        v.plantilla.tipo == TipoEjercicio.ORDENAR -> v.respuesta!!.split(" ")
        v.plantilla.tipo == TipoEjercicio.ESCRITURA -> "My family is big. I have two brothers and one sister. We play football on Sundays."
        else -> v.respuesta!!
    }

    private fun revision(puntaje: Double) =
        Revision(puntaje, listOf(CriterioCalificado("Ideas", puntaje, 10.0, "Bien")), "¡Buen trabajo!")

    /** La prueba de la sección de Writing, respondida entera y bien. Queda pendiente por el Writing. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun pruebaDeWriting(e: Estudios): String {
        e.depuracionCompletar(curso, "ingles", "writing", aprobar = false)
        val sesion = e.empezarPrueba(curso, "ingles", "writing")
        while (true) {
            val ej = e.siguiente(sesion.id) ?: break
            ahora = ahora.plus(Duration.ofSeconds(30))
            val r = bien(ej.variante)
            if (r is List<*>) e.responder(ej.intento.id, r as List<String>) else e.responder(ej.intento.id, r as String)
        }
        return sesion.id
    }

    @Test
    fun laNotaDelWritingCierraLaPruebaPendiente() = runTest {
        val e = estudios()
        val sesionId = pruebaDeWriting(e)
        val cierre = e.cerrar(sesionId)
        assertTrue(cierre.pendiente)
        val pendientes = e.writingPendientes()
        assertTrue(pendientes.isNotEmpty())
        assertEquals(pendientes.size, e.observarWritingPendientes().first())

        // El Writing de una prueba también se califica: calificar no es ayudar.
        for (id in pendientes) {
            val ctx = e.contextoTutor(id)
            assertEquals(TipoSesion.PRUEBA_SECCION, ctx.tipoSesion)
            assertEquals(EstadoRespuesta.PENDIENTE, ctx.intento.estado)
            e.registrarEscritura(id, revision(9.0))
        }
        assertEquals(0, e.writingPendientes().size)
        val resultado = e.resultado(sesionId)
        assertEquals(10.0, resultado.cierre.nota!!, 0.0)
        assertEquals(true, resultado.cierre.aprobada)
        val writing = resultado.preguntas.first { it.variante.plantilla.tipo == TipoEjercicio.ESCRITURA }.intento
        assertEquals(revision(9.0), Revision.desdeJson(writing.revision!!))
        // La nota y su detalle entran en la cola de subida.
        assertTrue(AlmacenSincro(db, "ana").lote(500).any { it is Cambio.DeIntento && it.fila.revision != null })
        // Con la nota, el concepto entra en el repaso espaciado.
        assertNotNull(db.estudio().memoria("ana", curso, writing.concepto))
    }

    @Test
    fun unWritingReprobadoCuentaComoFallo() = runTest {
        val e = estudios()
        val sesionId = pruebaDeWriting(e)
        e.cerrar(sesionId)
        e.writingPendientes().forEach { e.registrarEscritura(it, revision(4.0)) }
        val r = e.resultado(sesionId)
        assertTrue(r.cierre.nota!! < 10.0)
    }

    @Test
    fun elContextoTraeElMaterialDelConcepto() = runTest {
        val e = estudios()
        val tema = "mat.fracciones.equivalentes"
        val sesion = e.empezarTema(curso, tema)
        val ej = e.siguiente(sesion.id)!!
        val ctx = e.contextoTutor(ej.intento.id)
        val leccion = contenido.leccion(curso, tema)
        assertEquals("Matemática", ctx.materia)
        assertTrue(ctx.material.startsWith(leccion.titulo))
        assertTrue(ctx.material.length <= ContextoTutor.LIMITE_MATERIAL)
        assertTrue("sin dibujos", "<div class=\"figura\">" !in ctx.material)
        assertNull(ctx.intento.respondido)
    }

    @Test
    fun laMigracionA3AgregaLaRevision() {
        val nombre = "migracion-3.db"
        migraciones.createDatabase(nombre, 2).close()
        val migrada = migraciones.runMigrationsAndValidate(nombre, 3, true, BaseDatos.MIGRACION_2_3)
        migrada.query("SELECT revision FROM intentos").close()
        migrada.close()
    }
}
