package com.preparate.sincro

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.preparate.dominio.ProblemaUsuario
import com.preparate.dominio.Usuario
import kotlinx.coroutines.tasks.await

enum class Rol(val valor: String) {
    ESTUDIANTE("estudiante"),
    ADMIN("admin"),
    ;

    companion object {
        fun desde(valor: String?): Rol? = entries.firstOrNull { it.valor == valor }
    }
}

data class Sesion(val uid: String, val usuario: String, val nombre: String, val rol: Rol)

/** Por qué falló algo de la cuenta. La interfaz pone el texto desde `strings.xml`. */
enum class MotivoCuenta {
    SIN_INTERNET,
    USUARIO_O_CLAVE,
    USUARIO_REPETIDO,
    USUARIO_NO_VALIDO,
    CLAVE_VACIA,
    CLAVE_CORTA,
    CLAVES_DISTINTAS,
    CLAVE_IGUAL,
    CLAVE_ACTUAL_MAL,
    DEMASIADOS_INTENTOS,
    FALTA_NOMBRE,
    NO_PUDE_REVISAR,
    CUENTA_NO_LISTA,
    PERFIL_SIN_GUARDAR,
    SIN_SESION,
    OTRO,
}

open class ErrorCuenta(
    val motivo: MotivoCuenta,
    /** Solo con `USUARIO_NO_VALIDO`. */
    val problemaUsuario: ProblemaUsuario? = null,
    causa: Throwable? = null,
) : Exception(motivo.name, causa)

/** La cuenta se creó en Firebase Auth, pero falta su documento usuarios/{uid}. */
class PerfilSinGuardar(val uid: String, val nombre: String, val usuario: String) :
    ErrorCuenta(MotivoCuenta.PERFIL_SIN_GUARDAR)

/**
 * Inicio de sesión con usuario y clave, sin correos a la vista.
 * El rol se guarda también en el dispositivo para poder estudiar sin Internet.
 */
