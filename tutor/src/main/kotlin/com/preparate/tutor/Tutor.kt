package com.preparate.tutor

import com.preparate.dominio.AyudaProhibida
import com.preparate.dominio.CalificacionInvalida
import com.preparate.dominio.ContextoTutor
import com.preparate.dominio.Escritura
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Guardia
import com.preparate.dominio.Revision
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.ayudaPermitida

/** Un mensaje de la conversación sobre un ejercicio. Vive solo en la memoria de la pantalla. */
data class Turno(val deKiwi: Boolean, val texto: String)

/** Lo que se muestra tras hablar con Kiwi. null en su lugar: Kiwi no está disponible. */
sealed interface Dicho {
    /** Lo escribió Kiwi y pasó la guardia. */
    data class DeKiwi(val texto: String) : Dicho

    /** Kiwi iba a revelar la respuesta: en su lugar, una pista escrita que aún no vio. */
    data class PistaEscrita(val texto: String) : Dicho

    /** Kiwi iba a revelar la respuesta y ya vio todas las pistas: la pantalla pone un mensaje fijo. */
    data object Bloqueado : Dicho
}

/**
 * Kiwi en los ejercicios: pistas, ayuda tras un fallo, conversación y calificación del Writing.
 * Puerto de app/tutor/servicio.py, sin `mensajes_tutor`: las conversaciones nunca se guardan, ni en
 * Room ni en Firestore. Quien llama guarda en memoria lo que haga falta (las pistas ya vistas, la
 * charla) mientras dure el ejercicio.
 *
 * Nada de esto es imprescindible. Si LM Studio no responde, las funciones devuelven null y la
 * pantalla usa las pistas escritas; el Writing queda pendiente para más tarde.
 *
 * Regla 3: antes de cualquier llamada de ayuda se consulta `ayudaPermitida` de `:dominio`, la única
 * puerta. En pruebas y exámenes se lanza [AyudaProhibida] sin tocar el modelo.
 */
class Tutor(private val modelo: Modelo) {
    /** ¿Kiwi está encendido? Comprueba siempre, con 2 s de espera. */
    suspend fun disponible(): Boolean = !modelo.modelos().isNullOrEmpty()

    /**
     * La pista del nivel que toca, escrita por Kiwi; null si no puede (la pantalla ya muestra la de
     * la lista). La solución (después de las 3 pistas) siempre es la del contenido, revisada a mano.
     * `previas`: lo que Kiwi ya dijo como pista en este ejercicio.
     */
    suspend fun pista(ctx: ContextoTutor, nivel: Int, previas: List<String>): String? {
        exigirAyuda(ctx)
        if (nivel > ctx.variante.pistas.size) return null
        val mensajes = buildList {
            add(sistema(ctx, nivel))
            for (p in previas) {
                add(Mensaje("user", Prompts.PIDE_PISTA))
                add(Mensaje("assistant", p))
            }
            add(Mensaje("user", Prompts.PIDE_PISTA))
        }
        return (hablar(mensajes, ctx, vistas = null) as? Dicho.DeKiwi)?.texto
    }

    /**
     * Primer mensaje de Kiwi tras un fallo: el primer paso para resolverlo. Si iba a revelar la
     * respuesta, se usa la siguiente pista escrita que el estudiante aún no vio.
     */
    suspend fun trasFallo(ctx: ContextoTutor, vistas: Set<String>): Dicho? {
        exigirAyuda(ctx)
        exigirTrasResponder(ctx)
        val sistema = sistema(ctx).let { it.copy(texto = it.texto + Prompts.TAREA_TRAS_FALLO) }
        return hablar(listOf(sistema, Mensaje("user", turnoInicial(ctx))), ctx, vistas)
    }

    /** El estudiante le contesta a Kiwi sobre su respuesta. `historial`: la charla de este ejercicio. */
    suspend fun conversar(ctx: ContextoTutor, historial: List<Turno>, mensaje: String, vistas: Set<String>): Dicho? {
        exigirAyuda(ctx)
        exigirTrasResponder(ctx)
        val mensajes = buildList {
            add(sistema(ctx))
            add(Mensaje("user", turnoInicial(ctx)))
            for (t in historial.takeLast(
                MENSAJES_EN_MEMORIA,
            )) {
                add(Mensaje(if (t.deKiwi) "assistant" else "user", t.texto))
            }
            add(Mensaje("user", mensaje))
        }
        return hablar(mensajes, ctx, vistas)
    }

