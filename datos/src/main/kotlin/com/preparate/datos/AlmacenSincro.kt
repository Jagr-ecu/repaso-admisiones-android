package com.preparate.datos

import androidx.room.withTransaction
import com.preparate.datos.local.BaseDatos
import com.preparate.datos.local.IntentoFila
import com.preparate.datos.local.MemoriaFila
import com.preparate.datos.local.PuntosFila
import com.preparate.datos.local.SesionFila
import com.preparate.dominio.Intento
import com.preparate.dominio.Sesion
import com.preparate.dominio.Union
import kotlinx.coroutines.flow.Flow

/** Tablas que se suben. El nombre es también el de la colección en Firestore. */
enum class Tabla(val valor: String) {
    SESIONES("sesiones"),
    INTENTOS("intentos"),
    PUNTOS("puntos");

    companion object {
        fun desde(valor: String): Tabla? = entries.firstOrNull { it.valor == valor }
    }
}

/** Un registro para subir, con su estado actual. `hasta` es la última fila de la cola que cubre. */
sealed interface Cambio {
    val cursoId: String
    val ref: String
    val hasta: Long
    val tabla: Tabla

    data class DeSesion(val fila: SesionFila, override val hasta: Long) : Cambio {
        override val cursoId get() = fila.cursoId
        override val ref get() = fila.id
        override val tabla get() = Tabla.SESIONES
    }

    data class DeIntento(val fila: IntentoFila, override val hasta: Long) : Cambio {
        override val cursoId get() = fila.cursoId
        override val ref get() = fila.id
        override val tabla get() = Tabla.INTENTOS
    }

    data class DePuntos(val fila: PuntosFila, override val hasta: Long) : Cambio {
        override val cursoId get() = fila.cursoId
        override val ref get() = "${fila.motivo}:${fila.referencia}"
        override val tabla get() = Tabla.PUNTOS
    }
}

/** Lo que bajó de la nube, ya leído. Las filas traen el `usuarioId` del estudiante. */
data class Descarga(
    val sesiones: List<SesionFila> = emptyList(),
    val intentos: List<IntentoFila> = emptyList(),
    val puntos: List<PuntosFila> = emptyList(),
) {
    val vacia get() = sesiones.isEmpty() && intentos.isEmpty() && puntos.isEmpty()
}

/** Cuánto cambió en el dispositivo al unir una descarga. */
data class ResultadoUnion(val sesiones: Int, val intentos: Int, val puntos: Int, val omitidos: Int) {
    val total get() = sesiones + intentos + puntos
}

/**
 * El lado de Room de la sincronización, para UN estudiante: qué falta subir, confirmar lo subido y
 * unir lo que baja. No sabe nada de Firestore; eso vive en `:sincro`.
 */
class AlmacenSincro(private val db: BaseDatos, private val usuarioId: String) {
    private val dao = db.sincro()

    /** Registros distintos que esperan subir. */
    fun observarPendientes(): Flow<Int> = dao.observarPendientes(usuarioId)

    /**
     * Los próximos registros para subir, cada uno con su estado actual y sin repetir. Si un registro
     * ya no existe (no debería pasar), sus filas de la cola se quitan.
     */
    suspend fun lote(cuantos: Int): List<Cambio> {
        val cola = dao.cola(usuarioId, cuantos)
        val ultimos = cola.groupBy { Triple(it.tabla, it.cursoId, it.ref) }.mapValues { (_, filas) -> filas.maxOf { it.id } }
        return ultimos.mapNotNull { (clave, hasta) ->
            val (tabla, curso, ref) = clave
            val cambio = when (Tabla.desde(tabla)) {
                Tabla.SESIONES -> dao.sesion(usuarioId, ref)?.let { Cambio.DeSesion(it, hasta) }
                Tabla.INTENTOS -> dao.intento(usuarioId, ref)?.let { Cambio.DeIntento(it, hasta) }
                Tabla.PUNTOS -> {
                    val motivo = ref.substringBefore(':')
                    dao.puntos(usuarioId, curso, motivo, ref.substringAfter(':'))?.let { Cambio.DePuntos(it, hasta) }
                }
                null -> null
            }
            if (cambio == null) dao.quitar(usuarioId, tabla, curso, ref, hasta)
            cambio
        }
    }

