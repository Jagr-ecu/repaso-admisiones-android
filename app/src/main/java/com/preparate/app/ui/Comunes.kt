package com.preparate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.preparate.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tablet (pantalla amplia) o celular. Se diseña primero para la tablet, la MatePad en horizontal;
 * en el celular todo va en una columna. Sale de `WindowSizeClass` en MainActivity.
 */
enum class Disposicion { CELULAR, TABLET }

val LocalDisposicion = staticCompositionLocalOf { Disposicion.TABLET }

/**
 * Pantalla baja (menos de 480 dp de alto, como un celular apaisado): el encabezado se achica y
 * la barra de abajo deja fuera lo que no es esencial (Kiwi), para que se vea la pregunta.
 */
val LocalPantallaBaja = staticCompositionLocalOf { false }

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

/**
 * Marco de cada pantalla, con el estilo de la web: fondo crema, encabezado con el botón
 * redondo para volver, el título y lo que vaya a la derecha (los puntos ⭐), y una barra fija
 * abajo (`pie`) para los botones principales, que así siempre quedan a mano.
 */
@Composable
fun Marco(
    titulo: String,
    alVolver: (() -> Unit)? = null,
    desplazable: Boolean = true,
    colorTitulo: Color? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    pie: (@Composable ColumnScope.() -> Unit)? = null,
    colorPie: Color? = null,
    /** Al cambiar este valor (no nulo), el contenido se desliza hasta el final: así se ve la pista nueva. */
    desplazarAlFinal: Any? = null,
    /** Si se pasa uno guardado fuera de la pantalla, al volver a ella se queda donde estaba. */
    desplazamiento: ScrollState? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val p = paleta()
    Surface(Modifier.fillMaxSize(), color = p.fondo) {
        // imePadding: con el teclado abierto la pantalla se achica y se puede llegar a lo de abajo.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
          val baja = maxHeight < 480.dp
          CompositionLocalProvider(LocalPantallaBaja provides baja) {
            Column(Modifier.widthIn(max = anchoMaximo()).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = if (baja) 48.dp else 64.dp).padding(horizontal = 12.dp, vertical = if (baja) 0.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (alVolver != null) {
                        val volver = stringResource(R.string.volver_corto)
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = alVolver)
                                .semantics { contentDescription = volver },
                            contentAlignment = Alignment.Center,
                        ) {
                            Flecha(p.suave, izquierda = true)
                        }
                    }
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleLarge,
                        color = colorTitulo ?: p.tinta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    acciones()
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(p.borde))
                val desplazamiento = desplazamiento ?: rememberScrollState()
                LaunchedEffect(desplazarAlFinal) {
                    if (desplazarAlFinal != null) desplazamiento.animateScrollTo(desplazamiento.maxValue)
                }
                val cuerpo = Modifier.fillMaxWidth().weight(1f).let {
                    if (desplazable) it.verticalScroll(desplazamiento) else it
                }
                Column(
                    cuerpo.padding(horizontal = margen(), vertical = if (baja) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = contenido,
                )
                if (pie != null) {
                    if (colorPie == null) Box(Modifier.fillMaxWidth().height(2.dp).background(p.borde))
                    Column(
                        Modifier.fillMaxWidth()
                            .background(colorPie ?: p.fondo, if (colorPie != null) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RectangleShape)
                            .padding(horizontal = margen(), vertical = if (baja) 8.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (baja) 6.dp else 12.dp),
                        content = pie,
                    )
                }
            }
          }
        }
    }
}

@Composable
fun TextoError(mensaje: String?) {
    if (mensaje != null) Text(mensaje, color = paleta().mal700, style = MaterialTheme.typography.titleSmall)
}
