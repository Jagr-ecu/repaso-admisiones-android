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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.preparate.app.AppViewModel
import com.preparate.app.BuildConfig
import com.preparate.app.EstadoKiwi
import com.preparate.app.EstadoNube
import com.preparate.app.Pantalla
import com.preparate.app.R
import com.preparate.dominio.EstadoNodo
import com.preparate.dominio.ResumenPanel
import com.preparate.dominio.TipoSesion
import com.preparate.sincro.Rol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PantallaEntrar(vm: AppViewModel) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    Marco(stringResource(R.string.entrar_titulo)) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.entrar_ayuda), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text(stringResource(R.string.usuario)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = clave,
            onValueChange = { clave = it },
            label = { Text(stringResource(R.string.clave)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        TextoError(vm.error)
        Button(onClick = { vm.entrar(usuario, clave) }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.ocupado) stringResource(vm.paso ?: R.string.entrando) else stringResource(R.string.entrar))
        }
        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
        if (BuildConfig.DEBUG) {
            TextButton(onClick = { vm.vistaPrevia() }) { Text(stringResource(R.string.vista_previa)) }
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
            val texto = when {
                vm.nube == EstadoNube.SINCRONIZANDO -> stringResource(R.string.nube_sincronizando)
                pendientes > 0 -> pluralStringResource(R.plurals.sin_subir, pendientes, pendientes)
                vm.nube == EstadoNube.FALLO -> stringResource(R.string.nube_fallo)
                else -> stringResource(R.string.todo_subido)
            }
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = { vm.ir(Pantalla.Kiwi) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.probar_kiwi))
        }
        if (sesion != null) {
            OutlinedButton(onClick = { vm.abrirCambiarClave() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cambiar_mi_clave))
            }
        }
        OutlinedButton(onClick = {
            vm.pedirSalir()
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.salir)) }
    }
    if (vm.avisoSalir) AvisoSalir(vm, pendientes)
}

/** Salir con cambios sin subir: nunca se borran, pero se avisa y se ofrece subirlos primero. */
@Composable
private fun AvisoSalir(vm: AppViewModel, pendientes: Int) {
    AlertDialog(
        onDismissRequest = { vm.cancelarSalir() },
        title = { Text(stringResource(R.string.salir_aviso_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.salir_aviso, pendientes, pendientes))
                if (vm.ocupado) Text(stringResource(R.string.subiendo))
                TextoError(vm.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.subirYSalir()
            }, enabled = !vm.ocupado) { Text(stringResource(R.string.salir_subir_primero)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.cancelarSalir() }) { Text(stringResource(R.string.cancelar)) }
                TextButton(onClick = {
                    vm.salir()
                }, enabled = !vm.ocupado) { Text(stringResource(R.string.salir_igual)) }
            }
        },
    )
}

