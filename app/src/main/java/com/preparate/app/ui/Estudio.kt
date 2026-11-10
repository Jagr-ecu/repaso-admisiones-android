package com.preparate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preparate.app.AppViewModel
import com.preparate.app.EjercicioUi
import com.preparate.app.EstudioVm
import com.preparate.app.Pantalla
import com.preparate.app.R
import com.preparate.dominio.Celda
import com.preparate.dominio.EstadoNodo
import com.preparate.dominio.EstadoPrueba
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Estudio
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.TipoVerificador
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** Avisos comunes de las pantallas de estudio: motivo de bloqueo o error inesperado. */
@Composable
private fun Avisos(e: EstudioVm) {
    e.bloqueo?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    e.error?.let { Text(stringResource(R.string.error_inesperado, it), color = MaterialTheme.colorScheme.error) }
}

// --- Inicio y curso -------------------------------------------------------------------

/** Los cursos del estudiante. Hoy solo Admisiones; más adelante pueden ser varios. */
@Composable
fun InicioEstudiante(vm: AppViewModel, e: EstudioVm, nombre: String, pie: @Composable () -> Unit) {
    Marco(stringResource(R.string.hola, nombre)) {
        Text(stringResource(R.string.tus_cursos), style = MaterialTheme.typography.titleMedium)
        for (curso in e.estudios.cursos()) {
            val puntos by e.puntos(curso.id).collectAsState(initial = 0)
            Card(Modifier.fillMaxWidth().clickable { vm.ir(Pantalla.Curso(curso.id)) }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(curso.nombre, style = MaterialTheme.typography.headlineSmall)
                    if (curso.descripcion.isNotEmpty()) Text(curso.descripcion, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.puntos, puntos), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalDivider()
        pie()
    }
}

/** Las materias de un curso, cada una con su avance. */
@Composable
fun PantallaCurso(vm: AppViewModel, e: EstudioVm, cursoId: String) {
    val curso = remember(cursoId) { e.estudios.curso(cursoId) }
    val progreso by e.progreso(cursoId).collectAsState(initial = null)
    val puntos by e.puntos(cursoId).collectAsState(initial = 0)
    Marco(curso.nombre, alVolver = { vm.atras() }) {
        Text(stringResource(R.string.puntos, puntos), style = MaterialTheme.typography.titleMedium)
        for (materia in curso.materias) {
            val p = progreso?.firstOrNull { it.materiaId == materia.id }
            val temas = p?.secciones?.flatMap { it.lecciones }.orEmpty()
            Card(Modifier.fillMaxWidth().clickable { e.abrirMateria(cursoId, materia.id) }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(materia.nombre, style = MaterialTheme.typography.titleLarge)
                    val hechos = temas.count { it.estado == EstadoNodo.COMPLETADO }
                    LinearProgressIndicator(
                        progress = { if (temas.isEmpty()) 0f else hechos.toFloat() / temas.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.temas_hechos, hechos, temas.size), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// --- Camino de una materia ------------------------------------------------------------

@Composable
fun PantallaMateria(vm: AppViewModel, e: EstudioVm, cursoId: String, materiaId: String) {
    val materia = remember(cursoId, materiaId) { e.estudios.curso(cursoId).materias.single { it.id == materiaId } }
    val progreso by e.progreso(cursoId).collectAsState(initial = null)
    LaunchedEffect(progreso) { e.refrescarMateria(cursoId, materiaId) }
    val p = progreso?.firstOrNull { it.materiaId == materiaId }
    Marco(materia.nombre, alVolver = { vm.atras() }) {
        Avisos(e)
        if (p == null) {
            CircularProgressIndicator()
            return@Marco
        }
        if (e.vencidos.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.repaso_explicacion))
                    Button(onClick = { e.empezarRepaso(cursoId, materiaId) }, enabled = !e.ocupado) {
                        Text(pluralStringResource(R.plurals.repasar_ahora, e.vencidos.size, e.vencidos.size))
                    }
                }
            }
        }
        for (s in p.secciones) {
            Text(s.seccion.nombre, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            for (pl in s.lecciones) {
                val abierta = e.abiertas.any { it.tipo == TipoSesion.LECCION && it.leccionId == pl.leccion.id }
                NodoTema(pl.leccion.titulo, pl.estado, abierta) { e.abrirTema(cursoId, pl.leccion.id) }
            }
            if (e.depuracion) {
                TextButton(onClick = { e.depuracionCompletar(cursoId, materiaId, s.seccion.id, aprobar = false) }) {
                    Text("Depuración: dar por hechos los temas")
                }
            }
            NodoPrueba(s.prueba, e.abiertas.any { it.tipo == TipoSesion.PRUEBA_SECCION && it.seccionId == s.seccion.id },
                alEmpezar = { e.empezarPrueba(cursoId, materiaId, s.seccion.id) },
                alRepasar = { e.empezarRepaso(cursoId, materiaId, s.seccion.id) },
                ocupado = e.ocupado)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (e.depuracion) {
            TextButton(onClick = { e.depuracionCompletar(cursoId, materiaId, null, aprobar = true) }) {
                Text("Depuración: aprobar todas las secciones")
            }
        }
        NodoExamen(p.examenFinal, e.abiertas.any { it.tipo == TipoSesion.EXAMEN_FINAL }, e.ocupado) {
            e.empezarExamen(cursoId, materiaId)
        }
    }
}

@Composable
private fun NodoTema(titulo: String, estado: EstadoNodo, abierta: Boolean, alTocar: () -> Unit) {
    val bloqueado = estado == EstadoNodo.BLOQUEADO
    val (marca, color) = when (estado) {
        EstadoNodo.COMPLETADO -> "✓" to MaterialTheme.colorScheme.primary
        EstadoNodo.BLOQUEADO -> "🔒" to MaterialTheme.colorScheme.outline
        else -> "●" to MaterialTheme.colorScheme.secondary
    }
    Card(
        Modifier.fillMaxWidth().clickable(enabled = !bloqueado, onClick = alTocar),
        colors = CardDefaults.cardColors(
            containerColor = if (bloqueado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, color),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(marca, color = color, fontSize = 22.sp, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                val detalle = when {
                    abierta -> stringResource(R.string.continuar)
                    estado == EstadoNodo.COMPLETADO -> stringResource(R.string.tema_hecho)
                    bloqueado -> stringResource(R.string.tema_bloqueado)
                    else -> null
                }
                detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun NodoPrueba(estado: EstadoPrueba, abierta: Boolean, alEmpezar: () -> Unit, alRepasar: () -> Unit, ocupado: Boolean) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.prueba_de_seccion), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when (estado) {
                        EstadoPrueba.BLOQUEADA -> R.string.prueba_bloqueada
                        EstadoPrueba.DISPONIBLE -> R.string.prueba_disponible
                        EstadoPrueba.REPASO_REQUERIDO -> R.string.prueba_repaso
                        EstadoPrueba.PENDIENTE -> R.string.prueba_pendiente
                        EstadoPrueba.APROBADA -> R.string.prueba_aprobada
                    },
                ),
            )
            when (estado) {
                EstadoPrueba.DISPONIBLE -> Button(onClick = alEmpezar, enabled = !ocupado) {
                    Text(stringResource(if (abierta) R.string.continuar else R.string.empezar))
                }
                EstadoPrueba.REPASO_REQUERIDO -> Button(onClick = alRepasar, enabled = !ocupado) {
                    Text(stringResource(R.string.hacer_repaso))
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun NodoExamen(estado: EstadoNodo, abierto: Boolean, ocupado: Boolean, alEmpezar: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.examen_final), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    when (estado) {
                        EstadoNodo.BLOQUEADO -> R.string.examen_bloqueado
                        EstadoNodo.DISPONIBLE -> R.string.examen_disponible
                        EstadoNodo.PENDIENTE -> R.string.examen_pendiente
                        EstadoNodo.COMPLETADO -> R.string.examen_aprobado
                    },
                ),
            )
            if (estado == EstadoNodo.DISPONIBLE || estado == EstadoNodo.COMPLETADO) {
                Button(onClick = alEmpezar, enabled = !ocupado) {
                    Text(stringResource(if (abierto) R.string.continuar else R.string.empezar))
                }
            }
        }
    }
}

// --- Explicación del tema -------------------------------------------------------------

@Composable
fun PantallaExplicacion(vm: AppViewModel, e: EstudioVm, cursoId: String, leccionId: String) {
    val leccion = e.leccion?.takeIf { it.id == leccionId } ?: return
    Marco(leccion.titulo, alVolver = { vm.atras() }, desplazable = false) {
        Formula(leccion.contenido, Modifier.fillMaxWidth().weight(1f), modo = ModoFormula.LECCION)
        Avisos(e)
        Button(onClick = { e.empezarTema(cursoId, leccionId) }, enabled = !e.ocupado, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (e.temaAbierto != null) R.string.continuar else R.string.practicar))
        }
    }
}

// --- Ejercicio ------------------------------------------------------------------------

@Composable
fun PantallaEjercicio(vm: AppViewModel, e: EstudioVm) {
    val a = e.actual ?: return
    val sesion = a.ejercicio.sesion
    val titulo = when (sesion.tipo) {
        TipoSesion.LECCION -> e.leccion?.titulo ?: stringResource(R.string.practicar)
        TipoSesion.REPASO -> stringResource(R.string.hacer_repaso)
        TipoSesion.PRUEBA_SECCION -> stringResource(R.string.prueba_de_seccion)
        TipoSesion.EXAMEN_FINAL -> stringResource(R.string.examen_final)
    }
    Marco(titulo, alVolver = { vm.atras() }) {
        Encabezado(e, a)
        Formula(a.variante.enunciado, tamano = 22)
        Entrada(e, a)
        a.aviso?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.tertiary) }
        Retroalimentacion(a)
        Pistas(e, a)
        Avisos(e)
        if (a.respondido) {
            Button(onClick = { e.siguiente() }, enabled = !e.ocupado, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.siguiente))
            }
        } else {
            Button(onClick = { e.comprobar() }, enabled = a.listo && !e.ocupado, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (a.practica) R.string.comprobar else R.string.enviar))
            }
        }
    }
}

