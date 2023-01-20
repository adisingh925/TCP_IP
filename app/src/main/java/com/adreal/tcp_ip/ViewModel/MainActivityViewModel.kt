package com.adreal.tcp_ip.ViewModel

import android.os.CountDownTimer
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {

    var receiverIP : String = ""
    var receiverPORT : Int = 0
    var isConnectionEstablished = MutableLiveData<Boolean>()
    lateinit var timer : CountDownTimer
    var tick = MutableLiveData<Long>()

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