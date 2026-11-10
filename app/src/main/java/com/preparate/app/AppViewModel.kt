package com.preparate.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.preparate.datos.Estudios
import com.preparate.datos.LectorContenido
import com.preparate.datos.local.BaseDatos
import com.preparate.sincro.Rol
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
    data class DetalleEstudiante(val ficha: FichaEstudiante) : Pantalla
}

sealed interface EstadoKiwi {
    data object SinProbar : EstadoKiwi
    data object Probando : EstadoKiwi
    data class Activo(val modelo: String, val saludo: String?, val error: String? = null) : EstadoKiwi
    data object Apagado : EstadoKiwi
}

/** Usuario local de la vista previa de depuración: guarda en Room, nunca en la nube. */
const val USUARIO_VISTA_PREVIA = "vista-previa"

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val cuentas = Cuentas(app)
    private val progreso = Progreso()
    private val contenido = LectorContenido(app.assets)
    private val db by lazy { BaseDatos.abrir(app) }
    private val ajustes = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var sesion by mutableStateOf(cuentas.sesionGuardada()); private set

    /** El estudio del estudiante que entró; null para un admin o sin sesión. */
    var estudio by mutableStateOf<EstudioVm?>(null); private set
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
    }

    private fun prepararEstudio(s: Sesion) {
        estudio = if (s.rol == Rol.ESTUDIANTE) crearEstudio(s.uid, subir = true) else null
    }

    private fun crearEstudio(uid: String, subir: Boolean) = EstudioVm(
        estudios = Estudios(db, contenido, uid),
        depuracion = BuildConfig.DEBUG && uid == USUARIO_VISTA_PREVIA,
        alcance = viewModelScope,
        ir = ::ir,
        alGuardar = { ej, texto, resultado, pistas ->
            // Provisional (fase 1): la respuesta va directo a Firestore. En la fase 5 la sube la cola de Room.
            if (subir) {
                progreso.registrar(
                    uid,
                    Intento(
                        id = ej.intento.id,
                        sesionId = ej.sesion.id,
                        leccion = ej.sesion.leccionId ?: ej.sesion.tipo.valor,
                        plantilla = ej.variante.plantilla.id,
                        semilla = ej.variante.semilla,
                        respuesta = texto,
                        estado = resultado.estado.valor,
                        errorComun = resultado.errorComun,
                        pistasUsadas = pistas,
                    ),
                )
            }
        },
    )

    /** Solo en el APK de depuración: estudia sin cuenta. Guarda en la tablet, nunca en la nube. */
    fun vistaPrevia() {
        if (BuildConfig.DEBUG && sesion == null) {
            estudio = crearEstudio(USUARIO_VISTA_PREVIA, subir = false)
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
        prepararEstudio(nueva)
        ir(Pantalla.Inicio)
    }

    fun salir() {
        cuentas.salir()
        sesion = null
        uidActual.value = null
        estudio = null
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
