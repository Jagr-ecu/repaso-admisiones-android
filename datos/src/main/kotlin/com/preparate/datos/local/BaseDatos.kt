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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Progreso en el dispositivo: es la fuente de verdad. Firestore guarda la copia.
 * Cada fila lleva `usuario_id` (el UID de Firebase) y `curso_id`: varios estudiantes pueden
 * compartir la tablet, y los ids de temas y plantillas solo son únicos dentro de su curso.
 * Toda consulta filtra por usuario. Las fechas van en milisegundos UTC.
 * Las conversaciones con Kiwi no se guardan nunca.
 *
 * Desde la versión 2, cada cambio en `sesiones`, `intentos` y `puntos` entra en `cola_subida` con un
 * trigger de SQLite: queda en la misma transacción que el cambio, así que nada se olvida de subir.
 * Las columnas `subida` de la versión 1 ya no se usan.
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
    /** Sin uso desde la versión 2 (lo reemplaza `cola_subida`). */
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
    /** Sin uso desde la versión 2. */
    val subida: Boolean = false,
    /** Pistas que llevaba al responder: con ellas se reconstruye `memoria`. Null en intentos de la versión 1. */
    @ColumnInfo(name = "pistas_al_responder") val pistasAlResponder: Int? = null,
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

/**
 * Lo que falta subir a Firestore. Una fila por cambio; varias filas del mismo registro se suben una
 * sola vez, con su estado actual. `ref` es el id de la sesión o el intento, o `motivo:referencia`
 * en los puntos. Solo se borra lo que ya subió: un cambio que llega durante la subida se queda.
 */
@Entity(tableName = "cola_subida", indices = [Index("usuario_id")])
data class ColaFila(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "usuario_id") val usuarioId: String,
    val tabla: String,
    @ColumnInfo(name = "curso_id") val cursoId: String,
    val ref: String,
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

@Dao
interface SincroDao {
    @Query("SELECT COUNT(*) FROM (SELECT DISTINCT tabla, curso_id, ref FROM cola_subida WHERE usuario_id = :usuario)")
    fun observarPendientes(usuario: String): Flow<Int>

    @Query("SELECT * FROM cola_subida WHERE usuario_id = :usuario ORDER BY id LIMIT :cuantos")
    suspend fun cola(usuario: String, cuantos: Int): List<ColaFila>

    /** Quita lo que ya subió; lo que entró después (id mayor) se queda para la próxima. */
    @Query("DELETE FROM cola_subida WHERE usuario_id = :usuario AND tabla = :tabla AND curso_id = :curso AND ref = :ref AND id <= :hasta")
    suspend fun quitar(usuario: String, tabla: String, curso: String, ref: String, hasta: Long)

    @Query("DELETE FROM cola_subida WHERE usuario_id = :usuario AND tabla = :tabla AND curso_id = :curso AND ref = :ref")
    suspend fun quitarTodo(usuario: String, tabla: String, curso: String, ref: String)

    @Query("SELECT * FROM sesiones WHERE usuario_id = :usuario AND id = :id")
    suspend fun sesion(usuario: String, id: String): SesionFila?

    @Query("SELECT * FROM intentos WHERE usuario_id = :usuario AND id = :id")
    suspend fun intento(usuario: String, id: String): IntentoFila?

    @Query("SELECT * FROM puntos WHERE usuario_id = :usuario AND curso_id = :curso AND motivo = :motivo AND referencia = :referencia")
    suspend fun puntos(usuario: String, curso: String, motivo: String, referencia: String): PuntosFila?

    @Query("SELECT * FROM sesiones WHERE usuario_id = :usuario")
    suspend fun sesiones(usuario: String): List<SesionFila>

    @Query("SELECT * FROM intentos WHERE usuario_id = :usuario")
    suspend fun intentos(usuario: String): List<IntentoFila>

    @Query("SELECT * FROM intentos WHERE usuario_id = :usuario AND curso_id = :curso")
    suspend fun intentosDeCurso(usuario: String, curso: String): List<IntentoFila>

