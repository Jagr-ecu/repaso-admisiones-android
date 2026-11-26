package com.preparate.datos

import androidx.room.withTransaction
import com.preparate.datos.local.BaseDatos
import com.preparate.datos.local.IntentoFila
import com.preparate.datos.local.MemoriaFila
import com.preparate.datos.local.PuntosFila
import com.preparate.datos.local.SesionFila
import com.preparate.dominio.Calificacion
import com.preparate.dominio.ContextoTutor
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.EstadoTarjeta
import com.preparate.dominio.Estudio
import com.preparate.dominio.Intento
import com.preparate.dominio.Memoria
import com.preparate.dominio.Pista
import com.preparate.dominio.ProgresoMateria
import com.preparate.dominio.ReglaError
import com.preparate.dominio.Reglas
import com.preparate.dominio.Resultado
import com.preparate.dominio.Revision
import com.preparate.dominio.Seleccion
import com.preparate.dominio.Sesion
import com.preparate.dominio.SiguienteDelTema
import com.preparate.dominio.Tarjeta
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.Variante
import com.preparate.dominio.Verificador
import com.preparate.dominio.calcularProgreso
import com.preparate.dominio.esPractica
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Un ejercicio en pantalla: el intento guardado y la variante que se reconstruye con su semilla. */
data class Ejercicio(val sesion: Sesion, val intento: Intento, val variante: Variante, val numero: Int)

/** Cómo terminó una sesión. En las pruebas, `pendiente` si un Writing espera su nota. */
data class Cierre(
    val sesion: Sesion,
    val completada: Boolean,
    val nota: Double? = null,
    val aprobada: Boolean? = null,
    val puntos: Int = 0,
    val pendiente: Boolean = false,
)

/** Lo que muestra la barra de una sesión. */
data class EstadoSesion(
    val sesion: Sesion,
    /** Preguntas fijas de la prueba; en temas y repasos, las mostradas hasta ahora. */
    val total: Int,
    val respondidas: Int,
    /** Solo en temas. */
    val errores: Int?,
    /** Solo en el examen final. */
    val segundosRestantes: Long?,
)

/** Una pregunta de una prueba ya cerrada, con su solución. */
data class Pregunta(val numero: Int, val variante: Variante, val intento: Intento)

data class ResultadoPrueba(val cierre: Cierre, val preguntas: List<Pregunta>, val repasar: List<String>)

/**
 * El estudio de UN estudiante en el dispositivo: puerto de app/domain/estudio.py sobre Room.
 * Las reglas están en `:dominio` (Estudio, Reglas, Seleccion, Memoria); aquí solo se leen y
 * guardan los datos. Todo filtra por `usuarioId`.
 */
