package com.preparate.app

import androidx.annotation.StringRes
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
import com.preparate.dominio.Estudio
import com.preparate.dominio.Leccion
import com.preparate.dominio.Pista
import com.preparate.dominio.ProgresoMateria
import com.preparate.dominio.ReglaError
import com.preparate.dominio.Resultado
import com.preparate.dominio.Sesion
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.TipoVerificador
import com.preparate.dominio.ayudaPermitida
import com.preparate.dominio.esPractica
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
) {
    val variante get() = ejercicio.variante
    val tipo get() = ejercicio.sesion.tipo
    val respondido get() = resultado != null
    val practica get() = esPractica(tipo)

    val listo: Boolean
        get() = when (variante.plantilla.tipo) {
            TipoEjercicio.ORDENAR -> orden.size == variante.fragmentos.size
            TipoEjercicio.CUADERNO -> casillas.size == variante.cuenta!!.casillas && casillas.values.all { it.isNotEmpty() }
            else -> entrada.isNotBlank()
        }
}

/** Cómo terminó un tema o repaso; las pruebas van a su pantalla de resultado. */
data class FinPractica(val cierre: Cierre, val tipo: TipoSesion, val cursoId: String, val leccionId: String?, val materiaId: String)

/**
 * Las pantallas de estudio de UN estudiante. `alGuardar` recibe cada respuesta guardada, para
 * subirla a Firestore (provisional hasta la cola de subida de la fase 5).
 */
class EstudioVm(
    val estudios: Estudios,
    /** Vista previa del APK de depuración: muestra los atajos para probar. */
    val depuracion: Boolean = false,
    private val alcance: CoroutineScope,
    private val ir: (Pantalla) -> Unit,
    private val alGuardar: (Ejercicio, String, Resultado, Int) -> Unit,
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

    fun progreso(cursoId: String): Flow<List<ProgresoMateria>> = estudios.observarProgreso(cursoId)

    fun puntos(cursoId: String): Flow<Int> = estudios.observarPuntos(cursoId)

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
            val (resultado, texto) = when (v.plantilla.tipo) {
                TipoEjercicio.ORDENAR -> {
                    val partes = a.orden.map { v.fragmentos[it] }
                    estudios.responder(a.ejercicio.intento.id, partes) to partes.joinToString(" ")
                }
                TipoEjercicio.CUADERNO -> {
                    val partes = (0 until v.cuenta!!.casillas).map { a.casillas[it].orEmpty() }
                    estudios.responder(a.ejercicio.intento.id, partes) to Estudio.textoGuardado(v, partes)
                }
                else -> estudios.responder(a.ejercicio.intento.id, a.entrada.trim()) to a.entrada.trim()
            }
            if (resultado.estado == EstadoRespuesta.FORMATO_INVALIDO) {
                actual = a.copy(aviso = avisoIlegible(a))
                return@tarea
            }
            alGuardar(a.ejercicio, texto, resultado, a.pistas.size)
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
