package com.preparate.datos

import android.content.res.AssetManager
import com.preparate.dominio.Curso
import com.preparate.dominio.Leccion
import com.preparate.dominio.Manifiesto
import com.preparate.dominio.Paquete
import com.preparate.dominio.PlantillaConVariantes
import com.preparate.dominio.Variante

/**
 * Lee el paquete de contenido (solo lectura) que genera `exportar-paquete` en el proyecto web.
 * `leer` recibe una ruta relativa a la carpeta del paquete, como "cursos.json".
 * Cada curso tiene su carpeta: los ids de lecciones y plantillas solo son únicos dentro de su curso.
 */
class LectorContenido(private val leer: (String) -> String) {
    constructor(assets: AssetManager) : this({ ruta ->
        assets.open("contenido/$ruta").bufferedReader(Charsets.UTF_8).use { it.readText() }
    })

    val manifiesto: Manifiesto by lazy { Paquete.manifiesto(leer("manifiesto.json")) }

    val cursos: List<Curso> by lazy { Paquete.cursos(leer("cursos.json")) }

    fun curso(id: String): Curso = cursos.firstOrNull { it.id == id } ?: error("no existe el curso $id")

    private val plantillas = mutableMapOf<Pair<String, String>, PlantillaConVariantes>()

    fun leccion(cursoId: String, id: String): Leccion = Paquete.leccion(leer("$cursoId/lecciones/$id.json"))

    /** Cada plantilla va en su propio archivo y se carga solo cuando hace falta. */
    fun plantilla(cursoId: String, id: String): PlantillaConVariantes = synchronized(plantillas) {
        plantillas.getOrPut(cursoId to id) { Paquete.plantilla(leer("$cursoId/variantes/$id.json")) }
    }

    fun variantes(cursoId: String, plantillaId: String): List<Variante> = plantilla(cursoId, plantillaId).variantes
}
