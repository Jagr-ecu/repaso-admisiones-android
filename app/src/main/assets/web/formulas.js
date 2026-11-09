// Puerto de web/src/lib/texto.ts: texto con fórmulas $...$ y $$...$$ dibujadas con KaTeX.
// La app llama a pintar(texto, modo); la altura se avisa a Kotlin con Android.altura(px).

const FORMULA = /\$\$([\s\S]+?)\$\$|\$([^$\n]+?)\$/g;

function formula(tex, bloque) {
  return katex.renderToString(tex, { displayMode: bloque, throwOnError: false, output: 'html' });
}

function escapar(texto) {
  return texto
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

// Las fórmulas en línea usan \displaystyle: en una tablet, una fracción pequeña cuesta leerla.
function textoConFormulas(texto) {
  let html = '';
  let desde = 0;
  for (const m of texto.matchAll(FORMULA)) {
    html += escapar(texto.slice(desde, m.index));
    html += m[1] !== undefined ? formula(m[1], true) : formula('\\displaystyle ' + m[2], false);
    desde = m.index + m[0].length;
  }
  return html + escapar(texto.slice(desde));
}

// Las fórmulas se sacan antes del Markdown para que sus _ y \ no se confundan con cursivas.
function leccionAHtml(markdown) {
  const formulas = [];
  const sinFormulas = markdown.replace(FORMULA, (_, bloque, enLinea) => {
    formulas.push(formula(bloque ?? enLinea ?? '', bloque !== undefined));
    return '%%F' + (formulas.length - 1) + '%%';
  });
  const html = marked.parse(sinFormulas, { gfm: true, breaks: true, async: false });
  return html.replace(/%%F(\d+)%%/g, (_, i) => formulas[Number(i)]);
}

// Una opción "3/4" se muestra como fracción.
function opcionAHtml(opcion) {
  const fraccion = /^\s*(-?\d+)\s*\/\s*(\d+)\s*$/.exec(opcion);
  if (fraccion) return formula('\\dfrac{' + fraccion[1] + '}{' + fraccion[2] + '}', false);
  return textoConFormulas(opcion);
}

function avisarAltura() {
  if (window.Android) Android.altura(Math.ceil(document.getElementById('c').getBoundingClientRect().height));
}

function pintar(texto, modo) {
  const c = document.getElementById('c');
  c.className = modo;
  c.innerHTML = modo === 'leccion' ? leccionAHtml(texto) : modo === 'opcion' ? opcionAHtml(texto) : textoConFormulas(texto);
  avisarAltura();
  if (document.fonts) document.fonts.ready.then(avisarAltura);
  new ResizeObserver(avisarAltura).observe(c);
}
