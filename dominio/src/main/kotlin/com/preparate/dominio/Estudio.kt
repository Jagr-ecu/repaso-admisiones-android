package com.preparate.dominio

import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/*
 * Reglas de las sesiones de estudio: tema, repaso, prueba de sección y examen final.
 * Puerto de la lógica de app/domain/estudio.py, separada del acceso a la base: aquí todo son
 * funciones sobre listas de intentos. Room (fase 4) guarda los datos y llama a estas funciones.
 */

/** Una sesión de estudio de un estudiante. Los ids son UUID para que no choquen entre dispositivos. */
data class Sesion(
    val id: String,
    val tipo: TipoSesion,
    val cursoId: String,
    val materiaId: String,
    val seccionId: String? = null,
    val leccionId: String? = null,
    /** Repaso: los conceptos que repasa, por turnos. */
    val conceptos: List<String> = emptyList(),
    val inicio: Instant,
    val fin: Instant? = null,
    val nota: Double? = null,
    /** Práctica: si se completó. Prueba: si aprobó; null mientras espera la nota de un Writing. */
    val aprobada: Boolean? = null,
)

/** Un ejercicio mostrado. Guarda la semilla y la versión de la plantilla para reconstruir la variante. */
data class Intento(
    val id: String,
    val sesionId: String,
    val plantillaId: String,
    val concepto: String,
    val plantillaVersion: String,
    val semilla: Int,
    val huella: String,
    val mostrado: Instant,
    /** Es la repetición, al final del tema, del intento fallado con este id. */
    val repite: String? = null,
    val respondido: Instant? = null,
    val respuesta: String? = null,
    val estado: EstadoRespuesta? = null,
    /** null sin responder, o en un Writing que espera su nota. */
    val correcta: Boolean? = null,
    val errorComun: String? = null,
    val pistasUsadas: Int = 0,
    /**
     * Pistas que llevaba al responder. Tras responder se pueden pedir más (la solución), pero el
     * repaso espaciado cuenta solo estas; así `memoria` se reconstruye igual en otro dispositivo.
     */
    val pistasAlResponder: Int? = null,
    /** Nota del Writing que puso Kiwi con la rúbrica. */
    val puntaje: Double? = null,
    /** El detalle de esa nota (`Revision` en JSON): criterios y comentario. */
    val revision: String? = null,
)

/** Qué sigue en un tema. */
sealed interface SiguienteDelTema {
    data class Nuevo(val plantillaId: String) : SiguienteDelTema

    /** Volver a mostrar la misma variante (misma semilla) de un ejercicio fallado. */
    data class Repetir(val fallado: Intento) : SiguienteDelTema

    data object Ninguno : SiguienteDelTema
}

/** La nota de una prueba cerrada. Ambos null si un Writing aún espera su nota. */
data class NotaPrueba(val nota: Double?, val aprobada: Boolean?) {
    val pendiente: Boolean get() = nota == null
}

/** 1–3: pistas del contenido. 4: la solución, solo tras responder. */
data class Pista(val nivel: Int, val texto: String)

/** Por qué no se puede empezar algo. La interfaz muestra el texto de `strings.xml` de cada motivo. */
enum class Bloqueo {
    TEMA_BLOQUEADO,
    PRUEBA_BLOQUEADA,
    PRUEBA_PENDIENTE,
    SECCION_APROBADA,
    REPASO_REQUERIDO,
    SIN_REPASO_PENDIENTE,
    EXAMEN_BLOQUEADO,
    EXAMEN_PENDIENTE,
    NADA_PARA_REPASAR,
    SIN_EJERCICIOS,
}

class ReglaError(val motivo: Bloqueo) : Exception(motivo.name)

/** En pruebas y exámenes no hay pistas ni Kiwi (regla 3). */
class AyudaProhibida : Exception("en las pruebas y exámenes no hay ayuda")

object Estudio {
    /** Conceptos que repasa el repaso libre: los vencidos, como máximo estos. */
    const val CONCEPTOS_REPASO_LIBRE = 4

    // --- Historial ------------------------------------------------------------------

    /** Lo que ya hizo el estudiante en una materia, a partir de sus sesiones cerradas. */
    fun historial(sesiones: List<Sesion>): Historial {
        val cerradas = sesiones.filter { it.fin != null }
        return Historial(
            leccionesCompletadas = cerradas
                .filter { it.tipo == TipoSesion.LECCION && it.aprobada == true }
                .mapNotNull { it.leccionId }.toSet(),
            pruebas = cerradas.filter { it.tipo == TipoSesion.PRUEBA_SECCION && it.seccionId != null }
                .map { ResultadoPrueba(it.seccionId!!, it.aprobada, it.fin!!) },
            repasos = cerradas.filter { it.tipo == TipoSesion.REPASO && it.aprobada == true && it.seccionId != null }
                .map { RepasoHecho(it.seccionId!!, it.fin!!) },
            examenAprobado = cerradas.any { it.tipo == TipoSesion.EXAMEN_FINAL && it.aprobada == true },
            examenPendiente = cerradas.any { it.tipo == TipoSesion.EXAMEN_FINAL && it.aprobada == null },
        )
    }

