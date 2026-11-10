package com.preparate.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.preparate.datos.LectorContenido
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Leccion
import com.preparate.dominio.Resultado
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.Variante
import com.preparate.dominio.Verificador
import com.preparate.dominio.armarTema
import com.preparate.dominio.ayudaPermitida
import com.preparate.sincro.Cuentas
import com.preparate.sincro.ErrorCuenta
import com.preparate.sincro.FichaEstudiante
import com.preparate.sincro.Intento
import com.preparate.sincro.IntentoSubido
import com.preparate.sincro.PerfilSinGuardar
import com.preparate.sincro.Progreso
import com.preparate.sincro.Sesion
import com.preparate.tutor.ClienteKiwi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Pantalla {
    data object Entrar : Pantalla
    data object Inicio : Pantalla
    data object Leccion : Pantalla
    data object Practica : Pantalla
    data object Kiwi : Pantalla
    data object CrearEstudiante : Pantalla
    data class DetalleEstudiante(val ficha: FichaEstudiante) : Pantalla
}

data class EjercicioEnCurso(
    val variante: Variante,
    val entrada: String = "",
    val resultado: Resultado? = null,
    val aviso: String? = null,
    val pistasVistas: Int = 0,
    val verSolucion: Boolean = false,
)

data class TemaEnCurso(
    val leccion: Leccion,
    val sesionId: String,
    val ejercicios: List<Variante>,
    val indice: Int,
    val actual: EjercicioEnCurso,
    val aciertos: Int = 0,
    val terminado: Boolean = false,
)

sealed interface EstadoKiwi {
    data object SinProbar : EstadoKiwi
    data object Probando : EstadoKiwi
    data class Activo(val modelo: String, val saludo: String?, val error: String? = null) : EstadoKiwi
    data object Apagado : EstadoKiwi
}

