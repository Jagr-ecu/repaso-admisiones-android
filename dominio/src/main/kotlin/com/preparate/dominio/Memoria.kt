package com.preparate.dominio

import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/*
 * Repaso espaciado FSRS-6 por concepto, no por pregunta. Puerto del planificador de py-fsrs
 * (fsrs/scheduler.py) con la configuración de la web (app/scheduler/memoria.py): parámetros por
 * defecto, pasos de 1 y 10 minutos, reaprendizaje de 10 minutos y SIN dispersión de fechas
 * (`enable_fuzzing=False`), para que el repaso sea reproducible.
 *
 * Se eligió portarlo en lugar de usar una librería: son pocas fórmulas y así las fechas
 * coinciden con las de la web (referencia-memoria.json), lo que hace falta en el traspaso.
 */

/** Los valores coinciden con `fsrs.State`. */
enum class EstadoTarjeta(val valor: Int) {
    APRENDIENDO(1),
    REPASO(2),
    REAPRENDIENDO(3);

    companion object {
        fun desde(valor: Int): EstadoTarjeta = entries.first { it.valor == valor }
    }
}

/** El estado del repaso de un concepto. Una tarjeta nueva aún no tiene estabilidad ni dificultad. */
data class Tarjeta(
    val estado: EstadoTarjeta = EstadoTarjeta.APRENDIENDO,
    /** Paso de aprendizaje o reaprendizaje; null en REPASO. */
    val paso: Int? = 0,
    val estabilidad: Double? = null,
    val dificultad: Double? = null,
    val vence: Instant,
    val ultimoRepaso: Instant? = null,
)

