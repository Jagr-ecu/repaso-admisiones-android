package com.preparate.datos

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.preparate.datos.local.BaseDatos
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.Variante
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** La cola de subida, la unión de lo que baja y la migración a la versión 2, sobre Room real. */
@RunWith(AndroidJUnit4::class)
class SincroTest {
    private val curso = "admisiones"
    private val tema = "mat.fracciones.equivalentes"
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: BaseDatos
    private lateinit var otra: BaseDatos
    private lateinit var contenido: LectorContenido
    private var ahora = Instant.parse("2026-03-01T15:00:00Z")

    @get:Rule
    val migraciones = MigrationTestHelper(instrumentacion, BaseDatos::class.java)

    @Before
    fun abrir() {
        db = BaseDatos.enMemoria(instrumentacion.targetContext)
        otra = BaseDatos.enMemoria(instrumentacion.targetContext)
        contenido = LectorContenido(instrumentacion.context.assets)
    }

    @After
    fun cerrar() {
        db.close()
        otra.close()
    }

    private fun estudios(base: BaseDatos = db, usuario: String = "ana") =
        Estudios(base, contenido, usuario, reloj = { ahora }, azar = Random(1))

    private fun bien(v: Variante): Any = when {
        v.cuenta != null -> v.cuenta!!.esperadas
        v.plantilla.tipo == TipoEjercicio.ORDENAR -> v.respuesta!!.split(" ")
        else -> v.respuesta!!
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun responder(e: Estudios, ej: Ejercicio, correcto: Boolean) {
        val r = if (correcto) bien(ej.variante) else "respuesta-mala"
        if (r is List<*>) e.responder(ej.intento.id, r as List<String>) else e.responder(ej.intento.id, r as String)
    }

    /** Un tema completo con un fallo, con el reloj avanzando. Devuelve la sesión. */
    private suspend fun jugarTema(e: Estudios): String {
        val sesion = e.empezarTema(curso, tema)
        var n = 0
        while (true) {
            val ej = e.siguiente(sesion.id) ?: break
            ahora = ahora.plus(Duration.ofMinutes(1))
            if (n == 1) e.pista(ej.intento.id)
            responder(e, ej, correcto = n != 0)
            n++
        }
        e.cerrar(sesion.id)
        return sesion.id
    }

    @Test
    fun cadaCambioEntraEnLaColaYSaleAlConfirmar() = runTest {
        val almacen = AlmacenSincro(db, "ana")
        jugarTema(estudios())
        val pendientes = almacen.observarPendientes().first()
        assertTrue(pendientes > 2)

        val lote = almacen.lote(500)
        assertEquals(pendientes, lote.size)
        assertEquals(1, lote.count { it is Cambio.DeSesion })
        assertEquals(1, lote.count { it is Cambio.DePuntos })
        // Cada registro sale una vez, con su estado final: la sesión ya cerrada.
        assertTrue((lote.single { it is Cambio.DeSesion } as Cambio.DeSesion).fila.fin != null)

        almacen.confirmar(lote)
        assertEquals(0, almacen.observarPendientes().first())
    }

    @Test
    fun loQueCambiaDuranteLaSubidaSeQuedaEnLaCola() = runTest {
        val almacen = AlmacenSincro(db, "ana")
        val e = estudios()
        val sesion = e.empezarTema(curso, tema)
        val ej = e.siguiente(sesion.id)!!
        val lote = almacen.lote(500)
        e.pista(ej.intento.id) // llega mientras el lote sube
        almacen.confirmar(lote)

        val queda = almacen.lote(500)
        assertEquals(1, queda.size)
        assertEquals(1, (queda.single() as Cambio.DeIntento).fila.pistasUsadas)
    }

    @Test
    fun cadaEstudianteTieneSuColaYLaVistaPreviaNoSube() = runTest {
        jugarTema(estudios(usuario = "ana"))
        jugarTema(estudios(usuario = BaseDatos.SIN_NUBE))
        assertEquals(0, AlmacenSincro(db, "leo").observarPendientes().first())
        assertEquals(0, AlmacenSincro(db, BaseDatos.SIN_NUBE).observarPendientes().first())
        assertTrue(AlmacenSincro(db, "ana").lote(500).all {
            when (it) {
                is Cambio.DeSesion -> it.fila.usuarioId == "ana"
                is Cambio.DeIntento -> it.fila.usuarioId == "ana"
                is Cambio.DePuntos -> it.fila.usuarioId == "ana"
            }
        })
    }

    @Test
    fun otroDispositivoRecibeTodoYReconstruyeLaMemoria() = runTest {
        val sesionId = jugarTema(estudios())
        val origen = AlmacenSincro(db, "ana")
        val lote = origen.lote(500)
        val descarga = Descarga(
            sesiones = lote.filterIsInstance<Cambio.DeSesion>().map { it.fila },
            // Los intentos pueden llegar antes que su sesión dentro de la misma descarga.
            intentos = lote.filterIsInstance<Cambio.DeIntento>().map { it.fila }.reversed(),
            puntos = lote.filterIsInstance<Cambio.DePuntos>().map { it.fila },
        )

        val destino = AlmacenSincro(otra, "ana")
        val r = destino.unir(descarga)
        assertEquals(descarga.intentos.size, r.intentos)
        assertEquals(0, r.omitidos)
        // Lo que bajó es igual a lo de la nube: no se vuelve a subir.
        assertEquals(0, destino.observarPendientes().first())

        // Mismo progreso, mismos puntos y la misma memoria que en el dispositivo de origen.
        assertEquals(estudios(db).progreso(curso, "matematica"), estudios(otra).progreso(curso, "matematica"))
        assertEquals(estudios(db).observarPuntos(curso).first(), estudios(otra).observarPuntos(curso).first())
        val concepto = contenido.leccion(curso, tema).concepto
        assertEquals(db.estudio().memoria("ana", curso, concepto)?.copy(subida = false), otra.estudio().memoria("ana", curso, concepto))

        // Volver a unir lo mismo no cambia nada.
        assertEquals(0, destino.unir(descarga).total)
        assertTrue(otra.estudio().sesion("ana", sesionId)!!.fin != null)
    }

    @Test
    fun unaSesionCerradaEnLaNubeCierraLaAbierta() = runTest {
        val e = estudios()
        val sesion = e.empezarTema(curso, tema)
        val abierta = db.estudio().sesion("ana", sesion.id)!!
        AlmacenSincro(db, "ana").confirmar(AlmacenSincro(db, "ana").lote(500))

        val cerrada = abierta.copy(fin = ahora.plusSeconds(60).toEpochMilli(), aprobada = false)
        val r = AlmacenSincro(db, "ana").unir(Descarga(sesiones = listOf(cerrada)))
        assertEquals(1, r.sesiones)
        assertEquals(cerrada.fin, db.estudio().sesion("ana", sesion.id)!!.fin)
        assertEquals(0, AlmacenSincro(db, "ana").observarPendientes().first())

        // Al revés: lo abierto de la nube no reabre lo cerrado en el dispositivo.
        assertEquals(0, AlmacenSincro(db, "ana").unir(Descarga(sesiones = listOf(abierta))).total)
    }

    @Test
    fun noSeUneLoDeOtroEstudiante() = runTest {
        jugarTema(estudios(usuario = "leo"))
        val deLeo = AlmacenSincro(db, "leo").lote(500).filterIsInstance<Cambio.DeSesion>().map { it.fila }
        val r = AlmacenSincro(otra, "ana").unir(Descarga(sesiones = deLeo))
        assertEquals(0, r.total)
        assertEquals(deLeo.size, r.omitidos)
    }

    @Test
    fun laMigracionA2ConservaTodoYLoPoneEnLaCola() {
        val nombre = "migracion-prueba.db"
        migraciones.createDatabase(nombre, 1).apply {
            execSQL(
                "INSERT INTO sesiones (id, usuario_id, curso_id, materia_id, tipo, seccion_id, leccion_id, conceptos, inicio, fin, nota, aprobada, subida) " +
                    "VALUES ('s1', 'ana', 'admisiones', 'matematica', 'leccion', 'fracciones', '$tema', '', 1, 2, NULL, 1, 0)",
            )
            execSQL(
                "INSERT INTO sesiones (id, usuario_id, curso_id, materia_id, tipo, seccion_id, leccion_id, conceptos, inicio, fin, nota, aprobada, subida) " +
                    "VALUES ('s2', '${BaseDatos.SIN_NUBE}', 'admisiones', 'matematica', 'leccion', 'fracciones', '$tema', '', 1, 2, NULL, 1, 0)",
            )
            execSQL(
                "INSERT INTO intentos (id, usuario_id, curso_id, materia_id, sesion_id, orden, plantilla_id, concepto, plantilla_version, semilla, huella, mostrado, repite, respondido, respuesta, estado, correcta, error_comun, pistas_usadas, puntaje, subida) " +
                    "VALUES ('i1', 'ana', 'admisiones', 'matematica', 's1', 0, 'p', 'c', 'v', 1, 'h', 1, NULL, 2, '3', 'correcta', 1, NULL, 0, NULL, 0)",
            )
            execSQL("INSERT INTO puntos (usuario_id, curso_id, motivo, referencia, cantidad, creado, subida) VALUES ('ana', 'admisiones', 'leccion', '$tema', 10, 2, 0)")
            close()
        }
        val migrada = migraciones.runMigrationsAndValidate(nombre, 2, true, BaseDatos.MIGRACION_1_2)
        migrada.query("SELECT tabla, ref FROM cola_subida ORDER BY id").use { c ->
            val filas = buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            assertEquals(listOf("sesiones" to "s1", "intentos" to "i1", "puntos" to "leccion:$tema"), filas)
        }
        // Los triggers ya funcionan en la base migrada.
        migrada.execSQL("UPDATE intentos SET pistas_usadas = 1 WHERE id = 'i1'")
        migrada.query("SELECT COUNT(*) FROM cola_subida WHERE ref = 'i1'").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        migrada.close()
    }
}
