package com.preparate.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preparate.app.R

/**
 * La paleta de la web (`web/src/app.css`): fondo crema, violeta "uva" como color principal,
 * "sol" para puntos y premios, verde y rojo para bien y mal, y un color por materia.
 */
@Immutable
data class Paleta(
    val esOscura: Boolean,
    val fondo: Color,
    val tarjeta: Color,
    val tinta: Color,
    val suave: Color,
    val borde: Color,
    val uva50: Color,
    val uva100: Color,
    val uva500: Color,
    val uva700: Color,
    val sol100: Color,
    val sol400: Color,
    val sol600: Color,
    /** Texto amarillo: el sol600 no llega al contraste mínimo sobre sol100 ni sobre la tarjeta. */
    val sol700: Color,
    val bien100: Color,
    val bien500: Color,
    val bien700: Color,
    val mal100: Color,
    val mal500: Color,
    val mal700: Color,
    /** Lo que todavía no se abre: gris piedra. */
    val piedra300: Color,
    val piedra400: Color,
)

// Los colores fuertes (500 y los de materia) son los mismos en los dos modos.
private val Uva500 = Color(0xFF6D4AFF)
private val Sol400 = Color(0xFFFFC23D)
private val Bien500 = Color(0xFF17A673)
private val Mal500 = Color(0xFFE5484D)

private val PaletaClara = Paleta(
    esOscura = false,
    fondo = Color(0xFFFFF8EE),
    tarjeta = Color.White,
    tinta = Color(0xFF2B2350),
    suave = Color(0xFF6B6489),
    borde = Color(0xFFE8E0D4),
    uva50 = Color(0xFFF4F0FF),
    uva100 = Color(0xFFE9E2FF),
    uva500 = Uva500,
    uva700 = Color(0xFF4424B8),
    sol100 = Color(0xFFFFF1CC),
    sol400 = Sol400,
    sol600 = Color(0xFFD99400),
    sol700 = Color(0xFF8A5A00),
    bien100 = Color(0xFFDCF6EA),
    bien500 = Bien500,
    bien700 = Color(0xFF0C7A52),
    mal100 = Color(0xFFFDE4E4),
    mal500 = Mal500,
    mal700 = Color(0xFFB42328),
    piedra300 = Color(0xFFD6D3D1),
    piedra400 = Color(0xFFA8A29E),
)

/** Modo oscuro: la misma idea con fondos de noche violeta; los textos "700" se aclaran para leerse. */
private val PaletaOscura = Paleta(
    esOscura = true,
    fondo = Color(0xFF1C1830),
    tarjeta = Color(0xFF272142),
    tinta = Color(0xFFF3F0FF),
    suave = Color(0xFFB8B0D8),
    borde = Color(0xFF3D3560),
    uva50 = Color(0xFF2F2752),
    uva100 = Color(0xFF3B3170),
    uva500 = Uva500,
    uva700 = Color(0xFFC4B5FF),
    sol100 = Color(0xFF3F3315),
    sol400 = Sol400,
    sol600 = Color(0xFFFFC23D),
    sol700 = Color(0xFFFFD36B),
    bien100 = Color(0xFF173F31),
    bien500 = Bien500,
    bien700 = Color(0xFF7EE2B8),
    mal100 = Color(0xFF4A1E24),
    mal500 = Mal500,
    mal700 = Color(0xFFFFA8A8),
    piedra300 = Color(0xFF4A4562),
    piedra400 = Color(0xFF34304A),
)

val LocalPaleta = staticCompositionLocalOf { PaletaClara }

/** Atajo: `paleta().uva500`. */
@Composable
fun paleta(): Paleta = LocalPaleta.current

/** Color e ícono de cada materia, como en `web/src/lib/materias.ts`. */
@Immutable
data class ColorMateria(val fondo: Color, val oscuro: Color, val claro: Color, val icono: String) {
    /** Para textos sobre el fondo de la pantalla: el oscuro se lee en crema y el claro en la noche. */
    fun texto(p: Paleta): Color = if (p.esOscura) claro else oscuro
}

fun colorMateria(materiaId: String): ColorMateria = when (materiaId) {
    // Lengua e Inglés algo más oscuros que en la web: el texto blanco encima se lee mejor.
    "lengua" -> ColorMateria(Color(0xFFE85D4C), Color(0xFFC2412F), Color(0xFFFFA597), "📖")
    "ingles" -> ColorMateria(Color(0xFF2580D6), Color(0xFF1A64AD), Color(0xFF8CC4FF), "💬")
    else -> ColorMateria(Uva500, Color(0xFF4424B8), Color(0xFFC4B5FF), "➗")
}

/** Nunito, la letra redondeada de la web. Es una sola fuente variable para todos los grosores. */
@OptIn(ExperimentalTextApi::class)
val Nunito = FontFamily(
    listOf(400, 500, 600, 700, 800, 900).map { peso ->
        Font(R.font.nunito, FontWeight(peso), variationSettings = FontVariation.Settings(FontVariation.weight(peso)))
    },
)

private fun Typography.conNunito(): Typography {
    fun TextStyle.n(peso: FontWeight) = copy(fontFamily = Nunito, fontWeight = peso)
    return copy(
        displayLarge = displayLarge.n(FontWeight.Black),
        displayMedium = displayMedium.n(FontWeight.Black),
        displaySmall = displaySmall.n(FontWeight.Black),
        headlineLarge = headlineLarge.n(FontWeight.Black),
        headlineMedium = headlineMedium.n(FontWeight.Black),
        headlineSmall = headlineSmall.n(FontWeight.Black),
        titleLarge = titleLarge.n(FontWeight.ExtraBold),
        titleMedium = titleMedium.n(FontWeight.ExtraBold),
        titleSmall = titleSmall.n(FontWeight.ExtraBold),
        bodyLarge = bodyLarge.n(FontWeight.SemiBold),
        bodyMedium = bodyMedium.n(FontWeight.SemiBold),
        bodySmall = bodySmall.n(FontWeight.SemiBold),
        labelLarge = labelLarge.n(FontWeight.ExtraBold),
        labelMedium = labelMedium.n(FontWeight.ExtraBold),
        labelSmall = labelSmall.n(FontWeight.Bold),
    )
}

@Composable
fun TemaPreparate(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    val p = if (oscuro) PaletaOscura else PaletaClara
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    val colores = base.copy(
        primary = p.uva500,
        onPrimary = Color.White,
        primaryContainer = p.uva100,
        onPrimaryContainer = p.uva700,
        secondary = p.sol600,
        secondaryContainer = p.sol100,
        onSecondaryContainer = p.tinta,
        tertiary = p.bien500,
        tertiaryContainer = p.bien100,
        onTertiaryContainer = p.tinta,
        background = p.fondo,
        onBackground = p.tinta,
        surface = p.tarjeta,
        onSurface = p.tinta,
        surfaceVariant = p.uva50,
        onSurfaceVariant = p.suave,
        surfaceContainerHigh = p.tarjeta,
        surfaceContainerHighest = p.tarjeta,
        outline = p.borde,
        outlineVariant = p.borde,
        error = p.mal500,
        errorContainer = p.mal100,
        onErrorContainer = p.mal700,
    )
    val formas = Shapes(
        extraSmall = RoundedCornerShape(16.dp), // campos de texto
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp), // tarjetas
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp), // avisos
    )
    CompositionLocalProvider(LocalPaleta provides p) {
        MaterialTheme(colorScheme = colores, typography = Typography().conNunito(), shapes = formas, content = contenido)
    }
}
