package com.preparate.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preparate.app.R
import kotlin.random.Random

// --- Botones con relieve ------------------------------------------------------------------

/**
 * Botón grande con "relieve", como `.boton` de la web: un borde inferior más oscuro que se
 * hunde al tocarlo. La altura total no cambia al hundirse, así nada salta en la pantalla.
 */
@Composable
fun BotonRelieve(
    onClick: () -> Unit,
    fondo: Color,
    borde: Color,
    texto: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contorno: Color? = null,
    forma: Shape = RoundedCornerShape(16.dp),
    alto: Dp = 56.dp,
    relleno: Dp = 20.dp,
    /** Sin habilitar se ve pálido; con `atenuar = false` conserva su color (nodos bloqueados del camino). */
    atenuar: Boolean = true,
    contenido: @Composable RowScope.() -> Unit,
) {
    val fuente = remember { MutableInteractionSource() }
    val hundido by fuente.collectIsPressedAsState()
    val relieve = if (hundido && enabled) 2.dp else 4.dp
    Box(
        modifier
            .alpha(if (enabled || !atenuar) 1f else 0.4f)
            .clickable(fuente, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(top = 4.dp - relieve)
            .background(borde, forma)
            .padding(bottom = relieve)
            .background(fondo, forma)
            .let { if (contorno != null) it.border(2.dp, contorno, forma) else it }
            .heightIn(min = alto)
            .padding(horizontal = relleno, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides texto,
            LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(color = texto, fontSize = 18.sp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = contenido,
            )
        }
    }
}

@Composable
fun BotonPrimario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = paleta()
    BotonRelieve(onClick, p.uva500, p.uva700Fijo(), Color.White, modifier, enabled) { Text(texto) }
}

@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = paleta()
    BotonRelieve(onClick, p.tarjeta, p.borde, p.tinta, modifier, enabled, contorno = p.borde) { Text(texto) }
}

@Composable
fun BotonBien(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = paleta()
    // Verde algo más oscuro que bien500: el texto blanco llega al contraste mínimo.
    BotonRelieve(onClick, Color(0xFF0F8A5F), Color(0xFF0A6B49), Color.White, modifier, enabled) { Text(texto) }
}

@Composable
fun BotonMal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = paleta()
    BotonRelieve(onClick, p.mal500, Color(0xFFB42328), Color.White, modifier, enabled) { Text(texto) }
}

/** El borde del botón violeta es siempre el violeta oscuro, también en modo oscuro. */
private fun Paleta.uva700Fijo() = Color(0xFF4424B8)

/**
 * Colores de los campos de texto: fondo de tarjeta y borde violeta al escribir. Ya respondido,
 * lo escrito sigue legible y el borde dice si estuvo bien (verde) o mal (rojo).
 */
@Composable
fun coloresCampo(correcta: Boolean? = null): androidx.compose.material3.TextFieldColors {
    val p = paleta()
    return androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedContainerColor = p.tarjeta,
        unfocusedContainerColor = p.tarjeta,
        disabledContainerColor = when (correcta) {
            true -> p.bien100
            false -> p.mal100
            null -> p.uva50
        },
        unfocusedBorderColor = p.borde,
        focusedBorderColor = p.uva500,
        disabledBorderColor = when (correcta) {
            true -> p.bien500
            false -> p.mal500
            null -> p.borde
        },
        disabledTextColor = p.tinta,
        disabledLabelColor = p.suave,
    )
}

// --- Tarjetas, burbujas y píldoras --------------------------------------------------------

/** Tarjeta blanca con borde suave y esquinas muy redondas (`.tarjeta` de la web). */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    fondo: Color = paleta().tarjeta,
    borde: Color? = paleta().borde,
    onClick: (() -> Unit)? = null,
    relleno: Dp = 20.dp,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(24.dp)
    Column(
        modifier
            .background(fondo, forma)
            .let { if (borde != null) it.border(BorderStroke(2.dp, borde), forma) else it }
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(relleno),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = contenido,
    )
}

/** Lo que dice Kiwi: una tarjeta con un piquito que apunta a la mascota, a la izquierda. */
@Composable
fun Burbuja(texto: String, modifier: Modifier = Modifier) {
    val p = paleta()
    Box(modifier) {
        Canvas(Modifier.size(14.dp, 20.dp).align(Alignment.CenterStart)) {
            val pico = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(pico, p.borde)
        }
        Tarjeta(Modifier.padding(start = 12.dp), relleno = 16.dp) {
            Text(texto, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp))
        }
    }
}

