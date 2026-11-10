package com.preparate.app

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.preparate.datos.Estudios
import com.preparate.datos.LectorContenido
import com.preparate.datos.local.BaseDatos
import com.preparate.dominio.ProblemaUsuario
import com.preparate.dominio.ResumenPanel
import com.preparate.sincro.Cuentas
import com.preparate.sincro.ErrorCuenta
import com.preparate.sincro.FichaEstudiante
import com.preparate.sincro.MotivoCuenta
import com.preparate.sincro.PanelAdmin
import com.preparate.sincro.PerfilSinGuardar
import com.preparate.sincro.RespuestaSubida
import com.preparate.sincro.Rol
import com.preparate.sincro.Sesion
import com.preparate.sincro.SinConexion
import com.preparate.sincro.Sincronizacion
import com.preparate.sincro.SubidaWorker
import com.preparate.tutor.ClienteKiwi
import com.preparate.tutor.Tutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Pantalla {
    data object Entrar : Pantalla
    data object Inicio : Pantalla
    data class Curso(val cursoId: String) : Pantalla
    data class Materia(val cursoId: String, val materiaId: String) : Pantalla
    data class Explicacion(val cursoId: String, val leccionId: String) : Pantalla
    data class Ejercicio(val sesionId: String) : Pantalla
    data object FinPractica : Pantalla
    data class Resultado(val sesionId: String) : Pantalla
    data object Kiwi : Pantalla
    data object CrearEstudiante : Pantalla
    data object CambiarClave : Pantalla
    data class DetalleEstudiante(val ficha: FichaEstudiante) : Pantalla
}

sealed interface EstadoKiwi {
    data object SinProbar : EstadoKiwi
    data object Probando : EstadoKiwi
    data class Activo(val modelo: String, val saludo: String?, val error: String? = null) : EstadoKiwi
    data object Apagado : EstadoKiwi
}

/** Cómo va la copia en la nube del estudiante que entró. */
enum class EstadoNube { SIN_REVISAR, SINCRONIZANDO, AL_DIA, SIN_INTERNET, FALLO }

