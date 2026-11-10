package com.preparate.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.random.Random

/** El paquete que va en el APK, leído desde el disco. */
object PaqueteDePrueba {
    private val carpeta = File("../app/src/main/assets/contenido")
    private fun leer(ruta: String) = File(carpeta, ruta).readText(Charsets.UTF_8)

    val curso: Curso by lazy { Paquete.cursos(leer("cursos.json")).single { it.id == "admisiones" } }
    private val plantillas = mutableMapOf<String, PlantillaConVariantes>()

    fun materia(id: String) = curso.materias.single { it.id == id }
    fun leccion(id: String) = Paquete.leccion(leer("admisiones/lecciones/$id.json"))
    fun variantes(id: String) = plantillas.getOrPut(id) { Paquete.plantilla(leer("admisiones/variantes/$id.json")) }.variantes
}

/** Puerto de los tests de reglas, pruebas y cuaderno de la web, sobre la lógica pura. */
class EstudioTest {
    private val ahora = Instant.parse("2026-03-01T15:00:00Z")
    private val leccion = PaqueteDePrueba.leccion("mat.fracciones.suma-igual-denominador")
    private var siguienteId = 0

    /** Un tema simulado: muestra lo que toca y responde según `aciertos` (true = bien). */
    private fun jugarTema(vararg aciertos: Boolean): List<Intento> {
        val intentos = mutableListOf<Intento>()
        for (bien in aciertos) {
            val intento = when (val s = Estudio.siguienteDelTema(leccion, intentos)) {
                is SiguienteDelTema.Nuevo -> nuevo(s.plantillaId)
                is SiguienteDelTema.Repetir -> Estudio.repeticion(s.fallado, "i${siguienteId++}", ahora)
                SiguienteDelTema.Ninguno -> error("el tema ya no tiene ejercicios")
            }
            intentos += responder(intento, bien)
        }
        return intentos
    }

    private fun nuevo(plantillaId: String, sesion: String = "s1"): Intento {
        val v = PaqueteDePrueba.variantes(plantillaId)[siguienteId % 5]
        return Intento("i${siguienteId++}", sesion, plantillaId, v.plantilla.concepto, v.plantilla.version, v.semilla, v.huella, ahora)
    }

    private fun responder(i: Intento, bien: Boolean) = i.copy(
        respondido = ahora, correcta = bien,
        estado = if (bien) EstadoRespuesta.CORRECTA else EstadoRespuesta.INCORRECTA,
    )

    // --- Tema -------------------------------------------------------------------------

    @Test
    fun loFalladoSeRepiteTalCualAlFinalDelTema() {
        val intentos = jugarTema(false, true, true, true, true)
        val s = Estudio.siguienteDelTema(leccion, intentos)
        assertTrue(s is SiguienteDelTema.Repetir)
        val repeticion = Estudio.repeticion((s as SiguienteDelTema.Repetir).fallado, "r", ahora)
        assertEquals(intentos[0].semilla, repeticion.semilla)
        assertEquals(intentos[0].huella, repeticion.huella)
        assertEquals(intentos[0].id, repeticion.repite)
    }

    @Test
    fun siLaRepeticionFallaVuelveAlFinal() {
        val intentos = jugarTema(false, true, true, true, true, false)
        val s = Estudio.siguienteDelTema(leccion, intentos) as SiguienteDelTema.Repetir
        assertEquals(intentos.last().id, s.fallado.id)
        assertEquals(intentos[0].semilla, s.fallado.semilla)
    }

    @Test
    fun conTresErroresElTemaSigueYElCuartoLoCorta() {
        val tres = jugarTema(false, false, false, true, true)
        assertFalse(Estudio.temaPerdido(tres))
        assertTrue(Estudio.siguienteDelTema(leccion, tres) is SiguienteDelTema.Repetir)
        val cuatro = jugarTema(false, false, false, false)
        assertTrue(Estudio.temaPerdido(cuatro))
        assertEquals(SiguienteDelTema.Ninguno, Estudio.siguienteDelTema(leccion, cuatro))
        assertFalse(Estudio.temaCompleto(leccion, cuatro))
    }