@Composable
private fun Encabezado(e: EstudioVm, a: EjercicioUi) {
    val estado = a.estado
    val sesion = estado.sesion
    if (!a.practica) {
        LinearProgressIndicator(
            progress = { a.ejercicio.numero.toFloat() / estado.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (a.practica) stringResource(R.string.pregunta_n, a.ejercicio.numero)
            else stringResource(R.string.pregunta_n_de, a.ejercicio.numero, estado.total),
            style = MaterialTheme.typography.labelLarge,
        )
        estado.errores?.let { Text(stringResource(R.string.errores_tema, it), style = MaterialTheme.typography.labelLarge) }
        Estudio.limite(sesion)?.let { Reloj(it) { e.tiempoAgotado() } }
    }
}

/** Cuenta atrás del examen. Al llegar a cero, la app cierra el examen y muestra la nota. */
@Composable
private fun Reloj(limite: Instant, alAcabar: () -> Unit) {
    var restantes by remember(limite) { mutableLongStateOf(Duration.between(Instant.now(), limite).seconds) }
    LaunchedEffect(limite) {
        while (true) {
            restantes = Duration.between(Instant.now(), limite).seconds
            if (restantes <= 0) {
                alAcabar()
                break
            }
            delay(1_000)
        }
    }
    val r = restantes.coerceAtLeast(0)
    Text(
        stringResource(R.string.tiempo_restante, (r / 60).toInt(), (r % 60).toInt()),
        style = MaterialTheme.typography.labelLarge,
        color = if (r < 120) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Entrada(e: EstudioVm, a: EjercicioUi) {
    val v = a.variante
    when {
        v.plantilla.tipo == TipoEjercicio.CUADERNO -> Cuaderno(e, a)
        v.plantilla.tipo == TipoEjercicio.ORDENAR -> Ordenar(e, a)
        v.opciones.isNotEmpty() -> Opciones(e, a)
        v.plantilla.tipo == TipoEjercicio.ESCRITURA -> OutlinedTextField(
            value = a.entrada,
            onValueChange = e::escribir,
            label = { Text(stringResource(R.string.tu_texto)) },
            enabled = !a.respondido,
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> {
            val numero = v.plantilla.verificador == TipoVerificador.SYMPY
            OutlinedTextField(
                value = a.entrada,
                onValueChange = e::escribir,
                label = { Text(stringResource(R.string.tu_respuesta)) },
                placeholder = if (numero) ({ Text(stringResource(R.string.ejemplo_numero)) }) else null,
                singleLine = true,
                enabled = !a.respondido,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numero) KeyboardType.Ascii else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { e.comprobar() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Opciones(e: EstudioVm, a: EjercicioUi) {
    // Las opciones largas (oraciones enteras) van una por fila, para que se lean bien.
    val porFila = if (a.variante.opciones.any { it.length > 18 }) 1 else 2
    a.variante.opciones.chunked(porFila).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { opcion ->
                val elegida = a.entrada == opcion
                OutlinedButton(
                    onClick = { e.escribir(opcion) },
                    enabled = !a.respondido,
                    border = BorderStroke(if (elegida) 3.dp else 1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Formula(opcion, modo = ModoFormula.OPCION, tamano = if (porFila == 1) 20 else 22)
                }
            }
            if (fila.size < porFila) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Ordenar(e: EstudioVm, a: EjercicioUi) {
    val fragmentos = a.variante.fragmentos
    Text(stringResource(R.string.ordenar_ayuda), style = MaterialTheme.typography.bodySmall)
    Card(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.orden.isEmpty()) Text(" ", modifier = Modifier.height(40.dp))
            a.orden.forEachIndexed { posicion, i ->
                Button(onClick = { e.quitarFragmento(posicion) }, enabled = !a.respondido) { Text(fragmentos[i]) }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fragmentos.forEachIndexed { i, palabra ->
            OutlinedButton(onClick = { e.tocarFragmento(i) }, enabled = !a.respondido && i !in a.orden) { Text(palabra) }
        }
    }
}

/** La cuenta en columna: cifras impresas y casillas para una cifra cada una. */
@Composable
private fun Cuaderno(e: EstudioVm, a: EjercicioUi) {
    val cuenta = a.variante.cuenta ?: return
    val grupoMal = a.resultado?.grupoMal
    // Al escribir una cifra se pasa a la casilla siguiente en el orden real de la cuenta.
    val focos = remember(a.ejercicio.intento.id) { List(cuenta.casillas) { FocusRequester() } }
    val foco = LocalFocusManager.current
    val alEscribir: (Int, String) -> Unit = { n, texto ->
        e.escribirCasilla(n, texto)
        if (texto.any(Char::isDigit)) {
            if (n + 1 < focos.size) focos[n + 1].requestFocus() else foco.clearFocus()
        }
    }
    Text(stringResource(R.string.cuaderno_ayuda), style = MaterialTheme.typography.bodySmall)
    Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        for (fila in cuenta.filas) {
            fila.etiqueta?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row { fila.celdas.forEach { Celda(a, it, grupoMal, focos, alEscribir) } }
                if (fila.derecha.isNotEmpty()) {
                    Box(Modifier.width(2.dp).height(44.dp).background(MaterialTheme.colorScheme.onSurface))
                    Column {
                        Row { fila.derecha.forEach { Celda(a, it, grupoMal, focos, alEscribir) } }
                    }
                }
            }
            if (fila.linea) {
                Box(Modifier.width((fila.celdas.size * 44).dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface))
            }
        }
    }
    if (grupoMal != null) Text(stringResource(R.string.cuaderno_paso_mal), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Celda(a: EjercicioUi, celda: Celda, grupoMal: Int?, focos: List<FocusRequester>, alEscribir: (Int, String) -> Unit) {
    val caja = Modifier.size(44.dp).padding(2.dp)
    val estilo = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    when {
        celda.valor != null -> Box(caja, contentAlignment = Alignment.Center) {
            Text(celda.valor!!, style = estilo, fontWeight = if (celda.marca) FontWeight.Bold else FontWeight.Normal,
                color = if (celda.marca) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        celda.casilla != null -> {
            val n = celda.casilla!!
            val mal = grupoMal != null && celda.grupo == grupoMal
            val borde = if (mal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            BasicTextField(
                value = a.casillas[n].orEmpty(),
                onValueChange = { alEscribir(n, it) },
                enabled = !a.respondido,
                singleLine = true,
                textStyle = estilo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = caja.focusRequester(focos[n]).border(if (mal) 3.dp else 1.dp, borde, RoundedCornerShape(6.dp)).padding(top = 6.dp),
            )
        }
        celda.nota -> {
            // Casilla libre para lo que se lleva: no se califica ni se guarda.
            var nota by remember { androidx.compose.runtime.mutableStateOf("") }
            BasicTextField(
                value = nota,
                onValueChange = { nota = it.filter(Char::isDigit).takeLast(1) },
                textStyle = estilo.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.outline),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = caja.border(1.dp, Color.LightGray, RoundedCornerShape(6.dp)).padding(top = 10.dp),
            )
        }
        else -> Box(caja)
    }
}

@Composable
private fun Retroalimentacion(a: EjercicioUi) {
    val resultado = a.resultado ?: return
    if (resultado.estado == EstadoRespuesta.PENDIENTE) {
        Text(stringResource(R.string.writing_guardado), color = MaterialTheme.colorScheme.primary)
        return
    }
    val bien = resultado.correcta
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (bien) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (bien) R.string.muy_bien else R.string.todavia_no), style = MaterialTheme.typography.titleMedium)
            if (resultado.estado == EstadoRespuesta.FORMA_INCORRECTA) Text(stringResource(R.string.casi_simplificar))
            resultado.errorComun?.let { Text(it) }
            // Al acertar lo que había fallado, se explica por qué: pudo acertar por descarte.
            if (bien && a.ejercicio.intento.repite != null) {
                a.variante.solucion?.let {
                    Text(stringResource(R.string.por_que), style = MaterialTheme.typography.labelLarge)
                    Formula(it, tamano = 18)
                }
            }
        }
    }
}

@Composable
private fun Pistas(e: EstudioVm, a: EjercicioUi) {
    if (!a.practica) return // en pruebas y exámenes no hay pistas (regla 3)
    for (pista in a.pistas) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (pista.nivel <= a.variante.pistas.size) stringResource(R.string.pista_n, pista.nivel)
                    else stringResource(R.string.solucion),
                    style = MaterialTheme.typography.labelLarge,
                )
                Formula(pista.texto, tamano = 18)
            }
        }
    }
    val maximo = a.variante.pistas.size
    val vistas = a.pistas.maxOfOrNull { it.nivel } ?: 0
    val solucionPosible = a.respondido && a.resultado?.correcta == false && !a.variante.solucion.isNullOrEmpty() &&
        Estudio.solucionPermitida(a.tipo, a.ejercicio.intento.repite != null)
    when {
        vistas < maximo -> TextButton(onClick = { e.pedirPista() }, enabled = !e.ocupado) {
            Text(stringResource(if (vistas == 0) R.string.pedir_pista else R.string.otra_pista))
        }
        vistas == maximo && solucionPosible -> TextButton(onClick = { e.pedirPista() }, enabled = !e.ocupado) {
            Text(stringResource(R.string.ver_solucion))
        }
    }
}

// --- Fin de un tema o repaso ----------------------------------------------------------

@Composable
fun PantallaFinPractica(vm: AppViewModel, e: EstudioVm) {
    val fin = e.fin ?: return
    Marco("", alVolver = { vm.atras() }) {
        val c = fin.cierre
        if (fin.tipo == TipoSesion.LECCION) {
            if (c.completada) {
                Text(stringResource(R.string.tema_terminado), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (c.puntos > 0) stringResource(R.string.ganaste_puntos, c.puntos) else stringResource(R.string.ya_tenias_puntos),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(stringResource(R.string.tema_perdido), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { e.reiniciarTema(fin.cursoId, fin.leccionId!!) }, enabled = !e.ocupado, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.volver_a_empezar))
                }
            }
        } else {
            Text(
                stringResource(if (c.completada) R.string.repaso_terminado else R.string.repaso_incompleto),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Avisos(e)
        OutlinedButton(onClick = { e.abrirMateria(fin.cursoId, fin.materiaId) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.volver_al_camino))
        }
    }
}

// --- Resultado de una prueba ----------------------------------------------------------

@Composable
fun PantallaResultado(vm: AppViewModel, e: EstudioVm) {
    val r = e.resultado ?: return
    val sesion = r.cierre.sesion
    Marco(
        stringResource(if (sesion.tipo == TipoSesion.EXAMEN_FINAL) R.string.examen_final else R.string.prueba_de_seccion),
        alVolver = { vm.atras() },
    ) {
        when {
            r.cierre.pendiente -> Text(stringResource(R.string.nota_pendiente), style = MaterialTheme.typography.titleMedium)
            else -> {
                Text(
                    stringResource(R.string.tu_nota, "%.2f".format(r.cierre.nota ?: 0.0).trimEnd('0').trimEnd(',', '.')),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(if (r.cierre.aprobada == true) R.string.aprobaste else R.string.reprobaste),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (r.repasar.isNotEmpty()) {
            Text(stringResource(R.string.repasa_estos))
            val curso = e.estudios.curso(sesion.cursoId)
            val titulos = curso.materias.flatMap { m -> m.secciones.flatMap { it.lecciones } }.associate { it.concepto to it.titulo }
            r.repasar.forEach { Text("• ${titulos[it] ?: it}") }
            Button(onClick = { e.empezarRepaso(sesion.cursoId, sesion.materiaId, sesion.seccionId) }, enabled = !e.ocupado) {
                Text(stringResource(R.string.hacer_repaso))
            }
        }
        Avisos(e)
        for (p in r.preguntas) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.pregunta_n, p.numero), style = MaterialTheme.typography.labelLarge)
                        when (p.intento.correcta) {
                            true -> Text(stringResource(R.string.bien_marca), color = MaterialTheme.colorScheme.primary)
                            false -> Text(stringResource(R.string.mal_marca), color = MaterialTheme.colorScheme.error)
                            null -> {}
                        }
                    }
                    Formula(p.variante.enunciado, tamano = 18)
                    Text(p.intento.respuesta?.let { stringResource(R.string.respondiste, it) } ?: stringResource(R.string.sin_responder))
                    p.intento.errorComun?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (p.intento.correcta != true) {
                        p.variante.respuesta?.let { Text(stringResource(R.string.respuesta_correcta, it)) }
                        p.variante.solucion?.let { Formula(it, tamano = 16) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { e.abrirMateria(sesion.cursoId, sesion.materiaId) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.volver_al_camino))
        }
    }
}