    // --- Empezar --------------------------------------------------------------------

    fun comprobarTema(progreso: ProgresoMateria, leccionId: String) {
        val tema = progreso.leccion(leccionId)
        if (tema == null || tema.estado == EstadoNodo.BLOQUEADO) throw ReglaError(Bloqueo.TEMA_BLOQUEADO)
    }

    /** Los conceptos de la prueba de la sección, en el orden de sus temas. */
    fun comprobarPrueba(progreso: ProgresoMateria, seccionId: String): List<String> {
        val seccion = progreso.seccion(seccionId) ?: throw ReglaError(Bloqueo.PRUEBA_BLOQUEADA)
        when (seccion.prueba) {
            EstadoPrueba.BLOQUEADA -> throw ReglaError(Bloqueo.PRUEBA_BLOQUEADA)
            EstadoPrueba.PENDIENTE -> throw ReglaError(Bloqueo.PRUEBA_PENDIENTE)
            EstadoPrueba.APROBADA -> throw ReglaError(Bloqueo.SECCION_APROBADA)
            EstadoPrueba.REPASO_REQUERIDO -> throw ReglaError(Bloqueo.REPASO_REQUERIDO)
            EstadoPrueba.DISPONIBLE -> {}
        }
        return seccion.lecciones.map { it.leccion.concepto }.distinct()
    }

    /** Los conceptos del examen final: todos los de la materia. */
    fun comprobarExamen(progreso: ProgresoMateria): List<String> {
        when (progreso.examenFinal) {
            EstadoNodo.BLOQUEADO -> throw ReglaError(Bloqueo.EXAMEN_BLOQUEADO)
            EstadoNodo.PENDIENTE -> throw ReglaError(Bloqueo.EXAMEN_PENDIENTE)
            else -> {}
        }
        return progreso.secciones.flatMap { s -> s.lecciones.map { it.leccion.concepto } }.distinct()
    }

    /** El repaso obligatorio tras reprobar: los temas que más falló en la última prueba de la sección. */
    fun conceptosDelRepasoObligatorio(
        progreso: ProgresoMateria,
        seccionId: String,
        ultimaPrueba: List<Intento>,
    ): List<String> {
        if (progreso.seccion(seccionId)?.prueba != EstadoPrueba.REPASO_REQUERIDO) {
            throw ReglaError(Bloqueo.SIN_REPASO_PENDIENTE)
        }
        return temasParaRepasar(ultimaPrueba).ifEmpty { throw ReglaError(Bloqueo.NADA_PARA_REPASAR) }
    }

    /** El repaso libre: los conceptos de la materia que el repaso espaciado tiene vencidos. */
    fun conceptosDelRepasoLibre(proximas: Map<String, Instant>, ahora: Instant): List<String> =
        Seleccion.conceptosVencidos(proximas, ahora, CONCEPTOS_REPASO_LIBRE)
            .ifEmpty { throw ReglaError(Bloqueo.NADA_PARA_REPASAR) }

    /**
     * Las preguntas fijas de una prueba o examen, elegidas al empezar. Sin variantes ya vistas por el
     * estudiante, sin repetir una lectura y, con opciones fijas, sin repetir la respuesta.
     */
    fun preguntasDePrueba(
        materia: MateriaCurso,
        conceptos: List<String>,
        cantidad: Int,
        azar: Random,
        variantes: (String) -> List<Variante>,
        vistas: (String) -> Set<String>,
    ): List<Variante> {
        val plantillas = Seleccion.plantillasIntercaladas(materia, conceptos, cantidad, azar)
        if (plantillas.isEmpty()) throw ReglaError(Bloqueo.SIN_EJERCICIOS)
        val lecturas = mutableSetOf<String>()
        val elegidas = mutableListOf<Variante>()
        for (resumen in plantillas) {
            val lista = variantes(resumen.id)
            val usadas = respuestasDeLaSesion(lista.first().plantilla, elegidas)
            val v = Seleccion.varianteNueva(lista, vistas(resumen.id), azar, lecturas, usadas)
            Seleccion.lecturaDe(v).takeIf { it.isNotEmpty() }?.let(lecturas::add)
            elegidas += v
        }
        return elegidas
    }

