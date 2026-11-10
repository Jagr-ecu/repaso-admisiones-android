package com.preparate.dominio

import java.time.Instant

/*
 * Desbloqueo del camino de una materia. Puerto de app/domain/progreso.py.
 *
 * - Las secciones están todas abiertas: el estudiante empieza por la que quiera.
 * - Dentro de cada sección, los temas van en orden y no se saltan.
 * - La prueba de una sección se abre al terminar todos sus temas; aprobarla completa la sección.
 * - Si se reprueba una prueba, hay que hacer el repaso de esa sección antes de reintentarla.
 * - El examen final se habilita al completar todas las secciones.
 * - Una prueba con un Writing sin calificar queda pendiente: no aprueba ni reprueba hasta que
 *   Kiwi la califique, y mientras tanto no se puede volver a dar.
 *
 * El paquete solo trae temas publicados; una sección sin temas no aparece en el camino.
 */

enum class EstadoNodo(val valor: String) {
    BLOQUEADO("bloqueado"),
    DISPONIBLE("disponible"),
    COMPLETADO("completado"),

    /** Solo el examen final: espera la nota de un Writing. */
    PENDIENTE("pendiente"),
}

enum class EstadoPrueba(val valor: String) {
    /** Faltan temas de la sección. */
    BLOQUEADA("bloqueada"),
    DISPONIBLE("disponible"),

    /** Reprobó: primero el repaso, luego reintenta. */
    REPASO_REQUERIDO("repaso_requerido"),

    /** Espera la nota de un Writing. */
    PENDIENTE("pendiente"),
    APROBADA("aprobada"),
}

/** `aprobada` es null mientras espera la nota de un Writing. */
data class ResultadoPrueba(val seccion: String, val aprobada: Boolean?, val fecha: Instant)

data class RepasoHecho(val seccion: String, val fecha: Instant)

/** Lo que el estudiante ya hizo en una materia de un curso. */
data class Historial(
    val leccionesCompletadas: Set<String> = emptySet(),
    val pruebas: List<ResultadoPrueba> = emptyList(),
    val repasos: List<RepasoHecho> = emptyList(),
    val examenAprobado: Boolean = false,
    val examenPendiente: Boolean = false,
)

data class ProgresoLeccion(val leccion: LeccionResumen, val estado: EstadoNodo)

data class ProgresoSeccion(
    val seccion: SeccionCurso,
    val estado: EstadoNodo,
    val lecciones: List<ProgresoLeccion>,
    val prueba: EstadoPrueba,
)

data class ProgresoMateria(val materiaId: String, val secciones: List<ProgresoSeccion>, val examenFinal: EstadoNodo) {
    fun seccion(id: String): ProgresoSeccion? = secciones.firstOrNull { it.seccion.id == id }

    fun leccion(id: String): ProgresoLeccion? = secciones.flatMap { it.lecciones }.firstOrNull { it.leccion.id == id }
}

fun calcularProgreso(materia: MateriaCurso, historial: Historial): ProgresoMateria {
    val aprobadas = historial.pruebas.filter { it.aprobada == true }.map { it.seccion }.toSet()
    val secciones = materia.secciones.filter { it.lecciones.isNotEmpty() }.map { s ->
        if (s.id in aprobadas) {
            // Sección completada: sus temas quedan abiertos para practicar en cualquier orden.
            val lecciones = s.lecciones.map {
                ProgresoLeccion(it, if (it.id in historial.leccionesCompletadas) EstadoNodo.COMPLETADO else EstadoNodo.DISPONIBLE)
            }
            ProgresoSeccion(s, EstadoNodo.COMPLETADO, lecciones, EstadoPrueba.APROBADA)
        } else {
            val lecciones = leccionesEnOrden(s.lecciones, historial)
            val todas = lecciones.all { it.estado == EstadoNodo.COMPLETADO }
            ProgresoSeccion(s, EstadoNodo.DISPONIBLE, lecciones, estadoPrueba(s.id, todas, historial))
        }
    }
    val examen = when {
        secciones.isEmpty() || secciones.any { it.estado != EstadoNodo.COMPLETADO } -> EstadoNodo.BLOQUEADO
        historial.examenAprobado -> EstadoNodo.COMPLETADO
        historial.examenPendiente -> EstadoNodo.PENDIENTE
        else -> EstadoNodo.DISPONIBLE
    }
    return ProgresoMateria(materia.id, secciones, examen)
}

private fun leccionesEnOrden(lecciones: List<LeccionResumen>, historial: Historial): List<ProgresoLeccion> {
    var siguienteAbierta = true
    return lecciones.map {
        val estado = when {
            it.id in historial.leccionesCompletadas -> EstadoNodo.COMPLETADO
            siguienteAbierta -> EstadoNodo.DISPONIBLE.also { siguienteAbierta = false }
            else -> EstadoNodo.BLOQUEADO
        }
        ProgresoLeccion(it, estado)
    }
}

private fun estadoPrueba(seccionId: String, temasTerminados: Boolean, historial: Historial): EstadoPrueba {
    if (!temasTerminados) return EstadoPrueba.BLOQUEADA
    val pruebas = historial.pruebas.filter { it.seccion == seccionId }
    if (pruebas.isNotEmpty()) {
        val ultima = pruebas.maxBy { it.fecha }
        if (ultima.aprobada == null) return EstadoPrueba.PENDIENTE
        val repasoDespues = historial.repasos.any { it.seccion == seccionId && it.fecha > ultima.fecha }
        if (!ultima.aprobada && !repasoDespues) return EstadoPrueba.REPASO_REQUERIDO
    }
    return EstadoPrueba.DISPONIBLE
}
