package com.preparate.dominio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * El resumen que ve el admin. Puerto de app/domain/panel.py, sin las conversaciones con Kiwi,
 * que en Android no se guardan.
 *
 * Lo calcula el dispositivo del estudiante después de sincronizar y lo sube como un solo
 * documento: el admin lo lee con una sola lectura por estudiante. Las fechas van en milisegundos
 * UTC; los días se cuentan en la zona del dispositivo del estudiante.
 */

@Serializable
data class ResumenPanel(
    val version: Int = VERSION,
    val calculado: Long,
    /** Lo último que respondió o empezó; null si aún no estudió. */
    val ultimaActividad: Long?,
    /** Los últimos 7 días, del más antiguo a hoy. */
    val dias: List<DiaPanel>,
    val minutosSemana: Int,
    val minutosTotal: Int,
    val materias: List<MateriaPanel>,
    /** Los Writing que esperan la nota de Kiwi. */
    val pendientes: List<WritingPendiente>,
) {
    fun aJson(): String = formato.encodeToString(serializer(), this)

    companion object {
        const val VERSION = 1
        private val formato = Json { ignoreUnknownKeys = true }

        fun desdeJson(texto: String): ResumenPanel = formato.decodeFromString(serializer(), texto)
    }
}

/** `fecha` en formato ISO (2026-11-10). */
@Serializable
data class DiaPanel(val fecha: String, val minutos: Int)

@Serializable
data class MateriaPanel(
    val cursoId: String,
    val materiaId: String,
    val nombre: String,
    val temasHechos: Int,
    val temasTotal: Int,
    val seccionesAprobadas: Int,
    val seccionesTotal: Int,
    /** Un valor de `EstadoNodo`. */
    val examen: String,
    val minutos: Int,
    /** Pruebas y exámenes terminados, del más reciente al más antiguo. */
    val notas: List<NotaPanel>,
    /** Los temas con más errores primero. */
    val temas: List<TemaPanel>,
)

@Serializable
data class NotaPanel(
    /** Un valor de `TipoSesion`. */
    val tipo: String,
    val seccion: String?,
    val fecha: Long,
    /** null: espera la nota de un Writing. */
    val nota: Double?,
    val aprobada: Boolean?,
)

/** Cómo le va en un concepto, contando práctica, pruebas y exámenes. */
@Serializable
data class TemaPanel(
    val concepto: String,
    val titulo: String,
    val respondidos: Int,
    val fallos: Int,
    /** Ejercicios en los que pidió al menos una pista. */
    val conPista: Int,
    val pistas: Int,
)

@Serializable
data class WritingPendiente(
    val materiaId: String,
    val tipoSesion: String,
    val enunciado: String,
    val respuesta: String,
    val respondido: Long,
)

object Panel {
    /** Una sesión olvidada abierta no suma horas: se cuenta hasta su última actividad y como mucho esto. */
    const val MINUTOS_MAXIMOS_POR_SESION = 90L
    const val DIAS = 7
    const val NOTAS_POR_MATERIA = 10
    const val TEMAS_POR_MATERIA = 8

