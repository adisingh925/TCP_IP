package com.adreal.tcp_ip.DataClass

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("ConnectionTable", primaryKeys = ["userId","ip"])
data class ConnectionData(
    @ColumnInfo("userId") val userId : String,
    @ColumnInfo("ip") val ip : String,
    val port : String,
    val token : String,
    var status : String
)