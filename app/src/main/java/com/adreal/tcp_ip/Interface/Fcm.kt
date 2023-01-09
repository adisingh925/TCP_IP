package com.adreal.tcp_ip.Interface

import com.adreal.tcp_ip.Constants.Constants
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface Fcm {
    @POST("fcm/send")
    fun broadcast(@Body responseBody: ResponseBody)
}

object fcmobject {

    private val retrofit: Retrofit = Retrofit
        .Builder()
        .baseUrl(Constants.FCM_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create()).build()

    val fcmInstance: Fcm = retrofit.create(Fcm::class.java)
}