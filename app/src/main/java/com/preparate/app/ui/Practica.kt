package com.preparate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.preparate.app.AppViewModel
import com.preparate.app.TemaEnCurso
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.TipoEjercicio

@Composable
fun PantallaLeccion(vm: AppViewModel) {
    val leccion = vm.leccion ?: return
    Marco(leccion.titulo, alVolver = { vm.volverAlInicio() }, desplazable = false) {
        Formula(leccion.contenido, Modifier.fillMaxWidth().weight(1f), modo = ModoFormula.LECCION)
        Button(onClick = { vm.empezarPractica() }, modifier = Modifier.fillMaxWidth()) { Text("Practicar") }
    }
}

@Composable
fun PantallaPractica(vm: AppViewModel) {
    val tema = vm.tema ?: return
    Marco(tema.leccion.titulo, alVolver = { vm.volverAlInicio() }) {
        if (tema.terminado) Fin(vm, tema) else Ejercicio(vm, tema)
    }
}

@Composable
private fun Fin(vm: AppViewModel, tema: TemaEnCurso) {
    Text("¡Terminaste el tema!", style = MaterialTheme.typography.headlineSmall)
    Text("Respondiste bien ${tema.aciertos} de ${tema.ejercicios.size}.", style = MaterialTheme.typography.bodyLarge)
    Button(onClick = { vm.empezarPractica() }, modifier = Modifier.fillMaxWidth()) { Text("Practicar otra vez") }
    OutlinedButton(onClick = { vm.volverAlInicio() }, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
}

@Composable
private fun Ejercicio(vm: AppViewModel, tema: TemaEnCurso) {
    val actual = tema.actual
    val variante = actual.variante
    val respondido = actual.resultado != null

    LinearProgressIndicator(
        progress = { tema.indice.toFloat() / tema.ejercicios.size },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Pregunta ${tema.indice + 1} de ${tema.ejercicios.size}", style = MaterialTheme.typography.labelLarge)
    Formula(variante.enunciado, tamano = 22)

    if (variante.plantilla.tipo == TipoEjercicio.OPCION_MULTIPLE) {
        variante.opciones.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fila.forEach { opcion ->
                    val elegida = actual.entrada == opcion
                    OutlinedButton(
                        onClick = { vm.escribir(opcion) },
                        enabled = !respondido,
                        border = BorderStroke(if (elegida) 3.dp else 1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                    ) {
                        Formula(opcion, modo = ModoFormula.OPCION, tamano = 22)
                    }
                }
            }
        }
    } else {
        OutlinedTextField(
            value = actual.entrada,
            onValueChange = vm::escribir,
            label = { Text("Tu respuesta") },
            placeholder = { Text("Por ejemplo: 3/4") },
            singleLine = true,
            enabled = !respondido,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.comprobar() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    actual.aviso?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }

    val resultado = actual.resultado
    if (resultado != null) {
        val bien = resultado.estado == EstadoRespuesta.CORRECTA
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (bien) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (bien) "¡Muy bien!" else "Todavía no.", style = MaterialTheme.typography.titleMedium)
                resultado.errorComun?.let { Text(it) }
            }
        }
    }

    // Pistas: ilimitadas en práctica. Tras un fallo, la primera ya aparece sola (regla 9).
    variante.pistas.take(actual.pistasVistas).forEachIndexed { i, pista ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Pista ${i + 1}", style = MaterialTheme.typography.labelLarge)
                Formula(pista, tamano = 18)
            }
        }
    }
    if (actual.verSolucion && variante.solucion != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Solución", style = MaterialTheme.typography.labelLarge)
                Formula(variante.solucion!!, tamano = 18)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (actual.pistasVistas < variante.pistas.size) {
            TextButton(onClick = { vm.pedirPista() }) { Text(if (actual.pistasVistas == 0) "Pedir una pista" else "Otra pista") }
        }
        if (respondido && resultado?.correcta == false && !actual.verSolucion && variante.solucion != null) {
            TextButton(onClick = { vm.verSolucion() }) { Text("Ver la solución") }
        }
    }

    if (respondido) {
        Button(onClick = { vm.siguiente() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (tema.indice + 1 == tema.ejercicios.size) "Terminar" else "Siguiente")
        }
    } else {
        Button(
            onClick = { vm.comprobar() },
            enabled = actual.entrada.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Comprobar") }
    }
}