    @Test
    fun elTemaSeCompletaAlCorregirCadaFalloYDaPuntosUnaSolaVez() {
        val sinCorregir = jugarTema(true, false, true, true, true)
        assertFalse(Estudio.temaCompleto(leccion, sinCorregir))
        val corregido = jugarTema(true, false, true, true, true, true)
        assertTrue(Estudio.temaCompleto(leccion, corregido))
        assertEquals(Reglas.PUNTOS_LECCION, Estudio.puntosDelTema(completo = true, yaTeniaPuntos = false))
        assertEquals(0, Estudio.puntosDelTema(completo = true, yaTeniaPuntos = true))
        assertEquals(0, Estudio.puntosDelTema(completo = false, yaTeniaPuntos = false))
    }

    @Test
    fun unEjercicioSinResponderNoCompletaElTema() {
        val intentos = jugarTema(true, true, true, true) + nuevo(leccion.ejercicios.last().plantilla)
        assertFalse(Estudio.temaCompleto(leccion, intentos))
    }

    @Test
    fun unaRespuestaQueNoSePuedeLeerNoCuenta() {
        val v = PaqueteDePrueba.variantes("mat.fracciones.sumar-igual-denominador").first()
        val r = Verificador.verificar(v, "cinco novenos")
        assertEquals(EstadoRespuesta.FORMATO_INVALIDO, r.estado)
        assertFalse(Estudio.cuenta(r))
    }

    @Test
    fun unWritingConNotaBajaNoEsUnErrorDelTemaPeroSiEnLaPrueba() {
        val escrito = Intento("w", "s", "p", "c", "v", 1, "h", ahora, respondido = ahora, estado = EstadoRespuesta.PENDIENTE)
        val calificado = Estudio.registrarEscritura(escrito, 5.0)
        assertEquals(false, calificado.correcta)
        assertTrue(Estudio.fallos(listOf(calificado)).isEmpty())
        assertEquals(NotaPrueba(0.0, false), Estudio.calificarPrueba(listOf(calificado)))
        assertThrows(IllegalStateException::class.java) { Estudio.registrarEscritura(calificado, 9.0) }
    }

    // --- Pistas y solución ------------------------------------------------------------

    @Test
    fun pistasIlimitadasEnPracticaYProhibidasEnPruebas() {
        val v = PaqueteDePrueba.variantes("mat.fracciones.sumar-igual-denominador").first()
        val i = nuevo(v.plantilla.id)
        assertEquals(1, Estudio.pista(TipoSesion.LECCION, i, v)!!.nivel)
        assertEquals(3, Estudio.pista(TipoSesion.LECCION, i.copy(pistasUsadas = 7), v)!!.nivel)
        for (tipo in listOf(TipoSesion.PRUEBA_SECCION, TipoSesion.EXAMEN_FINAL)) {
            assertThrows(AyudaProhibida::class.java) { Estudio.pista(tipo, i, v) }
        }
    }

    @Test
    fun enUnTemaLaSolucionSaleSoloEnLaRepeticion() {
        val v = PaqueteDePrueba.variantes("mat.fracciones.sumar-igual-denominador").first()
        val respondido = responder(nuevo(v.plantilla.id), false).copy(pistasUsadas = 3)
        assertEquals(3, Estudio.pista(TipoSesion.LECCION, respondido, v)!!.nivel)
        assertEquals(4, Estudio.pista(TipoSesion.LECCION, respondido.copy(repite = "x"), v)!!.nivel)
        assertEquals(4, Estudio.pista(TipoSesion.REPASO, respondido, v)!!.nivel)
        assertEquals(v.solucion, Estudio.solucionAlAcertarRepeticion(responder(respondido.copy(repite = "x"), true), v))
        assertNull(Estudio.solucionAlAcertarRepeticion(responder(respondido, true), v))
    }

    // --- Pruebas y examen -------------------------------------------------------------

    private val matematica = PaqueteDePrueba.materia("matematica")

    private fun prueba(conceptos: List<String>, cantidad: Int, vistas: Map<String, Set<String>> = emptyMap(), semilla: Int = 1) =
        Estudio.preguntasDePrueba(matematica, conceptos, cantidad, Random(semilla), PaqueteDePrueba::variantes) { vistas[it].orEmpty() }

