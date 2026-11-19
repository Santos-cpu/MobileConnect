package com.ejemplo.mobileconnect.data.repository

import android.content.Context
import android.util.Log
import com.ejemplo.mobileconnect.data.local.Entrega
import com.ejemplo.mobileconnect.data.local.EntregaDbHelper
import com.ejemplo.mobileconnect.data.remote.ApiService
import com.ejemplo.mobileconnect.data.remote.EntregaRequestDto
import com.ejemplo.mobileconnect.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResultadoSync(
    val sincronizadas: Int,
    val fallidas: Int
)

class EntregaRepository(
    context: Context,
    private val apiService: ApiService = RetrofitClient.apiService
) {

    private val dbHelper = EntregaDbHelper.getInstance(context)

    companion object {
        private const val TAG = "MobileConnect_Repo"
    }

    suspend fun obtenerTodas(): List<Entrega> = withContext(Dispatchers.IO) {
        dbHelper.obtenerTodas()
    }

    suspend fun obtenerPorId(id: Long): Entrega? = withContext(Dispatchers.IO) {
        dbHelper.obtenerPorId(id)
    }

    /**
     * Guarda siempre en SQLite primero (Offline-First).
     * Si hay red disponible, intenta sincronizar por HTTPS sin bloquear el UI Thread.
     * Devuelve el ID guardado y si quedó sincronizada con el servidor.
     */
    suspend fun guardarOActualizarEntrega(
        entrega: Entrega,
        hayConexion: Boolean
    ): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val entregaLocal = entrega.copy(
            sincronizado = false,
            fechaActualizacion = System.currentTimeMillis()
        )

        val idGuardado = if (entregaLocal.id == 0L) {
            dbHelper.insertar(entregaLocal)
        } else {
            dbHelper.actualizar(entregaLocal)
            entregaLocal.id
        }

        if (!hayConexion || idGuardado <= 0L) {
            return@withContext Pair(idGuardado, false)
        }

        val sincronizada = enviarAlServidor(entregaLocal.copy(id = idGuardado))
        if (sincronizada) {
            dbHelper.marcarComoSincronizada(idGuardado)
        }

        Pair(idGuardado, sincronizada)
    }

    /**
     * Recorre todas las entregas pendientes de subir (sincronizado = false)
     * y las envía al servidor por HTTPS en un hilo de E/S.
     */
    suspend fun sincronizarPendientes(): ResultadoSync = withContext(Dispatchers.IO) {
        val pendientes = dbHelper.obtenerNoSincronizadas()
        var ok = 0
        var error = 0

        for (entrega in pendientes) {
            val subida = enviarAlServidor(entrega)
            if (subida) {
                dbHelper.marcarComoSincronizada(entrega.id)
                ok++
            } else {
                error++
            }
        }

        ResultadoSync(sincronizadas = ok, fallidas = error)
    }

    suspend fun eliminarEntrega(id: Long): Int = withContext(Dispatchers.IO) {
        dbHelper.eliminar(id)
    }

    private suspend fun enviarAlServidor(entrega: Entrega): Boolean {
        return try {
            val payload = EntregaRequestDto(
                title = "${entrega.destinatario} - ${entrega.estado}",
                body = "Dirección: ${entrega.direccion} | Tel: ${entrega.telefono} | Notas: ${entrega.notas}",
                userId = entrega.id
            )
            val response = apiService.enviarEntrega(payload)
            if (response.isSuccessful) {
                Log.d(TAG, "Entrega #${entrega.id} sincronizada por HTTPS (HTTP ${response.code()})")
                true
            } else {
                Log.w(TAG, "Error HTTP ${response.code()} al sincronizar entrega #${entrega.id}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sin conexión o fallo HTTPS al sincronizar entrega #${entrega.id}: ${e.message}")
            false
        }
    }
}