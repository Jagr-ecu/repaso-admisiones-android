package com.preparate.sincro

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.preparate.dominio.ResumenPanel
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Un estudiante en la lista del admin. `resumen` es null si su dispositivo aún no lo subió. */
data class FichaEstudiante(val uid: String, val nombre: String, val usuario: String, val resumen: ResumenPanel?)

/** Una respuesta, tal como llegó a la nube. */
data class RespuestaSubida(
    val id: String,
    val plantilla: String,
    val respuesta: String,
    val estado: String,
    val pistasUsadas: Int,
    val respondidoEn: Instant?,
    val subidoEn: Instant?,
)

/**
 * Lo que lee el admin. Solo lectura: las reglas de Firestore no le dejan escribir progreso.
 * Cada estudiante cuesta dos lecturas en la lista (su perfil y su resumen).
 */
class PanelAdmin {
    private val db = FirebaseFirestore.getInstance()

    suspend fun estudiantes(): List<FichaEstudiante> = coroutineScope {
        val perfiles = db.collection("usuarios").whereEqualTo("rol", Rol.ESTUDIANTE.valor).get().await().documents
        perfiles.map { p ->
            async {
                FichaEstudiante(p.id, p.getString("nombre").orEmpty(), p.getString("usuario").orEmpty(), resumen(p.id))
            }
        }.awaitAll().sortedBy { it.nombre.lowercase() }
    }

    /** Si el resumen falta o no se puede leer, el panel sigue funcionando sin él. */
    suspend fun resumen(uid: String): ResumenPanel? = runCatching {
        db.collection("estudiantes").document(uid).collection("resumen").document("panel").get().await()
            .getString("json")?.let(ResumenPanel::desdeJson)
    }.getOrNull()

    suspend fun ultimasRespuestas(uid: String, cuantas: Long = 30): List<RespuestaSubida> =
        db.collection("estudiantes").document(uid).collection("intentos")
            .orderBy("respondido", Query.Direction.DESCENDING).limit(cuantas).get().await().documents.map {
                RespuestaSubida(
                    id = it.id,
                    plantilla = it.getString("plantilla_id").orEmpty(),
                    respuesta = it.getString("respuesta").orEmpty(),
                    estado = it.getString("estado").orEmpty(),
                    pistasUsadas = it.getLong("pistas_usadas")?.toInt() ?: 0,
                    respondidoEn = it.getTimestamp("respondido")?.toDate()?.toInstant(),
                    subidoEn = it.getTimestamp(Documentos.ACTUALIZADO)?.toDate()?.toInstant(),
                )
            }
}
