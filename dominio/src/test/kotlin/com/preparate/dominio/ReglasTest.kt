package com.preparate.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasTest {
    @Test
    fun ayudaSoloEnPractica() {
        assertTrue(ayudaPermitida(TipoSesion.LECCION))
        assertTrue(ayudaPermitida(TipoSesion.REPASO))
        assertFalse(ayudaPermitida(TipoSesion.PRUEBA_SECCION))
        assertFalse(ayudaPermitida(TipoSesion.EXAMEN_FINAL))
    }

    @Test
    fun valoresIgualesALaWeb() {
        assertEquals(
            listOf("leccion", "repaso", "prueba_seccion", "examen_final"),
            TipoSesion.entries.map { it.valor },
        )
    }
}
