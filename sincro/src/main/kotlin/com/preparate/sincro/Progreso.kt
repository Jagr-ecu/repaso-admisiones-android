package com.preparate.sincro

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID

data class Intento(
    val id: String = UUID.randomUUID().toString(),
    val sesionId: String,
    val leccion: String,
    val plantilla: String,
    val semilla: Int,
    val respuesta: String,
    val estado: String,
    val errorComun: String?,
    val pistasUsadas: Int,
    val respondidoEn: Instant = Instant.now(),
)

data class FichaEstudiante(val uid: String, val nombre: String, val usuario: String)

data class IntentoSubido(
    val id: String,
    val plantilla: String,
    val respuesta: String,
    val estado: String,
    val pistasUsadas: Int,
    val respondidoEn: Instant?,
    val subidoEn: Instant?,
)

/**
 * PROVISIONAL (fase 1): el intento se escribe directo en Firestore y su caché sin
 * conexión lo guarda hasta que vuelve Internet. En la fase 5 esto pasa por Room y
 * la cola de subida.
 */
class Progreso {
    private val db = FirebaseFirestore.getInstance()

    private fun intentos(uid: String) = db.collection("estudiantes").document(uid).collection("intentos")

    /** No espera al servidor: sin Internet, Firestore lo guarda y lo sube después. */
    fun registrar(uid: String, intento: Intento) {
        intentos(uid).document(intento.id).set(
            mapOf(
                "sesion_id" to intento.sesionId,
                "leccion" to intento.leccion,
                "plantilla" to intento.plantilla,
                "semilla" to intento.semilla,
                "respuesta" to intento.respuesta,
                "estado" to intento.estado,
                "error_comun" to intento.errorComun,
                "pistas_usadas" to intento.pistasUsadas,
                "respondido_en" to Timestamp(intento.respondidoEn.epochSecond, intento.respondidoEn.nano),
                "subido_en" to FieldValue.serverTimestamp(),
            ),
        )
    }

    /** Cuántos intentos de este estudiante aún esperan Internet en este dispositivo. */
    fun pendientes(uid: String): Flow<Int> = callbackFlow {
        val registro = intentos(uid).addSnapshotListener(MetadataChanges.INCLUDE) { foto, _ ->
            if (foto != null) trySend(foto.documents.count { it.metadata.hasPendingWrites() })
        }
        awaitClose { registro.remove() }
    }

    suspend fun estudiantes(): List<FichaEstudiante> =
        db.collection("usuarios").whereEqualTo("rol", Rol.ESTUDIANTE.valor).get().await().documents
            .map { FichaEstudiante(it.id, it.getString("nombre").orEmpty(), it.getString("usuario").orEmpty()) }
            .sortedBy { it.nombre.lowercase() }

    suspend fun intentosDe(uid: String): List<IntentoSubido> =
        intentos(uid).orderBy("respondido_en", Query.Direction.DESCENDING).limit(50).get().await().documents.map {
            IntentoSubido(
                id = it.id,
                plantilla = it.getString("plantilla").orEmpty(),
                respuesta = it.getString("respuesta").orEmpty(),
                estado = it.getString("estado").orEmpty(),
                pistasUsadas = it.getLong("pistas_usadas")?.toInt() ?: 0,
                respondidoEn = it.getTimestamp("respondido_en")?.toInstant(),
                subidoEn = it.getTimestamp("subido_en")?.toInstant(),
            )
        }
}
