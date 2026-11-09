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
