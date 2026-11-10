package com.preparate.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lee los archivos del paquete de contenido (assets/contenido/). No sabe de dónde salen
 * los textos: la app los lee de los assets y los tests, del disco.
 */
object Paquete {
    /** El formato que entiende esta versión de la app. */
    const val FORMATO = 2

    // Estricto: si Python cambia el formato sin cambiar la app, se nota al leer, no a mitad de un tema.
    private val json = Json { ignoreUnknownKeys = false }

    fun manifiesto(texto: String): Manifiesto = json.decodeFromString<Manifiesto>(texto).also {
        require(it.formato == FORMATO) { "el paquete tiene el formato ${it.formato} y la app entiende el $FORMATO" }
    }

    fun cursos(texto: String): List<Curso> = json.decodeFromString<Cursos>(texto).cursos

    fun leccion(texto: String): Leccion = json.decodeFromString(texto)

    fun plantilla(texto: String): PlantillaConVariantes =
        json.decodeFromString<ArchivoPlantilla>(texto).aDominio()
}

/** <curso>/variantes/<plantilla>.json: los datos de la plantilla y sus variantes, sin repetir la plantilla en cada una. */
@Serializable
internal data class ArchivoPlantilla(
    val id: String,
    val version: String,
    val concepto: String,
    val tipo: TipoEjercicio,
    val dificultad: Int,
    val verificador: TipoVerificador,
    @SerialName("ignorar_tildes") val ignorarTildes: Boolean,
    @SerialName("forma_irreducible") val formaIrreducible: Boolean,
    val rubrica: List<CriterioRubrica> = emptyList(),
    @SerialName("opciones_fijas") val opcionesFijas: List<String>,
    val variantes: List<VarianteJson>,
) {
    fun plantilla() = Plantilla(
        id = id,
        tipo = tipo,
        verificador = verificador,
        formaIrreducible = formaIrreducible,
        ignorarTildes = ignorarTildes,
        version = version,
        concepto = concepto,
        dificultad = dificultad,
        rubrica = rubrica,
        opcionesFijas = opcionesFijas,
    )

    fun aDominio(): PlantillaConVariantes {
        val plantilla = plantilla()
        return PlantillaConVariantes(plantilla, variantes.map { it.aDominio(plantilla) })
    }
}

@Serializable
internal data class VarianteJson(
    val semilla: Int,
    val huella: String,
    val enunciado: String,
    val respuesta: String?,
    val aceptadas: List<String>,
    val opciones: List<String>,
    val fragmentos: List<String>,
    val pistas: List<String>,
    val solucion: String?,
    val errores: List<ErrorEsperado>,
    val cuenta: Cuenta? = null,
) {
    fun aDominio(plantilla: Plantilla) = Variante(
        plantilla = plantilla,
        semilla = semilla,
        enunciado = enunciado,
        respuesta = respuesta,
        huella = huella,
        aceptadas = aceptadas,
        opciones = opciones,
        fragmentos = fragmentos,
        pistas = pistas,
        solucion = solucion,
        errores = errores,
        cuenta = cuenta,
    )
}
