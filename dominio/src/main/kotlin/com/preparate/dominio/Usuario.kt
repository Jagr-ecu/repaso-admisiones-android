package com.preparate.dominio

/** Nombres de usuario: sin correos a la vista. Firebase Auth pide un correo, así que se arma uno reservado. */
object Usuario {
    /** Dominio reservado (RFC 2606): nunca recibe mensajes. */
    const val DOMINIO = "preparate.invalid"
    const val CLAVE_MINIMA = 6
    private val VALIDO = Regex("^[a-z0-9._-]{3,20}$")

    /** Lo que escribe el estudiante, limpio: sin espacios alrededor y en minúsculas. */
    fun limpiar(texto: String): String = texto.trim().lowercase()

    fun esValido(usuario: String): Boolean = VALIDO.matches(usuario)

    /** Motivo por el que un usuario no sirve, en palabras sencillas; null si sirve. */
    fun problema(usuario: String): String? = when {
        usuario.length < 3 -> "El usuario necesita al menos 3 letras o números."
        usuario.length > 20 -> "El usuario puede tener hasta 20 letras o números."
        !esValido(usuario) -> "Usa solo letras sin tilde, números, punto, guion o guion bajo. Sin espacios ni ñ."
        else -> null
    }

    fun aCorreo(usuario: String): String = "$usuario@$DOMINIO"

    fun desdeCorreo(correo: String): String = correo.removeSuffix("@$DOMINIO")
}