/** Usuario local de la vista previa de depuración: guarda en Room, nunca en la nube. */
const val USUARIO_VISTA_PREVIA = BaseDatos.SIN_NUBE

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val cuentas = Cuentas(app)
    private val panel = PanelAdmin()
    private val sincro = Sincronizacion(app)
    private val contenido = LectorContenido(app.assets)
    private val textos = app.resources
    private val db by lazy { BaseDatos.abrir(app) }
    private val ajustes = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var sesion by mutableStateOf(cuentas.sesionGuardada()); private set

    /** El estudio del estudiante que entró; null para un admin o sin sesión. */
    var estudio by mutableStateOf<EstudioVm?>(null); private set
    var pantalla by mutableStateOf<Pantalla>(if (sesion == null) Pantalla.Entrar else Pantalla.Inicio); private set
    var ocupado by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var nube by mutableStateOf(EstadoNube.SIN_REVISAR); private set

    /** Al entrar: el mensaje mientras baja el progreso. */
    @get:StringRes var paso by mutableStateOf<Int?>(null); private set

    private val uidEstudiante = MutableStateFlow(sesion?.takeIf { it.rol == Rol.ESTUDIANTE }?.uid)

    /** Registros guardados en la tablet que aún no llegan a la nube. */
    val pendientes = uidEstudiante
        .flatMapLatest { uid -> if (uid == null) flowOf(0) else sincro.pendientes(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun ir(destino: Pantalla) {
        error = null
        pantalla = destino
    }

    /** Un paso atrás en el camino: ejercicio → materia → curso → inicio. */
    fun atras() {
        val e = estudio
        val sesionActual = e?.actual?.ejercicio?.sesion ?: e?.resultado?.cierre?.sesion
        when (val p = pantalla) {
            is Pantalla.Materia -> ir(Pantalla.Curso(p.cursoId))
            is Pantalla.Curso -> ir(Pantalla.Inicio)
            is Pantalla.Explicacion -> e?.leccion?.let { e.abrirMateria(p.cursoId, it.materia) } ?: volverAlInicio()
            is Pantalla.Ejercicio, is Pantalla.Resultado -> sesionActual?.let { e?.abrirMateria(it.cursoId, it.materiaId) } ?: volverAlInicio()
            Pantalla.FinPractica -> e?.fin?.let { e.abrirMateria(it.cursoId, it.materiaId) } ?: volverAlInicio()
            else -> volverAlInicio()
        }
    }

    fun volverAlInicio() = ir(if (sesion == null && estudio == null) Pantalla.Entrar else Pantalla.Inicio)

    init {
        sesion?.let(::prepararEstudio)
        // Cada cambio guardado pide subir la cola; WorkManager espera a que haya red.
        viewModelScope.launch {
            pendientes.collect { n -> if (n > 0 && uidEstudiante.value != null) SubidaWorker.programar(app) }
        }
        // Al abrir la app con Internet, se baja lo que cambió en otro dispositivo.
        uidEstudiante.value?.let { sincronizar(it) }
    }

    private fun prepararEstudio(s: Sesion) {
        estudio = if (s.rol == Rol.ESTUDIANTE) crearEstudio(s.uid) else null
    }

    private fun crearEstudio(uid: String) = EstudioVm(
        estudios = Estudios(db, contenido, uid),
        depuracion = BuildConfig.DEBUG && uid == USUARIO_VISTA_PREVIA,
        alcance = viewModelScope,
        ir = ::ir,
        tutor = { tutor },
    )

    /** Solo en el APK de depuración: estudia sin cuenta. Guarda en la tablet, nunca en la nube. */
    fun vistaPrevia() {
        if (BuildConfig.DEBUG && sesion == null) {
            estudio = crearEstudio(USUARIO_VISTA_PREVIA)
            ir(Pantalla.Inicio)
        }
    }

    private fun tarea(accion: suspend () -> Unit) {
        viewModelScope.launch {
            ocupado = true
            error = null
            try {
                accion()
            } catch (e: ErrorCuenta) {
                error = texto(e)
            } catch (e: Exception) {
                error = textos.getString(R.string.error_inesperado, e.message.orEmpty())
            } finally {
                ocupado = false
                paso = null
            }
        }
    }

    private fun texto(e: ErrorCuenta): String {
        val id = when (e.motivo) {
            MotivoCuenta.OTRO -> return textos.getString(R.string.error_inesperado, e.cause?.message.orEmpty())
            MotivoCuenta.USUARIO_NO_VALIDO -> when (e.problemaUsuario) {
                ProblemaUsuario.MUY_CORTO -> R.string.cuenta_usuario_corto
                ProblemaUsuario.MUY_LARGO -> R.string.cuenta_usuario_largo
                ProblemaUsuario.CARACTERES, null -> R.string.cuenta_usuario_caracteres
            }
            MotivoCuenta.SIN_INTERNET -> R.string.cuenta_sin_internet
            MotivoCuenta.USUARIO_O_CLAVE -> R.string.cuenta_usuario_o_clave
            MotivoCuenta.USUARIO_REPETIDO -> R.string.cuenta_usuario_repetido
            MotivoCuenta.CLAVE_VACIA -> R.string.cuenta_clave_vacia
            MotivoCuenta.CLAVE_CORTA -> R.string.cuenta_clave_corta
            MotivoCuenta.CLAVES_DISTINTAS -> R.string.cuenta_claves_distintas
            MotivoCuenta.CLAVE_IGUAL -> R.string.cuenta_clave_igual
            MotivoCuenta.CLAVE_ACTUAL_MAL -> R.string.cuenta_clave_actual_mal
            MotivoCuenta.DEMASIADOS_INTENTOS -> R.string.cuenta_demasiados_intentos
            MotivoCuenta.FALTA_NOMBRE -> R.string.cuenta_falta_nombre
            MotivoCuenta.NO_PUDE_REVISAR -> R.string.cuenta_no_pude_revisar
            MotivoCuenta.CUENTA_NO_LISTA -> R.string.cuenta_no_lista
            MotivoCuenta.PERFIL_SIN_GUARDAR -> R.string.cuenta_perfil_sin_guardar
            MotivoCuenta.SIN_SESION -> R.string.cuenta_sin_sesion
        }
        return textos.getString(id)
    }

    // --- Nube -----------------------------------------------------------------

    /** Baja, une y sube. Sin Internet no pasa nada: todo sigue en la tablet y se sube después. */
    fun sincronizar(uid: String? = uidEstudiante.value) {
        if (uid == null) return
        viewModelScope.launch {
            nube = EstadoNube.SINCRONIZANDO
            nube = try {
                sincro.sincronizar(uid)
                if (pendientes.value == 0) EstadoNube.AL_DIA else EstadoNube.SIN_INTERNET
            } catch (e: SinConexion) {
                EstadoNube.SIN_INTERNET
            } catch (e: Exception) {
                EstadoNube.FALLO
            }
        }
    }

    // --- Cuentas --------------------------------------------------------------

    fun entrar(usuario: String, clave: String) = tarea {
        val nueva = cuentas.entrar(usuario, clave)
        if (nueva.rol == Rol.ESTUDIANTE) {
            // En un dispositivo nuevo se baja todo el historial antes de empezar.
            paso = R.string.bajando_progreso
            nube = try {
                sincro.sincronizar(nueva.uid)
                EstadoNube.AL_DIA
            } catch (e: SinConexion) {
                EstadoNube.SIN_INTERNET
            } catch (e: Exception) {
                EstadoNube.FALLO
            }
        }
        sesion = nueva
        uidEstudiante.value = nueva.uid.takeIf { nueva.rol == Rol.ESTUDIANTE }
        prepararEstudio(nueva)
        ir(Pantalla.Inicio)
    }

    /** Con registros sin subir, la pantalla avisa antes de salir. */
    var avisoSalir by mutableStateOf(false); private set

    fun pedirSalir() {
        if (pendientes.value > 0) avisoSalir = true else salir()
    }

    fun cancelarSalir() {
        avisoSalir = false
    }

    /** Intenta subir lo pendiente y sale solo si todo llegó a la nube. */
    fun subirYSalir() = tarea {
        val uid = uidEstudiante.value ?: return@tarea salir()
        paso = R.string.subiendo
        try {
            sincro.sincronizar(uid)
        } catch (e: Exception) {
            nube = EstadoNube.SIN_INTERNET
        }
        if (pendientes.value == 0 && sincro.pendientes(uid).first() == 0) salir() else error = textos.getString(R.string.salir_no_subio)
    }

    /** Lo que no subió se queda en la tablet y sube cuando este estudiante vuelva a entrar. */
    fun salir() {
        avisoSalir = false
        cuentas.salir()
        sesion = null
        uidEstudiante.value = null
        estudio = null
        nube = EstadoNube.SIN_REVISAR
        ir(Pantalla.Entrar)
    }

    var claveCambiada by mutableStateOf(false); private set

    fun abrirCambiarClave() {
        claveCambiada = false
        ir(Pantalla.CambiarClave)
    }

    fun cambiarClave(actual: String, nueva: String, repetida: String) = tarea {
        claveCambiada = false
        cuentas.cambiarClave(actual, nueva, repetida)
        claveCambiada = true
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
        estudianteCreado = textos.getString(R.string.estudiante_creado, listo.first, listo.second)
    }

    fun limpiarCreacion() {
        estudianteCreado = null
        perfilPendiente = null
        error = null
    }

    // --- Kiwi -----------------------------------------------------------------

    var direccionKiwi by mutableStateOf(ajustes.getString("kiwi", "").orEmpty()); private set
    var estadoKiwi by mutableStateOf<EstadoKiwi>(EstadoKiwi.SinProbar); private set

    /** Un solo cliente por dirección: recuerda si LM Studio estaba apagado y no espera 2 s en cada pista. */
    private var cliente = ClienteKiwi(direccionKiwi)

    /** Kiwi, si este dispositivo tiene guardada la dirección de LM Studio. */
    private val tutor: Tutor? get() = if (cliente.configurado) Tutor(cliente) else null

    fun probarKiwi(direccion: String) {
        val nuevo = ClienteKiwi(direccion)
        cliente = nuevo
        direccionKiwi = nuevo.base
        ajustes.edit().putString("kiwi", nuevo.base).apply()
        estadoKiwi = EstadoKiwi.Probando
        viewModelScope.launch {
            val modelos = nuevo.modelos()?.filterNot(ClienteKiwi::esDeEmbeddings)
            if (modelos.isNullOrEmpty()) {
                estadoKiwi = EstadoKiwi.Apagado
                return@launch
            }
            estadoKiwi = EstadoKiwi.Activo(modelos.first(), null)
            estadoKiwi = try {
                EstadoKiwi.Activo(modelos.first(), nuevo.saludar())
            } catch (e: Exception) {
                EstadoKiwi.Activo(modelos.first(), null, textos.getString(R.string.kiwi_no_contesta, e.message.orEmpty()))
            }
            // Con Kiwi encendido, se califican los Writing que esperaban.
            estudio?.calificarPendientes()
        }
    }

    // --- Panel de admin -------------------------------------------------------

    var estudiantes by mutableStateOf<List<FichaEstudiante>?>(null); private set
    var respuestas by mutableStateOf<List<RespuestaSubida>?>(null); private set
    var resumenAbierto by mutableStateOf<ResumenPanel?>(null); private set

    fun cargarEstudiantes() = tarea { estudiantes = panel.estudiantes() }

    fun abrirEstudiante(ficha: FichaEstudiante) {
        respuestas = null
        resumenAbierto = ficha.resumen
        ir(Pantalla.DetalleEstudiante(ficha))
        tarea { respuestas = panel.ultimasRespuestas(ficha.uid) }
    }
}
