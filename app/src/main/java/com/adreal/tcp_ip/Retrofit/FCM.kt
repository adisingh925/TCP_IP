package com.adreal.tcp_ip.Retrofit

import com.adreal.tcp_ip.DataClass.FcmResponse
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface FCM {
    @POST("fcm/send")
    fun sendSignal(@Header("Authorization") apiKey: String,
                   @Body requestBody: RequestBody
    ) : Call<FcmResponse>
}

object SendFcmSignalObject {

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(FCM_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val sendFcmSignal: FCM = retrofit.create(FCM::class.java)
}

const val FCM_BASE_URL = "https://fcm.googleapis.com/"