    @Test
    fun laPruebaDeSeccionTiene10PreguntasQueAlternanLosConceptos() {
        val fracciones = matematica.secciones.single { it.id == "fracciones" }
        val conceptos = fracciones.lecciones.map { it.concepto }.distinct()
        val preguntas = prueba(conceptos, Reglas.PREGUNTAS_PRUEBA_SECCION)
        assertEquals(10, preguntas.size)
        assertEquals(conceptos.toSet(), preguntas.map { it.plantilla.concepto }.toSet())
        preguntas.zipWithNext().forEach { (a, b) -> assertNotEquals(a.plantilla.concepto, b.plantilla.concepto) }
    }

    @Test
    fun laPruebaNoRepiteEjerciciosYaVistos() {
        val concepto = "mat.fracciones.suma-igual-denominador"
        val plantillas = matematica.secciones.flatMap { it.plantillas }.filter { it.concepto == concepto }
        val vistas = plantillas.associate { p -> p.id to PaqueteDePrueba.variantes(p.id).drop(3).map { it.huella }.toSet() }
        val preguntas = prueba(listOf(concepto), 3, vistas)
        assertTrue(preguntas.none { it.huella in vistas.getValue(it.plantilla.id) })
    }

    @Test
    fun conOpcionesFijasNoRepiteLaRespuestaHastaUsarlasTodas() {
        val ingles = PaqueteDePrueba.materia("ingles")
        val conector = ingles.secciones.flatMap { it.plantillas }.single { it.id.endsWith(".connector") }
        val plantilla = PaqueteDePrueba.variantes(conector.id).first().plantilla
        assertEquals(3, plantilla.opcionesFijas.size)
        val preguntas = Estudio.preguntasDePrueba(ingles, listOf(conector.concepto), 3, Random(5), PaqueteDePrueba::variantes) { emptySet() }
            .filter { it.plantilla.id == conector.id }
        assertEquals(preguntas.size, preguntas.map { it.respuesta }.toSet().size)
    }

    @Test
    fun notaDeLaPruebaYLoSinResponderCuentaComoError() {
        val intentos = (0 until 10).map { n ->
            val i = Intento("$n", "p", "x", "c${n % 3}", "v", n, "h$n", ahora)
            if (n < 7) responder(i, true) else i
        }
        assertEquals(NotaPrueba(7.0, true), Estudio.calificarPrueba(intentos))
        assertEquals(NotaPrueba(6.0, false), Estudio.calificarPrueba(intentos.mapIndexed { n, i -> if (n == 0) responder(i, false) else i }))
    }

    @Test
    fun conUnWritingSinCalificarLaPruebaQuedaPendiente() {
        val escrito = Intento("w", "p", "x", "c", "v", 1, "h", ahora, respondido = ahora, estado = EstadoRespuesta.PENDIENTE)
        assertTrue(Estudio.calificarPrueba(listOf(escrito)).pendiente)
    }

    @Test
    fun alReprobarSeRepasanLosTemasConMasErrores() {
        val intentos = listOf("a" to false, "b" to false, "b" to false, "c" to true, "a" to true).mapIndexed { n, (c, ok) ->
            responder(Intento("$n", "p", "x", c, "v", n, "h", ahora), ok)
        }
        assertEquals(listOf("b", "a"), Estudio.temasParaRepasar(intentos))
    }

    @Test
    fun elExamenSeCierraALos20MinutosConUnMargen() {
        val examen = Sesion("e", TipoSesion.EXAMEN_FINAL, "admisiones", "matematica", inicio = ahora)
        val limite = ahora.plusSeconds(20 * 60)
        assertEquals(limite, Estudio.limite(examen))
        assertEquals(1200L, Estudio.segundosRestantes(examen, ahora))
        assertFalse(Estudio.vencida(examen, limite.plusSeconds(Reglas.SEGUNDOS_DE_GRACIA.toLong())))
        assertTrue(Estudio.vencida(examen, limite.plusSeconds(Reglas.SEGUNDOS_DE_GRACIA + 1L)))
        assertEquals(0L, Estudio.segundosRestantes(examen, limite.plusSeconds(60)))
        assertEquals(limite, Estudio.horaDeCierre(examen, limite.plusSeconds(3600)))

        val prueba = examen.copy(tipo = TipoSesion.PRUEBA_SECCION)
        assertNull(Estudio.limite(prueba))
        assertFalse(Estudio.vencida(prueba, ahora.plusSeconds(86_400)))
    }

