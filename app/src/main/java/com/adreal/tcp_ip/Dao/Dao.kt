package com.adreal.tcp_ip.Dao

import androidx.lifecycle.LiveData
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adreal.tcp_ip.DataClass.ConnectionData

@androidx.room.Dao
interface Dao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addData(data : ConnectionData)

    @Query("SELECT * from ConnectionTable")
    fun readAllData() : LiveData<List<ConnectionData>>
}