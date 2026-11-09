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
import com.preparate.app.ui.PantallaKiwi
import com.preparate.app.ui.PantallaLeccion
import com.preparate.app.ui.PantallaPractica
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
    BackHandler(enabled = pantalla != Pantalla.Inicio && pantalla != Pantalla.Entrar) { vm.volverAlInicio() }
    when (pantalla) {
        Pantalla.Entrar -> PantallaEntrar(vm)
        Pantalla.Inicio -> PantallaInicio(vm)
        Pantalla.Leccion -> PantallaLeccion(vm)
        Pantalla.Practica -> PantallaPractica(vm)
        Pantalla.Kiwi -> PantallaKiwi(vm)
        Pantalla.CrearEstudiante -> PantallaCrearEstudiante(vm)
        is Pantalla.DetalleEstudiante -> PantallaDetalleEstudiante(vm, pantalla)
    }
}