@Composable
private fun InicioAdmin(vm: AppViewModel) {
    val sesion = vm.sesion ?: return
    LaunchedEffect(Unit) { vm.cargarEstudiantes() }
    Marco(stringResource(R.string.panel_de, sesion.nombre.ifEmpty { sesion.usuario })) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.limpiarCreacion()
                vm.ir(Pantalla.CrearEstudiante)
            }) { Text(stringResource(R.string.crear_estudiante)) }
            OutlinedButton(onClick = { vm.cargarEstudiantes() }) { Text(stringResource(R.string.actualizar)) }
        }
        TextoError(vm.error)
        Text(stringResource(R.string.estudiantes), style = MaterialTheme.typography.titleMedium)
        val lista = vm.estudiantes
        when {
            lista == null && vm.ocupado -> CircularProgressIndicator()

            lista.isNullOrEmpty() -> Text(stringResource(R.string.sin_estudiantes))

            else -> lista.forEach { ficha ->
                Card(Modifier.fillMaxWidth().clickable { vm.abrirEstudiante(ficha) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ficha.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.usuario_es, ficha.usuario),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val r = ficha.resumen
                        val ultima = r?.ultimaActividad
                        Text(
                            when {
                                r == null -> stringResource(R.string.sin_resumen)
                                ultima == null -> stringResource(R.string.ficha_nunca)
                                else -> stringResource(R.string.ficha_resumen, fecha(ultima), r.minutosSemana)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (r != null && r.pendientes.isNotEmpty()) {
                            Text(
                                pluralStringResource(R.plurals.ficha_writing, r.pendientes.size, r.pendientes.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        OutlinedButton(onClick = {
            vm.ir(Pantalla.Kiwi)
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.kiwi_direccion_pc)) }
        OutlinedButton(onClick = {
            vm.abrirCambiarClave()
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cambiar_mi_clave)) }
        OutlinedButton(onClick = {
            vm.salir()
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.salir)) }
    }
}

@Composable
fun PantallaCrearEstudiante(vm: AppViewModel) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    val pendiente = vm.perfilPendiente
    Marco(stringResource(R.string.crear_estudiante), alVolver = { vm.volverAlInicio() }) {
        Text(stringResource(R.string.solo_nombre), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            nombre,
            { nombre = it },
            label = { Text(stringResource(R.string.nombre)) },
            singleLine = true,
            enabled = pendiente == null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            usuario,
            {
                usuario = it
            },
            label = { Text(stringResource(R.string.usuario)) },
            singleLine = true,
            enabled = pendiente == null,
            supportingText = { Text(stringResource(R.string.usuario_reglas)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            clave,
            { clave = it },
            label = { Text(stringResource(R.string.clave)) },
            singleLine = true,
            enabled = pendiente == null,
            supportingText = { Text(stringResource(R.string.clave_reglas)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        TextoError(vm.error)
        vm.estudianteCreado?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = {
                vm.crearEstudiante(nombre, usuario, clave)
                if (pendiente == null) clave = ""
            },
            enabled = !vm.ocupado,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (pendiente !=
                    null
                ) {
                    stringResource(R.string.reintentar)
                } else if (vm.ocupado) {
                    stringResource(R.string.creando)
                } else {
                    stringResource(R.string.crear)
                },
            )
        }
    }
}

@Composable
fun PantallaCambiarClave(vm: AppViewModel) {
    var actual by rememberSaveable { mutableStateOf("") }
    var nueva by rememberSaveable { mutableStateOf("") }
    var repetida by rememberSaveable { mutableStateOf("") }
    Marco(stringResource(R.string.cambiar_mi_clave), alVolver = { vm.volverAlInicio() }) {
        Text(stringResource(R.string.cambiar_clave_ayuda), style = MaterialTheme.typography.bodyMedium)
        CampoClave(actual, { actual = it }, stringResource(R.string.clave_actual))
        CampoClave(nueva, { nueva = it }, stringResource(R.string.clave_nueva), stringResource(R.string.clave_reglas))
        CampoClave(repetida, { repetida = it }, stringResource(R.string.clave_repetir))
        TextoError(vm.error)
        if (vm.claveCambiada) Text(stringResource(R.string.clave_cambiada), color = MaterialTheme.colorScheme.primary)
        Button(
            onClick = { vm.cambiarClave(actual, nueva, repetida) },
            enabled = !vm.ocupado,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (vm.ocupado) R.string.cambiando else R.string.cambiar)) }
    }
}

@Composable
private fun CampoClave(valor: String, alCambiar: (String) -> Unit, etiqueta: String, ayuda: String? = null) {
    OutlinedTextField(
        valor,
        alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = ayuda?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es"))

private fun hora(instante: Instant?) = instante?.let { FORMATO_HORA.format(it) } ?: "—"

private fun fecha(milis: Long) = FORMATO_FECHA.format(Instant.ofEpochMilli(milis))

@Composable
fun PantallaDetalleEstudiante(vm: AppViewModel, destino: Pantalla.DetalleEstudiante) {
    Marco(destino.ficha.nombre, alVolver = { vm.volverAlInicio() }) {
        Text(stringResource(R.string.usuario_es, destino.ficha.usuario))
        TextoError(vm.error)
        val resumen = vm.resumenAbierto
        if (resumen == null) Text(stringResource(R.string.sin_resumen)) else DetalleResumen(resumen)
        HorizontalDivider()
        Text(stringResource(R.string.panel_ultimas), style = MaterialTheme.typography.titleMedium)
        val lista = vm.respuestas
        when {
            lista == null -> CircularProgressIndicator()

            lista.isEmpty() -> Text(stringResource(R.string.sin_respuestas))

            else -> lista.forEach { intento ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${intento.plantilla.substringAfterLast('.')}: «${intento.respuesta}» → ${intento.estado}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                R.string.detalle_intento,
                                intento.pistasUsadas,
                                hora(intento.respondidoEn),
                                hora(intento.subidoEn),
                            ),
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
    Marco(stringResource(R.string.kiwi), alVolver = { vm.volverAlInicio() }) {
        Text(stringResource(R.string.kiwi_ayuda))
        OutlinedTextField(
            direccion,
            { direccion = it },
            label = { Text(stringResource(R.string.direccion)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.probarKiwi(direccion) },
            enabled = vm.estadoKiwi != EstadoKiwi.Probando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.guardar_probar)) }
        when (val estado = vm.estadoKiwi) {
            EstadoKiwi.SinProbar -> Unit

            EstadoKiwi.Probando -> Text(stringResource(R.string.kiwi_buscando))

            EstadoKiwi.Apagado -> Text(
                stringResource(R.string.kiwi_apagado),
                color = MaterialTheme.colorScheme.error,
            )

            EstadoKiwi.NoLocal -> Text(
                stringResource(R.string.kiwi_no_local),
                color = MaterialTheme.colorScheme.error,
            )

            is EstadoKiwi.Activo -> {
                Text(stringResource(R.string.kiwi_activo, estado.modelo), color = MaterialTheme.colorScheme.primary)
                when {
                    estado.saludo != null -> Card(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.kiwi_dice, estado.saludo), Modifier.padding(16.dp))
                    }

                    estado.error != null -> TextoError(estado.error)

                    else -> Text(stringResource(R.string.kiwi_pensando))
                }
            }
        }
    }
}

/** El resumen que subió la tablet del estudiante: tiempo, avance por materia, notas y temas difíciles. */
@Composable
private fun DetalleResumen(r: ResumenPanel) {
    Text(stringResource(R.string.panel_calculado, fecha(r.calculado)), style = MaterialTheme.typography.bodySmall)
    if (r.pendientes.isNotEmpty()) {
        Text(stringResource(R.string.panel_pendientes), style = MaterialTheme.typography.titleMedium)
        r.pendientes.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.enunciado, style = MaterialTheme.typography.bodyMedium)
                    Text("«${p.respuesta}»", style = MaterialTheme.typography.bodyLarge)
                    Text(fecha(p.respondido), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(stringResource(R.string.panel_tiempo), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.panel_semana, r.minutosSemana, r.minutosTotal))
    r.dias.forEach { d ->
        Text(
            stringResource(R.string.panel_dia, FORMATO_DIA.format(LocalDate.parse(d.fecha)), d.minutos),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    r.materias.forEach { m ->
        HorizontalDivider()
        Text(m.nombre, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(
                R.string.panel_materia_avance,
                m.temasHechos,
                m.temasTotal,
                m.seccionesAprobadas,
                m.seccionesTotal,
                m.minutos,
            ),
        )
        val examen = when (m.examen) {
            EstadoNodo.COMPLETADO.valor -> R.string.panel_examen_completado
            EstadoNodo.DISPONIBLE.valor -> R.string.panel_examen_disponible
            EstadoNodo.PENDIENTE.valor -> R.string.panel_examen_pendiente
            else -> R.string.panel_examen_bloqueado
        }
        Text(stringResource(R.string.panel_examen, stringResource(examen)))
        if (m.notas.isNotEmpty()) {
            Text(stringResource(R.string.panel_notas), style = MaterialTheme.typography.titleSmall)
            m.notas.forEach { n ->
                val nombre = if (n.tipo == TipoSesion.EXAMEN_FINAL.valor) {
                    stringResource(R.string.panel_examen_final)
                } else {
                    stringResource(R.string.panel_prueba_seccion, n.seccion.orEmpty())
                }
                val nota = n.nota?.let { valor ->
                    stringResource(
                        R.string.panel_nota_valor,
                        "%.1f".format(valor),
                        stringResource(if (n.aprobada == true) R.string.bien_marca else R.string.mal_marca),
                    )
                } ?: stringResource(R.string.panel_nota_pendiente)
                Text(
                    stringResource(R.string.panel_nota, nombre, fecha(n.fecha), nota),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (m.temas.isNotEmpty()) {
            Text(stringResource(R.string.panel_temas), style = MaterialTheme.typography.titleSmall)
            m.temas.forEach { t ->
                Text(
                    stringResource(R.string.panel_tema, t.titulo, t.fallos, t.respondidos, t.conPista),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