/** Píldora de color con texto en negrita: los puntos ⭐, los corazones, el reloj. */
@Composable
fun Pildora(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier, grande: Boolean = false) {
    Text(
        texto,
        color = color,
        style = if (grande) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
        modifier = modifier
            .background(fondo, CircleShape)
            .padding(horizontal = if (grande) 24.dp else 14.dp, vertical = if (grande) 10.dp else 6.dp),
    )
}

@Composable
fun PildoraPuntos(puntos: Int, modifier: Modifier = Modifier) {
    val p = paleta()
    Pildora(stringResource(R.string.puntos_pildora, puntos), p.sol100, p.sol700, modifier)
}

/** Barra de avance gruesa y redonda, del color que se le pase. */
@Composable
fun BarraAvance(fraccion: Float, color: Color, fondo: Color, modifier: Modifier = Modifier, alto: Dp = 12.dp) {
    Box(modifier.heightIn(min = alto).background(fondo, CircleShape)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = fraccion.coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .background(color, CircleShape),
        )
    }
}

// --- Kiwi ---------------------------------------------------------------------------------

enum class Animo { NORMAL, FELIZ, PENSANDO, ANIMO }

/** Movimiento de Kiwi: flota suave, salta de alegría o se queda quieto. */
enum class Movimiento { QUIETO, FLOTAR, SALTAR }

private val pathCache = HashMap<String, Path>()

private fun ruta(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/**
 * Kiwi, la mascota, dibujado igual que `Mascota.svelte` de la web (lienzo de 120 × 120).
 * Con gorrito de estudiante; los ojos y mejillas cambian según el ánimo.
 */
@Composable
fun Mascota(
    modifier: Modifier = Modifier,
    animo: Animo = Animo.NORMAL,
    tamano: Dp = 96.dp,
    movimiento: Movimiento = Movimiento.QUIETO,
) {
    val medidor = rememberTextMeasurer()
    val transicion = rememberInfiniteTransition(label = "kiwi")
    val desplazamiento by when (movimiento) {
        Movimiento.QUIETO -> remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        Movimiento.FLOTAR -> transicion.animateFloat(
            0f, -6f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flotar",
        )
        Movimiento.SALTAR -> transicion.animateFloat(
            0f, 0f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 1400
                    0f at 0
                    -14f at 240 using FastOutSlowInEasing
                    0f at 600
                    0f at 1400
                },
            ),
            label = "saltar",
        )
    }
    val descripcion = stringResource(R.string.kiwi_mascota)
    Canvas(modifier.size(tamano).semantics { contentDescription = descripcion }) {
        val escala = size.minDimension / 120f
        translate(top = desplazamiento * escala) {
            scale(escala, pivot = Offset.Zero) { dibujarKiwi(animo) { texto, x, y ->
                drawText(
                    medidor, texto, Offset(x, y),
                    TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF6D4AFF)),
                )
            } }
        }
    }
}

private fun DrawScope.dibujarKiwi(animo: Animo, pregunta: DrawScope.(String, Float, Float) -> Unit) {
    val tinta = Color(0xFF2B2350)
    val naranja = Color(0xFFD99400)
    val amarillo = Color(0xFFFFC23D)
    fun trazo(d: String, color: Color, ancho: Float) =
        drawPath(ruta(d), color, style = Stroke(ancho, cap = StrokeCap.Round))
    fun ovalo(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alfa: Float = 1f) =
        drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = alfa)

    ovalo(58f, 110f, 34f, 5f, tinta, 0.12f) // sombra
    trazo("M46 96 l-4 12 M46 108 h-7 M46 108 h6", naranja, 4f) // patas
    trazo("M66 96 l2 12 M68 108 h-7 M68 108 h7", naranja, 4f)
    ovalo(56f, 66f, 40f, 36f, Color(0xFF8A5A3B)) // cuerpo
    ovalo(56f, 74f, 27f, 23f, Color(0xFFC98F62)) // barriga
    // Plumas. La de abajo va al costado: sobre la barriga parecía una boca triste.
    trazo("M24 58 q6 -4 10 2 M18 72 q4 -4 8 1 M82 54 q5 -4 9 2", Color(0xFF6E4329), 3f)
    val pico = ruta("M84 56 Q104 60 116 76 Q100 68 84 66 Z")
    drawPath(pico, amarillo)
    drawPath(pico, naranja, style = Stroke(2f))

    if (animo == Animo.FELIZ) {
        trazo("M58 50 q6 -7 12 0", tinta, 4f)
        trazo("M38 50 q6 -7 12 0", tinta, 4f)
    } else {
        val mirada = if (animo == Animo.PENSANDO) -3f else 0f
        drawCircle(Color.White, 8f, Offset(44f, 50f))
        drawCircle(Color.White, 8f, Offset(66f, 50f))
        drawCircle(tinta, 4.5f, Offset(46f, 51f + mirada))
        drawCircle(tinta, 4.5f, Offset(68f, 51f + mirada))
        drawCircle(Color.White, 1.5f, Offset(47.5f, 49.5f + mirada))
        drawCircle(Color.White, 1.5f, Offset(69.5f, 49.5f + mirada))
    }
    if (animo == Animo.FELIZ || animo == Animo.ANIMO) {
        drawCircle(Color(0xFFF26B5B), 5f, Offset(36f, 62f), alpha = 0.45f)
        drawCircle(Color(0xFFF26B5B), 5f, Offset(76f, 62f), alpha = 0.45f)
    }
    // Gorrito de estudiante
    drawPath(ruta("M34 30 L58 20 L82 30 L58 40 Z"), Color(0xFF6D4AFF))
    drawPath(ruta("M44 34 v8 q14 7 28 0 v-8"), Color(0xFF4424B8))
    trazo("M82 30 v12", amarillo, 3f)
    drawCircle(amarillo, 3f, Offset(82f, 44f))
    if (animo == Animo.PENSANDO) pregunta("?", 96f, 2f)
}

