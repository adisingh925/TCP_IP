package com.adreal.tcp_ip.ViewModel

import android.os.CountDownTimer
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.adreal.tcp_ip.DataClass.ChatModel

class MainActivityViewModel : ViewModel() {

    var receiverIP : String = ""
    var receiverPORT : Int = 0
    var isConnectionEstablished = MutableLiveData<Boolean>()
    lateinit var timer : CountDownTimer
    var tick = MutableLiveData<Long>()
    val chatData = ArrayList<ChatModel>()
    val chatList = MutableLiveData<ArrayList<ChatModel>>()
    var mode : Int = 1

    fun timer() {
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tick.postValue(millisUntilFinished)
            }

            override fun onFinish() {
                tick.postValue(0)
            }
        }
        timer.start()
    }
}