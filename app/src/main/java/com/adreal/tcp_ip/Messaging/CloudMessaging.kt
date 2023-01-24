package com.adreal.tcp_ip.Messaging

import android.util.Log
import com.google.firebase.messaging.Constants
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CloudMessaging : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM Token",token)
        FirebaseMessaging.getInstance().subscribeToTopic(com.adreal.tcp_ip.Constants.Constants.FCM_TOPIC).addOnCompleteListener(){
            if(it.isSuccessful){
                Log.d("Fcm subscribe","success")
            }else{
                Log.d("Fcm subscribe","failed")
            }
        }
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM data received",message.toString())
        super.onMessageReceived(message)
    }
}