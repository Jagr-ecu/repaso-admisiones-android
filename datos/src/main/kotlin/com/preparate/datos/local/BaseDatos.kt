package com.preparate.datos.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Progreso en el dispositivo: es la fuente de verdad (Firestore guarda la copia en la fase 5).
 * Cada fila lleva `usuario_id` (el UID de Firebase) y `curso_id`: varios estudiantes pueden
 * compartir la tablet, y los ids de temas y plantillas solo son únicos dentro de su curso.
 * Toda consulta filtra por usuario. Las fechas van en milisegundos UTC.
 * Las conversaciones con Kiwi no se guardan nunca.
 */

@Entity(tableName = "sesiones", indices = [Index("usuario_id", "curso_id", "materia_id", "tipo")])
data class SesionFila(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "curso_id") val cursoId: String,
    @ColumnInfo(name = "materia_id") val materiaId: String,
    val tipo: String,
    @ColumnInfo(name = "seccion_id") val seccionId: String?,
    @ColumnInfo(name = "leccion_id") val leccionId: String?,
    /** Repaso: los conceptos, separados por saltos de línea. */
    val conceptos: String,
    val inicio: Long,
    val fin: Long?,
    val nota: Double?,
    val aprobada: Boolean?,
    /** false mientras falte subir el cambio a Firestore (fase 5). */
    val subida: Boolean = false,
)

@Entity(
    tableName = "intentos",
    foreignKeys = [ForeignKey(entity = SesionFila::class, parentColumns = ["id"], childColumns = ["sesion_id"])],
    indices = [Index("sesion_id"), Index("usuario_id", "curso_id", "plantilla_id")],
)
data class IntentoFila(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "curso_id") val cursoId: String,
    @ColumnInfo(name = "materia_id") val materiaId: String,
    @ColumnInfo(name = "sesion_id") val sesionId: String,
    /** Lugar del ejercicio dentro de la sesión, desde 0. */
    val orden: Int,
    @ColumnInfo(name = "plantilla_id") val plantillaId: String,
    val concepto: String,
    @ColumnInfo(name = "plantilla_version") val plantillaVersion: String,
    val semilla: Int,
    val huella: String,
    val mostrado: Long,
    val repite: String?,
    val respondido: Long?,
    val respuesta: String?,
    val estado: String?,
    val correcta: Boolean?,
    @ColumnInfo(name = "error_comun") val errorComun: String?,
    @ColumnInfo(name = "pistas_usadas") val pistasUsadas: Int,
    val puntaje: Double?,
    val subida: Boolean = false,
)

/** El repaso espaciado (FSRS) de cada concepto. */
@Entity(tableName = "memoria", primaryKeys = ["usuario_id", "curso_id", "concepto"])
data class MemoriaFila(
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "curso_id") val cursoId: String,
    @ColumnInfo(name = "materia_id") val materiaId: String,
    val concepto: String,
    val estado: Int,
    val paso: Int?,
    val estabilidad: Double?,
    val dificultad: Double?,
    val vence: Long,
    @ColumnInfo(name = "ultimo_repaso") val ultimoRepaso: Long?,
    val subida: Boolean = false,
)

/** Puntos una sola vez por motivo y referencia (p. ej. por tema completado). */
@Entity(tableName = "puntos", primaryKeys = ["usuario_id", "curso_id", "motivo", "referencia"])
data class PuntosFila(
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    @ColumnInfo(name = "curso_id") val cursoId: String,
    val motivo: String,
    val referencia: String,
    val cantidad: Int,
    val creado: Long,
    val subida: Boolean = false,
)

data class ConceptoVence(val concepto: String, val vence: Long)

@Dao
interface EstudioDao {
    @Insert
    suspend fun insertarSesion(sesion: SesionFila)

    @Update
    suspend fun actualizarSesion(sesion: SesionFila)

    @Query("SELECT * FROM sesiones WHERE usuario_id = :usuario AND id = :id")
    suspend fun sesion(usuario: String, id: String): SesionFila?

    @Query("SELECT * FROM sesiones WHERE usuario_id = :usuario AND curso_id = :curso AND materia_id = :materia")
    suspend fun sesionesDeMateria(usuario: String, curso: String, materia: String): List<SesionFila>

    /** Cambia cada vez que se abre o cierra una sesión: la pantalla del camino se actualiza sola. */
    @Query("SELECT * FROM sesiones WHERE usuario_id = :usuario AND curso_id = :curso")
    fun observarSesiones(usuario: String, curso: String): Flow<List<SesionFila>>

    @Query(
        "SELECT * FROM sesiones WHERE usuario_id = :usuario AND curso_id = :curso AND materia_id = :materia " +
            "AND tipo = :tipo AND fin IS NULL ORDER BY inicio DESC",
    )
    suspend fun abiertas(usuario: String, curso: String, materia: String, tipo: String): List<SesionFila>

    @Insert
    suspend fun insertarIntentos(intentos: List<IntentoFila>)

    @Update
    suspend fun actualizarIntento(intento: IntentoFila)

    @Query("SELECT * FROM intentos WHERE usuario_id = :usuario AND id = :id")
    suspend fun intento(usuario: String, id: String): IntentoFila?

    @Query("SELECT * FROM intentos WHERE usuario_id = :usuario AND sesion_id = :sesion ORDER BY orden")
    suspend fun intentosDeSesion(usuario: String, sesion: String): List<IntentoFila>

    @Query("SELECT huella FROM intentos WHERE usuario_id = :usuario AND curso_id = :curso AND plantilla_id = :plantilla")
    suspend fun huellas(usuario: String, curso: String, plantilla: String): List<String>

    @Query("SELECT * FROM memoria WHERE usuario_id = :usuario AND curso_id = :curso AND concepto = :concepto")
    suspend fun memoria(usuario: String, curso: String, concepto: String): MemoriaFila?

    @Upsert
    suspend fun guardarMemoria(memoria: MemoriaFila)

    @Query("SELECT concepto, vence FROM memoria WHERE usuario_id = :usuario AND curso_id = :curso AND materia_id = :materia")
    suspend fun vencimientos(usuario: String, curso: String, materia: String): List<ConceptoVence>

    /** Devuelve -1 si ya tenía esos puntos. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun otorgar(puntos: PuntosFila): Long

    @Query("SELECT COUNT(*) > 0 FROM puntos WHERE usuario_id = :usuario AND curso_id = :curso AND motivo = :motivo AND referencia = :referencia")
    suspend fun tienePuntos(usuario: String, curso: String, motivo: String, referencia: String): Boolean

    @Query("SELECT COALESCE(SUM(cantidad), 0) FROM puntos WHERE usuario_id = :usuario AND curso_id = :curso")
    fun observarPuntos(usuario: String, curso: String): Flow<Int>
}

/**
 * La base local. Cada cambio de esquema sube `version` y lleva una migración probada:
 * el progreso de los estudiantes nunca se borra al actualizar la app.
 */
@Database(
    entities = [SesionFila::class, IntentoFila::class, MemoriaFila::class, PuntosFila::class],
    version = 1,
    exportSchema = true,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun estudio(): EstudioDao

    companion object {
        const val NOMBRE = "preparate.db"

        fun abrir(context: Context): BaseDatos =
            Room.databaseBuilder(context.applicationContext, BaseDatos::class.java, NOMBRE).build()

        /** Solo para tests. */
        fun enMemoria(context: Context): BaseDatos =
            Room.inMemoryDatabaseBuilder(context.applicationContext, BaseDatos::class.java).build()
    }
}
