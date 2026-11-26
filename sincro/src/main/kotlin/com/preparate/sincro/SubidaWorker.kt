package com.preparate.sincro

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Sube la cola cuando hay red, aunque la app esté cerrada, y reintenta si falla. Solo sube lo del
 * estudiante con la sesión iniciada. Los cambios no se pierden nunca: siguen en Room hasta subir.
 */
class SubidaWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        if (Cuentas.rolGuardado(applicationContext, uid) != Rol.ESTUDIANTE) return Result.success()
        val sincro = Sincronizacion(applicationContext)
        return try {
            sincro.subir(uid)
            // Si algo entró justo al terminar, se vuelve a intentar en un rato.
            if (sincro.pendientes(uid).first() > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NOMBRE = "subir-progreso"

        /** Pide subir la cola. Si ya hay una subida esperando, no se agrega otra. */
        fun programar(contexto: Context) {
            val pedido = OneTimeWorkRequestBuilder<SubidaWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(contexto).enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.KEEP, pedido)
        }
    }
}