    /**
     * Con 3 o más opciones fijas, tres veces «because» seguidas se acertarían sin pensar: se usan
     * todas las respuestas antes de repetir. Con solo dos no, porque se volvería una alternancia.
     */
    fun respuestasDeLaSesion(plantilla: Plantilla, mostradasEnLaSesion: List<Variante>): Set<String> {
        if (plantilla.opcionesFijas.size < 3) return emptySet()
        val usadas = mostradasEnLaSesion.filter { it.plantilla.id == plantilla.id }.mapNotNull { it.respuesta }.toSet()
        return if (usadas.size < plantilla.opcionesFijas.size) usadas else emptySet()
    }

    // --- Tema -----------------------------------------------------------------------

    /**
     * Los errores del tema. Solo los que califica el código: un Writing con nota baja de Kiwi no es
     * un error del tema, porque la rúbrica es retroalimentación y no debe cortarlo ni repetirse.
     */
    fun fallos(intentos: List<Intento>): List<Intento> = intentos.filter { it.correcta == false && it.puntaje == null }

    fun temaPerdido(intentos: List<Intento>): Boolean = Reglas.temaPerdido(fallos(intentos).size)

    /**
     * Primero los ejercicios del tema en orden; al final, otra vez y tal cual, los que falló.
     * Si la repetición también falla, se vuelve a poner al final. Con el 4.º error no sigue nada.
     */
    fun siguienteDelTema(leccion: Leccion, intentos: List<Intento>): SiguienteDelTema {
        if (temaPerdido(intentos)) return SiguienteDelTema.Ninguno
        for (ref in leccion.ejercicios) {
            val mostrados = intentos.count { it.plantillaId == ref.plantilla && it.repite == null }
            if (mostrados < ref.cantidad) return SiguienteDelTema.Nuevo(ref.plantilla)
        }
        val repetidos = intentos.mapNotNull { it.repite }.toSet()
        return fallos(intentos).firstOrNull { it.id !in repetidos }?.let { SiguienteDelTema.Repetir(it) }
            ?: SiguienteDelTema.Ninguno
    }

    /** La repetición de un intento fallado: misma plantilla, misma semilla, misma huella. */
    fun repeticion(fallado: Intento, id: String, ahora: Instant): Intento = Intento(
        id = id,
        sesionId = fallado.sesionId,
        plantillaId = fallado.plantillaId,
        concepto = fallado.concepto,
        plantillaVersion = fallado.plantillaVersion,
        semilla = fallado.semilla,
        huella = fallado.huella,
        mostrado = ahora,
        repite = fallado.id,
    )

    /** Todo respondido y cada fallo corregido al repetirlo, sin pasarse de los errores permitidos. */
    fun temaCompleto(leccion: Leccion, intentos: List<Intento>): Boolean = !temaPerdido(intentos) &&
        intentos.all { it.respondido != null } &&
        siguienteDelTema(leccion, intentos) == SiguienteDelTema.Ninguno

    /** Puntos al cerrar un tema: solo si se completó y la primera vez. Nunca por velocidad. */
    fun puntosDelTema(completo: Boolean, yaTeniaPuntos: Boolean): Int =
        if (completo && !yaTeniaPuntos) Reglas.PUNTOS_LECCION else 0

    // --- Repaso ---------------------------------------------------------------------

    /** Una plantilla por ejercicio, con un concepto distinto en cada turno. null al llegar a 5. */
    fun siguienteDeRepaso(materia: MateriaCurso, sesion: Sesion, hechos: Int, azar: Random): PlantillaResumen? {
        if (hechos >= Reglas.EJERCICIOS_REPASO || sesion.conceptos.isEmpty()) return null
        val concepto = sesion.conceptos[hechos % sesion.conceptos.size]
        return Seleccion.plantillasIntercaladas(materia, listOf(concepto), 1, azar).firstOrNull()
    }

    fun repasoCompleto(intentos: List<Intento>): Boolean =
        intentos.count { it.respondido != null } >= Reglas.EJERCICIOS_REPASO

    // --- Pruebas y examen -----------------------------------------------------------

    /** Hora en que se acaba el tiempo; solo el examen final tiene límite. */
    fun limite(sesion: Sesion): Instant? = if (sesion.tipo ==
        TipoSesion.EXAMEN_FINAL
    ) {
        sesion.inicio.plus(Duration.ofMinutes(Reglas.MINUTOS_EXAMEN_FINAL.toLong()))
    } else {
        null
    }

    /** Se acabó el tiempo, con unos segundos de margen para una respuesta enviada justo al final. */
    fun vencida(sesion: Sesion, ahora: Instant): Boolean {
        val limite = limite(sesion) ?: return false
        return ahora > limite.plusSeconds(Reglas.SEGUNDOS_DE_GRACIA.toLong())
    }

    fun segundosRestantes(sesion: Sesion, ahora: Instant): Long? {
        val limite = limite(sesion) ?: return null
        if (sesion.fin != null) return 0
        return maxOf(0L, Duration.between(ahora, limite).seconds)
    }

