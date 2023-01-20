package com.adreal.tcp_ip.DataClass

data class ChatModel(
    val isReceived : Int = 0,
    val msg : String,
    val id : Long
)