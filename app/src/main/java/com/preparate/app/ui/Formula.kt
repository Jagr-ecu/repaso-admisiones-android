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
    val p = paleta()
    val colores = ColoresPagina(p.tinta.toArgb(), p.uva700.toArgb(), p.uva50.toArgb())
    val html = remember(texto, modo, colores, tamano) { pagina(texto, modo, colores, tamano) }
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

/** Los colores de la paleta que usa la página: texto, títulos y fondo de las cuentas. */
private data class ColoresPagina(val tinta: Int, val titulo: Int, val fondoCuenta: Int)

private fun hex(color: Int) = String.format("#%06X", color and 0xFFFFFF)

private fun pagina(texto: String, modo: ModoFormula, colores: ColoresPagina, tamano: Int): String {
    val relleno = if (modo == ModoFormula.LECCION) "12px 20px 48px" else "0"
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="katex.min.css">
        <style>
          /* Nunito, como la web y el resto de la app. */
          @font-face { font-family: 'Nunito'; src: url('nunito.woff2') format('woff2'); font-weight: 200 1000; }
          html, body { margin: 0; padding: 0; background: transparent; color: ${hex(colores.tinta)};
            font-family: 'Nunito', sans-serif; font-weight: 600; font-size: ${tamano}px; line-height: 1.6;
            -webkit-text-size-adjust: none; }
          #c { padding: $relleno; }
          #c.opcion { text-align: center; font-weight: 700; }
          strong { font-weight: 800; color: ${hex(colores.titulo)}; }
          /* Cada bloque de una lección empieza con una pregunta, en violeta. */
          h3 { font-size: 1.25em; font-weight: 900; color: ${hex(colores.titulo)}; margin: 1.3em 0 0.3em; line-height: 1.3; }
          h3:first-child { margin-top: 0.4em; }
          p { margin: 0.4em 0; }
          ol, ul { padding-left: 1.4em; }
          li { margin: 0.2em 0; }
          li::marker { color: ${hex(colores.titulo)}; font-weight: 900; }
          .katex-display { overflow-x: auto; overflow-y: hidden; padding: 6px 0; margin: 8px 0; }
          /* Las cuentas y dibujos de las lecciones van aparte, sobre un fondo suave, como en el cuaderno. */
          #c.leccion .katex-display, #c.leccion .figura {
            background: ${hex(colores.fondoCuenta)}; border-radius: 16px; padding: 16px 12px; margin: 14px 0; }
          #c.leccion .katex-display > .katex { font-size: 1.2em; }
          .figura { display: flex; justify-content: center; }
          .figura svg { max-width: 100%; height: auto; }
        </style>
        <script src="katex.min.js"></script>
        <script src="marked.umd.js"></script>
        <script src="formulas.js"></script>
        </head><body><div id="c"></div>
        <script>pintar(${JSONObject.quote(texto)}, "${modo.css}");</script>
        </body></html>
    """.trimIndent()
}
