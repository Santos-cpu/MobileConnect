package com.ejemplo.mobileconnect.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ejemplo.mobileconnect.data.local.Entrega
import com.ejemplo.mobileconnect.databinding.ItemEntregaBinding

class EntregaAdapter(
    private var entregas: List<Entrega> = emptyList(),
    private val onEntregaClick: (Entrega) -> Unit
) : RecyclerView.Adapter<EntregaAdapter.EntregaViewHolder>() {

    inner class EntregaViewHolder(private val binding: ItemEntregaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entrega: Entrega) {
            binding.tvDestinatario.text = entrega.destinatario
            binding.tvDireccion.text = entrega.direccion
            binding.tvTelefono.text = "Tel: ${entrega.telefono}"

            // Estado de la entrega (Pendiente / Entregada)
            binding.tvEstadoEntrega.text = entrega.estado
            val colorEstado = if (entrega.estado == Entrega.ESTADO_ENTREGADA) {
                Color.parseColor("#2E7D32")
            } else {
                Color.parseColor("#EF6C00")
            }
            binding.tvEstadoEntrega.backgroundTintList = ColorStateList.valueOf(colorEstado)

            // Estado de sincronización con el servidor HTTPS
            if (entrega.sincronizado) {
                binding.tvEstadoSync.text = "Sincronizado"
                binding.tvEstadoSync.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#2E7D32"))
            } else {
                binding.tvEstadoSync.text = "Solo local (pendiente)"
                binding.tvEstadoSync.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#C62828"))
            }

            binding.root.setOnClickListener {
                onEntregaClick(entrega)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntregaViewHolder {
        val binding = ItemEntregaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EntregaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntregaViewHolder, position: Int) {
        holder.bind(entregas[position])
    }

    override fun getItemCount(): Int = entregas.size

    fun actualizarLista(nuevaLista: List<Entrega>) {
        entregas = nuevaLista
        notifyDataSetChanged()
    }
}