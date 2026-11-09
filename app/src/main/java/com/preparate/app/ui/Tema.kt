package com.preparate.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta propia, provisional hasta portar la de la web.
private val Verde = Color(0xFF2E7D32)
private val VerdeClaro = Color(0xFF81C784)

@Composable
fun TemaPreparate(contenido: @Composable () -> Unit) {
    val colores = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = VerdeClaro)
    } else {
        lightColorScheme(primary = Verde)
    }
    MaterialTheme(colorScheme = colores, content = contenido)
}
