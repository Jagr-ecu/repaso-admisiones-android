package com.preparate.sincro

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.preparate.dominio.Usuario
import kotlinx.coroutines.tasks.await

enum class Rol(val valor: String) {
    ESTUDIANTE("estudiante"),
    ADMIN("admin");

    companion object {
        fun desde(valor: String?): Rol? = entries.firstOrNull { it.valor == valor }
    }
}

data class Sesion(val uid: String, val usuario: String, val nombre: String, val rol: Rol)

open class ErrorCuenta(mensaje: String) : Exception(mensaje)

/** La cuenta se creó en Firebase Auth, pero falta su documento usuarios/{uid}. */
class PerfilSinGuardar(val uid: String, val nombre: String, val usuario: String) :
    ErrorCuenta("La cuenta se creó, pero no pude guardar su perfil. Toca Reintentar.")

/**
 * Inicio de sesión con usuario y clave, sin correos a la vista.
 * El rol se guarda también en el dispositivo para poder estudiar sin Internet.
 */
class Cuentas(private val contexto: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val preferencias = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    /** La sesión guardada en este dispositivo, si la hay. No necesita Internet. */
    fun sesionGuardada(): Sesion? {
        val usuarioAuth = auth.currentUser ?: return null
        val uid = usuarioAuth.uid
        val rol = Rol.desde(preferencias.getString("rol_$uid", null)) ?: return null
        return Sesion(
            uid = uid,
            usuario = Usuario.desdeCorreo(usuarioAuth.email.orEmpty()),
            nombre = preferencias.getString("nombre_$uid", null).orEmpty(),
            rol = rol,
        )
    }

    suspend fun entrar(textoUsuario: String, clave: String): Sesion {
        val usuario = Usuario.limpiar(textoUsuario)
        Usuario.problema(usuario)?.let { throw ErrorCuenta(it) }
        if (clave.isEmpty()) throw ErrorCuenta("Escribe tu clave.")
        val resultado = traducir { auth.signInWithEmailAndPassword(Usuario.aCorreo(usuario), clave).await() }
        val uid = resultado.user!!.uid
        val documento = try {
            db.collection("usuarios").document(uid).get().await()
        } catch (e: Exception) {
            auth.signOut()
            throw ErrorCuenta("No pude revisar tu cuenta. Revisa el Internet e inténtalo otra vez.")
        }
        val rol = Rol.desde(documento.getString("rol"))
        if (rol == null) {
            auth.signOut()
            throw ErrorCuenta("Tu cuenta todavía no está lista. Pide ayuda a un adulto.")
        }
        val nombre = documento.getString("nombre").orEmpty()
        preferencias.edit().putString("rol_$uid", rol.valor).putString("nombre_$uid", nombre).apply()
        return Sesion(uid, usuario, nombre, rol)
    }

    fun salir() = auth.signOut()

    /**
     * Crea un estudiante sin cerrar la sesión del admin: la cuenta se crea con una
     * segunda instancia de Firebase. Después el admin escribe usuarios/{uid}.
     */
    suspend fun crearEstudiante(nombre: String, textoUsuario: String, clave: String): String {
        val usuario = Usuario.limpiar(textoUsuario)
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isEmpty()) throw ErrorCuenta("Escribe el nombre del estudiante.")
        Usuario.problema(usuario)?.let { throw ErrorCuenta(it) }
        if (clave.length < Usuario.CLAVE_MINIMA) throw ErrorCuenta("La clave necesita al menos 6 caracteres.")

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
    } catch (e: FirebaseNetworkException) {
        throw ErrorCuenta("Necesitas Internet para esto.")
    } catch (e: FirebaseAuthUserCollisionException) {
        throw ErrorCuenta("Ese usuario ya existe. Elige otro.")
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw ErrorCuenta("La clave es muy débil. Usa al menos 6 caracteres.")
    } catch (e: FirebaseAuthInvalidUserException) {
        throw ErrorCuenta("Usuario o clave incorrectos.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw ErrorCuenta("Usuario o clave incorrectos.")
    } catch (e: ErrorCuenta) {
        throw e
    } catch (e: Exception) {
        throw ErrorCuenta("Algo salió mal: ${e.message}")
    }

    private companion object {
        const val APP_CREAR = "crear-estudiantes"
    }
}
