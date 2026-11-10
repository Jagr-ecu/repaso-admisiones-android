package com.preparate.dominio

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class ProgresoTest {
    private fun materia(vararg secciones: Pair<String, List<String>>) = MateriaCurso(
        id = "matematica", nombre = "Matemática", prefijo = "mat", peso = 1.0,
        secciones = secciones.map { (id, lecciones) ->
            SeccionCurso(id, id, lecciones.map { LeccionResumen(it, it, it) }, emptyList())
        },
    )

    private val ahora = java.time.Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun temasEnOrdenYSeccionesAbiertas() {
        val p = calcularProgreso(materia("a" to listOf("a1", "a2"), "b" to listOf("b1")), Historial(setOf("a1")))
        assertEquals(listOf(EstadoNodo.COMPLETADO, EstadoNodo.DISPONIBLE), p.seccion("a")!!.lecciones.map { it.estado })
        assertEquals(EstadoNodo.DISPONIBLE, p.leccion("b1")!!.estado)
        assertEquals(EstadoPrueba.BLOQUEADA, p.seccion("a")!!.prueba)
        assertEquals(EstadoNodo.BLOQUEADO, p.examenFinal)
    }

    @Test
    fun reprobarPideRepasoAntesDeReintentar() {
        val m = materia("a" to listOf("a1"))
        val reprobo = Historial(setOf("a1"), listOf(ResultadoPrueba("a", false, ahora)))
        assertEquals(EstadoPrueba.REPASO_REQUERIDO, calcularProgreso(m, reprobo).seccion("a")!!.prueba)
        val conRepaso = reprobo.copy(repasos = listOf(RepasoHecho("a", ahora.plusSeconds(60))))
        assertEquals(EstadoPrueba.DISPONIBLE, calcularProgreso(m, conRepaso).seccion("a")!!.prueba)
        val aprobo = conRepaso.copy(pruebas = conRepaso.pruebas + ResultadoPrueba("a", true, ahora.plusSeconds(120)))
        assertEquals(EstadoNodo.DISPONIBLE, calcularProgreso(m, aprobo).examenFinal)
    }

    /** Historiales al azar de Python (referencia-progreso.json): el camino debe salir igual. */
    @Test
    fun coincideConLosCasosDeReferencia() {
        var casos = 0
        for (m in Referencia.leer("progreso").getValue("materias").jsonArray.map { it.jsonObject }) {
            val materia = materia(*m.getValue("secciones").jsonArray.map {
                val s = it.jsonObject
                s.texto("id")!! to s.getValue("lecciones").jsonArray.map { l -> l.jsonPrimitive.content }
            }.toTypedArray())
            for (caso in m.getValue("casos").jsonArray.map { it.jsonObject }) {
                val p = calcularProgreso(materia, historial(caso.getValue("historial").jsonObject))
                val obtenido = mapOf(
                    "secciones" to p.secciones.map { s ->
                        mapOf(
                            "id" to s.seccion.id, "estado" to s.estado.valor, "prueba" to s.prueba.valor,
                            "lecciones" to s.lecciones.map { mapOf("id" to it.leccion.id, "estado" to it.estado.valor) },
                        )
                    },
                    "examen_final" to p.examenFinal.valor,
                )
                assertEquals(caso.toString(), plano(caso.getValue("progreso")), obtenido)
                casos++
            }
        }
        assertEquals(120, casos)
    }

    private fun historial(h: JsonObject) = Historial(
        leccionesCompletadas = h.getValue("lecciones_completadas").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        pruebas = h.getValue("pruebas").jsonArray.map {
            val p = it.jsonObject
            val aprobada = p.getValue("aprobada").let { a -> if (a is JsonNull) null else a.jsonPrimitive.boolean }
            ResultadoPrueba(p.texto("seccion")!!, aprobada, fecha(p.texto("fecha")!!))
        },
        repasos = h.getValue("repasos").jsonArray.map {
            RepasoHecho(it.jsonObject.texto("seccion")!!, fecha(it.jsonObject.texto("fecha")!!))
        },
        examenAprobado = h.getValue("examen_aprobado").jsonPrimitive.boolean,
        examenPendiente = h.getValue("examen_pendiente").jsonPrimitive.boolean,
    )

    private fun fecha(texto: String) = OffsetDateTime.parse(texto).toInstant()

    /** JSON → mapas y listas de Kotlin, para comparar con un solo assertEquals. */
    private fun plano(e: kotlinx.serialization.json.JsonElement): Any? = when (e) {
        is JsonObject -> e.mapValues { plano(it.value) }
        is kotlinx.serialization.json.JsonArray -> e.map { plano(it) }
        is JsonNull -> null
        else -> e.jsonPrimitive.content
    }
}
