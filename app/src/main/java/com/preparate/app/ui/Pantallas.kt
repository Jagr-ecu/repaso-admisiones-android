package com.preparate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.preparate.app.AppViewModel
import com.preparate.app.R
import com.preparate.app.BuildConfig
import com.preparate.app.EstadoKiwi
import com.preparate.app.Pantalla
import com.preparate.sincro.Rol
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PantallaEntrar(vm: AppViewModel) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    Marco("Prepárate") {
        Spacer(Modifier.height(24.dp))
        Text("Entra con tu usuario y tu clave.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = clave,
            onValueChange = { clave = it },
            label = { Text("Clave") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        TextoError(vm.error)
        Button(onClick = { vm.entrar(usuario, clave) }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.ocupado) "Entrando…" else "Entrar")
        }
        Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        if (BuildConfig.DEBUG) {
            TextButton(onClick = { vm.vistaPrevia() }) { Text("Vista previa del tema (solo depuración)") }
        }
    }
}

@Composable
fun PantallaInicio(vm: AppViewModel) {
    val sesion = vm.sesion
    if (sesion?.rol == Rol.ADMIN) {
        InicioAdmin(vm)
        return
    }
    val estudio = vm.estudio ?: return
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val nombre = sesion?.let { it.nombre.ifEmpty { it.usuario } } ?: "vista previa"
    InicioEstudiante(vm, estudio, nombre) {
        if (sesion != null) {
            Text(
                if (pendientes == 0) stringResource(R.string.todo_subido) else stringResource(R.string.sin_subir, pendientes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = { vm.ir(Pantalla.Kiwi) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.probar_kiwi))
        }
        OutlinedButton(onClick = { vm.salir() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.salir)) }
    }
}

@Composable
private fun InicioAdmin(vm: AppViewModel) {
    val sesion = vm.sesion ?: return
    LaunchedEffect(Unit) { vm.cargarEstudiantes() }
    Marco("Panel de ${sesion.nombre.ifEmpty { sesion.usuario }}") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.limpiarCreacion(); vm.ir(Pantalla.CrearEstudiante) }) { Text("Crear estudiante") }
            OutlinedButton(onClick = { vm.cargarEstudiantes() }) { Text("Actualizar") }
        }
        TextoError(vm.error)
        Text("Estudiantes", style = MaterialTheme.typography.titleMedium)
        val lista = vm.estudiantes
        when {
            lista == null && vm.ocupado -> CircularProgressIndicator()
            lista.isNullOrEmpty() -> Text("Todavía no hay estudiantes.")
            else -> lista.forEach { ficha ->
                Card(Modifier.fillMaxWidth().clickable { vm.abrirEstudiante(ficha) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(ficha.nombre, style = MaterialTheme.typography.titleMedium)
                        Text("Usuario: ${ficha.usuario}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        HorizontalDivider()
        OutlinedButton(onClick = { vm.ir(Pantalla.Kiwi) }, modifier = Modifier.fillMaxWidth()) { Text("Kiwi: dirección del PC") }
        OutlinedButton(onClick = { vm.salir() }, modifier = Modifier.fillMaxWidth()) { Text("Salir") }
    }
}

@Composable
fun PantallaCrearEstudiante(vm: AppViewModel) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    val pendiente = vm.perfilPendiente
    Marco("Crear estudiante", alVolver = { vm.volverAlInicio() }) {
        Text("Solo el nombre, sin apellidos.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true,
            enabled = pendiente == null, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true,
            enabled = pendiente == null,
            supportingText = { Text("De 3 a 20: letras sin tilde, números, punto, guion o guion bajo.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(clave, { clave = it }, label = { Text("Clave") }, singleLine = true,
            enabled = pendiente == null, supportingText = { Text("Al menos 6 caracteres.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
        TextoError(vm.error)
        vm.estudianteCreado?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = {
                vm.crearEstudiante(nombre, usuario, clave)
                if (pendiente == null) clave = ""
            },
            enabled = !vm.ocupado,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (pendiente != null) "Reintentar" else if (vm.ocupado) "Creando…" else "Crear") }
    }
}

private val FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())

private fun hora(instante: Instant?) = instante?.let { FORMATO_HORA.format(it) } ?: "—"

@Composable
fun PantallaDetalleEstudiante(vm: AppViewModel, destino: Pantalla.DetalleEstudiante) {
    Marco(destino.ficha.nombre, alVolver = { vm.volverAlInicio() }) {
        Text("Usuario: ${destino.ficha.usuario}")
        TextoError(vm.error)
        val lista = vm.intentos
        when {
            lista == null -> CircularProgressIndicator()
            lista.isEmpty() -> Text("Todavía no hay respuestas.")
            else -> lista.forEach { intento ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${intento.plantilla.substringAfterLast('.')}: «${intento.respuesta}» → ${intento.estado}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Pistas: ${intento.pistasUsadas} · Respondió: ${hora(intento.respondidoEn)} · " +
                                "Llegó a la nube: ${hora(intento.subidoEn)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaKiwi(vm: AppViewModel) {
    var direccion by rememberSaveable { mutableStateOf(vm.direccionKiwi.ifEmpty { "http://192.168.1." }) }
    Marco("Kiwi", alVolver = { vm.volverAlInicio() }) {
        Text("Dirección de LM Studio en el PC de la casa, con su puerto. Por ejemplo: http://192.168.1.50:1234")
        OutlinedTextField(
            direccion, { direccion = it }, label = { Text("Dirección") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.probarKiwi(direccion) },
            enabled = vm.estadoKiwi != EstadoKiwi.Probando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar y probar") }
        when (val estado = vm.estadoKiwi) {
            EstadoKiwi.SinProbar -> Unit
            EstadoKiwi.Probando -> Text("Buscando a Kiwi…")
            EstadoKiwi.Apagado -> Text(
                "Kiwi no responde. Revisa que LM Studio esté encendido, que acepte conexiones de la red " +
                    "local y que la tablet esté en el mismo wifi.",
                color = MaterialTheme.colorScheme.error,
            )
            is EstadoKiwi.Activo -> {
                Text("Kiwi está activo. Modelo: ${estado.modelo}", color = MaterialTheme.colorScheme.primary)
                when {
                    estado.saludo != null -> Card(Modifier.fillMaxWidth()) {
                        Text("Kiwi: ${estado.saludo}", Modifier.padding(16.dp))
                    }
                    estado.error != null -> TextoError(estado.error)
                    else -> Text("Kiwi está pensando…")
                }
            }
        }
    }
}