class Estudios(
    private val db: BaseDatos,
    private val contenido: LectorContenido,
    private val usuarioId: String,
    private val reloj: () -> Instant = Instant::now,
    private val azar: Random = Random.Default,
    private val nuevoId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.estudio()

    // --- Progreso --------------------------------------------------------------------

    fun cursos() = contenido.cursos

    fun curso(cursoId: String) = contenido.curso(cursoId)

    fun leccion(cursoId: String, leccionId: String) = contenido.leccion(cursoId, leccionId)

    suspend fun progreso(cursoId: String, materiaId: String): ProgresoMateria {
        val materia = contenido.curso(cursoId).materias.single { it.id == materiaId }
        return calcularProgreso(materia, Estudio.historial(sesionesDe(cursoId, materiaId)))
    }

    /** El camino de cada materia del curso; se recalcula cada vez que cambia una sesión. */
    fun observarProgreso(cursoId: String): Flow<List<ProgresoMateria>> =
        dao.observarSesiones(usuarioId, cursoId).map { filas ->
            val sesiones = filas.map { it.aDominio() }
            contenido.curso(cursoId).materias.map { m ->
                calcularProgreso(m, Estudio.historial(sesiones.filter { it.materiaId == m.id }))
            }
        }

    fun observarPuntos(cursoId: String): Flow<Int> = dao.observarPuntos(usuarioId, cursoId)

    /** Los conceptos de la materia que el repaso espaciado tiene vencidos ahora. */
    suspend fun conceptosVencidos(cursoId: String, materiaId: String): List<String> = try {
        Estudio.conceptosDelRepasoLibre(vencimientos(cursoId, materiaId), reloj())
    } catch (e: ReglaError) {
        emptyList()
    }

    // --- Empezar ---------------------------------------------------------------------

    /** Un tema. Si quedó uno a medias (p. ej. se cerró la app), lo retoma. */
    suspend fun empezarTema(cursoId: String, leccionId: String): Sesion {
        val leccion = contenido.leccion(cursoId, leccionId)
        abierta(cursoId, leccion.materia, TipoSesion.LECCION) { it.leccionId == leccionId }?.let { return it }
        Estudio.comprobarTema(progreso(cursoId, leccion.materia), leccionId)
        return crear(TipoSesion.LECCION, cursoId, leccion.materia, leccion.seccion, leccionId)
    }

    /** Volver a empezar un tema: el intento a medias se cierra sin puntos. */
    suspend fun reiniciarTema(cursoId: String, leccionId: String): Sesion {
        val leccion = contenido.leccion(cursoId, leccionId)
        db.withTransaction {
            for (s in dao.abiertas(usuarioId, cursoId, leccion.materia, TipoSesion.LECCION.valor)) {
                if (s.leccionId ==
                    leccionId
                ) {
                    dao.actualizarSesion(s.copy(fin = reloj().toEpochMilli(), aprobada = false, subida = false))
                }
            }
        }
        return empezarTema(cursoId, leccionId)
    }

    /** Con `seccionId`, el repaso obligatorio tras reprobar; sin ella, los conceptos vencidos. */
    suspend fun empezarRepaso(cursoId: String, materiaId: String, seccionId: String? = null): Sesion {
        abierta(cursoId, materiaId, TipoSesion.REPASO) { it.seccionId == seccionId }?.let { return it }
        val conceptos = if (seccionId != null) {
            val ultima = sesionesDe(cursoId, materiaId)
                .filter { it.tipo == TipoSesion.PRUEBA_SECCION && it.seccionId == seccionId && it.fin != null }
                .maxByOrNull { it.fin!! }
            val intentos = ultima?.let { intentosDe(it.id) }.orEmpty()
            Estudio.conceptosDelRepasoObligatorio(progreso(cursoId, materiaId), seccionId, intentos)
        } else {
            Estudio.conceptosDelRepasoLibre(vencimientos(cursoId, materiaId), reloj())
        }
        return crear(TipoSesion.REPASO, cursoId, materiaId, seccionId, conceptos = conceptos)
    }

    /** La prueba de una sección; si quedó una a medias, se retoma esa. */
    suspend fun empezarPrueba(cursoId: String, materiaId: String, seccionId: String): Sesion {
        abierta(cursoId, materiaId, TipoSesion.PRUEBA_SECCION) { it.seccionId == seccionId }?.let { return it }
        val conceptos = Estudio.comprobarPrueba(progreso(cursoId, materiaId), seccionId)
        return crearPrueba(
            TipoSesion.PRUEBA_SECCION,
            cursoId,
            materiaId,
            seccionId,
            conceptos,
            Reglas.PREGUNTAS_PRUEBA_SECCION,
        )
    }

    /** El examen final de 20 minutos; si quedó uno a medias y con tiempo, se retoma ese. */
    suspend fun empezarExamen(cursoId: String, materiaId: String): Sesion {
        cerrarVencidos(cursoId, materiaId)
        abierta(cursoId, materiaId, TipoSesion.EXAMEN_FINAL) { true }?.let { return it }
        val conceptos = Estudio.comprobarExamen(progreso(cursoId, materiaId))
        return crearPrueba(TipoSesion.EXAMEN_FINAL, cursoId, materiaId, null, conceptos, Reglas.PREGUNTAS_EXAMEN_FINAL)
    }

    /** Las sesiones a medias del estudiante en una materia, para ofrecer retomarlas. */
    suspend fun abiertas(cursoId: String, materiaId: String): List<Sesion> {
        cerrarVencidos(cursoId, materiaId)
        return sesionesDe(cursoId, materiaId).filter { it.fin == null }
    }

    // --- Ejercicios ------------------------------------------------------------------

    /** El ejercicio que toca, o null si la sesión ya no tiene más (o se acabó el tiempo). */
    suspend fun siguiente(sesionId: String): Ejercicio? {
        val sesion = sesionAbierta(sesionId)
        if (Estudio.vencida(sesion, reloj())) {
            cerrarPrueba(sesion, Estudio.limite(sesion)!!)
            return null
        }
        val intentos = intentosDe(sesionId)
        intentos.indexOfFirst { it.respondido == null }.takeIf { it >= 0 }?.let {
            return ejercicio(sesion, intentos[it], it + 1)
        }
        val nuevo: Intento = when (sesion.tipo) {
            TipoSesion.LECCION -> {
                val leccion = contenido.leccion(sesion.cursoId, sesion.leccionId!!)
                when (val s = Estudio.siguienteDelTema(leccion, intentos)) {
                    is SiguienteDelTema.Nuevo -> mostrar(sesion, s.plantillaId, intentos)
                    is SiguienteDelTema.Repetir -> Estudio.repeticion(s.fallado, nuevoId(), reloj())
                    SiguienteDelTema.Ninguno -> return null
                }
            }

            TipoSesion.REPASO -> {
                val materia = contenido.curso(sesion.cursoId).materias.single { it.id == sesion.materiaId }
                val plantilla = Estudio.siguienteDeRepaso(materia, sesion, intentos.size, azar) ?: return null
                mostrar(sesion, plantilla.id, intentos)
            }

            else -> return null // pruebas y exámenes tienen sus preguntas fijas desde el inicio
        }
        dao.insertarIntentos(listOf(nuevo.aFila(usuarioId, sesion, intentos.size)))
        return ejercicio(sesion, nuevo, intentos.size + 1)
    }

    /**
     * Califica con el código, nunca con la IA. Si no se pudo leer, no se guarda: el ejercicio sigue
     * pendiente sin gastar un error del tema ni la pregunta de la prueba.
     */
    suspend fun responder(intentoId: String, respuesta: String): Resultado = guardarRespuesta(intentoId) { v ->
        Verificador.verificar(v, respuesta) to respuesta
    }

    /** Las casillas del cuaderno o las palabras de `ordenar`. */
    suspend fun responder(intentoId: String, partes: List<String>): Resultado = guardarRespuesta(intentoId) { v ->
        Verificador.verificar(v, partes) to Estudio.textoGuardado(v, partes)
    }

    /** La siguiente pista o la solución. En pruebas y exámenes lanza `AyudaProhibida`. */
    suspend fun pista(intentoId: String): Pista? {
        val fila = dao.intento(usuarioId, intentoId) ?: error("no existe ese ejercicio")
        val sesion = sesionAbierta(fila.sesionId)
        val intento = fila.aDominio()
        val pista = Estudio.pista(sesion.tipo, intento, variante(sesion.cursoId, intento)) ?: return null
        dao.actualizarIntento(fila.copy(pistasUsadas = fila.pistasUsadas + 1, subida = false))
        return pista
    }

    suspend fun estado(sesionId: String): EstadoSesion {
        var sesion = sesion(sesionId)
        if (sesion.fin == null && Estudio.vencida(sesion, reloj())) {
            cerrarPrueba(sesion, Estudio.limite(sesion)!!)
            sesion = sesion(sesionId)
        }
        val intentos = intentosDe(sesionId)
        return EstadoSesion(
            sesion = sesion,
            total = intentos.size,
            respondidas = intentos.count { it.respondido != null },
            errores = if (sesion.tipo == TipoSesion.LECCION) Estudio.fallos(intentos).size else null,
            segundosRestantes = Estudio.segundosRestantes(sesion, reloj()),
        )
    }

    // --- Cerrar ----------------------------------------------------------------------

    /** Cierra la sesión y la califica. Si ya estaba cerrada, devuelve su resultado. */
    suspend fun cerrar(sesionId: String): Cierre {
        val sesion = sesion(sesionId)
        if (sesion.fin != null) return cierreGuardado(sesion)
        val ahora = reloj()
        if (!esPractica(sesion.tipo)) return cerrarPrueba(sesion, Estudio.horaDeCierre(sesion, ahora))

        val intentos = intentosDe(sesionId)
        return db.withTransaction {
            var puntos = 0
            val completada = if (sesion.tipo == TipoSesion.LECCION) {
                val leccionId = checkNotNull(sesion.leccionId)
                val completo = Estudio.temaCompleto(contenido.leccion(sesion.cursoId, leccionId), intentos)
                if (completo) puntos = otorgar(sesion.cursoId, "leccion", leccionId, Reglas.PUNTOS_LECCION, ahora)
                completo
            } else {
                Estudio.repasoCompleto(intentos)
            }
            val cerrada = sesion.copy(fin = ahora, aprobada = completada)
            dao.actualizarSesion(cerrada.aFila(usuarioId))
            Cierre(cerrada, completada, puntos = puntos)
        }
    }

    /** La prueba ya cerrada, pregunta por pregunta, con la solución y los temas para repasar. */
    suspend fun resultado(sesionId: String): ResultadoPrueba {
        val sesion = sesion(sesionId)
        check(!esPractica(sesion.tipo)) { "solo las pruebas y los exámenes tienen resultados" }
        check(sesion.fin != null) { "primero termina la prueba" }
        val intentos = intentosDe(sesionId)
        val cierre = cierreGuardado(sesion)
        val repasar = if (sesion.tipo == TipoSesion.PRUEBA_SECCION && cierre.aprobada == false) {
            Estudio.temasParaRepasar(intentos)
        } else {
            emptyList()
        }
        val preguntas = intentos.mapIndexed { n, i -> Pregunta(n + 1, variante(sesion.cursoId, i), i) }
        return ResultadoPrueba(cierre, preguntas, repasar)
    }

    // --- Kiwi ---------------------------------------------------------------------------

    /** Lo que Kiwi necesita de un ejercicio: el ejercicio, lo que respondió y el material del concepto. */
    suspend fun contextoTutor(intentoId: String): ContextoTutor {
        val fila = dao.intento(usuarioId, intentoId) ?: error("no existe ese ejercicio")
        val sesion = sesion(fila.sesionId)
        val intento = fila.aDominio()
        val materia = contenido.curso(sesion.cursoId).materias.single { it.id == sesion.materiaId }
        val lecciones = materia.secciones.flatMap { it.lecciones }.filter { it.concepto == intento.concepto }
        return ContextoTutor(
            tipoSesion = sesion.tipo,
            materia = materia.nombre,
            concepto = lecciones.firstOrNull()?.titulo ?: intento.concepto,
            material = ContextoTutor.material(lecciones.map { contenido.leccion(sesion.cursoId, it.id) }),
            variante = variante(sesion.cursoId, intento),
            intento = intento,
        )
    }

    /** Los Writing que esperan su nota, de cualquier sesión: también de pruebas y exámenes ya cerrados. */
    suspend fun writingPendientes(): List<String> = dao.writingPendientes(usuarioId)

    fun observarWritingPendientes(): Flow<Int> = dao.observarWritingPendientes(usuarioId)

    /**
     * Guarda la nota que Kiwi puso con la rúbrica (la calcula `Escritura`, no la IA). Si la prueba ya
     * estaba cerrada, se vuelve a calificar: esperaba esta nota. El repaso espaciado se recalcula
     * desde los intentos, así queda igual que en cualquier otro dispositivo.
     */
    suspend fun registrarEscritura(intentoId: String, revision: Revision): Intento = db.withTransaction {
        val fila = dao.intento(usuarioId, intentoId) ?: error("no existe ese ejercicio")
        val sesion = sesion(fila.sesionId)
        val calificado = Estudio.registrarEscritura(fila.aDominio(), revision.puntaje).copy(revision = revision.aJson())
        dao.actualizarIntento(calificado.aFila(usuarioId, sesion, fila.orden))
        if (sesion.fin != null && !esPractica(sesion.tipo)) {
            val nota = Estudio.calificarPrueba(intentosDe(sesion.id))
            dao.actualizarSesion(sesion.copy(nota = nota.nota, aprobada = nota.aprobada).aFila(usuarioId))
        }
        AlmacenSincro(db, usuarioId).reconstruirMemoria(sesion.cursoId)
        calificado
    }

    // --- Solo depuración ---------------------------------------------------------------

    /**
     * SOLO para probar en el APK de depuración (vista previa): da por hechos los temas de una sección
     * y, con `aprobar`, también su prueba. La app real nunca lo llama.
     */
    suspend fun depuracionCompletar(cursoId: String, materiaId: String, seccionId: String, aprobar: Boolean) {
        val materia = contenido.curso(cursoId).materias.single { it.id == materiaId }
        val seccion = materia.secciones.single { it.id == seccionId }
        val ahora = reloj()
        db.withTransaction {
            for (l in seccion.lecciones) {
                val tema = Sesion(
                    nuevoId(), TipoSesion.LECCION, cursoId, materiaId, seccionId, l.id,
                    inicio = ahora, fin = ahora, aprobada = true,
                )
                dao.insertarSesion(tema.aFila(usuarioId))
            }
            if (aprobar) {
                val prueba = Sesion(
                    nuevoId(), TipoSesion.PRUEBA_SECCION, cursoId, materiaId, seccionId,
                    inicio = ahora, fin = ahora, nota = 10.0, aprobada = true,
                )
                dao.insertarSesion(prueba.aFila(usuarioId))
            }
        }
    }

    // --- Internos --------------------------------------------------------------------

    private suspend fun sesionesDe(cursoId: String, materiaId: String) =
        dao.sesionesDeMateria(usuarioId, cursoId, materiaId).map { it.aDominio() }

    private suspend fun sesion(id: String): Sesion =
        dao.sesion(usuarioId, id)?.aDominio() ?: error("no existe esa sesión")

    private suspend fun sesionAbierta(id: String): Sesion =
        sesion(id).also { check(it.fin == null) { "esta sesión ya terminó" } }

    private suspend fun intentosDe(sesionId: String) = dao.intentosDeSesion(usuarioId, sesionId).map { it.aDominio() }

    private suspend fun abierta(
        cursoId: String,
        materiaId: String,
        tipo: TipoSesion,
        cual: (SesionFila) -> Boolean,
    ): Sesion? = dao.abiertas(usuarioId, cursoId, materiaId, tipo.valor).firstOrNull(cual)?.aDominio()

    private suspend fun vencimientos(cursoId: String, materiaId: String): Map<String, Instant> =
        dao.vencimientos(usuarioId, cursoId, materiaId).associate { it.concepto to Instant.ofEpochMilli(it.vence) }

    private suspend fun crear(
        tipo: TipoSesion,
        cursoId: String,
        materiaId: String,
        seccionId: String?,
        leccionId: String? = null,
        conceptos: List<String> = emptyList(),
    ): Sesion {
        val sesion = Sesion(nuevoId(), tipo, cursoId, materiaId, seccionId, leccionId, conceptos, reloj())
        dao.insertarSesion(sesion.aFila(usuarioId))
        return sesion
    }

    private suspend fun crearPrueba(
        tipo: TipoSesion,
        cursoId: String,
        materiaId: String,
        seccionId: String?,
        conceptos: List<String>,
        cantidad: Int,
    ): Sesion {
        val materia = contenido.curso(cursoId).materias.single { it.id == materiaId }
        val vistas = mutableMapOf<String, Set<String>>()
        for (p in materia.secciones.flatMap { it.plantillas }) {
            vistas[p.id] =
                dao.huellas(usuarioId, cursoId, p.id).toSet()
        }
        val variantes = Estudio.preguntasDePrueba(
            materia,
            conceptos,
            cantidad,
            azar,
            { contenido.variantes(cursoId, it) },
            { vistas[it].orEmpty() },
        )
        val sesion = Sesion(nuevoId(), tipo, cursoId, materiaId, seccionId, inicio = reloj())
        db.withTransaction {
            dao.insertarSesion(sesion.aFila(usuarioId))
            dao.insertarIntentos(variantes.mapIndexed { n, v -> intentoDe(sesion, v).aFila(usuarioId, sesion, n) })
        }
        return sesion
    }

    /** Una variante nueva (nunca vista por el estudiante) de la plantilla. */
    private suspend fun mostrar(sesion: Sesion, plantillaId: String, intentosDeLaSesion: List<Intento>): Intento {
        val variantes = contenido.variantes(sesion.cursoId, plantillaId)
        val mostradas = intentosDeLaSesion.filter { it.plantillaId == plantillaId }.map { variante(sesion.cursoId, it) }
        val usadas = Estudio.respuestasDeLaSesion(variantes.first().plantilla, mostradas)
        val vistas = dao.huellas(usuarioId, sesion.cursoId, plantillaId).toSet()
        return intentoDe(sesion, Seleccion.varianteNueva(variantes, vistas, azar, respuestasUsadas = usadas))
    }

    private fun intentoDe(sesion: Sesion, v: Variante) = Intento(
        id = nuevoId(),
        sesionId = sesion.id,
        plantillaId = v.plantilla.id,
        concepto = v.plantilla.concepto,
        plantillaVersion = v.plantilla.version,
        semilla = v.semilla,
        huella = v.huella,
        mostrado = reloj(),
    )

    /** Reconstruye la variante con su semilla, desde el paquete de contenido. */
    private fun variante(cursoId: String, intento: Intento): Variante =
        contenido.variantes(cursoId, intento.plantillaId).firstOrNull { it.semilla == intento.semilla }
            ?: error("la variante ${intento.plantillaId}#${intento.semilla} ya no está en el paquete")

    private fun ejercicio(sesion: Sesion, intento: Intento, numero: Int) =
        Ejercicio(sesion, intento, variante(sesion.cursoId, intento), numero)

    private suspend fun guardarRespuesta(
        intentoId: String,
        calificar: (Variante) -> Pair<Resultado, String>,
    ): Resultado {
        val fila = dao.intento(usuarioId, intentoId) ?: error("no existe ese ejercicio")
        val sesion = sesionAbierta(fila.sesionId)
        val ahora = reloj()
        if (Estudio.vencida(sesion, ahora)) {
            cerrarPrueba(sesion, Estudio.limite(sesion)!!)
            throw TiempoAgotado()
        }
        val intento = fila.aDominio()
        val v = variante(sesion.cursoId, intento)
        val (resultado, texto) = calificar(v)
        if (!Estudio.cuenta(resultado)) return resultado
        val respondido = Estudio.responder(intento, v, texto, resultado, ahora)
        db.withTransaction {
            dao.actualizarIntento(respondido.aFila(usuarioId, sesion, fila.orden))
            respondido.correcta?.let {
                actualizarMemoria(sesion, intento.concepto, Reglas.calificacionMemoria(it, intento.pistasUsadas), ahora)
            }
        }
        return resultado
    }

    private suspend fun actualizarMemoria(
        sesion: Sesion,
        concepto: String,
        calificacion: Calificacion,
        ahora: Instant,
    ) {
        val antes = dao.memoria(usuarioId, sesion.cursoId, concepto)?.let {
            Tarjeta(
                EstadoTarjeta.desde(it.estado),
                it.paso,
                it.estabilidad,
                it.dificultad,
                Instant.ofEpochMilli(it.vence),
                it.ultimoRepaso?.let(Instant::ofEpochMilli),
            )
        }
        val t = Memoria.repasar(antes, calificacion, ahora)
        dao.guardarMemoria(
            MemoriaFila(
                usuarioId, sesion.cursoId, sesion.materiaId, concepto,
                t.estado.valor, t.paso, t.estabilidad, t.dificultad,
                t.vence.toEpochMilli(), t.ultimoRepaso?.toEpochMilli(),
            ),
        )
    }

    private suspend fun otorgar(
        cursoId: String,
        motivo: String,
        referencia: String,
        cantidad: Int,
        ahora: Instant,
    ): Int = if (dao.otorgar(PuntosFila(usuarioId, cursoId, motivo, referencia, cantidad, ahora.toEpochMilli())) ==
        -1L
    ) {
        0
    } else {
        cantidad
    }

    /** Cierra los exámenes cuyo tiempo se acabó aunque nadie los cerrara (p. ej., se cerró la app). */
    private suspend fun cerrarVencidos(cursoId: String, materiaId: String) {
        for (fila in dao.abiertas(usuarioId, cursoId, materiaId, TipoSesion.EXAMEN_FINAL.valor)) {
            val s = fila.aDominio()
            if (Estudio.vencida(s, reloj())) cerrarPrueba(s, Estudio.limite(s)!!)
        }
    }

    private suspend fun cerrarPrueba(sesion: Sesion, fin: Instant): Cierre {
        val nota = Estudio.calificarPrueba(intentosDe(sesion.id))
        val cerrada = sesion.copy(fin = fin, nota = nota.nota, aprobada = nota.aprobada)
        dao.actualizarSesion(cerrada.aFila(usuarioId))
        return cierreGuardado(cerrada)
    }

    private fun cierreGuardado(sesion: Sesion): Cierre {
        val aprobada = sesion.aprobada
        return when {
            esPractica(sesion.tipo) -> Cierre(sesion, aprobada == true)
            aprobada == null -> Cierre(sesion, false, pendiente = true)
            else -> Cierre(sesion, aprobada, sesion.nota, aprobada)
        }
    }
}

