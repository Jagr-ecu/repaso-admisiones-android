package com.preparate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preparate.app.AppViewModel
import com.preparate.app.EjercicioUi
import com.preparate.app.EstudioVm
import com.preparate.app.Pantalla
import com.preparate.app.R
import com.preparate.dominio.Celda
import com.preparate.dominio.EstadoNodo
import com.preparate.dominio.EstadoPrueba
import com.preparate.dominio.EstadoRespuesta
import com.preparate.dominio.Estudio
import com.preparate.dominio.Reglas
import com.preparate.dominio.Revision
import com.preparate.dominio.TipoEjercicio
import com.preparate.dominio.TipoSesion
import com.preparate.dominio.TipoVerificador
import com.preparate.dominio.ayudaPermitida
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** Avisos comunes de las pantallas de estudio: motivo de bloqueo o error inesperado. */
@Composable
private fun Avisos(e: EstudioVm) {
    val p = paleta()
    e.bloqueo?.let { Text(stringResource(it), color = p.mal700, style = MaterialTheme.typography.titleSmall) }
    e.error?.let { Text(stringResource(R.string.error_inesperado, it), color = p.mal700, style = MaterialTheme.typography.titleSmall) }
}

// --- Inicio y curso -------------------------------------------------------------------

/** Los cursos del estudiante. Hoy solo Admisiones; más adelante pueden ser varios. */
@Composable
fun InicioEstudiante(vm: AppViewModel, e: EstudioVm, nombre: String, pie: @Composable () -> Unit) {
    // Si Kiwi está encendido, califica los Writing que esperaban su nota.
    LaunchedEffect(Unit) { e.calificarPendientes() }
    val p = paleta()
    Marco(stringResource(R.string.app_name), colorTitulo = p.uva700) {
        KiwiDice(stringResource(R.string.kiwi_hola, nombre))
        Text(stringResource(R.string.tus_cursos), style = MaterialTheme.typography.titleLarge)
        for (curso in e.estudios.cursos()) {
            val puntos by e.puntos(curso.id).collectAsState()
            BotonRelieve(
                onClick = { vm.ir(Pantalla.Curso(curso.id)) },
                fondo = p.uva500, borde = Color(0xFF4424B8), texto = Color.White,
                forma = RoundedCornerShape(24.dp), alto = 0.dp, relleno = 20.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconoMateria("🎒")
                Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(curso.nombre, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    if (curso.descripcion.isNotEmpty()) {
                        Text(curso.descripcion, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                    PildoraPuntos(puntos, Modifier.padding(top = 4.dp))
                }
                Flecha(Color.White, tamano = 32.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        pie()
    }
}

/** Cuadrito translúcido con el ícono de la materia o del curso, como en la web. */
@Composable
private fun IconoMateria(icono: String, tamano: Dp = 64.dp) {
    Box(
        Modifier.size(tamano).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(icono, fontSize = (tamano.value * 0.5f).sp) }
}

/** Las materias de un curso, cada una con su color y su avance. */
@Composable
fun PantallaCurso(vm: AppViewModel, e: EstudioVm, cursoId: String) {
    val curso = remember(cursoId) { e.estudios.curso(cursoId) }
    val progreso by e.progreso(cursoId).collectAsState()
    val puntos by e.puntos(cursoId).collectAsState()
    Marco(curso.nombre, alVolver = { vm.atras() }, acciones = { PildoraPuntos(puntos) }, desplazamiento = e.desplazamiento("curso/$cursoId")) {
        KiwiDice(stringResource(R.string.que_practicar))
        val tablet = columnas() > 1
        EnColumnas(curso.materias, porFila = if (tablet) 3 else 1) { materia ->
            val pm = progreso?.firstOrNull { it.materiaId == materia.id }
            val temas = pm?.secciones?.flatMap { it.lecciones }.orEmpty()
            val hechos = temas.count { it.estado == EstadoNodo.COMPLETADO }
            val c = colorMateria(materia.id)
            BotonRelieve(
                onClick = { e.abrirMateria(cursoId, materia.id) },
                fondo = c.fondo, borde = c.oscuro, texto = Color.White,
                forma = RoundedCornerShape(24.dp), alto = 0.dp, relleno = 20.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val avance = @Composable {
                    BarraAvance(
                        if (temas.isEmpty()) 0f else hechos.toFloat() / temas.size,
                        Color.White, Color.White.copy(alpha = 0.3f), Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.temas_hechos, hechos, temas.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = Color.White,
                    )
                }
                if (tablet) {
                    // En la tablet son tres columnas angostas: ícono arriba y el resto debajo.
                    Column(Modifier.weight(1f).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconoMateria(c.icono, 96.dp)
                        Text(materia.nombre, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        avance()
                    }
                } else {
                    IconoMateria(c.icono)
                    Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(materia.nombre, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        avance()
                    }
                    Flecha(Color.White, tamano = 32.dp)
                }
            }
        }
    }
}

// --- Camino de una materia ------------------------------------------------------------

/** El zigzag del camino, como en la web: cada tema se corre un poco a un lado. */
private val ZIGZAG = listOf(0, 48, 72, 48, 0, -48, -72, -48)

@Composable
fun PantallaMateria(vm: AppViewModel, e: EstudioVm, cursoId: String, materiaId: String) {
    val materia = remember(cursoId, materiaId) { e.estudios.curso(cursoId).materias.single { it.id == materiaId } }
    val progreso by e.progreso(cursoId).collectAsState()
    val puntos by e.puntos(cursoId).collectAsState()
    LaunchedEffect(progreso) { e.refrescarMateria(cursoId, materiaId) }
    val pm = progreso?.firstOrNull { it.materiaId == materiaId }
    val c = colorMateria(materiaId)
    val p = paleta()
    Marco(
        materia.nombre, alVolver = { vm.atras() }, colorTitulo = c.texto(p), acciones = { PildoraPuntos(puntos) },
        desplazamiento = e.desplazamiento("materia/$cursoId/$materiaId"),
    ) {
        Avisos(e)
        if (pm == null) {
            CircularProgressIndicator(color = c.fondo)
            return@Marco
        }
        if (e.vencidos.isNotEmpty()) {
            Tarjeta(Modifier.fillMaxWidth(), fondo = p.sol100, borde = null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Mascota(animo = Animo.ANIMO, tamano = 64.dp, movimiento = Movimiento.FLOTAR)
                    Text(stringResource(R.string.repaso_explicacion), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                BotonPrimario(
                    pluralStringResource(R.plurals.repasar_ahora, e.vencidos.size, e.vencidos.size),
                    { e.empezarRepaso(cursoId, materiaId) }, Modifier.fillMaxWidth(), enabled = !e.ocupado,
                )
            }
        }
        // "¡Aquí vas!" marca el siguiente paso de la primera sección sin aprobar: su tema o su prueba.
        val actual = remember(pm) {
            pm.secciones.filter { it.prueba != EstadoPrueba.APROBADA }.firstNotNullOfOrNull { s ->
                s.lecciones.firstOrNull { it.estado == EstadoNodo.DISPONIBLE }?.leccion?.id
                    ?: if (s.prueba == EstadoPrueba.DISPONIBLE || s.prueba == EstadoPrueba.REPASO_REQUERIDO) "prueba:${s.seccion.id}" else null
            }
        }
        val numeros = pm.secciones.withIndex().associate { (i, s) -> s.seccion.id to i + 1 }
        EnColumnas(pm.secciones) { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val etiqueta = when {
                    s.prueba == EstadoPrueba.APROBADA -> R.string.seccion_completada
                    s.lecciones.any { it.estado == EstadoNodo.COMPLETADO } -> R.string.seccion_en_curso
                    else -> R.string.seccion_sin_empezar
                }
                Column(
                    Modifier.fillMaxWidth().background(c.oscuro, RoundedCornerShape(24.dp)).padding(bottom = 4.dp)
                        .background(c.fondo, RoundedCornerShape(24.dp)).padding(20.dp),
                ) {
                    Text(
                        stringResource(R.string.seccion_n, numeros.getValue(s.seccion.id), stringResource(etiqueta)),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = Color.White,
                    )
                    Text(s.seccion.nombre, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                s.lecciones.forEachIndexed { j, pl ->
                    val abierta = e.abiertas.any { it.tipo == TipoSesion.LECCION && it.leccionId == pl.leccion.id }
                    NodoTema(
                        pl.leccion.titulo, pl.estado, abierta, esActual = pl.leccion.id == actual, color = c,
                        desplazamiento = ZIGZAG[j % ZIGZAG.size].dp,
                    ) { e.abrirTema(cursoId, pl.leccion.id) }
                }
                if (e.depuracion) {
                    TextButton(onClick = { e.depuracionCompletar(cursoId, materiaId, s.seccion.id, aprobar = false) }) {
                        Text("Depuración: dar por hechos los temas")
                    }
                }
                NodoPrueba(
                    s.prueba, e.abiertas.any { it.tipo == TipoSesion.PRUEBA_SECCION && it.seccionId == s.seccion.id },
                    esActual = actual == "prueba:${s.seccion.id}",
                    alEmpezar = { e.empezarPrueba(cursoId, materiaId, s.seccion.id) },
                    alRepasar = { e.empezarRepaso(cursoId, materiaId, s.seccion.id) },
                    ocupado = e.ocupado,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (e.depuracion) {
            TextButton(onClick = { e.depuracionCompletar(cursoId, materiaId, null, aprobar = true) }) {
                Text("Depuración: aprobar todas las secciones")
            }
        }
        NodoExamen(pm.examenFinal, e.abiertas.any { it.tipo == TipoSesion.EXAMEN_FINAL }, e.ocupado) {
            e.empezarExamen(cursoId, materiaId)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Cartelito que flota sobre el paso actual del camino. */
@Composable
private fun AquiVas(color: Color) {
    val p = paleta()
    Text(
        stringResource(R.string.aqui_vas),
        color = color,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
        modifier = Modifier.background(p.tarjeta, RoundedCornerShape(12.dp))
            .border(2.dp, p.borde, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Aro amarillo alrededor del paso actual. */
private fun Modifier.aro(activo: Boolean, color: Color): Modifier =
    if (activo) background(color.copy(alpha = 0.55f), CircleShape).padding(8.dp) else padding(8.dp)

@Composable
private fun NodoTema(
    titulo: String,
    estado: EstadoNodo,
    abierta: Boolean,
    esActual: Boolean,
    color: ColorMateria,
    desplazamiento: Dp,
    alTocar: () -> Unit,
) {
    val p = paleta()
    val bloqueado = estado == EstadoNodo.BLOQUEADO
    val marca = when (estado) {
        EstadoNodo.COMPLETADO -> "✓"
        EstadoNodo.BLOQUEADO -> "🔒"
        else -> "★"
    }
    // Un tema bloqueado no se abre, pero al tocarlo dice por qué.
    var avisoBloqueo by remember { mutableStateOf(false) }
    val tocar = { if (bloqueado) avisoBloqueo = true else alTocar() }
    val nombre = titulo + ": " + stringResource(
        when (estado) {
            EstadoNodo.COMPLETADO -> R.string.tema_hecho
            EstadoNodo.BLOQUEADO -> R.string.tema_bloqueado
            else -> R.string.tema_disponible
        },
    )
    // Todo el nodo es tocable: el niño suele tocar el nombre, que es lo que lee.
    Column(
        Modifier.fillMaxWidth().offset(x = desplazamiento)
            .clickable(role = Role.Button, onClickLabel = null, onClick = tocar)
            .semantics(mergeDescendants = true) { contentDescription = nombre },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (esActual) AquiVas(color.texto(p))
        Box(Modifier.aro(esActual, p.sol400)) {
            BotonRelieve(
                onClick = tocar,
                fondo = if (bloqueado) p.piedra300 else color.fondo,
                borde = if (bloqueado) p.piedra400 else color.oscuro,
                texto = Color.White,
                forma = CircleShape, alto = 0.dp, relleno = 0.dp,
                atenuar = false,
                modifier = Modifier.size(84.dp).clearAndSetSemantics {},
            ) { Text(marca, fontSize = 32.sp, fontWeight = FontWeight.Black) }
        }
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (bloqueado) p.suave else p.tinta,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        val detalle = when {
            abierta -> stringResource(R.string.continuar)
            estado == EstadoNodo.COMPLETADO -> stringResource(R.string.tema_hecho)
            bloqueado -> stringResource(R.string.tema_bloqueado)
            else -> null
        }
        detalle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (abierta) color.texto(p) else p.suave) }
        if (avisoBloqueo) {
            Text(
                stringResource(R.string.bloqueo_tema),
                style = MaterialTheme.typography.bodyMedium, color = p.suave, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

@Composable
private fun NodoPrueba(
    estado: EstadoPrueba,
    abierta: Boolean,
    esActual: Boolean,
    alEmpezar: () -> Unit,
    alRepasar: () -> Unit,
    ocupado: Boolean,
) {
    val p = paleta()
    val accion = when (estado) {
        EstadoPrueba.DISPONIBLE -> alEmpezar
        EstadoPrueba.REPASO_REQUERIDO -> alRepasar
        else -> null
    }
    val nombrePrueba = stringResource(R.string.prueba_de_seccion)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (esActual) AquiVas(p.sol700)
        Box(Modifier.aro(esActual, p.sol400)) {
            BotonRelieve(
                onClick = { accion?.invoke() },
                fondo = when (estado) {
                    EstadoPrueba.APROBADA -> p.sol400
                    EstadoPrueba.BLOQUEADA -> p.piedra300
                    else -> p.sol100
                },
                borde = if (estado == EstadoPrueba.BLOQUEADA) p.piedra400 else p.sol600,
                texto = p.tinta,
                forma = RoundedCornerShape(24.dp), alto = 0.dp, relleno = 0.dp,
                enabled = accion != null && !ocupado, atenuar = false,
                modifier = Modifier.size(84.dp).semantics { contentDescription = nombrePrueba },
            ) { Text("🏆", fontSize = 38.sp) }
        }
        Text(stringResource(R.string.prueba_de_seccion), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when (estado) {
                    EstadoPrueba.BLOQUEADA -> R.string.prueba_bloqueada
                    EstadoPrueba.DISPONIBLE -> R.string.prueba_disponible
                    EstadoPrueba.REPASO_REQUERIDO -> R.string.prueba_repaso
                    EstadoPrueba.PENDIENTE -> R.string.prueba_pendiente
                    EstadoPrueba.APROBADA -> R.string.prueba_aprobada
                },
            ),
            style = MaterialTheme.typography.bodyMedium, color = p.suave, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        when (estado) {
            EstadoPrueba.DISPONIBLE ->
                BotonPrimario(stringResource(if (abierta) R.string.continuar else R.string.empezar), alEmpezar, enabled = !ocupado)
            EstadoPrueba.REPASO_REQUERIDO ->
                BotonPrimario(stringResource(R.string.hacer_repaso), alRepasar, enabled = !ocupado)
            else -> {}
        }
    }
}

@Composable
private fun NodoExamen(estado: EstadoNodo, abierto: Boolean, ocupado: Boolean, alEmpezar: () -> Unit) {
    val p = paleta()
    val puede = estado == EstadoNodo.DISPONIBLE || estado == EstadoNodo.COMPLETADO
    val nombreExamen = stringResource(R.string.examen_final)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BotonRelieve(
            onClick = alEmpezar,
            fondo = if (estado == EstadoNodo.BLOQUEADO) p.piedra300 else p.sol400,
            borde = if (estado == EstadoNodo.BLOQUEADO) p.piedra400 else p.sol600,
            texto = p.tinta,
            forma = CircleShape, alto = 0.dp, relleno = 0.dp,
            enabled = puede && !ocupado, atenuar = false,
            modifier = Modifier.size(104.dp).semantics { contentDescription = nombreExamen },
        ) { Text("🎓", fontSize = 48.sp) }
        Text(stringResource(R.string.examen_final), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                when (estado) {
                    EstadoNodo.BLOQUEADO -> R.string.examen_bloqueado
                    EstadoNodo.DISPONIBLE -> R.string.examen_disponible
                    EstadoNodo.PENDIENTE -> R.string.examen_pendiente
                    EstadoNodo.COMPLETADO -> R.string.examen_aprobado
                },
            ),
            style = MaterialTheme.typography.bodyMedium, color = p.suave, textAlign = TextAlign.Center,
        )
        if (puede) {
            BotonPrimario(stringResource(if (abierto) R.string.continuar else R.string.empezar), alEmpezar, enabled = !ocupado)
        }
    }
}

// --- Explicación del tema -------------------------------------------------------------

@Composable
fun PantallaExplicacion(vm: AppViewModel, e: EstudioVm, cursoId: String, leccionId: String) {
    val leccion = e.leccion?.takeIf { it.id == leccionId } ?: return
    val p = paleta()
    Marco(
        leccion.titulo, alVolver = { vm.atras() }, desplazable = false, colorTitulo = p.uva700,
        pie = {
            Avisos(e)
            BotonPrimario(
                stringResource(if (e.temaAbierto != null) R.string.continuar else R.string.practicar),
                { e.empezarTema(cursoId, leccionId) }, Modifier.fillMaxWidth(), enabled = !e.ocupado,
            )
        },
    ) {
        val forma = RoundedCornerShape(24.dp)
        Box(Modifier.fillMaxWidth().weight(1f).clip(forma).background(p.tarjeta).border(2.dp, p.borde, forma)) {
            Formula(leccion.contenido, Modifier.fillMaxSize(), modo = ModoFormula.LECCION)
        }
    }
}

// --- Ejercicio ------------------------------------------------------------------------

@Composable
fun PantallaEjercicio(vm: AppViewModel, e: EstudioVm) {
    val a = e.actual ?: return
    val sesion = a.ejercicio.sesion
    val p = paleta()
    val titulo = when (sesion.tipo) {
        TipoSesion.LECCION -> e.leccion?.titulo ?: stringResource(R.string.practicar)
        TipoSesion.REPASO -> stringResource(R.string.hacer_repaso)
        TipoSesion.PRUEBA_SECCION -> stringResource(R.string.prueba_de_seccion)
        TipoSesion.EXAMEN_FINAL -> stringResource(R.string.examen_final)
    }
    val resultado = a.resultado
    val calificado = resultado != null && resultado.estado != EstadoRespuesta.PENDIENTE
    Marco(
        titulo,
        alVolver = { vm.atras() },
        colorPie = when {
            resultado == null -> null
            !calificado -> p.uva50
            resultado.correcta -> p.bien100
            else -> p.mal100
        },
        pie = { PieEjercicio(e, a) },
        desplazarAlFinal = if (a.pistas.isEmpty() && a.kiwi.isEmpty()) null
            else Triple(a.ejercicio.intento.id, a.pistas.size, a.kiwi.size),
    ) {
        Encabezado(e, a)
        Formula(a.variante.enunciado, tamano = if (columnas() > 1) 26 else 22)
        Entrada(e, a)
        a.aviso?.let { Text(stringResource(it), color = p.sol700, style = MaterialTheme.typography.titleSmall) }
        Retroalimentacion(a)
        Pistas(a)
        Kiwi(e, a)
        Avisos(e)
    }
}

/**
 * La barra de abajo, como en la web: antes de responder, la pista y "Comprobar"; después, el
 * resultado en verde o rojo con Kiwi, la pista o la solución si toca, y "Siguiente".
 */
@Composable
private fun PieEjercicio(e: EstudioVm, a: EjercicioUi) {
    val p = paleta()
    val baja = LocalPantallaBaja.current
    val resultado = a.resultado
    if (resultado != null) {
        if (resultado.estado == EstadoRespuesta.PENDIENTE) {
            if (a.revision == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!baja) Mascota(animo = Animo.PENSANDO, tamano = 64.dp, movimiento = Movimiento.FLOTAR)
                    Text(
                        stringResource(if (a.kiwiPensando) R.string.writing_revisando else R.string.writing_guardado),
                        style = MaterialTheme.typography.titleMedium, color = p.uva700, modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            val bien = resultado.correcta
            val color = if (bien) p.bien700 else p.mal700
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!baja) {
                    Mascota(
                        animo = if (bien) Animo.FELIZ else Animo.ANIMO, tamano = 64.dp,
                        movimiento = if (bien) Movimiento.SALTAR else Movimiento.QUIETO,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (baja) 2.dp else 4.dp)) {
                    Text(
                        stringResource(if (bien) R.string.muy_bien else R.string.todavia_no),
                        style = if (baja) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, color = color,
                    )
                    if (resultado.estado == EstadoRespuesta.FORMA_INCORRECTA) {
                        Text(stringResource(R.string.casi_simplificar), style = MaterialTheme.typography.titleSmall, color = color)
                    }
                    resultado.errorComun?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = color) }
                    if (!bien && !baja) {
                        Text(
                            stringResource(if (a.tipo == TipoSesion.LECCION) R.string.equivocarse else R.string.equivocarse_prueba),
                            style = MaterialTheme.typography.bodyMedium, color = color,
                        )
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonPista(e, a)
        val listo = Modifier.weight(1f)
        when {
            resultado == null -> BotonPrimario(
                stringResource(if (a.practica) R.string.comprobar else R.string.enviar),
                { e.comprobar() }, listo, enabled = a.listo && !e.ocupado,
            )
            resultado.estado == EstadoRespuesta.PENDIENTE ->
                BotonPrimario(stringResource(R.string.siguiente), { e.siguiente() }, listo, enabled = !e.ocupado)
            resultado.correcta -> BotonBien(stringResource(R.string.siguiente), { e.siguiente() }, listo, enabled = !e.ocupado)
            else -> BotonMal(stringResource(R.string.siguiente), { e.siguiente() }, listo, enabled = !e.ocupado)
        }
    }
}

/** Pedir la pista siguiente, o la solución cuando ya se vieron todas (solo en la práctica, regla 3). */
@Composable
private fun BotonPista(e: EstudioVm, a: EjercicioUi) {
    if (!a.practica) return
    if (a.resultado?.correcta == true) return // ya acertó: la pista solo distraería de "Siguiente"
    val maximo = a.variante.pistas.size
    val vistas = a.pistas.maxOfOrNull { it.nivel } ?: 0
    val solucionPosible = a.respondido && a.resultado?.correcta == false && !a.variante.solucion.isNullOrEmpty() &&
        Estudio.solucionPermitida(a.tipo, a.ejercicio.intento.repite != null)
    when {
        vistas < maximo -> BotonSecundario(
            stringResource(if (vistas == 0) R.string.pedir_pista else R.string.otra_pista), { e.pedirPista() }, enabled = !e.ocupado,
        )
        vistas == maximo && solucionPosible ->
            BotonSecundario(stringResource(R.string.ver_solucion), { e.pedirPista() }, enabled = !e.ocupado)
    }
}

@Composable
private fun Encabezado(e: EstudioVm, a: EjercicioUi) {
    val p = paleta()
    val estado = a.estado
    val sesion = estado.sesion
    if (!a.practica) {
        BarraAvance(a.ejercicio.numero.toFloat() / estado.total.coerceAtLeast(1), p.uva500, p.uva100, Modifier.fillMaxWidth(), alto = 14.dp)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pildora(
            if (a.practica) stringResource(R.string.pregunta_n, a.ejercicio.numero)
            else stringResource(R.string.pregunta_n_de, a.ejercicio.numero, estado.total),
            p.uva50, p.uva700,
        )
        if (a.ejercicio.intento.repite != null) Pildora(stringResource(R.string.otra_vez), p.sol100, p.sol700)
        estado.errores?.let { errores ->
            val quedan = (Reglas.ERRORES_PERMITIDOS_TEMA - errores).coerceAtLeast(0)
            if (quedan > 0) Pildora(pluralStringResource(R.plurals.vidas, quedan, quedan), p.uva50, p.suave)
            else Pildora(stringResource(R.string.ultimo_error), p.mal100, p.mal700)
        }
        Estudio.limite(sesion)?.let { Reloj(it) { e.tiempoAgotado() } }
    }
}

/** Cuenta atrás del examen. Al llegar a cero, la app cierra el examen y muestra la nota. */
@Composable
private fun Reloj(limite: Instant, alAcabar: () -> Unit) {
    val p = paleta()
    var restantes by remember(limite) { mutableLongStateOf(Duration.between(Instant.now(), limite).seconds) }
    LaunchedEffect(limite) {
        while (true) {
            restantes = Duration.between(Instant.now(), limite).seconds
            if (restantes <= 0) {
                alAcabar()
                break
            }
            delay(1_000)
        }
    }
    val r = restantes.coerceAtLeast(0)
    val poco = r < 120
    Pildora(
        "⏱ " + stringResource(R.string.tiempo_restante, (r / 60).toInt(), (r % 60).toInt()),
        if (poco) p.mal100 else p.sol100, if (poco) p.mal700 else p.sol700,
    )
}

@Composable
private fun Entrada(e: EstudioVm, a: EjercicioUi) {
    val v = a.variante
    val estiloCampo = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)
    val correcta = a.resultado?.takeIf { it.estado != EstadoRespuesta.PENDIENTE }?.correcta
    // En la tablet un número no necesita 1000 dp de campo: queda perdido a la izquierda.
    val anchoCampo = if (columnas() > 1) Modifier.widthIn(max = 480.dp).fillMaxWidth() else Modifier.fillMaxWidth()
    when {
        v.plantilla.tipo == TipoEjercicio.CUADERNO -> Cuaderno(e, a)
        v.plantilla.tipo == TipoEjercicio.ORDENAR -> Ordenar(e, a)
        v.opciones.isNotEmpty() -> Opciones(e, a)
        v.plantilla.tipo == TipoEjercicio.ESCRITURA -> OutlinedTextField(
            value = a.entrada,
            onValueChange = e::escribir,
            label = { Text(stringResource(R.string.tu_texto)) },
            enabled = !a.respondido,
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
            colors = coloresCampo(correcta),
            modifier = Modifier.fillMaxWidth(),
        )
        else -> {
            val numero = v.plantilla.verificador == TipoVerificador.SYMPY
            OutlinedTextField(
                value = a.entrada,
                onValueChange = e::escribir,
                label = { Text(stringResource(R.string.tu_respuesta)) },
                placeholder = if (numero) ({ Text(stringResource(R.string.ejemplo_numero)) }) else null,
                singleLine = true,
                enabled = !a.respondido,
                textStyle = estiloCampo,
                colors = coloresCampo(correcta),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numero) KeyboardType.Ascii else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { e.comprobar() }),
                modifier = anchoCampo,
            )
        }
    }
}

@Composable
private fun Opciones(e: EstudioVm, a: EjercicioUi) {
    val p = paleta()
    // Las opciones largas (oraciones enteras) van una por fila, para que se lean bien.
    val porFila = if (a.variante.opciones.any { it.length > 18 }) 1 else 2
    val bien = a.resultado?.correcta
    a.variante.opciones.chunked(porFila).forEach { fila ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { opcion ->
                val elegida = a.entrada == opcion
                // Solo se marca la elegida: la correcta no se revela, porque se vuelve a preguntar (regla 10).
                val (fondo, borde) = when {
                    elegida && bien == true -> p.bien100 to p.bien500
                    elegida && bien == false -> p.mal100 to p.mal500
                    elegida -> p.uva50 to p.uva500
                    else -> p.tarjeta to p.borde
                }
                BotonRelieve(
                    onClick = { e.escribir(opcion) },
                    fondo = fondo, borde = borde, texto = p.tinta, contorno = borde,
                    enabled = !a.respondido, atenuar = !elegida,
                    alto = 64.dp, relleno = 12.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    Formula(opcion, modo = ModoFormula.OPCION, tamano = if (porFila == 1) 20 else 22)
                }
            }
            if (fila.size < porFila) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Ordenar(e: EstudioVm, a: EjercicioUi) {
    val p = paleta()
    val fragmentos = a.variante.fragmentos
    Text(stringResource(R.string.ordenar_ayuda), style = MaterialTheme.typography.bodyMedium, color = p.suave)
    Tarjeta(Modifier.fillMaxWidth().heightIn(min = 96.dp), fondo = p.uva50, borde = p.uva100, relleno = 12.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.orden.forEachIndexed { posicion, i ->
                BotonRelieve(
                    onClick = { e.quitarFragmento(posicion) }, fondo = p.uva500, borde = Color(0xFF4424B8), texto = Color.White,
                    enabled = !a.respondido, atenuar = false, alto = 44.dp, relleno = 14.dp,
                    modifier = Modifier.widthIn(min = 56.dp),
                ) { Text(fragmentos[i]) }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fragmentos.forEachIndexed { i, palabra ->
            BotonRelieve(
                onClick = { e.tocarFragmento(i) }, fondo = p.tarjeta, borde = p.borde, texto = p.tinta, contorno = p.borde,
                enabled = !a.respondido && i !in a.orden, alto = 44.dp, relleno = 14.dp,
                modifier = Modifier.widthIn(min = 56.dp),
            ) { Text(palabra) }
        }
    }
}

/** La cuenta en columna: cifras impresas y casillas para una cifra cada una. */
@Composable
private fun Cuaderno(e: EstudioVm, a: EjercicioUi) {
    val p = paleta()
    val cuenta = a.variante.cuenta ?: return
    val grupoMal = a.resultado?.grupoMal
    // Al escribir una cifra se pasa a la casilla siguiente en el orden real de la cuenta.
    val focos = remember(a.ejercicio.intento.id) { List(cuenta.casillas) { FocusRequester() } }
    val foco = LocalFocusManager.current
    val alEscribir: (Int, String) -> Unit = { n, texto ->
        e.escribirCasilla(n, texto)
        if (texto.any(Char::isDigit)) {
            if (n + 1 < focos.size) focos[n + 1].requestFocus() else foco.clearFocus()
        }
    }
    Text(stringResource(R.string.cuaderno_ayuda), style = MaterialTheme.typography.bodyMedium, color = p.suave)
    Tarjeta(Modifier.fillMaxWidth(), relleno = 12.dp) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            for (fila in cuenta.filas) {
                fila.etiqueta?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = p.suave) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row { fila.celdas.forEach { Celda(a, it, grupoMal, focos, alEscribir) } }
                    if (fila.derecha.isNotEmpty()) {
                        Box(Modifier.width(2.dp).height(44.dp).background(p.tinta))
                        Column {
                            Row { fila.derecha.forEach { Celda(a, it, grupoMal, focos, alEscribir) } }
                        }
                    }
                }
                if (fila.linea) {
                    Box(Modifier.width((fila.celdas.size * 44).dp).height(2.dp).background(p.tinta))
                }
            }
        }
    }
    if (grupoMal != null) Text(stringResource(R.string.cuaderno_paso_mal), color = p.mal700, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Celda(a: EjercicioUi, celda: Celda, grupoMal: Int?, focos: List<FocusRequester>, alEscribir: (Int, String) -> Unit) {
    val p = paleta()
    val caja = Modifier.size(44.dp).padding(2.dp)
    val estilo = TextStyle(fontFamily = Nunito, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = p.tinta)
    when {
        celda.valor != null -> Box(caja, contentAlignment = Alignment.Center) {
            Text(
                celda.valor!!, style = estilo, fontWeight = if (celda.marca) FontWeight.Black else FontWeight.Bold,
                color = if (celda.marca) p.uva500 else p.tinta,
            )
        }
        celda.casilla != null -> {
            val n = celda.casilla!!
            val mal = grupoMal != null && celda.grupo == grupoMal
            val borde = if (mal) p.mal500 else p.uva500
            BasicTextField(
                value = a.casillas[n].orEmpty(),
                onValueChange = { alEscribir(n, it) },
                enabled = !a.respondido,
                singleLine = true,
                textStyle = estilo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = caja.focusRequester(focos[n])
                    .background(if (mal) p.mal100 else p.uva50, RoundedCornerShape(8.dp))
                    .border(if (mal) 3.dp else 2.dp, borde, RoundedCornerShape(8.dp)).padding(top = 6.dp),
            )
        }
        celda.nota -> {
            // Casilla libre para lo que se lleva: no se califica ni se guarda.
            var nota by remember { mutableStateOf("") }
            BasicTextField(
                value = nota,
                onValueChange = { nota = it.filter(Char::isDigit).takeLast(1) },
                textStyle = estilo.copy(fontSize = 14.sp, color = p.suave),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = caja.border(1.dp, p.borde, RoundedCornerShape(8.dp)).padding(top = 10.dp),
            )
        }
        else -> Box(caja)
    }
}

/** Lo que va en la pantalla después de responder; el veredicto va en la barra de abajo. */
@Composable
private fun Retroalimentacion(a: EjercicioUi) {
    val p = paleta()
    val resultado = a.resultado ?: return
    if (resultado.estado == EstadoRespuesta.PENDIENTE) {
        a.revision?.let { TarjetaRevision(it) }
        return
    }
    // Al acertar lo que había fallado, se explica por qué: pudo acertar por descarte.
    if (resultado.correcta && a.ejercicio.intento.repite != null) {
        a.variante.solucion?.let {
            Tarjeta(Modifier.fillMaxWidth(), fondo = p.bien100, borde = null, relleno = 16.dp) {
                Text(stringResource(R.string.por_que), style = MaterialTheme.typography.titleMedium, color = p.bien700)
                Formula(it, tamano = 18)
            }
        }
    }
}

@Composable
private fun Pistas(a: EjercicioUi) {
    if (!a.practica) return // en pruebas y exámenes no hay pistas (regla 3)
    val p = paleta()
    for (pista in a.pistas) {
        val esPista = pista.nivel <= a.variante.pistas.size
        Tarjeta(Modifier.fillMaxWidth(), fondo = p.sol100, borde = null, relleno = 16.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (esPista) "💡" else "📝", fontSize = 26.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (esPista) stringResource(R.string.pista_n, pista.nivel) else stringResource(R.string.solucion),
                        style = MaterialTheme.typography.titleMedium, color = p.uva700,
                    )
                    Formula(pista.texto, tamano = 18)
                }
            }
        }
    }
}

/** La nota de un Writing: la puso Kiwi con la rúbrica y la sumó el código. */
@Composable
fun TarjetaRevision(r: Revision) {
    val p = paleta()
    val aprobado = r.puntaje >= Reglas.NOTA_MINIMA_APROBAR
    Tarjeta(Modifier.fillMaxWidth(), fondo = if (aprobado) p.bien100 else p.sol100, borde = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mascota(animo = if (aprobado) Animo.FELIZ else Animo.ANIMO, tamano = 56.dp)
            Text(
                stringResource(R.string.writing_nota, numero(r.puntaje)),
                style = MaterialTheme.typography.headlineSmall, color = if (aprobado) p.bien700 else p.tinta,
            )
        }
        if (r.comentario.isNotEmpty()) Text(r.comentario, style = MaterialTheme.typography.bodyLarge)
        for (c in r.criterios) {
            Text(
                stringResource(R.string.writing_criterio, c.criterio, numero(c.puntos), numero(c.maximo)),
                style = MaterialTheme.typography.titleSmall,
            )
            if (c.comentario.isNotEmpty()) Text(c.comentario, style = MaterialTheme.typography.bodyMedium, color = p.suave)
        }
    }
}

private fun numero(x: Double): String = if (x == Math.floor(x)) x.toLong().toString() else "%.1f".format(x)

/**
 * Kiwi en la práctica (nunca en pruebas ni exámenes, regla 3): lo que dijo tras un fallo o al pedir una
 * pista, y una caja para contestarle. Solo aparece si falló o en un Writing (regla 9). La charla vive
 * en memoria: al salir del ejercicio se pierde.
 */
@Composable
private fun Kiwi(e: EstudioVm, a: EjercicioUi) {
    if (!ayudaPermitida(a.tipo)) return
    val p = paleta()
    for (m in a.kiwi) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, if (m.deKiwi) Alignment.Start else Alignment.End),
            verticalAlignment = Alignment.Top,
        ) {
            if (m.deKiwi) Mascota(animo = Animo.NORMAL, tamano = 44.dp)
            Tarjeta(
                Modifier.widthIn(max = 560.dp).padding(start = if (m.deKiwi) 0.dp else 48.dp, end = if (m.deKiwi) 48.dp else 0.dp),
                fondo = if (m.deKiwi) p.uva50 else p.tarjeta,
                borde = if (m.deKiwi) null else p.borde,
                relleno = 14.dp,
            ) {
                Text(
                    when {
                        !m.deKiwi -> stringResource(R.string.kiwi_tu)
                        m.pista != null -> stringResource(R.string.kiwi_pista_n, m.pista)
                        else -> stringResource(R.string.kiwi)
                    },
                    style = MaterialTheme.typography.labelLarge, color = p.uva700,
                )
                if (m.bloqueado) Text(stringResource(R.string.kiwi_bloqueado)) else Formula(m.texto.orEmpty(), tamano = 18)
            }
        }
    }
    if (a.kiwiPensando && a.resultado?.estado != EstadoRespuesta.PENDIENTE) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mascota(animo = Animo.PENSANDO, tamano = 44.dp, movimiento = Movimiento.FLOTAR)
            Text(stringResource(R.string.kiwi_pensando), color = p.uva700, style = MaterialTheme.typography.titleSmall)
        }
    }
    if (a.kiwiDescansa && !a.kiwiPensando && a.resultado?.estado != EstadoRespuesta.PENDIENTE) {
        Text(stringResource(R.string.kiwi_descansa), style = MaterialTheme.typography.bodyMedium, color = p.suave)
    }
    // Contestarle a Kiwi: tras un fallo o en un Writing ya revisado.
    val puedeHablar = a.respondido && !a.kiwiDescansa && a.kiwi.isNotEmpty() &&
        (a.resultado?.correcta == false || a.revision != null)
    if (puedeHablar) {
        var texto by rememberSaveable(a.ejercicio.intento.id) { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text(stringResource(R.string.kiwi_escribele)) },
                colors = coloresCampo(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { e.escribirAKiwi(texto); texto = "" }),
                modifier = Modifier.weight(1f),
            )
            BotonPrimario(
                stringResource(R.string.kiwi_enviar), { e.escribirAKiwi(texto); texto = "" },
                enabled = texto.isNotBlank() && !a.kiwiPensando,
            )
        }
    }
}

// --- Fin de un tema o repaso ----------------------------------------------------------

@Composable
fun PantallaFinPractica(vm: AppViewModel, e: EstudioVm) {
    val fin = e.fin ?: return
    val p = paleta()
    val c = fin.cierre
    val festejo = c.completada
    Box(Modifier.fillMaxSize()) {
        Marco(
            "", alVolver = { vm.atras() },
            pie = {
                Avisos(e)
                if (fin.tipo == TipoSesion.LECCION && !c.completada) {
                    BotonPrimario(
                        stringResource(R.string.volver_a_empezar), { e.reiniciarTema(fin.cursoId, fin.leccionId!!) },
                        Modifier.fillMaxWidth(), enabled = !e.ocupado,
                    )
                    BotonSecundario(stringResource(R.string.volver_al_camino), { e.abrirMateria(fin.cursoId, fin.materiaId) }, Modifier.fillMaxWidth())
                } else {
                    BotonPrimario(stringResource(R.string.volver_al_camino), { e.abrirMateria(fin.cursoId, fin.materiaId) }, Modifier.fillMaxWidth())
                }
            },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Mascota(
                    animo = if (festejo) Animo.FELIZ else Animo.ANIMO, tamano = 170.dp,
                    movimiento = if (festejo) Movimiento.SALTAR else Movimiento.FLOTAR,
                )
                if (fin.tipo == TipoSesion.LECCION) {
                    if (c.completada) {
                        Titular(stringResource(R.string.tema_terminado))
                        if (c.puntos > 0) {
                            Pildora(stringResource(R.string.mas_puntos, c.puntos), p.sol100, p.sol700, grande = true)
                        } else {
                            Text(stringResource(R.string.ya_tenias_puntos), style = MaterialTheme.typography.titleMedium, color = p.suave, textAlign = TextAlign.Center)
                        }
                    } else {
                        Titular(stringResource(R.string.buen_trabajo))
                        Text(stringResource(R.string.tema_perdido), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                } else {
                    Titular(stringResource(if (c.completada) R.string.repaso_terminado else R.string.repaso_incompleto))
                }
            }
        }
        if (festejo) Confeti()
    }
}

@Composable
private fun Titular(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
        color = paleta().uva700,
        textAlign = TextAlign.Center,
    )
}

// --- Resultado de una prueba ----------------------------------------------------------

@Composable
fun PantallaResultado(vm: AppViewModel, e: EstudioVm) {
    val r = e.resultado ?: return
    val p = paleta()
    val sesion = r.cierre.sesion
    val aprobada = r.cierre.aprobada == true
    Box(Modifier.fillMaxSize()) {
        Marco(
            stringResource(if (sesion.tipo == TipoSesion.EXAMEN_FINAL) R.string.examen_final else R.string.prueba_de_seccion),
            alVolver = { vm.atras() },
            pie = {
                BotonSecundario(stringResource(R.string.volver_al_camino), { e.abrirMateria(sesion.cursoId, sesion.materiaId) }, Modifier.fillMaxWidth())
            },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (r.cierre.pendiente) {
                    Mascota(animo = Animo.PENSANDO, tamano = 140.dp, movimiento = Movimiento.FLOTAR)
                    Text(stringResource(R.string.nota_pendiente), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Mascota(
                            animo = if (aprobada) Animo.FELIZ else Animo.ANIMO, tamano = 130.dp,
                            movimiento = if (aprobada) Movimiento.SALTAR else Movimiento.FLOTAR,
                        )
                        val nota = "%.2f".format(r.cierre.nota ?: 0.0).trimEnd('0').trimEnd(',', '.')
                        Column(
                            Modifier.size(128.dp).background(if (aprobada) p.bien500 else p.mal500, CircleShape),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(nota, style = MaterialTheme.typography.displaySmall, color = Color.White)
                            Text(stringResource(R.string.de_10), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                    }
                    Text(
                        stringResource(if (aprobada) R.string.aprobaste else R.string.reprobaste),
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                        color = if (aprobada) p.bien700 else p.tinta,
                    )
                }
            }
            if (r.repasar.isNotEmpty()) {
                Tarjeta(Modifier.fillMaxWidth(), fondo = p.sol100, borde = null) {
                    Text(stringResource(R.string.repasa_estos), style = MaterialTheme.typography.titleMedium)
                    val curso = e.estudios.curso(sesion.cursoId)
                    val titulos = curso.materias.flatMap { m -> m.secciones.flatMap { it.lecciones } }.associate { it.concepto to it.titulo }
                    r.repasar.forEach { Text("• ${titulos[it] ?: it}", style = MaterialTheme.typography.bodyLarge) }
                    BotonPrimario(
                        stringResource(R.string.hacer_repaso), { e.empezarRepaso(sesion.cursoId, sesion.materiaId, sesion.seccionId) },
                        Modifier.fillMaxWidth(), enabled = !e.ocupado,
                    )
                }
            }
            Avisos(e)
            for (pr in r.preguntas) {
                Tarjeta(Modifier.fillMaxWidth(), relleno = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (pr.intento.correcta) {
                            true -> Circulo("✓", p.bien500, Color.White)
                            false -> Circulo("✗", p.mal500, Color.White)
                            null -> Circulo("…", p.uva100, p.uva700)
                        }
                        Text(stringResource(R.string.pregunta_n, pr.numero), style = MaterialTheme.typography.titleMedium)
                    }
                    Formula(pr.variante.enunciado, tamano = 18)
                    Text(
                        pr.intento.respuesta?.let { stringResource(R.string.respondiste, it) } ?: stringResource(R.string.sin_responder),
                        style = MaterialTheme.typography.titleSmall,
                        color = when (pr.intento.correcta) {
                            true -> p.bien700
                            false -> p.mal700
                            null -> p.suave
                        },
                    )
                    pr.intento.revision?.let { TarjetaRevision(Revision.desdeJson(it)) }
                    pr.intento.errorComun?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.suave) }
                    if (pr.intento.correcta != true) {
                        pr.variante.respuesta?.let {
                            Text(stringResource(R.string.respuesta_correcta, it), style = MaterialTheme.typography.titleSmall, color = p.bien700)
                        }
                        pr.variante.solucion?.let { Formula(it, tamano = 16) }
                    }
                }
            }
        }
        if (aprobada && !r.cierre.pendiente) Confeti()
    }
}
