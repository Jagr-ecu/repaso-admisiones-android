package com.preparate.dominio

import java.time.Instant
import kotlin.random.Random

/*
 * Qué ejercicio toca: variantes nunca vistas y repasos intercalados, siempre dentro de UNA materia.
 * Puerto de app/scheduler/seleccion.py. En la web las variantes se generan con una semilla al azar;
 * aquí se elige entre las variantes que ya trae el paquete.
 */
object Seleccion {
    /** El texto de lectura del enunciado (todo menos el último párrafo); vacío si no tiene. */
    fun lecturaDe(variante: Variante): String {
        val corte = variante.enunciado.lastIndexOf("\n\n")
        return if (corte < 0) "" else variante.enunciado.substring(0, corte)
    }

    /**
     * Una variante que el estudiante no haya visto, sin una lectura ya usada en esta prueba y sin
     * repetir una respuesta de la sesión. Si no queda ninguna así, cede en ese orden: primero
     * repite un ejercicio visto, luego la respuesta, y por último la lectura. Es mejor que
     * bloquear la práctica, y `validate-content` avisa de las plantillas con pocas variantes.
     */
    fun varianteNueva(
        variantes: List<Variante>,
        vistas: Set<String>,
        azar: Random,
        lecturasUsadas: Set<String> = emptySet(),
        respuestasUsadas: Set<String> = emptySet(),
    ): Variante {
        require(variantes.isNotEmpty()) { "la plantilla no tiene variantes" }
        val orden = variantes.shuffled(azar)
        fun lecturaLibre(v: Variante) = lecturaDe(v) !in lecturasUsadas
        fun respuestaLibre(v: Variante) = v.respuesta == null || v.respuesta !in respuestasUsadas
        return orden.firstOrNull { lecturaLibre(it) && respuestaLibre(it) && it.huella !in vistas }
            ?: orden.firstOrNull { lecturaLibre(it) && respuestaLibre(it) }
            ?: orden.firstOrNull { lecturaLibre(it) }
            ?: orden.first()
    }

    /** Conceptos cuya próxima revisión ya llegó, los más atrasados primero. */
    fun conceptosVencidos(proximas: Map<String, Instant>, ahora: Instant, limite: Int): List<String> =
        proximas.filterValues { it <= ahora }
            .entries.sortedWith(compareBy({ it.value }, { it.key }))
            .take(limite)
            .map { it.key }

    /**
     * Ejercicios que mezclan los conceptos de UNA materia. Reparte por turnos entre conceptos, para
     * que el estudiante tenga que reconocer qué tipo de problema tiene enfrente, sin dos seguidos del
     * mismo concepto cuando hay más de uno. Dentro de cada concepto las plantillas también van por
     * turnos (barajadas), para que ninguna quede fuera.
     */
    fun plantillasIntercaladas(
        materia: MateriaCurso,
        conceptos: List<String>,
        cantidad: Int,
        azar: Random,
    ): List<PlantillaResumen> {
        val todas = materia.secciones.flatMap { it.plantillas }
        val porConcepto = conceptos.distinct().associateWith { c -> todas.filter { it.concepto == c }.shuffled(azar) }
        val orden = conceptos.distinct().filter { porConcepto.getValue(it).isNotEmpty() }.shuffled(azar)
        if (orden.isEmpty()) return emptyList()
        val turno = orden.associateWith { 0 }.toMutableMap()
        return (0 until cantidad).map { i ->
            val concepto = orden[i % orden.size]
            val plantillas = porConcepto.getValue(concepto)
            plantillas[turno.getValue(concepto) % plantillas.size].also {
                turno[concepto] = turno.getValue(concepto) + 1
            }
        }
    }
}
