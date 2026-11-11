package com.preparate.app

import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.preparate.datos.Cierre
import com.preparate.datos.Ejercicio
import com.preparate.datos.EstadoSesion
import com.preparate.datos.Estudios
import com.preparate.datos.ResultadoPrueba
import com.preparate.datos.TiempoAgotado
import com.preparate.dominio.AyudaProhibida
import com.preparate.dominio.Bloqueo
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Leccion
import com.preparate.dominio.Pista
import com.preparate.dominio.ProgresoMateria
import com.preparate.dominio.ReglaError
import com.preparate.dominio.Resultado
import com.preparate.dominio.Sesion
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.TipoVerificador
import com.preparate.dominio.Revision
import com.preparate.dominio.ayudaPermitida
import com.preparate.tutor.Dicho
import com.preparate.tutor.Turno
import com.preparate.tutor.Tutor
import com.preparate.tutor.TutorNoDisponible
import com.preparate.dominio.esPractica
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que el estudiante está respondiendo ahora. */
data class EjercicioUi(
    val ejercicio: Ejercicio,
    val estado: EstadoSesion,
    /** Texto escrito, u opción elegida. */
    val entrada: String = "",
    /** `ordenar`: índices de `fragmentos` en el orden en que los tocó. */
    val orden: List<Int> = emptyList(),
    /** Cuaderno: lo escrito en cada casilla, por número de casilla. */
    val casillas: Map<Int, String> = emptyMap(),
    val resultado: Resultado? = null,
    val pistas: List<Pista> = emptyList(),
    @StringRes val aviso: Int? = null,
    /** La charla con Kiwi sobre este ejercicio. Solo en memoria: nunca se guarda (PLAN, sección 6). */
    val kiwi: List<MensajeKiwi> = emptyList(),
    /** Cuántos pedidos a Kiwi siguen esperando respuesta. */
    val kiwiEsperando: Int = 0,
    /** Kiwi no respondió: se muestra un aviso amable y siguen las pistas escritas. */
    val kiwiDescansa: Boolean = false,
    /** Writing: la nota de Kiwi con la rúbrica, o null mientras espera. */
    val revision: Revision? = null,
) {
    val variante get() = ejercicio.variante
    val tipo get() = ejercicio.sesion.tipo
    val respondido get() = resultado != null
    val kiwiPensando get() = kiwiEsperando > 0
    val practica get() = esPractica(tipo)

    val listo: Boolean
        get() = when (variante.plantilla.tipo) {
            TipoEjercicio.ORDENAR -> orden.size == variante.fragmentos.size
            TipoEjercicio.CUADERNO -> casillas.size == variante.cuenta!!.casillas && casillas.values.all { it.isNotEmpty() }
            else -> entrada.isNotBlank()
        }
}

/**
 * Un mensaje en la charla con Kiwi. `pista`: el nivel, si Kiwi escribió una pista; `escrita`: la puso
 * la guardia en lugar de lo que Kiwi iba a decir; `bloqueado`: Kiwi iba a decir la respuesta y no
 * quedan pistas, así que la pantalla pone un texto fijo.
 */
data class MensajeKiwi(
    val deKiwi: Boolean,
    val texto: String? = null,
    val pista: Int? = null,
    val escrita: Boolean = false,
    val bloqueado: Boolean = false,
)

/** Cómo terminó un tema o repaso; las pruebas van a su pantalla de resultado. */
data class FinPractica(val cierre: Cierre, val tipo: TipoSesion, val cursoId: String, val leccionId: String?, val materiaId: String)

