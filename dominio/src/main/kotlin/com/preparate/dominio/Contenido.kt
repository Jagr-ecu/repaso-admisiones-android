package com.preparate.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/*
 * Modelos del paquete de contenido que genera `exportar-paquete` en el proyecto web
 * (app/exportar.py). Los nombres JSON coinciden con los de Python.
 */

@Serializable
enum class TipoEjercicio(val valor: String) {
    @SerialName("abierta") ABIERTA("abierta"),
    @SerialName("opcion_multiple") OPCION_MULTIPLE("opcion_multiple"),
    @SerialName("completar") COMPLETAR("completar"),
    @SerialName("ordenar") ORDENAR("ordenar"),
    @SerialName("escritura") ESCRITURA("escritura"),
    @SerialName("cuaderno") CUADERNO("cuaderno");

    companion object {
        fun desde(valor: String): TipoEjercicio = entries.first { it.valor == valor }
    }
}

@Serializable
enum class TipoVerificador(val valor: String) {
    @SerialName("sympy") SYMPY("sympy"),
    @SerialName("texto") TEXTO("texto");

    companion object {
        fun desde(valor: String): TipoVerificador = entries.first { it.valor == valor }
    }
}

// --- manifiesto.json y cursos.json ---------------------------------------------

@Serializable
data class Manifiesto(
    val formato: Int,
    val fecha: String,
    val commit: String,
    /** true en un paquete de desarrollo, que publica también lo que está en borrador. */
    val borradores: Boolean,
    val semillas: Int,
    val cursos: List<String>,
    val lecciones: Int,
    val plantillas: Int,
    val variantes: Int,
)

@Serializable
data class Cursos(val cursos: List<Curso>)

/**
 * Un curso agrupa materias, secciones y temas. Hoy solo existe Admisiones. Los cursos nunca
 * se mezclan, y el progreso de cada uno se guarda aparte.
 */
@Serializable
data class Curso(
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val materias: List<MateriaCurso>,
)

@Serializable
data class MateriaCurso(
    val id: String,
    val nombre: String,
    val prefijo: String,
    val peso: Double,
    val secciones: List<SeccionCurso>,
)

/** Solo trae lo publicado. Los temas van en el orden del camino. */
@Serializable
data class SeccionCurso(
    val id: String,
    val nombre: String,
    val lecciones: List<LeccionResumen>,
    val plantillas: List<PlantillaResumen>,
)

@Serializable
data class LeccionResumen(val id: String, val titulo: String, val concepto: String)

@Serializable
data class PlantillaResumen(val id: String, val concepto: String, val tipo: TipoEjercicio, val dificultad: Int)

// --- <curso>/lecciones/<id>.json ------------------------------------------------------

@Serializable
data class EjercicioRef(val plantilla: String, val cantidad: Int)

@Serializable
data class Leccion(
    val id: String,
    val curso: String,
    val materia: String,
    val seccion: String,
    val titulo: String,
    val concepto: String,
    val prerequisitos: List<String> = emptyList(),
    /** Markdown con fórmulas KaTeX. */
    val contenido: String,
    val ejercicios: List<EjercicioRef>,
)

// --- <curso>/variantes/<plantilla>.json -----------------------------------------------

@Serializable
data class CriterioRubrica(val criterio: String, val descripcion: String, val puntos: Double)

@Serializable
data class Plantilla(
    val id: String,
    val tipo: TipoEjercicio,
    val verificador: TipoVerificador,
    @SerialName("forma_irreducible") val formaIrreducible: Boolean = false,
    @SerialName("ignorar_tildes") val ignorarTildes: Boolean = false,
    /** Cambia si cambia la plantilla: con otra versión, la misma semilla puede dar otra variante. */
    val version: String = "",
    val concepto: String = "",
    val dificultad: Int = 1,
    /** Solo en escritura (Writing), que califica Kiwi. */
    val rubrica: List<CriterioRubrica> = emptyList(),
    /** Las opciones fijas de la plantilla, iguales en todas sus variantes; vacía si salen de los errores comunes. */
    @SerialName("opciones_fijas") val opcionesFijas: List<String> = emptyList(),
)

@Serializable
data class ErrorEsperado(val valor: String, val explicacion: String)

/** Una variante ya generada en Python: aquí no se calcula ninguna respuesta (regla 1). */
data class Variante(
    val plantilla: Plantilla,
    val semilla: Int,
    val enunciado: String,
    /** null solo en escritura, que no tiene una respuesta única. */
    val respuesta: String?,
    /** Identifica lo que ve el estudiante; dos semillas pueden dar el mismo ejercicio. */
    val huella: String = "",
    val aceptadas: List<String> = emptyList(),
    val opciones: List<String> = emptyList(),
    /** Palabras desordenadas en `ordenar`. */
    val fragmentos: List<String> = emptyList(),
    val pistas: List<String> = emptyList(),
    val solucion: String? = null,
    val errores: List<ErrorEsperado> = emptyList(),
    /** Solo en cuaderno. */
    val cuenta: Cuenta? = null,
)

/** Una plantilla con todas sus variantes distintas. */
data class PlantillaConVariantes(val plantilla: Plantilla, val variantes: List<Variante>)

// --- Cuaderno: la cuenta en columna, armada en Python (generator/cuaderno.py) ----

@Serializable
data class Celda(
    /** Ya impreso: una cifra o un signo. */
    val valor: String? = null,
    /** La llena el estudiante. */
    val casilla: Int? = null,
    /** El paso al que pertenece la casilla. */
    val grupo: Int? = null,
    /** Impreso y destacado, como el 0 de la segunda fila. */
    val marca: Boolean = false,
    /** Casilla libre para lo que se lleva: no se envía ni se califica. */
    val nota: Boolean = false,
)

@Serializable
data class Fila(
    val celdas: List<Celda>,
    val linea: Boolean = false,
    val etiqueta: String? = null,
    /** División: al otro lado de la raya, el divisor y el cociente. */
    val derecha: List<Celda> = emptyList(),
)

/** Un paso de la cuenta. `casillas` va en orden de lectura, de izquierda a derecha. */
@Serializable
data class Grupo(
    val id: Int,
    val clase: String,
    val casillas: List<Int>,
    val sufijo: String = "",
    val factor: Int? = null,
)

@Serializable
data class Cuenta(
    val operacion: String,
    val a: Long,
    val b: Long,
    /** Cuántas casillas llena el estudiante. */
    val casillas: Int,
    val filas: List<Fila>,
    val grupos: List<Grupo>,
    /** Lo que va en cada casilla. Nunca se muestra antes de responder. */
    val esperadas: List<String>,
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
