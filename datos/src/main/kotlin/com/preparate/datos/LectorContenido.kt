package com.preparate.datos

import android.content.res.AssetManager
import com.preparate.dominio.EjercicioRef
import com.preparate.dominio.ErrorEsperado
import com.preparate.dominio.Leccion
import com.preparate.dominio.Plantilla
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoVerificador
import com.preparate.dominio.Variante
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lee el paquete de contenido de assets/contenido/ (solo lectura).
 * Formato provisional de la fase 1; el definitivo sale de `exportar-paquete` en la fase 2.
 */
class LectorContenido(private val assets: AssetManager) {
    private val variantes = mutableMapOf<String, List<Variante>>()

    fun leccion(id: String): Leccion {
        val j = leer("contenido/lecciones/$id.json")
        return Leccion(
            id = j.getString("id"),
            materia = j.getString("materia"),
            seccion = j.getString("seccion"),
            titulo = j.getString("titulo"),
            concepto = j.getString("concepto"),
            contenido = j.getString("contenido"),
            ejercicios = j.getJSONArray("ejercicios").objetos().map {
                EjercicioRef(it.getString("plantilla"), it.getInt("cantidad"))
            },
        )
    }

    /** Cada plantilla va en su propio archivo y se carga solo cuando hace falta. */
    fun variantes(plantillaId: String): List<Variante> = synchronized(variantes) {
        variantes.getOrPut(plantillaId) {
            val j = leer("contenido/variantes/$plantillaId.json")
            val plantilla = Plantilla(
                id = j.getString("id"),
                tipo = TipoEjercicio.desde(j.getString("tipo")),
                verificador = TipoVerificador.desde(j.getString("verificador")),
                formaIrreducible = j.getBoolean("forma_irreducible"),
                ignorarTildes = j.getBoolean("ignorar_tildes"),
            )
            j.getJSONArray("variantes").objetos().map { v ->
                Variante(
                    plantilla = plantilla,
                    semilla = v.getInt("semilla"),
                    enunciado = v.getString("enunciado"),
                    respuesta = v.getString("respuesta"),
                    aceptadas = v.getJSONArray("aceptadas").textos(),
                    opciones = v.getJSONArray("opciones").textos(),
                    pistas = v.getJSONArray("pistas").textos(),
                    solucion = if (v.isNull("solucion")) null else v.getString("solucion"),
                    errores = v.getJSONArray("errores").objetos().map {
                        ErrorEsperado(it.getString("valor"), it.getString("explicacion"))
                    },
                )
            }
        }
    }

    private fun leer(ruta: String): JSONObject =
        JSONObject(assets.open(ruta).bufferedReader(Charsets.UTF_8).use { it.readText() })

    private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.textos(): List<String> = (0 until length()).map { getString(it) }
}
