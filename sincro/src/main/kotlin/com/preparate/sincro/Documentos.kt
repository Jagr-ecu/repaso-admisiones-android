package com.preparate.sincro

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.preparate.datos.Cambio
import com.preparate.datos.local.IntentoFila
import com.preparate.datos.local.PuntosFila
import com.preparate.datos.local.SesionFila
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.TipoSesion
import java.util.Date

/*
 * El formato de los documentos en Firestore (PLAN-MIGRACION.md, sección 4):
 *
 *   estudiantes/{uid}/sesiones/{uuid}
 *   estudiantes/{uid}/intentos/{uuid}
 *   estudiantes/{uid}/puntos/{curso}~{motivo}~{referencia}
 *   estudiantes/{uid}/resumen/panel
 *
 * Las fechas van como Timestamp y `actualizado` lo pone el servidor: con él se baja solo lo que
 * cambió. Al subir se omiten los campos vacíos y se usa `merge`, así un valor vacío nunca borra
 * uno lleno (p. ej. la nota de un Writing calificado en otro dispositivo).
 * El usuario sale de la ruta, nunca de un campo.
 */
internal object Documentos {
    const val ACTUALIZADO = "actualizado"
    const val VERSION = 1

    fun idPuntos(f: PuntosFila) = "${f.cursoId}~${f.motivo}~${f.referencia}"

    fun id(c: Cambio): String = when (c) {
        is Cambio.DeSesion -> c.fila.id
        is Cambio.DeIntento -> c.fila.id
        is Cambio.DePuntos -> idPuntos(c.fila)
    }

    fun datos(c: Cambio): Map<String, Any> = when (c) {
        is Cambio.DeSesion -> sesion(c.fila)
        is Cambio.DeIntento -> intento(c.fila)
        is Cambio.DePuntos -> puntos(c.fila)
    }

    private fun sinVacios(vararg campos: Pair<String, Any?>): Map<String, Any> = buildMap {
        for ((k, v) in campos) if (v != null) put(k, v)
        put("formato", VERSION)
        put(ACTUALIZADO, FieldValue.serverTimestamp())
    }

    private fun fecha(milis: Long?): Timestamp? = milis?.let { Timestamp(Date(it)) }

    private fun milis(d: DocumentSnapshot, campo: String): Long? = d.getTimestamp(campo)?.toDate()?.time

    fun sesion(f: SesionFila) = sinVacios(
        "curso_id" to f.cursoId,
        "materia_id" to f.materiaId,
        "tipo" to f.tipo,
        "seccion_id" to f.seccionId,
        "leccion_id" to f.leccionId,
        "conceptos" to f.conceptos.split("\n").filter { it.isNotEmpty() },
        "inicio" to fecha(f.inicio),
        "fin" to fecha(f.fin),
        "nota" to f.nota,
        "aprobada" to f.aprobada,
    )

    fun intento(f: IntentoFila) = sinVacios(
        "curso_id" to f.cursoId,
        "materia_id" to f.materiaId,
        "sesion_id" to f.sesionId,
        "orden" to f.orden,
        "plantilla_id" to f.plantillaId,
        "concepto" to f.concepto,
        "plantilla_version" to f.plantillaVersion,
        "semilla" to f.semilla,
        "huella" to f.huella,
        "mostrado" to fecha(f.mostrado),
        "repite" to f.repite,
        "respondido" to fecha(f.respondido),
        "respuesta" to f.respuesta,
        "estado" to f.estado,
        "correcta" to f.correcta,
        "error_comun" to f.errorComun,
        "pistas_usadas" to f.pistasUsadas,
        "pistas_al_responder" to f.pistasAlResponder,
        "puntaje" to f.puntaje,
        "revision" to f.revision,
    )

    fun puntos(f: PuntosFila) = sinVacios(
        "curso_id" to f.cursoId,
        "motivo" to f.motivo,
        "referencia" to f.referencia,
        "cantidad" to f.cantidad,
        "creado" to fecha(f.creado),
    )

    // --- Al bajar: null si el documento no tiene el formato (p. ej. los de la prueba de la fase 1) ---

    fun sesion(uid: String, d: DocumentSnapshot): SesionFila? {
        val tipo = d.getString("tipo")?.takeIf { t -> TipoSesion.entries.any { it.valor == t } } ?: return null
        return SesionFila(
            id = d.id,
            usuarioId = uid,
            cursoId = d.getString("curso_id") ?: return null,
            materiaId = d.getString("materia_id") ?: return null,
            tipo = tipo,
            seccionId = d.getString("seccion_id"),
            leccionId = d.getString("leccion_id"),
            conceptos = (d.get("conceptos") as? List<*>)?.filterIsInstance<String>().orEmpty().joinToString("\n"),
            inicio = milis(d, "inicio") ?: return null,
            fin = milis(d, "fin"),
            nota = d.getDouble("nota"),
            aprobada = d.getBoolean("aprobada"),
        )
    }

    fun intento(uid: String, d: DocumentSnapshot): IntentoFila? {
        val estado = d.getString("estado")
        if (estado != null && EstadoRespuesta.entries.none { it.valor == estado }) return null
        return IntentoFila(
            id = d.id,
            usuarioId = uid,
            cursoId = d.getString("curso_id") ?: return null,
            materiaId = d.getString("materia_id") ?: return null,
            sesionId = d.getString("sesion_id") ?: return null,
            orden = d.getLong("orden")?.toInt() ?: return null,
            plantillaId = d.getString("plantilla_id") ?: return null,
            concepto = d.getString("concepto") ?: return null,
            plantillaVersion = d.getString("plantilla_version") ?: return null,
            semilla = d.getLong("semilla")?.toInt() ?: return null,
            huella = d.getString("huella") ?: return null,
            mostrado = milis(d, "mostrado") ?: return null,
            repite = d.getString("repite"),
            respondido = milis(d, "respondido"),
            respuesta = d.getString("respuesta"),
            estado = estado,
            correcta = d.getBoolean("correcta"),
            errorComun = d.getString("error_comun"),
            pistasUsadas = d.getLong("pistas_usadas")?.toInt() ?: 0,
            puntaje = d.getDouble("puntaje"),
            pistasAlResponder = d.getLong("pistas_al_responder")?.toInt(),
            revision = d.getString("revision"),
        )
    }

    fun puntos(uid: String, d: DocumentSnapshot): PuntosFila? {
        return PuntosFila(
            usuarioId = uid,
            cursoId = d.getString("curso_id") ?: return null,
            motivo = d.getString("motivo") ?: return null,
            referencia = d.getString("referencia") ?: return null,
            cantidad = d.getLong("cantidad")?.toInt() ?: return null,
            creado = milis(d, "creado") ?: return null,
        )
    }
}