    @Upsert
    suspend fun guardarSesion(sesion: SesionFila)

    @Upsert
    suspend fun guardarIntento(intento: IntentoFila)

    /** Devuelve -1 si ya los tenía. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun otorgar(puntos: PuntosFila): Long

    @Query("DELETE FROM memoria WHERE usuario_id = :usuario AND curso_id = :curso")
    suspend fun borrarMemoria(usuario: String, curso: String)

    @Insert
    suspend fun insertarMemoria(memoria: List<MemoriaFila>)
}

/**
 * La base local. Cada cambio de esquema sube `version` y lleva una migración probada:
 * el progreso de los estudiantes nunca se borra al actualizar la app.
 */
@Database(
    entities = [SesionFila::class, IntentoFila::class, MemoriaFila::class, PuntosFila::class, ColaFila::class],
    version = 2,
    exportSchema = true,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun estudio(): EstudioDao
    abstract fun sincro(): SincroDao

    companion object {
        const val NOMBRE = "preparate.db"

        /** Usuario de la vista previa de depuración: su progreso nunca entra en la cola de subida. */
        const val SIN_NUBE = "vista-previa"

        @Volatile private var instancia: BaseDatos? = null

        /**
         * Una sola base por proceso: la app y la subida en segundo plano comparten la misma, y así
         * las pantallas se enteran de lo que baja de la nube.
         */
        fun abrir(context: Context): BaseDatos = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, BaseDatos::class.java, NOMBRE)
                .addMigrations(MIGRACION_1_2)
                .addCallback(CrearTriggers)
                .build()
                .also { instancia = it }
        }

        /** Solo para tests. */
        fun enMemoria(context: Context): BaseDatos =
            Room.inMemoryDatabaseBuilder(context.applicationContext, BaseDatos::class.java)
                .addCallback(CrearTriggers)
                .build()

        /**
         * Triggers que llenan `cola_subida`. Se crean al crear la base y en la migración a la versión 2.
         * Room no los conoce: por eso viven aquí y no en las entidades.
         */
        val TRIGGERS: List<String> = buildList {
            fun encolar(tabla: String, evento: String, ref: String) = add(
                "CREATE TRIGGER IF NOT EXISTS cola_${tabla}_${evento.lowercase()} AFTER $evento ON $tabla " +
                    "WHEN NEW.usuario_id <> '$SIN_NUBE' BEGIN " +
                    "INSERT INTO cola_subida (usuario_id, tabla, curso_id, ref) " +
                    "VALUES (NEW.usuario_id, '$tabla', NEW.curso_id, $ref); END",
            )
            encolar("sesiones", "INSERT", "NEW.id")
            encolar("sesiones", "UPDATE", "NEW.id")
            encolar("intentos", "INSERT", "NEW.id")
            encolar("intentos", "UPDATE", "NEW.id")
            encolar("puntos", "INSERT", "NEW.motivo || ':' || NEW.referencia")
        }

        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intentos ADD COLUMN pistas_al_responder INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cola_subida` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`usuario_id` TEXT NOT NULL, `tabla` TEXT NOT NULL, `curso_id` TEXT NOT NULL, `ref` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cola_subida_usuario_id` ON `cola_subida` (`usuario_id`)")
                TRIGGERS.forEach(db::execSQL)
                // Lo guardado antes de la versión 2 se sube entero, ya con el formato nuevo.
                val encolar = "INSERT INTO cola_subida (usuario_id, tabla, curso_id, ref)"
                val sinNube = "WHERE usuario_id <> '$SIN_NUBE'"
                db.execSQL("$encolar SELECT usuario_id, 'sesiones', curso_id, id FROM sesiones $sinNube")
                db.execSQL("$encolar SELECT usuario_id, 'intentos', curso_id, id FROM intentos $sinNube")
                db.execSQL("$encolar SELECT usuario_id, 'puntos', curso_id, motivo || ':' || referencia FROM puntos $sinNube")
            }
        }
    }

    private object CrearTriggers : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            TRIGGERS.forEach(db::execSQL)
        }
    }
}