    /**
     * Califica un Writing con la rúbrica de su plantilla. La suma y la nota las hace el código
     * (`Escritura`). Calificar no es ayudar: también se hace con los Writing de pruebas y exámenes.
     * Lanza [TutorNoDisponible] si Kiwi no está o no devolvió una calificación usable.
     */
    suspend fun calificar(ctx: ContextoTutor): Revision {
        val variante = ctx.variante
        check(variante.plantilla.tipo == TipoEjercicio.ESCRITURA) { "solo se califican los Writing" }
        check(ctx.intento.estado == EstadoRespuesta.PENDIENTE) { "este texto ya fue calificado" }
        val rubrica = variante.plantilla.rubrica
        val sistema = Prompts.completar(
            Prompts.ESCRITURA,
            mapOf(
                "rubrica" to Escritura.rubricaEnTexto(rubrica),
                "enunciado" to variante.enunciado,
                "contexto" to ctx.material,
            ),
        )
        val texto = modelo.chat(
            listOf(Mensaje("system", sistema), Mensaje("user", ctx.intento.respuesta.orEmpty())),
            esquema = Prompts.esquemaEscritura(rubrica),
            maxTokens = 800,
        )
        return try {
            Escritura.revision(rubrica, texto)
        } catch (e: CalificacionInvalida) {
            throw TutorNoDisponible(e.message ?: "calificación inválida")
        }
    }

    // --- Internos -------------------------------------------------------------------

    private fun exigirAyuda(ctx: ContextoTutor) {
        if (!ayudaPermitida(ctx.tipoSesion)) throw AyudaProhibida()
    }

    /** Tras responder, Kiwi solo habla de un fallo o de un Writing (regla 9). */
    private fun exigirTrasResponder(ctx: ContextoTutor) {
        val i = ctx.intento
        check(i.respondido != null) { "primero responde el ejercicio" }
        check(i.correcta != true || ctx.variante.plantilla.tipo == TipoEjercicio.ESCRITURA) {
            "acertaste: no hace falta Kiwi"
        }
    }

    private fun sistema(ctx: ContextoTutor, nivel: Int? = null): Mensaje {
        val v = ctx.variante
        val i = ctx.intento
        return Mensaje(
            "system",
            Prompts.completar(
                Prompts.TUTOR,
                mapOf(
                    "nivel" to (nivel?.toString() ?: Prompts.SIN_NIVEL),
                    "materia" to ctx.materia,
                    "concepto" to ctx.concepto,
                    "fragmentos_de_la_leccion" to ctx.material,
                    "enunciado" to v.enunciado,
                    "pistas" to v.pistas.joinToString(" / ").ifEmpty { "ninguna" },
                    "respuesta" to (v.respuesta ?: SIN_RESPUESTA_UNICA),
                    "respuesta_estudiante" to
                        (if (i.respondido != null) i.respuesta.orEmpty() else Prompts.SIN_RESPONDER),
                    "diagnostico" to diagnostico(ctx),
                ),
            ),
        )
    }

    private fun diagnostico(ctx: ContextoTutor): String = when (ctx.intento.estado) {
        EstadoRespuesta.CORRECTA -> "respuesta correcta"
        EstadoRespuesta.FORMA_INCORRECTA -> "el valor es correcto, pero no está simplificado al máximo"
        EstadoRespuesta.FORMATO_INVALIDO -> "no se pudo leer su respuesta como un número"
        else -> ctx.intento.errorComun ?: "ninguno"
    }

    private fun turnoInicial(ctx: ContextoTutor) = "Mi respuesta: ${ctx.intento.respuesta.orEmpty()}"

    /**
     * Lo que dice el modelo, o null si no está. Si iba a revelar la respuesta (regla 2), se descarta:
     * en su lugar va la siguiente pista escrita que aún no vio (`vistas`), o [Dicho.Bloqueado].
     */
    private suspend fun hablar(mensajes: List<Mensaje>, ctx: ContextoTutor, vistas: Set<String>?): Dicho? {
        val texto = try {
            modelo.chat(mensajes)
        } catch (e: TutorNoDisponible) {
            return null
        }
        val aLaVista = listOfNotNull(ctx.intento.respuesta?.takeIf { it.isNotEmpty() })
        if (!Guardia.revelaRespuesta(ctx.variante, texto, aLaVista)) return Dicho.DeKiwi(texto)
        val pista = vistas?.let { v -> ctx.variante.pistas.firstOrNull { it !in v } }
        return if (pista != null) Dicho.PistaEscrita(pista) else Dicho.Bloqueado
    }

    companion object {
        /** Últimos mensajes de la conversación que recibe el modelo. */
        const val MENSAJES_EN_MEMORIA = 12

        /** Solo para el prompt (lo lee el modelo, no el estudiante). */
        private const val SIN_RESPUESTA_UNICA = "(texto libre, sin una respuesta única)"
    }
}
