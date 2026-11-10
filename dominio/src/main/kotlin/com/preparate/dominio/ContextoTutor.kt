package com.preparate.dominio

/**
 * Lo que Kiwi necesita saber de un ejercicio. Lo arma `:datos` desde Room y el paquete de
 * contenido; `:tutor` solo lo lee. Nunca mezcla materias: el material es solo del concepto.
 */
data class ContextoTutor(
    val tipoSesion: TipoSesion,
    val materia: String,
    /** El título de la lección del concepto, o el id del concepto si no hay lección. */
    val concepto: String,
    /** Las lecciones del concepto, sin dibujos. */
    val material: String,
    val variante: Variante,
    val intento: Intento,
) {
    companion object {
        /**
         * La web manda la lección completa si el material del concepto tiene hasta 2 500 caracteres y,
         * si no, busca fragmentos con embeddings. En la app no hay embeddings: el límite sube lo justo
         * para que quepa el concepto más largo medido (2 589). Si alguno lo pasa, se corta por párrafos.
         */
        const val LIMITE_MATERIAL = 3_000
        const val SIN_MATERIAL = "(no hay material de este tema)"

        private val FIGURA = Regex("""<div class="figura">.*?</div>""", RegexOption.DOT_MATCHES_ALL)

        fun sinFiguras(markdown: String): String = FIGURA.replace(markdown, "")

        /** El material del concepto para el prompt: cada lección con su título. */
        fun material(lecciones: List<Leccion>): String {
            val completo = lecciones.joinToString("\n\n") { "${it.titulo}\n\n${sinFiguras(it.contenido)}" }
            if (completo.isEmpty()) return SIN_MATERIAL
            if (completo.length <= LIMITE_MATERIAL) return completo
            val parrafos = completo.split("\n\n")
            val elegidos = StringBuilder()
            for (p in parrafos) {
                if (elegidos.length + p.length + 2 > LIMITE_MATERIAL) break
                if (elegidos.isNotEmpty()) elegidos.append("\n\n")
                elegidos.append(p)
            }
            return elegidos.toString().ifEmpty { completo.take(LIMITE_MATERIAL) }
        }
    }
}
