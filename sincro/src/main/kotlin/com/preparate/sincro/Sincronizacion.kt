package com.preparate.sincro

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.preparate.datos.AlmacenSincro
import com.preparate.datos.Descarga
import com.preparate.datos.LectorContenido
import com.preparate.datos.ResultadoUnion
import com.preparate.datos.local.BaseDatos
import com.preparate.dominio.Panel
import com.preparate.dominio.ResumenPanel
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Lo que hizo una sincronización. */
data class Informe(val bajados: Int, val subidos: Int)

/** Firestore no confirmó a tiempo; lo que no subió sigue en la cola. */
class SinConexion(causa: Throwable? = null) : Exception("no hay conexión con la nube", causa)

/**
 * Sube la cola de Room a Firestore y une en Room lo que cambió en la nube (PLAN-MIGRACION.md,
 * sección 4). Solo trabaja con el estudiante que tiene la sesión iniciada: Firestore rechaza
 * escribir en nombre de otro, y lo de otro estudiante espera a que vuelva a entrar.
 */
class Sincronizacion(contexto: Context) {
    private val app = contexto.applicationContext
    private val db = BaseDatos.abrir(app)
    private val contenido by lazy { LectorContenido(app.assets) }
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val estado = app.getSharedPreferences("sincro", Context.MODE_PRIVATE)

    private fun estudiante(uid: String) = firestore.collection("estudiantes").document(uid)

    fun pendientes(uid: String): Flow<Int> = AlmacenSincro(db, uid).observarPendientes()

    /** Baja, une, sube y deja el resumen del panel al día. */
    suspend fun sincronizar(uid: String): Informe = cerrojo.withLock {
        val bajados = bajarSinCerrojo(uid).total
        val subidos = subirSinCerrojo(uid)
        if (bajados > 0 || subidos > 0 || !resumenAlDia(uid)) subirResumen(uid)
        Informe(bajados, subidos)
    }

    /** Solo sube. El resumen se rehace como mucho cada [MINUTOS_RESUMEN] minutos mientras estudia. */
    suspend fun subir(uid: String): Int = cerrojo.withLock {
        val subidos = subirSinCerrojo(uid)
        val ultimo = estado.getLong("resumen_$uid", 0)
        if (subidos > 0 && System.currentTimeMillis() - ultimo > MINUTOS_RESUMEN * 60_000) subirResumen(uid)
        subidos
    }

    /** Baja lo que cambió desde la última vez (todo, la primera vez en este dispositivo). */
    suspend fun bajar(uid: String): ResultadoUnion = cerrojo.withLock { bajarSinCerrojo(uid) }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) { "la sesión iniciada es de otro usuario" }
    }

    private suspend fun bajarSinCerrojo(uid: String): ResultadoUnion {
        comprobarSesion(uid)
        val desde = estado.getLong("bajado_$uid", 0)
        // Margen por si un documento se confirmó con una hora un poco anterior a la última vista.
        val corte = if (desde == 0L) null else Timestamp(Date(desde - MARGEN_MS))
        var masNuevo = desde

        suspend fun leer(coleccion: String): List<DocumentSnapshot> {
            var q: Query = estudiante(uid).collection(coleccion)
            if (corte != null) q = q.whereGreaterThan(Documentos.ACTUALIZADO, corte)
            val docs = conTiempo { q.get(Source.SERVER).await() }.documents
            docs.mapNotNull { it.getTimestamp(Documentos.ACTUALIZADO)?.toDate()?.time }.maxOrNull()?.let {
                masNuevo =
                    maxOf(masNuevo, it)
            }
            return docs
        }

        val descarga = Descarga(
            sesiones = leer("sesiones").mapNotNull { Documentos.sesion(uid, it) },
            intentos = leer("intentos").mapNotNull { Documentos.intento(uid, it) },
            puntos = leer("puntos").mapNotNull { Documentos.puntos(uid, it) },
        )
        val r = if (descarga.vacia) ResultadoUnion(0, 0, 0, 0) else AlmacenSincro(db, uid).unir(descarga)
        // Si se omitió algo (un intento sin su sesión), la próxima vez se vuelve a pedir desde el mismo punto.
        if (r.omitidos == 0) estado.edit().putLong("bajado_$uid", masNuevo).apply()
        return r
    }

    private suspend fun subirSinCerrojo(uid: String): Int {
        comprobarSesion(uid)
        val almacen = AlmacenSincro(db, uid)
        var total = 0
        while (true) {
            val lote = almacen.lote(LOTE)
            if (lote.isEmpty()) return total
            val batch = firestore.batch()
            for (c in lote) {
                val doc = estudiante(uid).collection(c.tabla.valor).document(Documentos.id(c))
                batch.set(doc, Documentos.datos(c), SetOptions.merge())
            }
            // Sin red, Firestore guarda el lote y lo envía solo; aquí se espera la confirmación.
            conTiempo { batch.commit().await() }
            almacen.confirmar(lote)
            total += lote.size
        }
    }

    /** El resumen del panel, como un solo documento: el admin lo lee con una lectura. */
    private suspend fun subirResumen(uid: String) {
        comprobarSesion(uid)
        val (sesiones, intentos) = AlmacenSincro(db, uid).todo()
        val resumen = Panel.resumen(contenido.cursos, sesiones, intentos, Instant.now(), ZoneId.systemDefault()) {
                curso,
                i,
            ->
            runCatching {
                contenido.variantes(curso, i.plantillaId).firstOrNull { it.semilla == i.semilla }?.enunciado
            }.getOrNull()
        }
        conTiempo {
            estudiante(uid).collection("resumen").document("panel").set(
                mapOf(
                    "json" to resumen.aJson(),
                    "version" to resumen.version,
                    Documentos.ACTUALIZADO to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        estado.edit().putLong(
            "resumen_$uid",
            System.currentTimeMillis(),
        ).putInt("resumen_version_$uid", resumen.version).apply()
    }

    private fun resumenAlDia(uid: String) =
        estado.getLong("resumen_$uid", 0) > 0 && estado.getInt("resumen_version_$uid", 0) == ResumenPanel.VERSION

    private suspend fun <T> conTiempo(accion: suspend () -> T): T = try {
        withTimeout(ESPERA_MS) { accion() }
    } catch (e: TimeoutCancellationException) {
        throw SinConexion(e)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw SinConexion(e) else throw e
    }

    private companion object {
        /** La app y la subida en segundo plano no sincronizan a la vez. */
        val cerrojo = Mutex()
        const val LOTE = 200
        const val MARGEN_MS = 2 * 60_000L
        const val ESPERA_MS = 60_000L
        const val MINUTOS_RESUMEN = 10
    }
}
