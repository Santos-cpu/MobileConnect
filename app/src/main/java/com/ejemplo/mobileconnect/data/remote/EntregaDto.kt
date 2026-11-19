package com.ejemplo.mobileconnect.data.remote

data class EntregaRequestDto(
    val title: String,
    val body: String,
    val userId: Long
)

data class EntregaResponseDto(
    val id: Int,
    val title: String?,
    val body: String?,
    val userId: Long?
)