package com.preparate.datos

import com.preparate.dominio.TipoEjercicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lee el paquete que va dentro del APK, tal como lo hace la app. */
class LectorContenidoTest {
    private val carpeta = File("../app/src/main/assets/contenido")
    private val lector = LectorContenido { ruta -> File(carpeta, ruta).readText(Charsets.UTF_8) }

    @Test
    fun leeTodoElCursoConSusVariantes() {
        val manifiesto = lector.manifiesto
        val secciones = lector.curso.materias.flatMap { it.secciones }
        val lecciones = secciones.flatMap { it.lecciones }
        val plantillas = secciones.flatMap { it.plantillas }
        assertEquals(manifiesto.lecciones, lecciones.size)
        assertEquals(manifiesto.plantillas, plantillas.size)
        assertEquals(manifiesto.plantillas, carpeta.resolve("variantes").list()!!.size)

        var variantes = 0
        for (resumen in plantillas) {
            val (plantilla, lista) = lector.plantilla(resumen.id)
            assertEquals(resumen.id, plantilla.id)
            assertEquals(resumen.tipo, plantilla.tipo)
            assertTrue(plantilla.version.isNotEmpty())
            assertTrue("${plantilla.id} sin variantes", lista.isNotEmpty())
            assertEquals("${plantilla.id}: huellas repetidas", lista.size, lista.map { it.huella }.toSet().size)
            for (v in lista) {
                when (plantilla.tipo) {
                    TipoEjercicio.ESCRITURA -> {
                        assertNull(v.respuesta)
                        assertFalse(plantilla.rubrica.isEmpty())
                    }
                    TipoEjercicio.CUADERNO -> assertEquals(v.cuenta!!.casillas, v.cuenta!!.esperadas.size)
                    TipoEjercicio.ORDENAR -> assertEquals(v.respuesta!!.split(" ").sorted(), v.fragmentos.sorted())
                    else -> assertNotNull(v.respuesta)
                }
                if (plantilla.tipo != TipoEjercicio.ESCRITURA) assertEquals(3, v.pistas.size)
            }
            variantes += lista.size
        }
        assertEquals(manifiesto.variantes, variantes)

        for (resumen in lecciones) {
            val leccion = lector.leccion(resumen.id)
            assertEquals(resumen.titulo, leccion.titulo)
            assertEquals(5, leccion.ejercicios.sumOf { it.cantidad })
            for (ej in leccion.ejercicios) assertTrue(lector.variantes(ej.plantilla).isNotEmpty())
        }
    }

    @Test
    fun lasMateriasNoSeMezclan() {
        for (materia in lector.curso.materias) {
            for (seccion in materia.secciones) {
                val prefijo = "${materia.prefijo}.${seccion.id}."
                (seccion.lecciones.map { it.id } + seccion.plantillas.map { it.id }).forEach {
                    assertTrue("$it no es de $prefijo", it.startsWith(prefijo))
                }
                for (resumen in seccion.lecciones) assertEquals(materia.id, lector.leccion(resumen.id).materia)
            }
        }
    }
}
