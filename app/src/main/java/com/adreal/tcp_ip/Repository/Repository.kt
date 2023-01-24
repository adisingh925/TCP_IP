package com.adreal.tcp_ip.Repository

import com.adreal.tcp_ip.Dao.Dao
import com.adreal.tcp_ip.DataClass.ConnectionData

class Repository(private val connectionDao: Dao) {

    val readAllData = connectionDao.readAllData()
    fun insertData(data : ConnectionData){
        connectionDao.addData(data)
    }
}