    /** Un examen vencido se cierra a su hora límite, no cuando alguien vuelve a abrir la app. */
    fun horaDeCierre(sesion: Sesion, ahora: Instant): Instant = limite(sesion)?.let { minOf(it, ahora) } ?: ahora

    /** Nota sobre 10. Lo no respondido cuenta como incorrecto; un Writing sin nota deja todo pendiente. */
    fun calificarPrueba(intentos: List<Intento>): NotaPrueba {
        if (intentos.any { it.estado == EstadoRespuesta.PENDIENTE }) return NotaPrueba(null, null)
        val nota = Reglas.nota(intentos.count { it.correcta == true }, intentos.size)
        return NotaPrueba(nota, Reglas.aprobada(nota))
    }

    /** Los conceptos que más falló en una prueba, para el repaso tras reprobar. */
    fun temasParaRepasar(intentos: List<Intento>): List<String> =
        Reglas.temasConMasErrores(intentos.map { it.concepto to (it.correcta == true) })

    // --- Responder y pedir ayuda ----------------------------------------------------

    /**
     * Si no se pudo leer (p. ej. "cinco" donde va un número), no cuenta: el ejercicio sigue
     * pendiente, sin gastar un error del tema ni la pregunta de la prueba.
     */
    fun cuenta(resultado: Resultado): Boolean = resultado.estado != EstadoRespuesta.FORMATO_INVALIDO

    /** El intento ya respondido. En un Writing, `correcta` queda null hasta que Kiwi lo califique. */
    fun responder(
        intento: Intento,
        variante: Variante,
        respuesta: String,
        resultado: Resultado,
        ahora: Instant,
    ): Intento {
        check(intento.respondido == null) { "este ejercicio ya fue respondido" }
        check(cuenta(resultado)) { "una respuesta ilegible no se guarda" }
        return intento.copy(
            respondido = ahora,
            respuesta = respuesta,
            estado = resultado.estado,
            correcta = if (resultado.estado == EstadoRespuesta.PENDIENTE) null else resultado.correcta,
            errorComun = resultado.errorComun,
            pistasAlResponder = intento.pistasUsadas,
        )
    }

    /**
     * La nota de Kiwi para un Writing, sobre 10 según la rúbrica. Con 7 o más cuenta como correcto.
     * Si la prueba ya estaba cerrada, quien llama la vuelve a calificar con `calificarPrueba`.
     */
    fun registrarEscritura(intento: Intento, puntaje: Double): Intento {
        check(intento.estado == EstadoRespuesta.PENDIENTE) { "este texto ya fue calificado" }
        val correcta = Reglas.aprobada(puntaje)
        return intento.copy(
            puntaje = puntaje,
            estado = if (correcta) EstadoRespuesta.CORRECTA else EstadoRespuesta.INCORRECTA,
            correcta = correcta,
        )
    }

    /** La cuenta se guarda como se lee en el cuaderno («68 340 408»): así la entienden Kiwi y el panel. */
    fun textoGuardado(variante: Variante, casillas: List<String>): String =
        variante.cuenta?.aTexto(casillas) ?: casillas.joinToString(" ")

    /**
     * En un tema lo fallado vuelve tal cual al final: si la solución saliera antes, en la repetición
     * solo recordaría el número. Por eso en un tema se muestra recién en la repetición.
     */
    fun solucionPermitida(tipo: TipoSesion, repite: Boolean): Boolean = tipo != TipoSesion.LECCION || repite

    /** Al acertar lo que había fallado se explica por qué: en la repetición pudo acertar por descarte. */
    fun solucionAlAcertarRepeticion(intento: Intento, variante: Variante): String? =
        if (intento.repite != null && intento.correcta == true) variante.solucion?.ifEmpty { null } else null

    /**
     * La siguiente pista. Ilimitadas y sin costo en práctica (quien llama suma 1 a `pistasUsadas`);
     * prohibidas en pruebas y exámenes. Tras las tres pistas y ya respondido, la solución.
     */
    fun pista(tipo: TipoSesion, intento: Intento, variante: Variante): Pista? {
        if (!ayudaPermitida(tipo)) throw AyudaProhibida()
        val pistas = variante.pistas
        if (pistas.isEmpty()) return null
        val usadas = intento.pistasUsadas
        val solucion = variante.solucion
        if (usadas >= pistas.size && intento.respondido != null && !solucion.isNullOrEmpty() &&
            solucionPermitida(tipo, intento.repite != null)
        ) {
            return Pista(pistas.size + 1, solucion)
        }
        val nivel = minOf(usadas + 1, pistas.size)
        return Pista(nivel, pistas[nivel - 1])
    }
}
