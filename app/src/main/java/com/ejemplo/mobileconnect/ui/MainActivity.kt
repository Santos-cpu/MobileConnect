package com.ejemplo.mobileconnect.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejemplo.mobileconnect.data.repository.EntregaRepository
import com.ejemplo.mobileconnect.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: EntregaRepository
    private lateinit var adapter: EntregaAdapter
    private lateinit var connectivityManager: ConnectivityManager

    private var hayConexion: Boolean = false
    private var networkCallbackRegistrado: Boolean = false
    private var syncJob: Job? = null

    companion object {
        private const val TAG_LIFECYCLE = "MobileConnect_Lifecycle"
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG_LIFECYCLE, "Red recuperada -> iniciando sincronización automática")
            lifecycleScope.launch {
                hayConexion = true
                actualizarIndicadorRed(true)
                sincronizarConServidor(automatica = true)
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG_LIFECYCLE, "Conexión perdida -> pasando a modo Offline")
            lifecycleScope.launch {
                hayConexion = comprobarConexionActual()
                actualizarIndicadorRed(hayConexion)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG_LIFECYCLE, "MainActivity -> onCreate()")

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = EntregaRepository(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        configurarRecyclerView()
        configurarBotones()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG_LIFECYCLE, "MainActivity -> onStart() [Registrando monitor de red]")
        hayConexion = comprobarConexionActual()
        actualizarIndicadorRed(hayConexion)
        registrarMonitorRed()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_LIFECYCLE, "MainActivity -> onResume() [Cargando datos desde SQLite]")
        cargarEntregasLocales()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG_LIFECYCLE, "MainActivity -> onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG_LIFECYCLE, "MainActivity -> onStop() [Liberando monitor de red y tareas para ahorrar batería]")
        desregistrarMonitorRed()
        syncJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_LIFECYCLE, "MainActivity -> onDestroy()")
    }

    private fun configurarRecyclerView() {
        adapter = EntregaAdapter { entrega ->
            val intent = Intent(this, DetalleEntregaActivity::class.java).apply {
                putExtra(DetalleEntregaActivity.EXTRA_ENTREGA_ID, entrega.id)
            }
            startActivity(intent)
        }

        binding.rvEntregas.layoutManager = LinearLayoutManager(this)
        binding.rvEntregas.adapter = adapter
    }

    private fun configurarBotones() {
        binding.fabNuevaEntrega.setOnClickListener {
            val intent = Intent(this, DetalleEntregaActivity::class.java)
            startActivity(intent)
        }

        binding.btnSincronizar.setOnClickListener {
            if (!comprobarConexionActual()) {
                actualizarIndicadorRed(false)
                Toast.makeText(
                    this,
                    "Sin cobertura. Los datos siguen a salvo en la BD local.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                sincronizarConServidor(automatica = false)
            }
        }
    }

    private fun cargarEntregasLocales() {
        lifecycleScope.launch {
            val entregas = repository.obtenerTodas()
            adapter.actualizarLista(entregas)

            binding.tvVacio.visibility = if (entregas.isEmpty()) View.VISIBLE else View.GONE

            val pendientesSync = entregas.count { !it.sincronizado }
            binding.tvResumenSync.text = if (pendientesSync == 0) {
                "Todas las entregas (${entregas.size}) están sincronizadas"
            } else {
                "$pendientesSync entrega(s) pendiente(s) de sincronizar"
            }
        }
    }

    private fun sincronizarConServidor(automatica: Boolean) {
        if (syncJob?.isActive == true) return

        syncJob = lifecycleScope.launch {
            binding.progressSync.visibility = View.VISIBLE
            binding.btnSincronizar.isEnabled = false

            val resultado = repository.sincronizarPendientes()

            binding.progressSync.visibility = View.GONE
            binding.btnSincronizar.isEnabled = true

            cargarEntregasLocales()

            if (!automatica || resultado.sincronizadas > 0) {
                val mensaje = when {
                    resultado.sincronizadas == 0 && resultado.fallidas == 0 ->
                        "Todo ya estaba sincronizado"
                    resultado.fallidas == 0 ->
                        "Sincronización HTTPS completada (${resultado.sincronizadas} subidas)"
                    else ->
                        "Subidas: ${resultado.sincronizadas} | Pendientes por fallo de red: ${resultado.fallidas}"
                }
                Toast.makeText(this@MainActivity, mensaje, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registrarMonitorRed() {
        if (networkCallbackRegistrado) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistrado = true
        } catch (e: Exception) {
            Log.e(TAG_LIFECYCLE, "Error registrando NetworkCallback: ${e.message}")
        }
    }

    private fun desregistrarMonitorRed() {
        if (!networkCallbackRegistrado) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            networkCallbackRegistrado = false
        } catch (e: Exception) {
            Log.e(TAG_LIFECYCLE, "Error liberando NetworkCallback: ${e.message}")
        }
    }

    private fun comprobarConexionActual(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun actualizarIndicadorRed(online: Boolean) {
        if (online) {
            binding.tvEstadoRed.text = "ONLINE"
            binding.tvEstadoRed.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        } else {
            binding.tvEstadoRed.text = "OFFLINE"
            binding.tvEstadoRed.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#C62828"))
        }
    }
}