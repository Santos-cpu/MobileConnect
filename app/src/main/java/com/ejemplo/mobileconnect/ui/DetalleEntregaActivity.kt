package com.ejemplo.mobileconnect.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ejemplo.mobileconnect.data.local.Entrega
import com.ejemplo.mobileconnect.data.repository.EntregaRepository
import com.ejemplo.mobileconnect.databinding.ActivityDetalleEntregaBinding
import kotlinx.coroutines.launch

class DetalleEntregaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleEntregaBinding
    private lateinit var repository: EntregaRepository

    private var entregaId: Long = 0L
    private var entregaActual: Entrega? = null
    private var smsPendienteTelefono: String? = null
    private var smsPendienteMensaje: String? = null

    companion object {
        const val EXTRA_ENTREGA_ID = "extra_entrega_id"
        private const val TAG = "MobileConnect_Detalle"
    }

    private val solicitarPermisoSms = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        val telefono = smsPendienteTelefono
        val mensaje = smsPendienteMensaje
        smsPendienteTelefono = null
        smsPendienteMensaje = null

        if (telefono != null && mensaje != null) {
            if (concedido) {
                enviarSmsDirecto(telefono, mensaje)
            } else {
                Toast.makeText(
                    this,
                    "Permiso SMS denegado. Abriendo aplicación de mensajes...",
                    Toast.LENGTH_SHORT
                ).show()
                abrirAppMensajesFallback(telefono, mensaje)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "DetalleEntregaActivity -> onCreate()")

        enableEdgeToEdge()
        binding = ActivityDetalleEntregaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.detalleMain) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = EntregaRepository(this)
        entregaId = intent.getLongExtra(EXTRA_ENTREGA_ID, 0L)

        if (entregaId > 0L) {
            cargarEntrega(entregaId)
        } else {
            configurarModoNuevaEntrega()
        }

        configurarBotones()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DetalleEntregaActivity -> onDestroy()")
    }

    private fun configurarModoNuevaEntrega() {
        binding.tvTituloDetalle.text = "Nueva entrega"
        binding.btnEliminar.visibility = View.GONE
        actualizarBadges(Entrega.ESTADO_PENDIENTE, sincronizado = false)
    }

    private fun cargarEntrega(id: Long) {
        lifecycleScope.launch {
            val entrega = repository.obtenerPorId(id)
            if (entrega == null) {
                Toast.makeText(this@DetalleEntregaActivity, "Entrega no encontrada", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            entregaActual = entrega
            binding.tvTituloDetalle.text = "Entrega #${entrega.id}"
            binding.etDestinatario.setText(entrega.destinatario)
            binding.etDireccion.setText(entrega.direccion)
            binding.etTelefono.setText(entrega.telefono)
            binding.etNotas.setText(entrega.notas)
            binding.btnEliminar.visibility = View.VISIBLE

            actualizarBadges(entrega.estado, entrega.sincronizado)
        }
    }

    private fun configurarBotones() {
        binding.btnConfirmarYSms.setOnClickListener {
            guardarEntrega(marcarEntregada = true, enviarSms = true)
        }

        binding.btnGuardar.setOnClickListener {
            guardarEntrega(marcarEntregada = false, enviarSms = false)
        }

        binding.btnSoloSms.setOnClickListener {
            val destinatario = binding.etDestinatario.text.toString().trim()
            val telefono = binding.etTelefono.text.toString().trim()
            if (telefono.isEmpty()) {
                binding.etTelefono.error = "Introduce un teléfono válido"
                return@setOnClickListener
            }
            val nombre = destinatario.ifEmpty { "cliente" }
            val mensaje = "Mobile Connect: Hola $nombre, tu repartidor está en camino con tu pedido."
            gestionarEnvioSms(telefono, mensaje)
        }

        binding.btnEliminar.setOnClickListener {
            confirmarEliminacion()
        }
    }

    private fun guardarEntrega(marcarEntregada: Boolean, enviarSms: Boolean) {
        val destinatario = binding.etDestinatario.text.toString().trim()
        val direccion = binding.etDireccion.text.toString().trim()
        val telefono = binding.etTelefono.text.toString().trim()
        val notas = binding.etNotas.text.toString().trim()

        if (destinatario.isEmpty()) {
            binding.etDestinatario.error = "Obligatorio"
            return
        }
        if (direccion.isEmpty()) {
            binding.etDireccion.error = "Obligatorio"
            return
        }
        if (telefono.isEmpty()) {
            binding.etTelefono.error = "Obligatorio"
            return
        }

        val nuevoEstado = if (marcarEntregada) {
            Entrega.ESTADO_ENTREGADA
        } else {
            entregaActual?.estado ?: Entrega.ESTADO_PENDIENTE
        }

        val entregaAGuardar = Entrega(
            id = entregaActual?.id ?: 0L,
            destinatario = destinatario,
            direccion = direccion,
            telefono = telefono,
            estado = nuevoEstado,
            notas = notas,
            sincronizado = false
        )

        lifecycleScope.launch {
            mostrarCargando(true)
            val hayRed = hayConexionDisponible()
            val (idGuardado, sincronizada) = repository.guardarOActualizarEntrega(entregaAGuardar, hayRed)
            mostrarCargando(false)

            if (idGuardado <= 0L) {
                Toast.makeText(this@DetalleEntregaActivity, "Error guardando en SQLite", Toast.LENGTH_SHORT).show()
                return@launch
            }

            entregaId = idGuardado
            entregaActual = entregaAGuardar.copy(id = idGuardado, sincronizado = sincronizada)
            actualizarBadges(nuevoEstado, sincronizada)
            binding.btnEliminar.visibility = View.VISIBLE

            val msgSync = if (sincronizada) {
                "Guardado local y sincronizado por HTTPS"
            } else {
                "Guardado en BD local (pendiente de sincronizar)"
            }
            Toast.makeText(this@DetalleEntregaActivity, msgSync, Toast.LENGTH_LONG).show()

            if (enviarSms) {
                val textoSms = "Mobile Connect: Hola $destinatario, confirmamos que tu entrega en $direccion ha sido completada."
                gestionarEnvioSms(telefono, textoSms)
            } else {
                finish()
            }
        }
    }

    private fun gestionarEnvioSms(telefono: String, mensaje: String) {
        val tienePermiso = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (tienePermiso) {
            enviarSmsDirecto(telefono, mensaje)
        } else {
            smsPendienteTelefono = telefono
            smsPendienteMensaje = mensaje
            solicitarPermisoSms.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun enviarSmsDirecto(telefono: String, mensaje: String) {
        try {
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                abrirAppMensajesFallback(telefono, mensaje)
                return
            }

            val partes = smsManager.divideMessage(mensaje)
            if (partes.size > 1) {
                smsManager.sendMultipartTextMessage(telefono, null, partes, null, null)
            } else {
                smsManager.sendTextMessage(telefono, null, mensaje, null, null)
            }

            Log.d(TAG, "SMS de confirmación enviado a $telefono")
            Toast.makeText(this, "SMS de confirmación enviado a $telefono", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando SMS directo: ${e.message}")
            Toast.makeText(
                this,
                "No se pudo enviar directo. Abriendo app de SMS...",
                Toast.LENGTH_SHORT
            ).show()
            abrirAppMensajesFallback(telefono, mensaje)
        }
    }

    private fun abrirAppMensajesFallback(telefono: String, mensaje: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$telefono")
            putExtra("sms_body", mensaje)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No hay aplicación de SMS instalada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmarEliminacion() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar entrega")
            .setMessage("¿Seguro que quieres eliminar esta entrega de la base de datos local?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    repository.eliminarEntrega(entregaId)
                    Toast.makeText(this@DetalleEntregaActivity, "Entrega eliminada", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun actualizarBadges(estado: String, sincronizado: Boolean) {
        binding.tvDetalleEstado.text = estado
        val colorEstado = if (estado == Entrega.ESTADO_ENTREGADA) "#2E7D32" else "#EF6C00"
        binding.tvDetalleEstado.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorEstado))

        if (sincronizado) {
            binding.tvDetalleSync.text = "Sincronizado HTTPS"
            binding.tvDetalleSync.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        } else {
            binding.tvDetalleSync.text = "Solo local (pendiente)"
            binding.tvDetalleSync.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressGuardar.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnConfirmarYSms.isEnabled = !cargando
        binding.btnGuardar.isEnabled = !cargando
        binding.btnSoloSms.isEnabled = !cargando
    }

    private fun hayConexionDisponible(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}