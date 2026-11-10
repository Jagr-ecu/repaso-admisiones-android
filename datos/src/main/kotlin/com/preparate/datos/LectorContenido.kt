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
 * `leer` recibe una ruta relativa a la carpeta del paquete, como "curso.json".
 */
class LectorContenido(private val leer: (String) -> String) {
    constructor(assets: AssetManager) : this({ ruta ->
        assets.open("contenido/$ruta").bufferedReader(Charsets.UTF_8).use { it.readText() }
    })

    val manifiesto: Manifiesto by lazy { Paquete.manifiesto(leer("manifiesto.json")) }

    val curso: Curso by lazy { Paquete.curso(leer("curso.json")) }

    private val plantillas = mutableMapOf<String, PlantillaConVariantes>()

    fun leccion(id: String): Leccion = Paquete.leccion(leer("lecciones/$id.json"))

    /** Cada plantilla va en su propio archivo y se carga solo cuando hace falta. */
    fun plantilla(id: String): PlantillaConVariantes = synchronized(plantillas) {
        plantillas.getOrPut(id) { Paquete.plantilla(leer("variantes/$id.json")) }
    }

    fun variantes(plantillaId: String): List<Variante> = plantilla(plantillaId).variantes
}