/** PROVISIONAL (fase 1): un solo tema de prueba. La navegación real llega en la fase 4. */
const val CURSO_PRUEBA = "admisiones"
const val LECCION_PRUEBA = "mat.fracciones.suma-igual-denominador"

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val cuentas = Cuentas(app)
    private val progreso = Progreso()
    private val contenido = LectorContenido(app.assets)
    private val ajustes = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var sesion by mutableStateOf(cuentas.sesionGuardada()); private set
    var pantalla by mutableStateOf<Pantalla>(if (sesion == null) Pantalla.Entrar else Pantalla.Inicio); private set
    var ocupado by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    private val uidActual = MutableStateFlow(sesion?.uid)
    val pendientes = uidActual
        .flatMapLatest { uid -> if (uid == null) flowOf(0) else progreso.pendientes(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun ir(destino: Pantalla) {
        error = null
        pantalla = destino
    }

    fun volverAlInicio() = ir(if (sesion == null) Pantalla.Entrar else Pantalla.Inicio)

    /** Solo en el APK de depuración: abre el tema sin cuenta y sin guardar nada. */
    fun vistaPrevia() {
        if (BuildConfig.DEBUG && sesion == null) abrirLeccion()
    }

    private fun tarea(accion: suspend () -> Unit) {
        viewModelScope.launch {
            ocupado = true
            error = null
            try {
                accion()
            } catch (e: ErrorCuenta) {
                error = e.message
            } catch (e: Exception) {
                error = "Algo salió mal: ${e.message}"
            } finally {
                ocupado = false
            }
        }
    }

    // --- Cuentas --------------------------------------------------------------

    fun entrar(usuario: String, clave: String) = tarea {
        val nueva = cuentas.entrar(usuario, clave)
        sesion = nueva
        uidActual.value = nueva.uid
        ir(Pantalla.Inicio)
    }

    fun salir() {
        cuentas.salir()
        sesion = null
        uidActual.value = null
        tema = null
        ir(Pantalla.Entrar)
    }

    var estudianteCreado by mutableStateOf<String?>(null); private set

    /** Cuenta creada sin perfil: el botón Reintentar solo vuelve a guardar el perfil. */
    var perfilPendiente by mutableStateOf<PerfilSinGuardar?>(null); private set

    fun crearEstudiante(nombre: String, usuario: String, clave: String) = tarea {
        estudianteCreado = null
        val pendiente = perfilPendiente
        val listo = try {
            if (pendiente != null) {
                cuentas.guardarPerfil(pendiente.uid, pendiente.nombre, pendiente.usuario)
                pendiente.nombre to pendiente.usuario
            } else {
                cuentas.crearEstudiante(nombre, usuario, clave)
                nombre.trim() to usuario.trim().lowercase()
            }
        } catch (e: PerfilSinGuardar) {
            perfilPendiente = e
            throw e
        }
        perfilPendiente = null
        estudianteCreado = "Listo: ${listo.first} ya puede entrar con el usuario ${listo.second}."
    }

    fun limpiarCreacion() {
        estudianteCreado = null
        perfilPendiente = null
        error = null
    }

    // --- Tema de práctica -----------------------------------------------------

    var leccion by mutableStateOf<Leccion?>(null); private set
    var tema by mutableStateOf<TemaEnCurso?>(null); private set

    fun abrirLeccion() {
        leccion = contenido.leccion(CURSO_PRUEBA, LECCION_PRUEBA)
        ir(Pantalla.Leccion)
    }

    fun empezarPractica() {
        val lec = leccion ?: contenido.leccion(CURSO_PRUEBA, LECCION_PRUEBA)
        val variantes = lec.ejercicios.associate { it.plantilla to contenido.variantes(lec.curso, it.plantilla) }
        val ejercicios = armarTema(lec, variantes)
        tema = TemaEnCurso(lec, UUID.randomUUID().toString(), ejercicios, 0, EjercicioEnCurso(ejercicios.first()))
        ir(Pantalla.Practica)
    }

    private fun cambiarActual(cambio: (EjercicioEnCurso) -> EjercicioEnCurso) {
        val t = tema ?: return
        tema = t.copy(actual = cambio(t.actual))
    }

    fun escribir(texto: String) = cambiarActual { if (it.resultado == null) it.copy(entrada = texto, aviso = null) else it }

    fun comprobar() {
        val t = tema ?: return
        val actual = t.actual
        if (actual.resultado != null || actual.entrada.isBlank()) return
        val resultado = Verificador.verificar(actual.variante, actual.entrada)
        when (resultado.estado) {
            // No cuentan como error: se vuelve a escribir.
            EstadoRespuesta.FORMATO_INVALIDO -> cambiarActual {
                it.copy(aviso = "No entendí tu respuesta. Escribe la fracción con números, por ejemplo 3/4.")
            }
            EstadoRespuesta.FORMA_INCORRECTA -> cambiarActual {
                it.copy(aviso = "¡Casi! El valor está bien, pero falta simplificar.")
            }
            else -> {
                // Sin sesión solo puede ser la vista previa de depuración: no se guarda nada.
                sesion?.let { sesionActual ->
                progreso.registrar(
                    sesionActual.uid,
                    Intento(
                        sesionId = t.sesionId,
                        leccion = t.leccion.id,
                        plantilla = actual.variante.plantilla.id,
                        semilla = actual.variante.semilla,
                        respuesta = actual.entrada.trim(),
                        estado = resultado.estado.valor,
                        errorComun = resultado.errorComun,
                        pistasUsadas = actual.pistasVistas,
                    ),
                )
                }
                // Regla 9: tras un fallo, la pista de la lista sale primero.
                val pistas = if (!resultado.correcta && actual.pistasVistas == 0) 1 else actual.pistasVistas
                tema = t.copy(
                    actual = actual.copy(resultado = resultado, pistasVistas = pistas, aviso = null),
                    aciertos = t.aciertos + if (resultado.correcta) 1 else 0,
                )
            }
        }
    }

    fun pedirPista() {
        if (!ayudaPermitida(TipoSesion.LECCION)) return
        cambiarActual { it.copy(pistasVistas = minOf(it.pistasVistas + 1, it.variante.pistas.size)) }
    }

    fun verSolucion() = cambiarActual { it.copy(verSolucion = true) }

    fun siguiente() {
        val t = tema ?: return
        val proximo = t.indice + 1
        tema = if (proximo >= t.ejercicios.size) {
            t.copy(terminado = true)
        } else {
            t.copy(indice = proximo, actual = EjercicioEnCurso(t.ejercicios[proximo]))
        }
    }

    // --- Kiwi -----------------------------------------------------------------

    var direccionKiwi by mutableStateOf(ajustes.getString("kiwi", "").orEmpty()); private set
    var estadoKiwi by mutableStateOf<EstadoKiwi>(EstadoKiwi.SinProbar); private set

    fun probarKiwi(direccion: String) {
        val cliente = ClienteKiwi(direccion)
        direccionKiwi = cliente.base
        ajustes.edit().putString("kiwi", cliente.base).apply()
        estadoKiwi = EstadoKiwi.Probando
        viewModelScope.launch {
            val modelos = cliente.modelos()
            if (modelos.isNullOrEmpty()) {
                estadoKiwi = EstadoKiwi.Apagado
                return@launch
            }
            estadoKiwi = EstadoKiwi.Activo(modelos.first(), null)
            estadoKiwi = try {
                EstadoKiwi.Activo(modelos.first(), cliente.saludar(modelos.first()))
            } catch (e: Exception) {
                EstadoKiwi.Activo(modelos.first(), null, "LM Studio responde, pero no pudo contestar: ${e.message}")
            }
        }
    }

    // --- Panel de admin -------------------------------------------------------

    var estudiantes by mutableStateOf<List<FichaEstudiante>?>(null); private set
    var intentos by mutableStateOf<List<IntentoSubido>?>(null); private set

    fun cargarEstudiantes() = tarea { estudiantes = progreso.estudiantes() }

    fun abrirEstudiante(ficha: FichaEstudiante) {
        intentos = null
        ir(Pantalla.DetalleEstudiante(ficha))
        tarea { intentos = progreso.intentosDe(ficha.uid) }
    }
}
