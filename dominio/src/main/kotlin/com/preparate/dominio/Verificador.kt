package com.preparate.dominio

import java.math.BigInteger
import java.text.Normalizer

/** Puerto de app/generator/verificador.py. Se comprueba contra los casos de referencia de Python. */
enum class EstadoRespuesta(val valor: String) {
    CORRECTA("correcta"),
    INCORRECTA("incorrecta"),
    /** Valor correcto, pero no en la forma pedida (sin simplificar). */
    FORMA_INCORRECTA("forma_incorrecta"),
    /** No se pudo leer, por ejemplo letras donde va un número. */
    FORMATO_INVALIDO("formato_invalido"),
    /** Escritura: la califica Kiwi con la rúbrica. */
    PENDIENTE("pendiente"),
}

/** `grupoMal` es el primer paso equivocado de una cuenta en el cuaderno. */
data class Resultado(val estado: EstadoRespuesta, val errorComun: String? = null, val grupoMal: Int? = null) {
    val correcta: Boolean get() = estado == EstadoRespuesta.CORRECTA
}

/** Fracción exacta, siempre simplificada y con el signo arriba. */
class Racional private constructor(val num: BigInteger, val den: BigInteger) {
    override fun equals(other: Any?) = other is Racional && num == other.num && den == other.den
    override fun hashCode() = 31 * num.hashCode() + den.hashCode()
    override fun toString() = if (den == BigInteger.ONE) "$num" else "$num/$den"

    operator fun plus(otro: Racional) = de(num * otro.den + otro.num * den, den * otro.den)
    operator fun unaryMinus() = Racional(-num, den)

    companion object {
        fun de(num: BigInteger, den: BigInteger): Racional {
            require(den.signum() != 0) { "denominador cero" }
            val m = num.gcd(den).let { if (den.signum() < 0) -it else it }
            return Racional(num / m, den / m)
        }

        fun de(num: Long, den: Long = 1) = de(BigInteger.valueOf(num), BigInteger.valueOf(den))

        /** "0,75" o "0.75", exacto: 3/4. */
        fun deDecimal(texto: String): Racional {
            val limpio = texto.replace(',', '.')
            val decimales = limpio.substringAfter('.', "").length
            return de(BigInteger(limpio.replace(".", "")), BigInteger.TEN.pow(decimales))
        }
    }
}

object Verificador {
    private val NUMERO = Regex(
        "^(?<signo>[-+−])?\\s*(?:" +
            "(?<mixto>\\d+)\\s+(?<mnum>\\d+)\\s*/\\s*(?<mden>\\d+)" + // 1 1/2
            "|(?<num>\\d+)\\s*/\\s*(?<den>\\d+)" + // 3/4
            "|(?<decimal>\\d+[.,]\\d+)" + // 0,75
            "|(?<entero>\\d+)" +
            ")$",
    )

    /** Un Writing de una o dos palabras («asdf», «no se») no es una oración: se pide de nuevo. */
    const val MIN_PALABRAS_ESCRITURA = 3
    // Como `[^\W\d_]` de Python: letras y números que no son cifras (², ½), sin el guion bajo.
    private const val LETRAS = """[\p{L}\p{Nl}\p{No}]+"""
    private val PALABRA = Regex("$LETRAS(?:['’]$LETRAS)?")

    /** Una lista: las casillas del cuaderno, una cifra en cada una, o las palabras de `ordenar`. */
    fun verificar(variante: Variante, entradas: List<String>): Resultado = when (variante.plantilla.tipo) {
        TipoEjercicio.CUADERNO -> verificarCuaderno(variante, entradas)
        TipoEjercicio.ESCRITURA -> Resultado(EstadoRespuesta.FORMATO_INVALIDO)
        else -> verificar(variante, entradas.joinToString(" "))
    }

