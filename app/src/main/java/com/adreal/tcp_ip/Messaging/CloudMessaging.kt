package com.adreal.tcp_ip.Messaging

import android.util.Log
import com.adreal.tcp_ip.DataClass.ConnectionData
import com.adreal.tcp_ip.Database.Database
import com.adreal.tcp_ip.SharedPreferences.SharedPreferences
import com.google.firebase.messaging.Constants
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CloudMessaging : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM Token",token)
        SharedPreferences.init(this)
        SharedPreferences.write("FcmToken",token)
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM data received",message.toString())

        val userId = message.data["userId"].toString()
        val publicIp = message.data["publicIp"].toString()
        val publicPort = message.data["publicPort"].toString()
        val token = message.data["token"].toString()
        val status = message.data["status"].toString()

        Database.getDatabase(applicationContext).connectionDao().addData(ConnectionData(userId,publicIp,publicPort,token,status))

        super.onMessageReceived(message)
    }
}