class Cuentas(private val contexto: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val preferencias = preferencias(contexto)

    /** La sesión guardada en este dispositivo, si la hay. No necesita Internet. */
    fun sesionGuardada(): Sesion? {
        val usuarioAuth = auth.currentUser ?: return null
        val uid = usuarioAuth.uid
        val rol = rolGuardado(contexto, uid) ?: return null
        return Sesion(
            uid = uid,
            usuario = Usuario.desdeCorreo(usuarioAuth.email.orEmpty()),
            nombre = preferencias.getString("nombre_$uid", null).orEmpty(),
            rol = rol,
        )
    }

    suspend fun entrar(textoUsuario: String, clave: String): Sesion {
        val usuario = Usuario.limpiar(textoUsuario)
        Usuario.problema(usuario)?.let { throw ErrorCuenta(MotivoCuenta.USUARIO_NO_VALIDO, it) }
        if (clave.isEmpty()) throw ErrorCuenta(MotivoCuenta.CLAVE_VACIA)
        val resultado = traducir { auth.signInWithEmailAndPassword(Usuario.aCorreo(usuario), clave).await() }
        val uid = resultado.user!!.uid
        val documento = try {
            db.collection("usuarios").document(uid).get().await()
        } catch (e: Exception) {
            auth.signOut()
            throw ErrorCuenta(MotivoCuenta.NO_PUDE_REVISAR, causa = e)
        }
        val rol = Rol.desde(documento.getString("rol"))
        if (rol == null) {
            auth.signOut()
            throw ErrorCuenta(MotivoCuenta.CUENTA_NO_LISTA)
        }
        val nombre = documento.getString("nombre").orEmpty()
        preferencias.edit().putString("rol_$uid", rol.valor).putString("nombre_$uid", nombre).apply()
        return Sesion(uid, usuario, nombre, rol)
    }

    fun salir() = auth.signOut()

    /** Cambia la clave de quien tiene la sesión iniciada. Firebase pide la clave actual otra vez. */
    suspend fun cambiarClave(actual: String, nueva: String, repetida: String) {
        val usuario = auth.currentUser ?: throw ErrorCuenta(MotivoCuenta.SIN_SESION)
        if (actual.isEmpty()) throw ErrorCuenta(MotivoCuenta.CLAVE_VACIA)
        if (nueva.length < Usuario.CLAVE_MINIMA) throw ErrorCuenta(MotivoCuenta.CLAVE_CORTA)
        if (nueva != repetida) throw ErrorCuenta(MotivoCuenta.CLAVES_DISTINTAS)
        if (nueva == actual) throw ErrorCuenta(MotivoCuenta.CLAVE_IGUAL)
        try {
            usuario.reauthenticate(EmailAuthProvider.getCredential(usuario.email.orEmpty(), actual)).await()
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw ErrorCuenta(MotivoCuenta.CLAVE_ACTUAL_MAL, causa = e)
        } catch (e: Exception) {
            throw traducirError(e)
        }
        traducir { usuario.updatePassword(nueva).await() }
    }

    /**
     * Crea un estudiante sin cerrar la sesión del admin: la cuenta se crea con una
     * segunda instancia de Firebase. Después el admin escribe usuarios/{uid}.
     */
    suspend fun crearEstudiante(nombre: String, textoUsuario: String, clave: String): String {
        val usuario = Usuario.limpiar(textoUsuario)
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) throw ErrorCuenta(MotivoCuenta.FALTA_NOMBRE)
        Usuario.problema(usuario)?.let { throw ErrorCuenta(MotivoCuenta.USUARIO_NO_VALIDO, it) }
        if (clave.length < Usuario.CLAVE_MINIMA) throw ErrorCuenta(MotivoCuenta.CLAVE_CORTA)

        val segunda = FirebaseApp.getApps(contexto).firstOrNull { it.name == APP_CREAR }
            ?: FirebaseApp.initializeApp(contexto, FirebaseApp.getInstance().options, APP_CREAR)
        val authSegunda = FirebaseAuth.getInstance(segunda)
        val uid = try {
            traducir { authSegunda.createUserWithEmailAndPassword(Usuario.aCorreo(usuario), clave).await() }.user!!.uid
        } finally {
            authSegunda.signOut()
        }
        guardarPerfil(uid, nombreLimpio, usuario)
        return uid
    }

    /** Segundo paso de crear un estudiante; se puede reintentar si falla. */
    suspend fun guardarPerfil(uid: String, nombre: String, usuario: String) {
        try {
            db.collection("usuarios").document(uid)
                .set(mapOf("nombre" to nombre, "usuario" to usuario, "rol" to Rol.ESTUDIANTE.valor))
                .await()
        } catch (e: Exception) {
            throw PerfilSinGuardar(uid, nombre, usuario)
        }
    }

    private suspend fun <T> traducir(accion: suspend () -> T): T = try {
        accion()
    } catch (e: Exception) {
        throw traducirError(e)
    }

    private fun traducirError(e: Exception): ErrorCuenta = when (e) {
        is ErrorCuenta -> e

        is FirebaseNetworkException -> ErrorCuenta(MotivoCuenta.SIN_INTERNET, causa = e)

        is FirebaseTooManyRequestsException -> ErrorCuenta(MotivoCuenta.DEMASIADOS_INTENTOS, causa = e)

        is FirebaseAuthUserCollisionException -> ErrorCuenta(MotivoCuenta.USUARIO_REPETIDO, causa = e)

        is FirebaseAuthWeakPasswordException -> ErrorCuenta(MotivoCuenta.CLAVE_CORTA, causa = e)

        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException ->
            ErrorCuenta(MotivoCuenta.USUARIO_O_CLAVE, causa = e)

        else -> ErrorCuenta(MotivoCuenta.OTRO, causa = e)
    }

    companion object {
        private const val APP_CREAR = "crear-estudiantes"

        private fun preferencias(contexto: Context) = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

        /** El rol guardado al entrar; null si en este dispositivo nunca entró. */
        fun rolGuardado(contexto: Context, uid: String): Rol? =
            Rol.desde(preferencias(contexto).getString("rol_$uid", null))
    }
}
