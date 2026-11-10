package com.preparate.dominio

/** Tipos de sesión de estudio. Los valores coinciden con los de la web (app/domain/reglas.py). */
enum class TipoSesion(val valor: String) {
    LECCION("leccion"),
    REPASO("repaso"),
    PRUEBA_SECCION("prueba_seccion"),
    EXAMEN_FINAL("examen_final"),
}

fun esPractica(tipo: TipoSesion): Boolean =
    tipo == TipoSesion.LECCION || tipo == TipoSesion.REPASO

/**
 * Regla 3: tutor y pistas solo en práctica; en pruebas y exámenes, nunca.
 * Es la única puerta: :tutor la consulta antes de cualquier llamada.
 */
fun ayudaPermitida(tipo: TipoSesion): Boolean = esPractica(tipo)

/*
 * Reglas del curso. Puerto de app/domain/reglas.py: funciones puras, cada una se prueba sola
 * y se compara con referencia-reglas.json.
 */
object Reglas {
    const val NOTA_MAXIMA = 10
    const val NOTA_MINIMA_APROBAR = 7
    const val PREGUNTAS_PRUEBA_SECCION = 10
    const val PREGUNTAS_EXAMEN_FINAL = 15
    const val MINUTOS_EXAMEN_FINAL = 20

    /** Margen para una respuesta enviada justo al acabarse el tiempo. */
    const val SEGUNDOS_DE_GRACIA = 5
    const val EJERCICIOS_REPASO = 5
    const val TEMAS_REPASO_TRAS_REPROBAR = 2

    /** Puntos por completar un tema; nunca por velocidad ni rachas. */
    const val PUNTOS_LECCION = 10

    /** Errores que se permiten en un tema; con el siguiente, el tema se corta. */
    const val ERRORES_PERMITIDOS_TEMA = 3

    /** Con 3 errores el tema sigue; el cuarto lo corta. */
    fun temaPerdido(errores: Int): Boolean = errores > ERRORES_PERMITIDOS_TEMA

    /** Nota sobre 10, con dos decimales: 11 de 15 → 7.33. Redondea como Python (`round`). */
    fun nota(aciertos: Int, total: Int): Double {
        require(total > 0) { "una prueba necesita al menos una pregunta" }
        require(aciertos in 0..total) { "aciertos fuera de rango: $aciertos de $total" }
        // Python redondea el double con "mitad al par" sobre su valor binario exacto; BigDecimal(double) hace lo mismo.
        return java.math.BigDecimal(NOTA_MAXIMA.toDouble() * aciertos / total)
            .setScale(2, java.math.RoundingMode.HALF_EVEN)
            .toDouble()
    }

    fun aprobada(nota: Double): Boolean = nota >= NOTA_MINIMA_APROBAR

    /** Cómo cuenta un intento para el repaso: las pistas no restan puntos, pero el concepto aún no está firme. */
    fun calificacionMemoria(correcta: Boolean, pistasUsadas: Int): Calificacion = when {
        !correcta -> Calificacion.OTRA_VEZ
        pistasUsadas > 0 -> Calificacion.DIFICIL
        else -> Calificacion.BIEN
    }

    /**
     * Conceptos con más fallos en una prueba, para el repaso tras reprobar. `resultados` son pares
     * (concepto, correcta). Los empates se resuelven por orden de aparición en la prueba.
     */
    fun temasConMasErrores(resultados: List<Pair<String, Boolean>>, cuantos: Int = TEMAS_REPASO_TRAS_REPROBAR): List<String> {
        val fallos = LinkedHashMap<String, Int>()
        for ((concepto, correcta) in resultados) {
            fallos[concepto] = (fallos[concepto] ?: 0) + if (correcta) 0 else 1
        }
        return fallos.filterValues { it > 0 }.keys.sortedByDescending { fallos.getValue(it) }.take(cuantos)
    }
}

/** Las calificaciones de FSRS, con los mismos valores que `fsrs.Rating`. */
enum class Calificacion(val valor: Int) {
    OTRA_VEZ(1),
    DIFICIL(2),
    BIEN(3),
    FACIL(4),
}
