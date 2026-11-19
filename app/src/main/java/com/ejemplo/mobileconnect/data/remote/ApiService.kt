package com.ejemplo.mobileconnect.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("posts")
    suspend fun enviarEntrega(@Body entrega: EntregaRequestDto): Response<EntregaResponseDto>
}