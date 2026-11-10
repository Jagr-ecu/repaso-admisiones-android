package com.preparate.dominio

/*
 * Unir lo que baja de la nube con lo que ya hay en el dispositivo (PLAN-MIGRACION.md, sección 4).
 *
 * Un intento se crea al mostrarse y se completa al responder; una sesión se crea abierta y se
 * cierra después. Por eso los campos casi siempre pasan de vacío a lleno, y la unión es por campo:
 * - gana el intento ya respondido sobre el que no se respondió;
 * - gana la sesión cerrada sobre la abierta;
 * - una nota (de una prueba o de un Writing) nunca se borra con un valor vacío.
 * Si los dos lados están igual de avanzados, se queda lo del dispositivo.
 */
object Union {
    fun sesion(local: Sesion?, remota: Sesion): Sesion {
        if (local == null) return remota
        val (base, otra) = if (local.fin == null && remota.fin != null) remota to local else local to remota
        return base.copy(
            fin = base.fin ?: otra.fin,
            nota = base.nota ?: otra.nota,
            aprobada = base.aprobada ?: otra.aprobada,
        )
    }

    fun intento(local: Intento?, remoto: Intento): Intento {
        if (local == null) return remoto
        val (base, otro) = if (local.respondido == null && remoto.respondido != null) remoto to local else local to remoto
        val unido = base.copy(pistasUsadas = maxOf(base.pistasUsadas, otro.pistasUsadas))
        // Un Writing calificado en otro dispositivo trae su nota: no se pierde.
        return if (unido.puntaje == null && otro.puntaje != null && unido.estado == EstadoRespuesta.PENDIENTE) {
            unido.copy(puntaje = otro.puntaje, estado = otro.estado, correcta = otro.correcta)
        } else {
            unido
        }
    }

    /**
     * `memoria` no se sincroniza: se recalcula desde los intentos, en el orden en que se respondieron.
     * Así un dispositivo atrasado no pisa lo nuevo. Devuelve la tarjeta de cada concepto.
     */
    fun memoria(intentos: List<Intento>): Map<String, Tarjeta> {
        val tarjetas = mutableMapOf<String, Tarjeta>()
        intentos
            .filter { it.respondido != null && it.correcta != null }
            .sortedWith(compareBy<Intento> { it.respondido }.thenBy { it.id })
            .forEach { i ->
                val calificacion = Reglas.calificacionMemoria(i.correcta!!, i.pistasAlResponder ?: i.pistasUsadas)
                tarjetas[i.concepto] = Memoria.repasar(tarjetas[i.concepto], calificacion, i.respondido!!)
            }
        return tarjetas
    }
}
