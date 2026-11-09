package com.preparate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Ancho máximo del contenido: en la tablet las líneas no se estiran. */
val ANCHO_MAXIMO = 720.dp

/** Marco de cada pantalla: encabezado con "Volver" y contenido centrado con ancho máximo. */
@Composable
fun Marco(
    titulo: String,
    alVolver: (() -> Unit)? = null,
    desplazable: Boolean = true,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ANCHO_MAXIMO).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alVolver != null) TextButton(onClick = alVolver) { Text("‹ Volver") }
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                val cuerpo = Modifier.fillMaxWidth().weight(1f).let {
                    if (desplazable) it.verticalScroll(rememberScrollState()) else it
                }
                Column(
                    cuerpo.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = contenido,
                )
            }
        }
    }
}

@Composable
fun TextoError(mensaje: String?) {
    if (mensaje != null) Text(mensaje, color = MaterialTheme.colorScheme.error)
}
