package com.preparate.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

enum class ModoFormula(val css: String) {
    /** Enunciado, pista o solución: una o pocas líneas con fórmulas. */
    TEXTO("texto"),

    /** Una opción de respuesta: "3/4" se muestra como fracción. */
    OPCION("opcion"),

    /** Una lección completa en Markdown; el WebView se desplaza solo. */
    LECCION("leccion"),
}

/**
 * Texto con fórmulas dibujado con KaTeX dentro de un WebView, con los archivos
 * incluidos en la app: funciona sin Internet y se ve igual que en la web.
 *
 * En TEXTO y OPCION la altura se ajusta al contenido y los toques pasan a lo que
 * está debajo (botones y desplazamiento de la pantalla).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Formula(
    texto: String,
    modifier: Modifier = Modifier,
    modo: ModoFormula = ModoFormula.TEXTO,
    tamano: Int = 20,
) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val html = remember(texto, modo, color, tamano) { pagina(texto, modo, color, tamano) }
    var altura by remember(html) { mutableIntStateOf(0) }
    val alCambiarAltura by rememberUpdatedState<(Int) -> Unit> { altura = it }

    val vista = @Composable { mod: Modifier ->
        AndroidView(
            modifier = mod,
            factory = { contexto ->
                WebView(contexto).apply {
                    settings.javaScriptEnabled = true
                    setBackgroundColor(Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = modo == ModoFormula.LECCION
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(mensaje: ConsoleMessage): Boolean {
                            Log.w("Formula", "${mensaje.message()} (${mensaje.sourceId()}:${mensaje.lineNumber()})")
                            return true
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun altura(px: Int) {
                                post { alCambiarAltura(px) }
                            }
                        },
                        "Android",
                    )
                }
            },
            update = { web ->
                if (web.tag != html) {
                    web.tag = html
                    web.loadDataWithBaseURL("file:///android_asset/web/", html, "text/html", "utf-8", null)
                }
            },
        )
    }

    if (modo == ModoFormula.LECCION) {
        vista(modifier)
    } else {
        // 1 px de CSS equivale a 1 dp con viewport width=device-width.
        Box(modifier.fillMaxWidth().height(maxOf(altura, 24).dp)) {
            vista(Modifier.matchParentSize())
            // Capa encima que no consume los toques: así no los atrapa el WebView.
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                },
            )
        }
    }
}

private fun pagina(texto: String, modo: ModoFormula, color: Int, tamano: Int): String {
    val hex = String.format("#%06X", color and 0xFFFFFF)
    val relleno = if (modo == ModoFormula.LECCION) "16px 20px 48px" else "0"
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="katex.min.css">
        <style>
          html, body { margin: 0; padding: 0; background: transparent; color: $hex;
            font-family: sans-serif; font-size: ${tamano}px; line-height: 1.45; -webkit-text-size-adjust: none; }
          #c { padding: $relleno; }
          #c.opcion { text-align: center; }
          .katex-display { overflow-x: auto; overflow-y: hidden; padding: 6px 0; margin: 8px 0; }
          h3 { font-size: 1.2em; margin: 1.2em 0 0.4em; }
          p { margin: 0.4em 0; }
          ol, ul { padding-left: 1.4em; }
        </style>
        <script src="katex.min.js"></script>
        <script src="marked.umd.js"></script>
        <script src="formulas.js"></script>
        </head><body><div id="c"></div>
        <script>pintar(${JSONObject.quote(texto)}, "${modo.css}");</script>
        </body></html>
    """.trimIndent()
}
