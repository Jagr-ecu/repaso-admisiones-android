package com.preparate.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsuarioTest {
    @Test
    fun aceptaLosValidos() {
        listOf("ana", "leo.2027", "maria_jose", "a-b", "x".repeat(20)).forEach { assertTrue(it, Usuario.esValido(it)) }
    }

    @Test
    fun rechazaLosNoValidos() {
        listOf("al", "x".repeat(21), "maría", "niño", "ana luz", "Ana", "ana@x", "").forEach {
            assertFalse(it, Usuario.esValido(it))
        }
    }

    @Test
    fun limpiaYArmaElCorreo() {
        assertEquals("ana", Usuario.limpiar("  Ana "))
        assertEquals("ana@preparate.invalid", Usuario.aCorreo("ana"))
        assertEquals("ana", Usuario.desdeCorreo("ana@preparate.invalid"))
        assertNull(Usuario.problema("ana"))
    }
}
