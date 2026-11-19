package com.ejemplo.mobileconnect.data.local

data class Entrega(
    val id: Long = 0L,
    val destinatario: String,
    val direccion: String,
    val telefono: String,
    val estado: String = ESTADO_PENDIENTE,
    val notas: String = "",
    val sincronizado: Boolean = false,
    val fechaActualizacion: Long = System.currentTimeMillis()
) {
    companion object {
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_ENTREGADA = "ENTREGADA"
    }
}