package com.preparate.dominio

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Compara las reglas con referencia-reglas.json, que genera Python. */
class ReglasReferenciaTest {
    private val ref = Referencia.leer("reglas")

    private fun lista(clave: String) = ref.getValue(clave).jsonArray.map { it.jsonObject }

    @Test
    fun constantesIgualesALaWeb() {
        val c = ref.getValue("constantes").jsonObject.mapValues { it.value.jsonPrimitive.int }
        val kotlin = mapOf(
            "NOTA_MAXIMA" to Reglas.NOTA_MAXIMA,
            "NOTA_MINIMA_APROBAR" to Reglas.NOTA_MINIMA_APROBAR,
            "PREGUNTAS_PRUEBA_SECCION" to Reglas.PREGUNTAS_PRUEBA_SECCION,
            "PREGUNTAS_EXAMEN_FINAL" to Reglas.PREGUNTAS_EXAMEN_FINAL,
            "MINUTOS_EXAMEN_FINAL" to Reglas.MINUTOS_EXAMEN_FINAL,
            "SEGUNDOS_DE_GRACIA" to Reglas.SEGUNDOS_DE_GRACIA,
            "EJERCICIOS_REPASO" to Reglas.EJERCICIOS_REPASO,
            "TEMAS_REPASO_TRAS_REPROBAR" to Reglas.TEMAS_REPASO_TRAS_REPROBAR,
            "PUNTOS_LECCION" to Reglas.PUNTOS_LECCION,
            "ERRORES_PERMITIDOS_TEMA" to Reglas.ERRORES_PERMITIDOS_TEMA,
        )
        assertEquals(c, kotlin)
    }

    @Test
    fun notas() {
        for (caso in lista("notas")) {
            val a = caso.getValue("aciertos").jsonPrimitive.int
            val t = caso.getValue("total").jsonPrimitive.int
            val nota = Reglas.nota(a, t)
            assertEquals("$a de $t", caso.getValue("nota").jsonPrimitive.double, nota, 0.0)
            assertEquals("$a de $t", caso.getValue("aprobada").jsonPrimitive.boolean, Reglas.aprobada(nota))
        }
    }

    @Test
    fun temaPerdido() {
        for (caso in lista("tema_perdido")) {
            val e = caso.getValue("errores").jsonPrimitive.int
            assertEquals("$e errores", caso.getValue("perdido").jsonPrimitive.boolean, Reglas.temaPerdido(e))
        }
    }

    @Test
    fun calificacionDelRepaso() {
        for (caso in lista("calificacion_memoria")) {
            val r = Reglas.calificacionMemoria(
                caso.getValue("correcta").jsonPrimitive.boolean, caso.getValue("pistas").jsonPrimitive.int,
            )
            assertEquals(caso.toString(), caso.getValue("calificacion").jsonPrimitive.int, r.valor)
        }
    }

    @Test
    fun temasConMasErrores() {
        for (caso in lista("temas_con_mas_errores")) {
            val resultados = caso.getValue("resultados").jsonArray.map {
                it.jsonObject.getValue("concepto").jsonPrimitive.content to it.jsonObject.getValue("correcta").jsonPrimitive.boolean
            }
            val esperados = caso.getValue("conceptos").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(caso.toString(), esperados, Reglas.temasConMasErrores(resultados, caso.getValue("cuantos").jsonPrimitive.int))
        }
    }
}
