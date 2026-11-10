package com.preparate.dominio

/*
 * Revisión de una cuenta en columna. Puerto de la parte de revisión de app/generator/cuaderno.py:
 * la cuenta ya viene armada desde Python, con lo que va en cada casilla.
 *
 * Cada casilla tiene un número: su lugar en `esperadas` y en la lista que responde el estudiante.
 * La retroalimentación señala el primer paso equivocado, porque los siguientes suelen estar mal
 * solo por arrastrar ese error.
 */

const val MULTIPLICACION = "multiplicacion"
const val DIVISION = "division"

/** El primer paso equivocado y qué revisar. */
data class ErrorCuaderno(val grupo: Int, val consejo: String)

/** El número al que llega la cuenta, según sus propias casillas: el producto o el cociente. */
fun Cuenta.resultado(): String =
    if (operacion == DIVISION) {
        grupos.filter { it.clase == "cociente" }.joinToString("") { it.texto(esperadas) }
    } else {
        grupos.last().texto(esperadas)
    }

/** Tal como se lee en el cuaderno: «68 340 408». Es lo que se guarda en el intento. */
fun Cuenta.aTexto(entradas: List<String>): String = grupos.joinToString(" ") { it.texto(entradas) }

/** Lo contrario de `aTexto`. null si el texto no tiene la forma de esta cuenta. */
fun Cuenta.desdeTexto(texto: String): List<String>? {
    val partes = texto.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (partes.size != grupos.size) return null
    val entradas = MutableList(esperadas.size) { "" }
    for ((grupo, parte) in grupos.zip(partes)) {
        val cifras = if (grupo.sufijo.isNotEmpty()) parte.removeSuffix(grupo.sufijo) else parte
        if (cifras.length != grupo.casillas.size) return null
        grupo.casillas.forEachIndexed { i, casilla -> entradas[casilla] = cifras[i].toString() }
    }
    return entradas
}

/** Una cifra en cada casilla, y ni una más. */
fun Cuenta.valida(entradas: List<String>): Boolean =
    entradas.size == esperadas.size && entradas.all { it.length == 1 && it[0] in '0'..'9' }

/** El primer paso equivocado y qué revisar. null si todo está bien. */
fun Cuenta.primerError(entradas: List<String>): ErrorCuaderno? {
    val grupo = grupos.firstOrNull { g -> g.casillas.any { entradas[it] != esperadas[it] } } ?: return null
    return ErrorCuaderno(grupo.id, consejo(grupo, entradas))
}

private fun Grupo.texto(cifras: List<String>): String = casillas.joinToString("") { cifras[it] } + sufijo

private fun Cuenta.consejo(grupo: Grupo, entradas: List<String>): String {
    if (operacion == DIVISION) {
        return when (grupo.clase) {
            "cociente" -> "Revisa la cifra del cociente. Busca qué número por $b se acerca sin pasarse."
            "producto" -> "Revisa la multiplicación: la cifra del cociente por $b."
            "resto" -> "Revisa la resta y baja la cifra que sigue."
            "ultimo_resto" -> "Revisa la última resta."
            else -> error("clase de paso desconocida: ${grupo.clase}")
        }
    }
    if (grupo.clase == "suma") return "Las filas de arriba están bien. Suma columna por columna."
    val factor = grupo.factor ?: 1
    val cifra = if (factor >= 10) factor / 10 else factor
    if (sinLlevar(a, cifra) == grupo.casillas.joinToString("") { entradas[it] }) {
        return "Te faltó sumar lo que llevabas en $a × $factor."
    }
    return "Revisa la fila de $a × $factor. Multiplica cifra por cifra, desde la derecha."
}

/** Lo que escribe quien multiplica cada cifra y olvida sumar lo que lleva: 47 × 3 → 121. */
private fun sinLlevar(a: Long, cifra: Int): String {
    val cifras = a.toString().reversed().map { it.digitToInt() }
    val escrito = cifras.dropLast(1).joinToString("") { (it * cifra % 10).toString() }.reversed()
    return "${cifras.last() * cifra}$escrito"
}
