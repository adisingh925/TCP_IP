package com.adreal.tcp_ip.DataClass

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("ConnectionTable")
data class ConnectionData(
    @PrimaryKey val userId : String,
    val ip : String,
    val port : String,
    val token : String,
    val status : String
)