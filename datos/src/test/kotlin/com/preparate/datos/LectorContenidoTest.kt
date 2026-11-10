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
    fun todoVaDentroDelCursoAdmisiones() {
        assertEquals(listOf("admisiones" to "Admisiones"), lector.cursos.map { it.id to it.nombre })
        assertEquals(listOf("admisiones"), lector.manifiesto.cursos)
        assertEquals(setOf("admisiones", "cursos.json", "manifiesto.json"), carpeta.list()!!.toSet())
    }

    @Test
    fun leeTodoElCursoConSusVariantes() {
        val manifiesto = lector.manifiesto
        var lecciones = 0
        var plantillas = 0
        var variantes = 0
        for (curso in lector.cursos) {
            val secciones = curso.materias.flatMap { it.secciones }
            for (resumen in secciones.flatMap { it.plantillas }) {
                val (plantilla, lista) = lector.plantilla(curso.id, resumen.id)
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
                plantillas++
                variantes += lista.size
            }
            assertEquals(secciones.sumOf { it.plantillas.size }, carpeta.resolve("${curso.id}/variantes").list()!!.size)
            for (resumen in secciones.flatMap { it.lecciones }) {
                val leccion = lector.leccion(curso.id, resumen.id)
                assertEquals(curso.id, leccion.curso)
                assertEquals(resumen.titulo, leccion.titulo)
                assertEquals(5, leccion.ejercicios.sumOf { it.cantidad })
                for (ej in leccion.ejercicios) assertTrue(lector.variantes(curso.id, ej.plantilla).isNotEmpty())
                lecciones++
            }
        }
        assertEquals(manifiesto.lecciones, lecciones)
        assertEquals(manifiesto.plantillas, plantillas)
        assertEquals(manifiesto.variantes, variantes)
    }

    @Test
    fun lasMateriasNoSeMezclan() {
        for (curso in lector.cursos) {
            for (materia in curso.materias) {
                for (seccion in materia.secciones) {
                    val prefijo = "${materia.prefijo}.${seccion.id}."
                    (seccion.lecciones.map { it.id } + seccion.plantillas.map { it.id }).forEach {
                        assertTrue("$it no es de $prefijo", it.startsWith(prefijo))
                    }
                    for (resumen in seccion.lecciones) {
                        assertEquals(materia.id, lector.leccion(curso.id, resumen.id).materia)
                    }
                }
            }
        }
    }
}
