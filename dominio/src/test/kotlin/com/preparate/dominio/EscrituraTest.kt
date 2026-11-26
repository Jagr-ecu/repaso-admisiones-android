package com.preparate.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EscrituraTest {
    private val rubrica = listOf(
        CriterioRubrica("Ideas", "Responde la consigna.", 4.0),
        CriterioRubrica("Gramática", "Usa bien el presente simple.", 3.0),
        CriterioRubrica("Vocabulario", "Usa palabras del tema.", 3.0),
    )

    @Test
    fun laNotaLaCalculaElCodigo() {
        val r = Escritura.revision(
            rubrica,
            """```json
            {"criterios": [
              {"criterio": "Ideas", "puntos": 3.5, "comentario": " Bien. "},
              {"criterio": "Gramática", "puntos": 9, "comentario": "Pasado de más."},
              {"criterio": "Vocabulario", "puntos": -1, "comentario": "Pocas palabras."}
            ], "comentario": "¡Buen trabajo!"}
            ```""",
        )
        // Se recortan al máximo de cada criterio: 3.5 + 3 + 0 = 6.5 de 10.
        assertEquals(listOf(3.5, 3.0, 0.0), r.criterios.map { it.puntos })
        assertEquals(6.5, r.puntaje, 0.0)
        assertEquals("Bien.", r.criterios.first().comentario)
        assertEquals("¡Buen trabajo!", r.comentario)
        assertEquals(r, Revision.desdeJson(r.aJson()))
    }

    @Test
    fun redondeaAUnDecimal() {
        val tres = listOf(CriterioRubrica("A", "", 3.0))
        assertEquals(
            3.3,
            Escritura.revision(
                tres,
                """{"criterios": [{"criterio": "A", "puntos": 1, "comentario": ""}], "comentario": ""}""",
            ).puntaje,
            0.0,
        )
    }

    @Test
    fun sinUnCriterioOConPuntosQueNoSonNumeroNoHayNota() {
        assertThrows(CalificacionInvalida::class.java) {
            Escritura.revision(
                rubrica,
                """{"criterios": [{"criterio": "Ideas", "puntos": 4, "comentario": ""}], "comentario": ""}""",
            )
        }
        assertThrows(CalificacionInvalida::class.java) {
            Escritura.revision(
                rubrica.take(1),
                """{"criterios": [{"criterio": "Ideas", "puntos": "4", "comentario": ""}], "comentario": ""}""",
            )
        }
        assertThrows(CalificacionInvalida::class.java) { Escritura.revision(rubrica, "no es JSON") }
    }

    @Test
    fun laRubricaEnTextoComoEnLaWeb() {
        assertEquals(
            "- Ideas (máximo 4 puntos): Responde la consigna.\n" +
                "- Gramática (máximo 3 puntos): Usa bien el presente simple.\n" +
                "- Vocabulario (máximo 3 puntos): Usa palabras del tema.",
            Escritura.rubricaEnTexto(rubrica),
        )
    }
}