    @Test
    fun laPruebaSoloSeDaConTodosLosTemasTerminados() {
        val fracciones = matematica.secciones.single { it.id == "fracciones" }
        val temas = fracciones.lecciones.map { it.id }.toSet()
        val sinTerminar = calcularProgreso(matematica, Historial(temas.drop(1).toSet()))
        assertEquals(Bloqueo.PRUEBA_BLOQUEADA, assertThrows(ReglaError::class.java) { Estudio.comprobarPrueba(sinTerminar, "fracciones") }.motivo)
        val listos = calcularProgreso(matematica, Historial(temas))
        assertEquals(fracciones.lecciones.map { it.concepto }.distinct(), Estudio.comprobarPrueba(listos, "fracciones"))
        val reprobo = calcularProgreso(matematica, Historial(temas, listOf(ResultadoPrueba("fracciones", false, ahora))))
        assertEquals(Bloqueo.REPASO_REQUERIDO, assertThrows(ReglaError::class.java) { Estudio.comprobarPrueba(reprobo, "fracciones") }.motivo)
        assertEquals(Bloqueo.EXAMEN_BLOQUEADO, assertThrows(ReglaError::class.java) { Estudio.comprobarExamen(listos) }.motivo)
    }

    @Test
    fun unTemaBloqueadoNoSePuedeEmpezar() {
        val fracciones = matematica.secciones.single { it.id == "fracciones" }
        val progreso = calcularProgreso(matematica, Historial())
        Estudio.comprobarTema(progreso, fracciones.lecciones[0].id)
        assertThrows(ReglaError::class.java) { Estudio.comprobarTema(progreso, fracciones.lecciones[1].id) }
    }

    // --- Repaso -----------------------------------------------------------------------

    @Test
    fun elRepasoNoMezclaMateriasYAlternaConceptos() {
        val conceptos = listOf("mat.fracciones.suma-igual-denominador", "mat.fracciones.equivalentes")
        val sesion = Sesion("r", TipoSesion.REPASO, "admisiones", "matematica", conceptos = conceptos, inicio = ahora)
        val azar = Random(3)
        val elegidas = (0 until Reglas.EJERCICIOS_REPASO).map { Estudio.siguienteDeRepaso(matematica, sesion, it, azar)!! }
        assertTrue(elegidas.all { it.id.startsWith("mat.") })
        assertEquals(listOf(conceptos[0], conceptos[1], conceptos[0], conceptos[1], conceptos[0]), elegidas.map { it.concepto })
        assertNull(Estudio.siguienteDeRepaso(matematica, sesion, Reglas.EJERCICIOS_REPASO, azar))
    }

    @Test
    fun elRepasoLibreTomaLosConceptosMasAtrasados() {
        val proximas = mapOf(
            "a" to ahora.minusSeconds(10), "b" to ahora.minusSeconds(1000), "c" to ahora.plusSeconds(60),
            "d" to ahora.minusSeconds(5), "e" to ahora.minusSeconds(7), "f" to ahora.minusSeconds(8),
        )
        assertEquals(listOf("b", "a", "f", "e"), Estudio.conceptosDelRepasoLibre(proximas, ahora))
        assertEquals(Bloqueo.NADA_PARA_REPASAR,
            assertThrows(ReglaError::class.java) { Estudio.conceptosDelRepasoLibre(mapOf("c" to ahora.plusSeconds(1)), ahora) }.motivo)
    }

    // --- Cuaderno ---------------------------------------------------------------------

    @Test
    fun laCuentaSeGuardaComoSeLeeYSeVuelveALeer() {
        val plantilla = PaqueteDePrueba.curso.materias.flatMap { m -> m.secciones.flatMap { it.plantillas } }
            .first { it.tipo == TipoEjercicio.CUADERNO }
        val v = PaqueteDePrueba.variantes(plantilla.id).first()
        val cuenta = v.cuenta!!
        val texto = Estudio.textoGuardado(v, cuenta.esperadas)
        assertEquals(cuenta.esperadas, cuenta.desdeTexto(texto))
        assertEquals(EstadoRespuesta.CORRECTA, Verificador.verificar(v, texto).estado)
        val mal = cuenta.esperadas.toMutableList().also { it[0] = if (it[0] == "1") "2" else "1" }
        val r = Verificador.verificar(v, mal)
        assertEquals(EstadoRespuesta.INCORRECTA, r.estado)
        assertTrue(r.grupoMal != null && r.errorComun != null)
        assertFalse(r.errorComun!!.contains(v.respuesta!!))
    }
}