/** El examen se acabó antes de que llegara la respuesta. */
class TiempoAgotado : Exception("se acabó el tiempo del examen")

// --- Conversión entre filas de Room y el dominio ---------------------------------------

internal fun SesionFila.aDominio() = Sesion(
    id = id,
    tipo = TipoSesion.entries.first { it.valor == tipo },
    cursoId = cursoId,
    materiaId = materiaId,
    seccionId = seccionId,
    leccionId = leccionId,
    conceptos = conceptos.split("\n").filter { it.isNotEmpty() },
    inicio = Instant.ofEpochMilli(inicio),
    fin = fin?.let(Instant::ofEpochMilli),
    nota = nota,
    aprobada = aprobada,
)

internal fun Sesion.aFila(usuarioId: String) = SesionFila(
    id = id,
    usuarioId = usuarioId,
    cursoId = cursoId,
    materiaId = materiaId,
    tipo = tipo.valor,
    seccionId = seccionId,
    leccionId = leccionId,
    conceptos = conceptos.joinToString("\n"),
    inicio = inicio.toEpochMilli(),
    fin = fin?.toEpochMilli(),
    nota = nota,
    aprobada = aprobada,
)

internal fun IntentoFila.aDominio() = Intento(
    id = id,
    sesionId = sesionId,
    plantillaId = plantillaId,
    concepto = concepto,
    plantillaVersion = plantillaVersion,
    semilla = semilla,
    huella = huella,
    mostrado = Instant.ofEpochMilli(mostrado),
    repite = repite,
    respondido = respondido?.let(Instant::ofEpochMilli),
    respuesta = respuesta,
    estado = estado?.let { e -> EstadoRespuesta.entries.first { it.valor == e } },
    correcta = correcta,
    errorComun = errorComun,
    pistasUsadas = pistasUsadas,
    pistasAlResponder = pistasAlResponder,
    puntaje = puntaje,
    revision = revision,
)

internal fun Intento.aFila(usuarioId: String, sesion: Sesion, orden: Int) = IntentoFila(
    id = id,
    usuarioId = usuarioId,
    cursoId = sesion.cursoId,
    materiaId = sesion.materiaId,
    sesionId = sesion.id,
    orden = orden,
    plantillaId = plantillaId,
    concepto = concepto,
    plantillaVersion = plantillaVersion,
    semilla = semilla,
    huella = huella,
    mostrado = mostrado.toEpochMilli(),
    repite = repite,
    respondido = respondido?.toEpochMilli(),
    respuesta = respuesta,
    estado = estado?.valor,
    correcta = correcta,
    errorComun = errorComun,
    pistasUsadas = pistasUsadas,
    pistasAlResponder = pistasAlResponder,
    puntaje = puntaje,
    revision = revision,
)