object Memoria {
    private val W = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )
    private const val RETENCION_DESEADA = 0.9
    private const val INTERVALO_MAXIMO = 36500L
    private const val ESTABILIDAD_MINIMA = 0.001
    private const val DIFICULTAD_MINIMA = 1.0
    private const val DIFICULTAD_MAXIMA = 10.0
    private val PASOS_APRENDIZAJE = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10))
    private val PASOS_REAPRENDIZAJE = listOf(Duration.ofMinutes(10))

    private val DECAY = -W[20]
    private val FACTOR = pot(0.9, 1 / DECAY) - 1

    /** Aplica un intento al concepto. Sin tarjeta, empieza una nueva que vence ahora. */
    fun repasar(tarjeta: Tarjeta?, calificacion: Calificacion, ahora: Instant): Tarjeta {
        val actual = tarjeta ?: Tarjeta(vence = ahora)
        val r = calificacion.valor
        // timedelta.days de Python: días completos, redondeando hacia abajo.
        val dias = actual.ultimoRepaso?.let { Math.floorDiv(Duration.between(it, ahora).seconds, 86_400L) }
        var estado = actual.estado
        var paso = actual.paso
        var s = actual.estabilidad
        var d = actual.dificultad
        val intervalo: Duration

        fun aRepaso(): Duration {
            estado = EstadoTarjeta.REPASO
            paso = null
            return Duration.ofDays(siguienteIntervalo(s!!))
        }

        when (estado) {
            EstadoTarjeta.APRENDIENDO, EstadoTarjeta.REAPRENDIENDO -> {
                if (s == null || d == null) {
                    s = estabilidadInicial(r)
                    d = limitarDificultad(dificultadInicial(r))
                } else if (dias != null && dias < 1) {
                    s = estabilidadCortoPlazo(s, r)
                    d = siguienteDificultad(d, r)
                } else {
                    s = siguienteEstabilidad(d, s, recuperabilidad(actual, ahora), r)
                    d = siguienteDificultad(d, r)
                }
                val pasos = if (estado == EstadoTarjeta.APRENDIENDO) PASOS_APRENDIZAJE else PASOS_REAPRENDIZAJE
                val p = paso!!
                intervalo = if (pasos.isEmpty() || (p >= pasos.size && r >= 2)) {
                    aRepaso()
                } else {
                    when (calificacion) {
                        Calificacion.OTRA_VEZ -> {
                            paso = 0
                            pasos[0]
                        }
                        Calificacion.DIFICIL -> when {
                            p == 0 && pasos.size == 1 -> pasos[0].multipliedBy(3).dividedBy(2)
                            p == 0 -> pasos[0].plus(pasos[1]).dividedBy(2)
                            else -> pasos[p]
                        }
                        Calificacion.BIEN -> if (p + 1 == pasos.size) {
                            aRepaso()
                        } else {
                            paso = p + 1
                            pasos[p + 1]
                        }
                        Calificacion.FACIL -> aRepaso()
                    }
                }
            }
            EstadoTarjeta.REPASO -> {
                s = if (dias != null && dias < 1) {
                    estabilidadCortoPlazo(s!!, r)
                } else {
                    siguienteEstabilidad(d!!, s!!, recuperabilidad(actual, ahora), r)
                }
                d = siguienteDificultad(d!!, r)
                intervalo = if (calificacion == Calificacion.OTRA_VEZ && PASOS_REAPRENDIZAJE.isNotEmpty()) {
                    estado = EstadoTarjeta.REAPRENDIENDO
                    paso = 0
                    PASOS_REAPRENDIZAJE[0]
                } else {
                    Duration.ofDays(siguienteIntervalo(s))
                }
            }
        }
        return Tarjeta(estado, paso, s, d, ahora.plus(intervalo), ahora)
    }

    // Python calcula `math.e ** x` y `a ** b` con pow; StrictMath da el mismo resultado en todos los dispositivos.
    private fun pot(base: Double, exponente: Double) = StrictMath.pow(base, exponente)

    private fun ePot(x: Double) = pot(Math.E, x)

    private fun recuperabilidad(t: Tarjeta, ahora: Instant): Double {
        val ultimo = t.ultimoRepaso ?: return 0.0
        val s = t.estabilidad ?: return 0.0
        val dias = max(0L, Math.floorDiv(Duration.between(ultimo, ahora).seconds, 86_400L))
        return pot(1 + FACTOR * dias / s, DECAY)
    }

    private fun limitarDificultad(d: Double) = min(max(d, DIFICULTAD_MINIMA), DIFICULTAD_MAXIMA)

    private fun limitarEstabilidad(s: Double) = max(s, ESTABILIDAD_MINIMA)

    private fun estabilidadInicial(r: Int) = limitarEstabilidad(W[r - 1])

    private fun dificultadInicial(r: Int) = W[4] - ePot(W[5] * (r - 1)) + 1

    /** Días hasta el próximo repaso: redondeo de Python (mitad al par), entre 1 y el máximo. */
    private fun siguienteIntervalo(s: Double): Long {
        val dias = (s / FACTOR) * (pot(RETENCION_DESEADA, 1 / DECAY) - 1)
        return min(max(Math.rint(dias).toLong(), 1L), INTERVALO_MAXIMO)
    }

    private fun estabilidadCortoPlazo(s: Double, r: Int): Double {
        var aumento = ePot(W[17] * (r - 3 + W[18])) * pot(s, -W[19])
        if (r >= 2) aumento = max(aumento, 1.0)
        return limitarEstabilidad(s * aumento)
    }

    private fun siguienteDificultad(d: Double, r: Int): Double {
        val delta = -(W[6] * (r - 3))
        val amortiguada = d + (10.0 - d) * delta / 9.0
        return limitarDificultad(W[7] * dificultadInicial(4) + (1 - W[7]) * amortiguada)
    }

    private fun siguienteEstabilidad(d: Double, s: Double, rec: Double, r: Int): Double {
        val nueva = if (r == 1) {
            val largo = W[11] * pot(d, -W[12]) * (pot(s + 1, W[13]) - 1) * ePot((1 - rec) * W[14])
            val corto = s / ePot(W[17] * W[18])
            min(largo, corto)
        } else {
            val penalidad = if (r == 2) W[15] else 1.0
            val bono = if (r == 4) W[16] else 1.0
            s * (1 + ePot(W[8]) * (11 - d) * pot(s, -W[9]) * (ePot((1 - rec) * W[10]) - 1) * penalidad * bono)
        }
        return limitarEstabilidad(nueva)
    }
}