/** Kiwi a la izquierda y lo que dice en una burbuja. */
@Composable
fun KiwiDice(texto: String, modifier: Modifier = Modifier, animo: Animo = Animo.FELIZ, tamano: Dp = 88.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mascota(animo = animo, tamano = tamano, movimiento = Movimiento.FLOTAR)
        Burbuja(texto, Modifier.weight(1f))
    }
}

// --- Confeti ------------------------------------------------------------------------------

private class PapelConfeti(val izquierda: Float, val retraso: Float, val duracion: Float, val giro: Float, val color: Color, val ancho: Float)

private val COLORES_CONFETI = listOf(Color(0xFF6D4AFF), Color(0xFFFFC23D), Color(0xFF17A673), Color(0xFFF26B5B), Color(0xFF2E8FE8))

/** Lluvia de confeti que cae una vez sobre toda la pantalla, como al terminar un tema en la web. */
@Composable
fun Confeti(modifier: Modifier = Modifier.fillMaxSize()) {
    val papeles = remember {
        List(36) { i ->
            PapelConfeti(
                izquierda = Random.nextFloat(),
                retraso = Random.nextFloat() * 0.8f,
                duracion = 2.2f + Random.nextFloat() * 1.6f,
                giro = Random.nextFloat() * 720f - 360f,
                color = COLORES_CONFETI[i % COLORES_CONFETI.size],
                ancho = 8f + Random.nextFloat() * 6f,
            )
        }
    }
    val tiempo = remember { Animatable(0f) }
    LaunchedEffect(Unit) { tiempo.animateTo(4.6f, tween(4600, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = tiempo.value
        for (p in papeles) {
            val avance = ((t - p.retraso) / p.duracion).coerceIn(0f, 1f)
            if (avance <= 0f || avance >= 1f) continue
            val x = p.izquierda * size.width
            val y = -24.dp.toPx() + avance * size.height * 1.1f
            val ancho = p.ancho.dp.toPx()
            val alto = 16.dp.toPx()
            rotate(p.giro * avance, Offset(x + ancho / 2, y + alto / 2)) {
                drawRoundRect(p.color, Offset(x, y), Size(ancho, alto), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
        }
    }
}

/** Contenedor que pone el confeti encima de todo lo demás, sin atrapar los toques. */
@Composable
fun ConConfeti(activo: Boolean, contenido: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        contenido()
        if (activo) Confeti()
    }
}

/** Flecha gruesa y redonda, hacia la izquierda (volver) o hacia la derecha (entrar). */
@Composable
fun Flecha(color: Color, modifier: Modifier = Modifier, izquierda: Boolean = false, tamano: Dp = 28.dp) {
    Canvas(modifier.size(tamano)) {
        val w = size.width
        val trazo = Path().apply {
            if (izquierda) {
                moveTo(w * 0.62f, w * 0.2f); lineTo(w * 0.32f, w * 0.5f); lineTo(w * 0.62f, w * 0.8f)
            } else {
                moveTo(w * 0.38f, w * 0.2f); lineTo(w * 0.68f, w * 0.5f); lineTo(w * 0.38f, w * 0.8f)
            }
        }
        drawPath(
            trazo, color,
            style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/** Círculo de color con un texto o emoji grande en el centro: nodos del camino, marcas ✓ y ✗. */
@Composable
fun Circulo(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier, tamano: Dp = 40.dp) {
    Box(modifier.size(tamano).background(fondo, CircleShape), contentAlignment = Alignment.Center) {
        Text(texto, color = color, style = MaterialTheme.typography.titleMedium.copy(fontSize = (tamano.value * 0.45f).sp))
    }
}
