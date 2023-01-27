package com.adreal.tcp_ip.DataClass

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity("ConnectionTable", primaryKeys = ["userId"], indices = [Index(value = ["ip"], unique = true)])
data class ConnectionData(
    val userId : String,
    val ip : String,
    val port : String,
    val token : String,
    var status : String
)