    fun verificar(variante: Variante, entrada: String): Resultado {
        val plantilla = variante.plantilla
        when (plantilla.tipo) {
            TipoEjercicio.ESCRITURA -> return Resultado(
                if (PALABRA.findAll(entrada).count() >= MIN_PALABRAS_ESCRITURA) EstadoRespuesta.PENDIENTE
                else EstadoRespuesta.FORMATO_INVALIDO,
            )
            // Un texto es la cuenta como se lee: «68 340 408».
            TipoEjercicio.CUADERNO -> return verificarCuaderno(variante, checkNotNull(variante.cuenta).desdeTexto(entrada))
            else -> {}
        }
        val respuesta = checkNotNull(variante.respuesta) { "${plantilla.id}: la variante no tiene respuesta" }
        if (plantilla.verificador == TipoVerificador.SYMPY) {
            val numero = leerNumero(entrada) ?: return Resultado(EstadoRespuesta.FORMATO_INVALIDO)
            if (numero == leerNumero(respuesta)) {
                if (plantilla.formaIrreducible && !irreducible(entrada)) {
                    return Resultado(EstadoRespuesta.FORMA_INCORRECTA)
                }
                return Resultado(EstadoRespuesta.CORRECTA)
            }
        } else if ((listOf(respuesta) + variante.aceptadas).any { equivalentes(plantilla, entrada, it) }) {
            return Resultado(EstadoRespuesta.CORRECTA)
        }
        val error = variante.errores.firstOrNull { equivalentes(plantilla, entrada, it.valor) }
        return Resultado(EstadoRespuesta.INCORRECTA, error?.explicacion)
    }

    private fun verificarCuaderno(variante: Variante, entradas: List<String>?): Resultado {
        val cuenta = checkNotNull(variante.cuenta) { "${variante.plantilla.id}: cuaderno sin cuenta" }
        if (entradas == null || !cuenta.valida(entradas)) return Resultado(EstadoRespuesta.FORMATO_INVALIDO)
        val error = cuenta.primerError(entradas) ?: return Resultado(EstadoRespuesta.CORRECTA)
        return Resultado(EstadoRespuesta.INCORRECTA, error.consejo, error.grupo)
    }

    fun equivalentes(plantilla: Plantilla, entrada: String, esperado: String): Boolean =
        if (plantilla.verificador == TipoVerificador.SYMPY) {
            val numero = leerNumero(entrada)
            numero != null && numero == leerNumero(esperado)
        } else {
            normalizar(entrada, plantilla.ignorarTildes) == normalizar(esperado, plantilla.ignorarTildes)
        }

    fun leerNumero(texto: String): Racional? {
        val m = NUMERO.matchEntire(texto.trim()) ?: return null
        fun g(nombre: String) = m.groups[nombre]?.value
        if (listOf("den", "mden").any { g(it)?.toBigInteger()?.signum() == 0 }) return null
        val valor = when {
            g("mixto") != null -> Racional.de(g("mixto")!!.toBigInteger(), BigInteger.ONE) +
                Racional.de(g("mnum")!!.toBigInteger(), g("mden")!!.toBigInteger())
            g("num") != null -> Racional.de(g("num")!!.toBigInteger(), g("den")!!.toBigInteger())
            g("decimal") != null -> Racional.deDecimal(g("decimal")!!)
            else -> Racional.de(g("entero")!!.toBigInteger(), BigInteger.ONE)
        }
        return if (g("signo") == "-" || g("signo") == "−") -valor else valor
    }

    /** Forma más simple: un entero, una fracción irreducible o un número mixto propio. */
    fun irreducible(texto: String): Boolean {
        val m = NUMERO.matchEntire(texto.trim()) ?: return false
        fun g(nombre: String) = m.groups[nombre]?.value
        if (g("decimal") != null) return false
        if (g("entero") != null) return true
        if (g("mixto") != null) {
            val num = g("mnum")!!.toBigInteger()
            val den = g("mden")!!.toBigInteger()
            return num.signum() > 0 && num < den && num.gcd(den) == BigInteger.ONE
        }
        val num = g("num")!!.toBigInteger()
        val den = g("den")!!.toBigInteger()
        return den != BigInteger.ONE && num.gcd(den) == BigInteger.ONE
    }

    // Aguda, grave y diéresis; la virgulilla de la ñ se conserva.
    private val TILDES = setOf('́', '̀', '̈')
    private const val BORDES = "¿¡.?!,;: "

    fun normalizar(texto: String, ignorarTildes: Boolean = false): String {
        var t = Normalizer.normalize(texto, Normalizer.Form.NFC).lowercase().trim().replace('’', '\'')
        t = t.replace(Regex("(?U)\\s*-\\s*"), "-")
        t = t.replace(Regex("(?U)\\s+"), " ")
        t = t.trim { it in BORDES }
        if (ignorarTildes) {
            t = Normalizer.normalize(t, Normalizer.Form.NFD).filterNot { it in TILDES }
            t = Normalizer.normalize(t, Normalizer.Form.NFC)
        }
        return t
    }
}
