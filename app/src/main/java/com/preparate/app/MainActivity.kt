package com.preparate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.preparate.app.ui.PantallaCrearEstudiante
import com.preparate.app.ui.PantallaDetalleEstudiante
import com.preparate.app.ui.PantallaEntrar
import com.preparate.app.ui.PantallaInicio
import com.preparate.app.ui.PantallaCurso
import com.preparate.app.ui.PantallaEjercicio
import com.preparate.app.ui.PantallaExplicacion
import com.preparate.app.ui.PantallaFinPractica
import com.preparate.app.ui.PantallaKiwi
import com.preparate.app.ui.PantallaMateria
import com.preparate.app.ui.PantallaResultado
import com.preparate.app.ui.TemaPreparate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaPreparate {
                App(viewModel())
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel) {
    val pantalla = vm.pantalla
    BackHandler(enabled = pantalla != Pantalla.Inicio && pantalla != Pantalla.Entrar) { vm.atras() }
    val estudio = vm.estudio
    when (pantalla) {
        Pantalla.Entrar -> PantallaEntrar(vm)
        Pantalla.Inicio -> PantallaInicio(vm)
        Pantalla.Kiwi -> PantallaKiwi(vm)
        Pantalla.CrearEstudiante -> PantallaCrearEstudiante(vm)
        is Pantalla.DetalleEstudiante -> PantallaDetalleEstudiante(vm, pantalla)
        else -> if (estudio == null) PantallaInicio(vm) else when (pantalla) {
            is Pantalla.Curso -> PantallaCurso(vm, estudio, pantalla.cursoId)
            is Pantalla.Materia -> PantallaMateria(vm, estudio, pantalla.cursoId, pantalla.materiaId)
            is Pantalla.Explicacion -> PantallaExplicacion(vm, estudio, pantalla.cursoId, pantalla.leccionId)
            is Pantalla.Ejercicio -> PantallaEjercicio(vm, estudio)
            Pantalla.FinPractica -> PantallaFinPractica(vm, estudio)
            is Pantalla.Resultado -> PantallaResultado(vm, estudio)
            else -> PantallaInicio(vm)
        }
    }
}