/** Las pantallas de estudio de UN estudiante. */
class EstudioVm(
    val estudios: Estudios,
    /** Vista previa del APK de depuración: muestra los atajos para probar. */
    val depuracion: Boolean = false,
    private val alcance: CoroutineScope,
    private val ir: (Pantalla) -> Unit,
    /** Kiwi, si hay una dirección de LM Studio guardada en este dispositivo. */
    private val tutor: () -> Tutor? = { null },
) {
    var ocupado by mutableStateOf(false); private set
    @get:StringRes var bloqueo by mutableStateOf<Int?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    var leccion by mutableStateOf<Leccion?>(null); private set
    var temaAbierto by mutableStateOf<Sesion?>(null); private set
    var vencidos by mutableStateOf<List<String>>(emptyList()); private set
    var abiertas by mutableStateOf<List<Sesion>>(emptyList()); private set
    var actual by mutableStateOf<EjercicioUi?>(null); private set
    var fin by mutableStateOf<FinPractica?>(null); private set
    var resultado by mutableStateOf<ResultadoPrueba?>(null); private set

    // El progreso y los puntos se observan una sola vez por curso y se guardan: al volver a una
    // pantalla ya están listos, sin un instante de carga que encoja la lista y la suba arriba.
    private val progresos = mutableMapOf<String, StateFlow<List<ProgresoMateria>?>>()
    private val puntosPorCurso = mutableMapOf<String, StateFlow<Int>>()

    fun progreso(cursoId: String): StateFlow<List<ProgresoMateria>?> = progresos.getOrPut(cursoId) {
        estudios.observarProgreso(cursoId).stateIn(alcance, SharingStarted.Eagerly, null)
    }

    fun puntos(cursoId: String): StateFlow<Int> = puntosPorCurso.getOrPut(cursoId) {
        estudios.observarPuntos(cursoId).stateIn(alcance, SharingStarted.Eagerly, 0)
    }

    /** Hasta dónde bajó el estudiante en cada pantalla (curso o materia), para volver al mismo lugar. */
    private val desplazamientos = mutableMapOf<String, ScrollState>()

    fun desplazamiento(clave: String): ScrollState = desplazamientos.getOrPut(clave) { ScrollState(0) }

    fun limpiarAvisos() {
        bloqueo = null
        error = null
    }

    private fun tarea(accion: suspend () -> Unit) {
        alcance.launch {
            ocupado = true
            limpiarAvisos()
            try {
                accion()
            } catch (e: ReglaError) {
                bloqueo = textoDe(e.motivo)
            } catch (e: TiempoAgotado) {
                actual?.let { terminar(it.ejercicio.sesion) }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                ocupado = false
            }
        }
    }

    // --- Camino ----------------------------------------------------------------------

    fun abrirMateria(cursoId: String, materiaId: String) {
        ir(Pantalla.Materia(cursoId, materiaId))
        refrescarMateria(cursoId, materiaId)
    }

    fun refrescarMateria(cursoId: String, materiaId: String) = tarea {
        vencidos = estudios.conceptosVencidos(cursoId, materiaId)
        abiertas = estudios.abiertas(cursoId, materiaId)
    }

    fun abrirTema(cursoId: String, leccionId: String) = tarea {
        leccion = estudios.leccion(cursoId, leccionId)
        temaAbierto = abiertas.firstOrNull { it.tipo == TipoSesion.LECCION && it.leccionId == leccionId }
        ir(Pantalla.Explicacion(cursoId, leccionId))
    }

    fun empezarTema(cursoId: String, leccionId: String) = tarea { jugar(estudios.empezarTema(cursoId, leccionId)) }

    fun reiniciarTema(cursoId: String, leccionId: String) = tarea { jugar(estudios.reiniciarTema(cursoId, leccionId)) }

    fun empezarRepaso(cursoId: String, materiaId: String, seccionId: String? = null) =
        tarea { jugar(estudios.empezarRepaso(cursoId, materiaId, seccionId)) }

    fun empezarPrueba(cursoId: String, materiaId: String, seccionId: String) =
        tarea { jugar(estudios.empezarPrueba(cursoId, materiaId, seccionId)) }

    fun empezarExamen(cursoId: String, materiaId: String) = tarea { jugar(estudios.empezarExamen(cursoId, materiaId)) }

    fun retomar(sesion: Sesion) = tarea { jugar(sesion) }

    /** Solo en la vista previa de depuración: atajos para probar pruebas y el examen. */
    fun depuracionCompletar(cursoId: String, materiaId: String, seccionId: String?, aprobar: Boolean) = tarea {
        val secciones = estudios.curso(cursoId).materias.single { it.id == materiaId }.secciones
        for (s in secciones) {
            if (seccionId == null || s.id == seccionId) estudios.depuracionCompletar(cursoId, materiaId, s.id, aprobar)
        }
    }

    // --- Ejercicios ------------------------------------------------------------------

    private suspend fun jugar(sesion: Sesion) {
        val ej = estudios.siguiente(sesion.id)
        if (ej == null) {
            terminar(sesion)
            return
        }
        actual = EjercicioUi(ej, estudios.estado(sesion.id))
        ir(Pantalla.Ejercicio(sesion.id))
    }

    private suspend fun terminar(sesion: Sesion) {
        val cierre = estudios.cerrar(sesion.id)
        actual = null
        if (esPractica(sesion.tipo)) {
            fin = FinPractica(cierre, sesion.tipo, sesion.cursoId, sesion.leccionId, sesion.materiaId)
            ir(Pantalla.FinPractica)
        } else {
            resultado = estudios.resultado(sesion.id)
            ir(Pantalla.Resultado(sesion.id))
            if (resultado?.cierre?.pendiente == true) calificarPendientes()
        }
    }

    private fun cambiar(cambio: (EjercicioUi) -> EjercicioUi) {
        actual = actual?.let { if (it.respondido) it else cambio(it).copy(aviso = null) }
    }

    fun escribir(texto: String) = cambiar { it.copy(entrada = texto) }

    fun tocarFragmento(indice: Int) = cambiar { if (indice in it.orden) it else it.copy(orden = it.orden + indice) }

    fun quitarFragmento(posicion: Int) = cambiar { it.copy(orden = it.orden.filterIndexed { i, _ -> i != posicion }) }

    fun escribirCasilla(casilla: Int, cifra: String) = cambiar {
        val limpio = cifra.filter(Char::isDigit).takeLast(1)
        it.copy(casillas = if (limpio.isEmpty()) it.casillas - casilla else it.casillas + (casilla to limpio))
    }

    fun comprobar() {
        val a = actual ?: return
        if (a.respondido || !a.listo) return
        tarea {
            val v = a.variante
            // Lo guardado en Room entra solo en la cola de subida (un trigger de la base).
            val resultado = when (v.plantilla.tipo) {
                TipoEjercicio.ORDENAR -> estudios.responder(a.ejercicio.intento.id, a.orden.map { v.fragmentos[it] })
                TipoEjercicio.CUADERNO ->
                    estudios.responder(a.ejercicio.intento.id, (0 until v.cuenta!!.casillas).map { a.casillas[it].orEmpty() })
                else -> estudios.responder(a.ejercicio.intento.id, a.entrada.trim())
            }
            if (resultado.estado == EstadoRespuesta.FORMATO_INVALIDO) {
                actual = a.copy(aviso = avisoIlegible(a))
                return@tarea
            }
            if (!a.practica) {
                // En pruebas y exámenes no se dice si estuvo bien: se sigue con la próxima.
                jugar(a.ejercicio.sesion)
                return@tarea
            }
            var pistas = a.pistas
            // Regla 9: tras un fallo, la pista de la lista sale primero, sin esperar a Kiwi.
            if (resultado.estado == EstadoRespuesta.INCORRECTA && pistas.isEmpty() && resultado.grupoMal == null) {
                estudios.pista(a.ejercicio.intento.id)?.let { pistas = listOf(it) }
            }
            actual = a.copy(resultado = resultado, pistas = pistas, estado = estudios.estado(a.ejercicio.sesion.id))
            // Después, Kiwi: solo si falló o si es un Writing (regla 9). Nunca bloquea el botón Siguiente.
            when {
                resultado.estado == EstadoRespuesta.PENDIENTE -> calificarWriting(a.ejercicio.intento.id)
                resultado.correcta == false -> kiwiTrasFallo(a.ejercicio.intento.id)
            }
        }
    }

    // --- Kiwi --------------------------------------------------------------------------

    /** Cambia el ejercicio en pantalla solo si sigue siendo el mismo: Kiwi puede tardar. */
    private fun enEste(intentoId: String, cambio: (EjercicioUi) -> EjercicioUi) {
        actual = actual?.let { if (it.ejercicio.intento.id == intentoId) cambio(it) else it }
    }

    /** Las pistas escritas que ya están a la vista, para que la guardia no repita una. */
    private fun vistas(a: EjercicioUi): Set<String> =
        (a.pistas.map { it.texto } + a.kiwi.mapNotNull { m -> m.texto?.takeIf { m.escrita } }).toSet()

    /** Kiwi trabaja aparte: la pantalla sigue respondiendo mientras piensa (puede tardar hasta 2 min). */
    private fun conKiwi(intentoId: String, accion: suspend (Tutor) -> Unit) {
        val t = tutor() ?: return enEste(intentoId) { it.copy(kiwiDescansa = true) }
        enEste(intentoId) { it.copy(kiwiEsperando = it.kiwiEsperando + 1) }
        alcance.launch {
            try {
                accion(t)
            } catch (_: AyudaProhibida) {
            } catch (e: Exception) {
                enEste(intentoId) { it.copy(kiwiDescansa = true) }
            } finally {
                enEste(intentoId) { it.copy(kiwiEsperando = (it.kiwiEsperando - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun mensaje(d: Dicho): MensajeKiwi = when (d) {
        is Dicho.DeKiwi -> MensajeKiwi(true, d.texto)
        is Dicho.PistaEscrita -> MensajeKiwi(true, d.texto, escrita = true)
        Dicho.Bloqueado -> MensajeKiwi(true, bloqueado = true)
    }

    private fun agregar(intentoId: String, dicho: Dicho?) =
        enEste(intentoId) { if (dicho == null) it.copy(kiwiDescansa = true) else it.copy(kiwi = it.kiwi + mensaje(dicho), kiwiDescansa = false) }

    private fun kiwiTrasFallo(intentoId: String) = conKiwi(intentoId) { t ->
        val a = actual ?: return@conKiwi
        agregar(intentoId, t.trasFallo(estudios.contextoTutor(intentoId), vistas(a)))
    }

    /** El estudiante le escribe a Kiwi sobre su respuesta. */
    fun escribirAKiwi(texto: String) {
        val a = actual ?: return
        val limpio = texto.trim()
        if (limpio.isEmpty() || !ayudaPermitida(a.tipo) || a.kiwiPensando) return
        val id = a.ejercicio.intento.id
        val historial = a.kiwi.mapNotNull { m -> m.texto?.let { Turno(m.deKiwi, it) } }
        enEste(id) { it.copy(kiwi = it.kiwi + MensajeKiwi(false, limpio), kiwiDescansa = false) }
        conKiwi(id) { t ->
            agregar(id, t.conversar(estudios.contextoTutor(id), historial, limpio, vistas(actual ?: a)))
        }
    }

    /** La pista de Kiwi del mismo nivel que la escrita que ya se muestra. */
    private fun kiwiPista(intentoId: String, nivel: Int) = conKiwi(intentoId) { t ->
        val previas = actual?.kiwi.orEmpty().filter { it.pista != null }.mapNotNull { it.texto }
        val texto = t.pista(estudios.contextoTutor(intentoId), nivel, previas)
        enEste(intentoId) {
            if (texto == null) it.copy(kiwiDescansa = true) else it.copy(kiwi = it.kiwi + MensajeKiwi(true, texto, pista = nivel), kiwiDescansa = false)
        }
    }

    /** Califica un Writing de práctica en cuanto se envía. Sin Kiwi, queda pendiente para más tarde. */
    private fun calificarWriting(intentoId: String) = conKiwi(intentoId) { t ->
        val revision = t.calificar(estudios.contextoTutor(intentoId))
        estudios.registrarEscritura(intentoId, revision)
        enEste(intentoId) { it.copy(revision = revision) }
    }

    private var calificando = false

    /**
     * Califica los Writing que quedaron pendientes (de práctica, pruebas o exámenes) si Kiwi está.
     * Para en cuanto Kiwi deja de responder. Calificar no es ayudar: no pasa por `ayudaPermitida`.
     */
    fun calificarPendientes() {
        if (calificando) return
        val t = tutor() ?: return
        calificando = true
        alcance.launch {
            try {
                // Comprueba siempre (2 s): así se nota enseguida si LM Studio volvió.
                if (estudios.writingPendientes().isEmpty() || !t.disponible()) return@launch
                for (id in estudios.writingPendientes()) {
                    val revision = try {
                        t.calificar(estudios.contextoTutor(id))
                    } catch (e: TutorNoDisponible) {
                        break
                    }
                    estudios.registrarEscritura(id, revision)
                }
                // Si está a la vista el resultado de una prueba que esperaba su nota, se actualiza.
                resultado?.let { r -> if (r.cierre.pendiente) resultado = estudios.resultado(r.cierre.sesion.id) }
            } catch (_: Exception) {
            } finally {
                calificando = false
            }
        }
    }

    private fun avisoIlegible(a: EjercicioUi): Int = when {
        a.variante.plantilla.tipo == TipoEjercicio.ESCRITURA -> R.string.writing_corto
        a.variante.plantilla.tipo == TipoEjercicio.CUADERNO -> R.string.cuaderno_ayuda
        a.variante.plantilla.verificador == TipoVerificador.SYMPY -> R.string.no_entendi_numero
        else -> R.string.no_entendi_texto
    }

    /** Pistas ilimitadas en práctica; tras las tres y ya respondido, la solución. */
    fun pedirPista() {
        val a = actual ?: return
        if (!ayudaPermitida(a.tipo)) return
        tarea {
            try {
                val pista = estudios.pista(a.ejercicio.intento.id) ?: return@tarea
                actual = actual?.copy(pistas = (actual?.pistas.orEmpty() + pista).distinctBy { it.nivel })
                // La escrita sale al instante y Kiwi la explica a su manera después. La solución es siempre la escrita.
                if (pista.nivel <= a.variante.pistas.size) kiwiPista(a.ejercicio.intento.id, pista.nivel)
            } catch (_: AyudaProhibida) {
            }
        }
    }

    fun siguiente() {
        val a = actual ?: return
        tarea { jugar(a.ejercicio.sesion) }
    }

    /** El examen se quedó sin tiempo con la pantalla abierta. */
    fun tiempoAgotado() {
        val a = actual ?: return
        tarea { jugar(a.ejercicio.sesion) }
    }

    @StringRes
    private fun textoDe(motivo: Bloqueo): Int = when (motivo) {
        Bloqueo.TEMA_BLOQUEADO -> R.string.bloqueo_tema
        Bloqueo.PRUEBA_BLOQUEADA -> R.string.bloqueo_prueba
        Bloqueo.PRUEBA_PENDIENTE -> R.string.bloqueo_prueba_pendiente
        Bloqueo.SECCION_APROBADA -> R.string.bloqueo_seccion_aprobada
        Bloqueo.REPASO_REQUERIDO -> R.string.bloqueo_repaso_requerido
        Bloqueo.SIN_REPASO_PENDIENTE -> R.string.bloqueo_sin_repaso
        Bloqueo.EXAMEN_BLOQUEADO -> R.string.bloqueo_examen
        Bloqueo.EXAMEN_PENDIENTE -> R.string.bloqueo_examen_pendiente
        Bloqueo.NADA_PARA_REPASAR -> R.string.bloqueo_nada_repasar
        Bloqueo.SIN_EJERCICIOS -> R.string.bloqueo_sin_ejercicios
    }
}