    /** Firestore ya confirmó estos cambios. Lo que entró después en la cola se queda. */
    suspend fun confirmar(subidos: List<Cambio>) = db.withTransaction {
        for (c in subidos) dao.quitar(usuarioId, c.tabla.valor, c.cursoId, c.ref, c.hasta)
    }

    /**
     * Une lo que bajó con lo que hay (reglas en `dominio.Union`). Cuando lo unido es igual a lo de
     * la nube, no hace falta volver a subirlo y sale de la cola. Si cambió algún intento, `memoria`
     * se reconstruye desde los intentos.
     */
    suspend fun unir(descarga: Descarga): ResultadoUnion = db.withTransaction {
        var sesiones = 0
        var intentos = 0
        var puntos = 0
        var omitidos = 0
        val cursosConIntentos = mutableSetOf<String>()

        for (remota in descarga.sesiones) {
            if (remota.usuarioId != usuarioId) { omitidos++; continue }
            val local = dao.sesion(usuarioId, remota.id)
            val unida = Union.sesion(local?.aDominio(), remota.aDominio())
            val fila = (local ?: remota).conSesion(unida).copy(subida = false)
            if (fila == local?.copy(subida = false)) continue
            dao.guardarSesion(fila)
            if (fila == remota.copy(subida = false)) dao.quitarTodo(usuarioId, Tabla.SESIONES.valor, fila.cursoId, fila.id)
            sesiones++
        }

        for (remoto in descarga.intentos) {
            // Un intento sin su sesión no se puede guardar (llave foránea): se omite hasta que llegue.
            if (remoto.usuarioId != usuarioId || dao.sesion(usuarioId, remoto.sesionId) == null) { omitidos++; continue }
            val local = dao.intento(usuarioId, remoto.id)
            val unido = Union.intento(local?.aDominio(), remoto.aDominio())
            val fila = (local ?: remoto).conIntento(unido).copy(subida = false)
            if (fila == local?.copy(subida = false)) continue
            dao.guardarIntento(fila)
            if (fila == remoto.copy(subida = false)) dao.quitarTodo(usuarioId, Tabla.INTENTOS.valor, fila.cursoId, fila.id)
            cursosConIntentos += fila.cursoId
            intentos++
        }

        for (remotos in descarga.puntos) {
            if (remotos.usuarioId != usuarioId) { omitidos++; continue }
            if (dao.otorgar(remotos.copy(subida = false)) != -1L) {
                dao.quitarTodo(usuarioId, Tabla.PUNTOS.valor, remotos.cursoId, "${remotos.motivo}:${remotos.referencia}")
                puntos++
            }
        }

        cursosConIntentos.forEach { reconstruirMemoria(it) }
        ResultadoUnion(sesiones, intentos, puntos, omitidos)
    }

    /** Recalcula el repaso espaciado de un curso desde sus intentos, en orden. */
    suspend fun reconstruirMemoria(cursoId: String) = db.withTransaction {
        val filas = dao.intentosDeCurso(usuarioId, cursoId)
        val materiaDe = filas.associate { it.concepto to it.materiaId }
        val tarjetas = Union.memoria(filas.map { it.aDominio() })
        dao.borrarMemoria(usuarioId, cursoId)
        dao.insertarMemoria(
            tarjetas.map { (concepto, t) ->
                MemoriaFila(
                    usuarioId, cursoId, materiaDe.getValue(concepto), concepto, t.estado.valor, t.paso, t.estabilidad,
                    t.dificultad, t.vence.toEpochMilli(), t.ultimoRepaso?.toEpochMilli(),
                )
            },
        )
    }

    /** Todo lo del estudiante, para el resumen del panel. */
    suspend fun todo(): Pair<List<Sesion>, List<Intento>> =
        dao.sesiones(usuarioId).map { it.aDominio() } to dao.intentos(usuarioId).map { it.aDominio() }
}

/** La fila con los campos que cambian en una sesión; los demás no cambian nunca. */
private fun SesionFila.conSesion(s: Sesion) = copy(
    fin = s.fin?.toEpochMilli(),
    nota = s.nota,
    aprobada = s.aprobada,
)

private fun IntentoFila.conIntento(i: Intento) = copy(
    respondido = i.respondido?.toEpochMilli(),
    respuesta = i.respuesta,
    estado = i.estado?.valor,
    correcta = i.correcta,
    errorComun = i.errorComun,
    pistasUsadas = i.pistasUsadas,
    pistasAlResponder = i.pistasAlResponder,
    puntaje = i.puntaje,
    revision = i.revision,
)
