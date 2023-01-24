package com.adreal.tcp_ip.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.adreal.tcp_ip.DataClass.ConnectionData
import com.adreal.tcp_ip.Database.Database
import com.adreal.tcp_ip.Repository.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {

    private val readAllData : LiveData<List<ConnectionData>>

    private val repository: Repository

    init {
        val fcmDao = Database.getDatabase(application).connectionDao()
        repository = Repository(fcmDao)
        readAllData = repository.readAllData
    }

    fun addData(data: ConnectionData){
        CoroutineScope(Dispatchers.IO).launch {
            repository.insertData(data)
        }
    }
}