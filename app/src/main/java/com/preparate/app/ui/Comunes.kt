package com.preparate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.preparate.app.R

/**
 * Tablet (pantalla amplia) o celular. Se diseña primero para la tablet, la MatePad en horizontal;
 * en el celular todo va en una columna. Sale de `WindowSizeClass` en MainActivity.
 */
enum class Disposicion { CELULAR, TABLET }

val LocalDisposicion = staticCompositionLocalOf { Disposicion.TABLET }

/** Ancho máximo del contenido: en la tablet las líneas no se estiran de lado a lado. */
@Composable
fun anchoMaximo(): Dp = if (LocalDisposicion.current == Disposicion.TABLET) 1000.dp else 720.dp

@Composable
fun margen(): Dp = if (LocalDisposicion.current == Disposicion.TABLET) 32.dp else 16.dp

/** Cuántas columnas caben: dos en la tablet, una en el celular. */
@Composable
fun columnas(): Int = if (LocalDisposicion.current == Disposicion.TABLET) 2 else 1

/** Reparte los elementos en filas de `columnas()` tarjetas del mismo ancho. */
@Composable
fun <T> EnColumnas(elementos: List<T>, porFila: Int = columnas(), contenido: @Composable (T) -> Unit) {
    elementos.chunked(porFila).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            fila.forEach { Box(Modifier.weight(1f)) { contenido(it) } }
            repeat(porFila - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Marco de cada pantalla: encabezado con "Volver" y contenido centrado con ancho máximo. */
@Composable
fun Marco(
    titulo: String,
    alVolver: (() -> Unit)? = null,
    desplazable: Boolean = true,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // imePadding: con el teclado abierto la pantalla se achica y se puede llegar a lo de abajo.
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = anchoMaximo()).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alVolver != null) TextButton(onClick = alVolver) { Text(stringResource(R.string.volver)) }
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
                    cuerpo.padding(horizontal = margen(), vertical = 8.dp),
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
