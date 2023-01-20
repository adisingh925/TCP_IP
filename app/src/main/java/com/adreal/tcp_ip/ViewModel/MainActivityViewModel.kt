package com.adreal.tcp_ip.ViewModel

import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {

    var receiverIP : String = ""
    var receiverPORT : Int = 0
    var isConnectionEstablished = 0
}