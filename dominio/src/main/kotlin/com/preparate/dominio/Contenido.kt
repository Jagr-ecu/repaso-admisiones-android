package com.preparate.dominio

import kotlin.random.Random

enum class TipoEjercicio(val valor: String) {
    ABIERTA("abierta"),
    OPCION_MULTIPLE("opcion_multiple"),
    COMPLETAR("completar"),
    ORDENAR("ordenar"),
    ESCRITURA("escritura"),
    CUADERNO("cuaderno");

    companion object {
        fun desde(valor: String): TipoEjercicio = entries.first { it.valor == valor }
    }
}

enum class TipoVerificador(val valor: String) {
    SYMPY("sympy"),
    TEXTO("texto");

    companion object {
        fun desde(valor: String): TipoVerificador = entries.first { it.valor == valor }
    }
}

data class Plantilla(
    val id: String,
    val tipo: TipoEjercicio,
    val verificador: TipoVerificador,
    val formaIrreducible: Boolean = false,
    val ignorarTildes: Boolean = false,
)

data class ErrorEsperado(val valor: String, val explicacion: String)

/** Una variante ya generada en Python: aquí no se calcula ninguna respuesta (regla 1). */
data class Variante(
    val plantilla: Plantilla,
    val semilla: Int,
    val enunciado: String,
    val respuesta: String,
    val aceptadas: List<String> = emptyList(),
    val opciones: List<String> = emptyList(),
    val pistas: List<String> = emptyList(),
    val solucion: String? = null,
    val errores: List<ErrorEsperado> = emptyList(),
)

data class EjercicioRef(val plantilla: String, val cantidad: Int)

data class Leccion(
    val id: String,
    val materia: String,
    val seccion: String,
    val titulo: String,
    val concepto: String,
    val contenido: String,
    val ejercicios: List<EjercicioRef>,
)

/**
 * PROVISIONAL (fase 1): los ejercicios de un tema, en el orden de la lección.
 * La selección real (scheduler/seleccion.py) se porta en la fase 3.
 */
fun armarTema(leccion: Leccion, variantes: Map<String, List<Variante>>, azar: Random = Random.Default): List<Variante> =
    leccion.ejercicios.flatMap { ref ->
        val disponibles = variantes[ref.plantilla].orEmpty()
        require(disponibles.isNotEmpty()) { "no hay variantes de ${ref.plantilla}" }
        disponibles.shuffled(azar).take(ref.cantidad)
    }
