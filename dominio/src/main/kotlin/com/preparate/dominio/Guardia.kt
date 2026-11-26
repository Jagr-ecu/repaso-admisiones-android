package com.preparate.dominio

/*
 * Red de seguridad: Kiwi nunca revela la respuesta (regla 2). Puerto de app/tutor/guardia.py.
 *
 * El prompt ya se lo prohíbe, pero un modelo pequeño puede fallar. Antes de mostrar un texto
 * del modelo se busca en él la respuesta calculada por el código; si aparece, ese texto se
 * descarta y se usa una pista escrita del contenido.
 *
 * Prefiere equivocarse por precaución: un "3" suelto cuenta como revelar la respuesta 3, aunque
 * el modelo hablara de otra cosa. Lo que ya está a la vista no cuenta: repetir el 6/27 del
 * enunciado (o la respuesta del estudiante) no revela que la respuesta es 2/9, aunque valgan lo mismo.
 *
 * Las clases de caracteres se escriben a mano (sin \d, \s ni \w): el motor de expresiones de
 * Android (ICU) y el de la JVM no las entienden igual, y la guardia debe portarse igual en los dos.
 */
object Guardia {
    private const val D = "[0-9]"
    private const val S = ESPACIO
    private const val PALABRA = "[\\p{L}\\p{N}_]"

    private val FRACCION_LATEX = Regex("""\\[dt]?frac$S*\{$S*(-?$D+)$S*\}$S*\{$S*($D+)$S*\}""")

    // Cada forma por separado: "1 1/2" es un mixto, pero sus partes también se revisan.
    private val NUMEROS = listOf(
        Regex("""(?<![0-9/])$D+$S+$D+$S*/$S*$D+(?![0-9/])"""),
        Regex("""(?<![0-9/])$D+$S*/$S*$D+(?![0-9/])"""),
        // Un punto o una coma solo cuentan como decimales si los sigue otra cifra: "es 5." es un 5.
        Regex("""(?<![0-9])(?<![0-9][.,])$D+[.,]$D+(?![.,]?[0-9])"""),
        Regex("""(?<![0-9])(?<![0-9][.,])$D+(?![.,]?[0-9])"""),
    )
    private val BARRA = Regex("$S*/$S*")

    /** ¿El texto dice la respuesta? `aLaVista`: lo que el estudiante ya ve además del enunciado. */
    fun revelaRespuesta(variante: Variante, texto: String, aLaVista: List<String> = emptyList()): Boolean {
        val plantilla = variante.plantilla
        val respuesta = variante.respuesta ?: return false // Writing: no hay una respuesta única que esconder
        if (plantilla.verificador == TipoVerificador.SYMPY) {
            val compacta = compacto(respuesta)
            val vistos = (listOf(variante.enunciado) + aLaVista).flatMap(::numeros).toSet() - compacta
            return numeros(texto).any { n ->
                compacto(n) !in vistos && Verificador.equivalentes(plantilla, n, respuesta)
            }
        }
        val buscada = Verificador.normalizar(respuesta, ignorarTildes = true)
        if (buscada.isEmpty()) return false
        val patron = Regex("(?<!$PALABRA)${Regex.escape(buscada)}(?!$PALABRA)")
        return patron.containsMatchIn(Verificador.normalizar(texto, ignorarTildes = true))
    }

    private fun numeros(texto: String): List<String> {
        // Más cauta que la web: las cifras de otros alfabetos (p. ej. ١٤) se leen como 0–9.
        val ascii = buildString(texto.length) {
            for (c in texto) append(if (c !in '0'..'9' && Character.isDigit(c)) '0' + Character.digit(c, 10) else c)
        }
        val plano = FRACCION_LATEX.replace(ascii, "$1/$2")
        return NUMEROS.flatMap { p -> p.findAll(plano).map { compacto(it.value) }.toList() }
    }

    private fun compacto(numero: String): String = numero.trim().replace(BARRA, "/")
}
