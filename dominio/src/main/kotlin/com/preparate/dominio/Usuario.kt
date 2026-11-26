package com.preparate.dominio

/** Nombres de usuario: sin correos a la vista. Firebase Auth pide un correo, así que se arma uno reservado. */
object Usuario {
    /** Dominio reservado (RFC 2606): nunca recibe mensajes. */
    const val DOMINIO = "preparate.invalid"

    /** Para claves nuevas o cambiadas (ADR-0021). Entrar con una clave más corta, de antes, sigue funcionando. */
    const val CLAVE_MINIMA = 8
    private val VALIDO = Regex("^[a-z0-9._-]{3,20}$")

    /** Lo que escribe el estudiante, limpio: sin espacios alrededor y en minúsculas. */
    fun limpiar(texto: String): String = texto.trim().lowercase()

    fun esValido(usuario: String): Boolean = VALIDO.matches(usuario)

    /** Motivo por el que un usuario no sirve; null si sirve. La interfaz pone el texto. */
    fun problema(usuario: String): ProblemaUsuario? = when {
        usuario.length < 3 -> ProblemaUsuario.MUY_CORTO
        usuario.length > 20 -> ProblemaUsuario.MUY_LARGO
        !esValido(usuario) -> ProblemaUsuario.CARACTERES
        else -> null
    }

    fun aCorreo(usuario: String): String = "$usuario@$DOMINIO"

    fun desdeCorreo(correo: String): String = correo.removeSuffix("@$DOMINIO")
}

enum class ProblemaUsuario { MUY_CORTO, MUY_LARGO, CARACTERES }