    /**
     * El resumen de un estudiante en todos los cursos. `enunciado` reconstruye el texto que vio
     * (null si la plantilla ya no está en el paquete).
     */
    fun resumen(
        cursos: List<Curso>,
        sesiones: List<Sesion>,
        intentos: List<Intento>,
        ahora: Instant,
        zona: ZoneId,
        enunciado: (cursoId: String, intento: Intento) -> String?,
    ): ResumenPanel {
        val porSesion = intentos.groupBy { it.sesionId }
        val sesionDe = sesiones.associateBy { it.id }
        val hoy = ahora.atZone(zona).toLocalDate()
        val porDia = (DIAS - 1 downTo 0).associate { hoy.minusDays(it.toLong()) to 0 }.toMutableMap()
        val minutosPorMateria = mutableMapOf<Pair<String, String>, Int>()
        var total = 0
        for (s in sesiones) {
            val minutos = minutos(s, porSesion[s.id].orEmpty()) ?: continue
            total += minutos
            val clave = s.cursoId to s.materiaId
            minutosPorMateria[clave] = (minutosPorMateria[clave] ?: 0) + minutos
            val dia: LocalDate = s.inicio.atZone(zona).toLocalDate()
            porDia[dia]?.let { porDia[dia] = it + minutos }
        }
        val dias = porDia.entries.sortedBy { it.key }.map { DiaPanel(it.key.toString(), it.value) }

        val materias = cursos.flatMap { curso ->
            curso.materias.map { m ->
                val propias = sesiones.filter { it.cursoId == curso.id && it.materiaId == m.id }
                val susIntentos = propias.flatMap { porSesion[it.id].orEmpty() }
                materia(curso, m, propias, susIntentos, minutosPorMateria[curso.id to m.id] ?: 0)
            }
        }

        val pendientes = intentos
            .filter { it.estado == EstadoRespuesta.PENDIENTE && it.respondido != null }
            .mapNotNull { i ->
                val s = sesionDe[i.sesionId] ?: return@mapNotNull null
                WritingPendiente(
                    materiaId = s.materiaId,
                    tipoSesion = s.tipo.valor,
                    enunciado = enunciado(s.cursoId, i) ?: "(${i.plantillaId})",
                    respuesta = i.respuesta.orEmpty(),
                    respondido = i.respondido!!.toEpochMilli(),
                )
            }
            .sortedBy { it.respondido }

        val ultima = (sesiones.map { it.fin ?: it.inicio } + intentos.map { it.respondido ?: it.mostrado }).maxOrNull()
        return ResumenPanel(
            calculado = ahora.toEpochMilli(),
            ultimaActividad = ultima?.toEpochMilli(),
            dias = dias,
            minutosSemana = dias.sumOf { it.minutos },
            minutosTotal = total,
            materias = materias,
            pendientes = pendientes,
        )
    }

    /** Minutos de una sesión; null si nunca tuvo actividad (abierta y sin ejercicios). */
    fun minutos(sesion: Sesion, intentos: List<Intento>): Int? {
        val fin = sesion.fin ?: intentos.maxOfOrNull { it.respondido ?: it.mostrado } ?: return null
        val milis = Duration.between(sesion.inicio, fin).toMillis().coerceAtLeast(0)
        // Math.rint redondea la mitad al par, como round() de Python.
        val redondeados = Math.rint(milis / 60_000.0).toLong()
        return minOf(MINUTOS_MAXIMOS_POR_SESION, redondeados).toInt()
    }

    private fun materia(curso: Curso, m: MateriaCurso, sesiones: List<Sesion>, intentos: List<Intento>, minutos: Int): MateriaPanel {
        val progreso = calcularProgreso(m, Estudio.historial(sesiones))
        val nombres = m.secciones.associate { it.id to it.nombre }
        val notas = sesiones
            .filter { (it.tipo == TipoSesion.PRUEBA_SECCION || it.tipo == TipoSesion.EXAMEN_FINAL) && it.fin != null }
            .sortedByDescending { it.fin }
            .take(NOTAS_POR_MATERIA)
            .map { NotaPanel(it.tipo.valor, it.seccionId?.let { id -> nombres[id] ?: id }, it.fin!!.toEpochMilli(), it.nota, it.aprobada) }
        return MateriaPanel(
            cursoId = curso.id,
            materiaId = m.id,
            nombre = m.nombre,
            temasHechos = progreso.secciones.sumOf { s -> s.lecciones.count { it.estado == EstadoNodo.COMPLETADO } },
            temasTotal = progreso.secciones.sumOf { it.lecciones.size },
            seccionesAprobadas = progreso.secciones.count { it.prueba == EstadoPrueba.APROBADA },
            seccionesTotal = progreso.secciones.size,
            examen = progreso.examenFinal.valor,
            minutos = minutos,
            notas = notas,
            temas = temas(m, intentos).take(TEMAS_POR_MATERIA),
        )
    }

    /** Todos los conceptos que practicó, con los que más falla primero. */
    fun temas(m: MateriaCurso, intentos: List<Intento>): List<TemaPanel> {
        val titulos = m.secciones.flatMap { it.lecciones }.associate { it.concepto to it.titulo }
        return intentos
            .filter { it.respondido != null && it.estado != EstadoRespuesta.PENDIENTE }
            .groupBy { it.concepto }
            .map { (concepto, lista) ->
                TemaPanel(
                    concepto = concepto,
                    titulo = titulos[concepto] ?: concepto,
                    respondidos = lista.size,
                    fallos = lista.count { it.correcta == false },
                    conPista = lista.count { it.pistasUsadas > 0 },
                    pistas = lista.sumOf { it.pistasUsadas },
                )
            }
            .sortedWith(
                compareByDescending<TemaPanel> { it.fallos }
                    .thenByDescending { it.fallos.toDouble() / it.respondidos }
                    .thenBy { it.titulo },
            )
    }
}
