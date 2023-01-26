package com.adreal.tcp_ip

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Contacts.People
import android.provider.Settings
import android.provider.Settings.*
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.adreal.tcp_ip.Adapter.ChatAdapter
import com.adreal.tcp_ip.Adapter.PeopleAdapter
import com.adreal.tcp_ip.Connection.ConnectionLiveData
import com.adreal.tcp_ip.Constants.Constants
import com.adreal.tcp_ip.DataClass.ChatModel
import com.adreal.tcp_ip.DataClass.ConnectionData
import com.adreal.tcp_ip.SharedPreferences.SharedPreferences
import com.adreal.tcp_ip.ViewModel.DatabaseViewModel
import com.adreal.tcp_ip.ViewModel.MainActivityViewModel
import com.adreal.tcp_ip.databinding.ActivityMainBinding
import com.adreal.tcp_ip.databinding.ConfigureBinding
import com.adreal.tcp_ip.databinding.PeopleLayoutBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.System
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*


class MainActivity : AppCompatActivity(), PeopleAdapter.OnItemClickListener {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val dialog by lazy {
        Dialog(this)
    }

    private val mainActivityViewModel by lazy {
        ViewModelProvider(this)[MainActivityViewModel::class.java]
    }

    private val adapter by lazy {
        ChatAdapter(this)
    }

    private val recyclerView by lazy {
        binding.recyclerView
    }

    private val databaseViewModel by lazy {
        ViewModelProvider(this)[DatabaseViewModel::class.java]
    }

    private val userDialog by lazy {
        Dialog(this)
    }

    private val connectionLiveData by lazy {
        ConnectionLiveData(this)
    }

    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream

    companion object {
        const val PORT = 60001
        const val TCP_PORT = 50001
        const val GOOGLE_STUN_SERVER_IP = "74.125.197.127"
        const val GOOGLE_STUN_SERVER_PORT = 19302
        const val CONNECTION_ESTABLISH_STRING = "$@6%9*4!&2#0"
        const val STUNTMAN_STUN_SERVER_IP = "18.191.223.12"
        const val STUNTMAN_STUN_SERVER_PORT_TCP = 3478
        const val TIMER_TIME: Long = 3600000
        const val TOPIC_DESTINATION = "/topics/${Constants.FCM_TOPIC}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme()
        setContentView(binding.root)

        SharedPreferences.init(this)

        mainActivityViewModel.receiverData()

        binding.mainActivityPrivateCredentials.text = "${mainActivityViewModel.getIPAddress(true)} : $PORT"

        if(mainActivityViewModel.isDataInitialized == 0){
            connectionLiveData.observe(this){

                binding.mainActivityPeopleButton.isEnabled = false
                binding.mainActivityConfigureButton.isEnabled = false

                if(it){
                    Log.d("Main Activity","Online")
                    mainActivityViewModel.sendBindingRequest()
                    binding.mainActivityPrivateCredentials.text = "${mainActivityViewModel.getIPAddress(true)} : $PORT"
                }else{
                    Log.d("Main Activity","Offline")
                }
            }

            mainActivityViewModel.isDataInitialized = 1
        }

        mainActivityViewModel.isConnectionTimerFinished.observe(this){
            if(it){
                Log.d("Connection Timer","finished")
                binding.mainActivityLinesrProgressIndicator.isVisible = mainActivityViewModel.isProgressBarVisible
                binding.mainActivityLinesrProgressIndicator.isIndeterminate = mainActivityViewModel.isProgressBarVisible
                mainActivityViewModel.disconnectedTimer()
            }
        }

        mainActivityViewModel.isDisconnectedTimerFinished.observe(this){
            if(it){
                Toast.makeText(this,"Connection Terminated...",Toast.LENGTH_SHORT).show()

                mainActivityViewModel.isProgressBarVisible = false
                binding.mainActivityLinesrProgressIndicator.isVisible = mainActivityViewModel.isProgressBarVisible

                if(mainActivityViewModel.isTimerRunning.value == true){
                    mainActivityViewModel.isTimerRunning.postValue(false)
                    mainActivityViewModel.timer.cancel()
                }

                mainActivityViewModel.isEditTextEnabled = false
                mainActivityViewModel.isButtonEnabled = false

                binding.mainActivityUDPClientEditText.isEnabled = mainActivityViewModel.isEditTextEnabled
                binding.mainActivityUDPClientButton.isEnabled = mainActivityViewModel.isButtonEnabled
            }
        }

        mainActivityViewModel.isUdpRetryTimerFinished.observe(this){
            mainActivityViewModel.sendBindingRequest()
            binding.mainActivityPrivateCredentials.text = "${mainActivityViewModel.getIPAddress(true)} : $PORT"
        }

        mainActivityViewModel.isTcpRetryTimerFinished.observe(this){
            mainActivityViewModel.sendTcpBindingRequest()
            binding.mainActivityPrivateCredentials.text = "${mainActivityViewModel.getIPAddress(true)} : $PORT"
        }

        databaseViewModel.readAllData.observe(this){
            Log.d("people data activity",it.toString())
            for(i in it){
                if(i.status == "2"){
                    initiateConnection(i.ip,i.port,null)
                    mainActivityViewModel.token = i.token
                    i.status = "1"
                    databaseViewModel.addData(i)
                }
            }
        }

        binding.mainActivityPeopleButton.setOnClickListener {
            showUserDialog()
        }

        if (SharedPreferences.read("UserId", "null") == "null") {
            val uuid = Secure.getString(contentResolver, Secure.ANDROID_ID)
            SharedPreferences.write("UserId", uuid)
            Log.d("Device Id", uuid)
        }

        mainActivityViewModel.isTimerFinished.observe(this) {
            Log.d("reinitializing timer", "reinitialized")
            mainActivityViewModel.timer(TIMER_TIME)
        }

        binding.mainActivityUDPClientEditText.isEnabled = mainActivityViewModel.isEditTextEnabled
        binding.mainActivityUDPClientButton.isEnabled = mainActivityViewModel.isButtonEnabled
        binding.mainActivityLinesrProgressIndicator.isVisible = mainActivityViewModel.isProgressBarVisible

        initDialog()
        initUserDialog()
        initRecycler()

        mainActivityViewModel.isConnectionEstablished.observe(this@MainActivity) {
            if (mainActivityViewModel.isObserverNeeded) {
                if (it) {
                    Log.d("connection", "established")
                    Toast.makeText(this, "Connection Established", Toast.LENGTH_SHORT).show()

                    mainActivityViewModel.isProgressBarVisible = false
                    mainActivityViewModel.isButtonEnabled = true
                    mainActivityViewModel.isEditTextEnabled = true

                    binding.mainActivityLinesrProgressIndicator.isVisible = !(mainActivityViewModel.isProgressBarVisible)
                    binding.mainActivityLinesrProgressIndicator.isIndeterminate = mainActivityViewModel.isProgressBarVisible
                    binding.mainActivityLinesrProgressIndicator.setProgressCompat(100, true)

                    binding.mainActivityUDPClientEditText.isEnabled = mainActivityViewModel.isEditTextEnabled
                    binding.mainActivityUDPClientButton.isEnabled = mainActivityViewModel.isButtonEnabled
                }

                mainActivityViewModel.isObserverNeeded = false
            }
        }

        mainActivityViewModel.chatList.observe(this) {
            adapter.setData(it)
            recyclerView.scrollToPosition(it.size - 1)
        }

        binding.mainActivityConfigureButton.setOnClickListener {
            showDialog()
        }

        mainActivityViewModel.tcpStunDataReceived.observe(this) {
            binding.mainActivityTCPPublicCredentials.text = it
        }

        mainActivityViewModel.stunDataReceived.observe(this) { data ->

            if(mainActivityViewModel.isTimerRunning.value == true){
                Log.d("reinitializing connection","connecting")
                initiateConnection(mainActivityViewModel.receiverIP,mainActivityViewModel.receiverPORT.toString(),mainActivityViewModel.token)
            }

            Log.d("stun","response received")

            binding.mainActivityPeopleButton.isEnabled = true
            binding.mainActivityConfigureButton.isEnabled = true

            var flag = 0

            binding.mainActivityPublicCredentials.text = "${data[0]} : ${data[1]}"

            if (SharedPreferences.read("isSubscribed", "null") == "null") {
                FirebaseMessaging.getInstance().subscribeToTopic(Constants.FCM_TOPIC)
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            SharedPreferences.write("isSubscribed", "y")
                            Log.d("Fcm subscribe", "success")
                            if (SharedPreferences.read("myIp", "null") == "null") {
                                SharedPreferences.write("myIp", data[0])
                            } else if (SharedPreferences.read("myIp", "null") != data[0]) {
                                SharedPreferences.write("myIp", data[0])
                            }

                            if (SharedPreferences.read("myPort", "null") == "null") {
                                SharedPreferences.write("myPort", data[1])
                            } else if (SharedPreferences.read("myPort", "null") != data[1]) {
                                SharedPreferences.write("myPort", data[1])
                            }

                            mainActivityViewModel.transmitTableUpdate(
                                SharedPreferences.read("UserId", "null").toString(),
                                data[0],
                                data[1],
                                SharedPreferences.read("FcmToken", "null").toString(),
                                TOPIC_DESTINATION,
                                "1"
                            )
                        } else {
                            Log.d("Fcm subscribe", "failed")
                        }
                    }
            } else {
                if (SharedPreferences.read("myIp", "null") == "null") {
                    SharedPreferences.write("myIp", data[0])
                } else if (SharedPreferences.read("myIp", "null") != data[0]) {
                    SharedPreferences.write("myIp", data[0])
                }

                if (SharedPreferences.read("myPort", "null") == "null") {
                    SharedPreferences.write("myPort", data[1])
                } else if (SharedPreferences.read("myPort", "null") != data[1]) {
                    SharedPreferences.write("myPort", data[1])
                }

                mainActivityViewModel.transmitTableUpdate(
                    SharedPreferences.read("UserId", "null").toString(),
                    data[0],
                    data[1],
                    SharedPreferences.read("FcmToken", "null").toString(),
                    TOPIC_DESTINATION
                    ,"1"
                )
            }
        }

        binding.mainActivityUDPClientButton.setOnClickListener {
            if (mainActivityViewModel.mode == 1) {
                val text = binding.mainActivityUDPClientEditText.text.toString().trim()
                if (text.isNotBlank()) {
                    mainActivityViewModel.sendData(text)
                    binding.mainActivityUDPClientEditText.setText("")
                }
            } else {

            }
        }
    }

    private fun showUserDialog() {
        userDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val bind = PeopleLayoutBinding.inflate(layoutInflater)

        val peopleAdapter = PeopleAdapter(this,this)
        val recyclerView = bind.peopleLayoutRecyclerView
        recyclerView.adapter = peopleAdapter
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        databaseViewModel.readAllData.observe(this){
            Log.d("people data",it.toString())
            peopleAdapter.setData(it)
        }

        userDialog.setCancelable(true)
        userDialog.setContentView(bind.root)
        userDialog.show()
    }

    private fun initUserDialog() {
        userDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun sendTcpData() {
        displayProgressIndicator()

        val data = java.lang.StringBuilder()

        val tcpSocket = Socket()
        tcpSocket.reuseAddress = true

        try {
            tcpSocket.bind(InetSocketAddress(TCP_PORT))
        } catch (e: Exception) {
            createToast(e.message.toString())
            Log.d("tcp client bind failed", e.message.toString())
        }

        val tcpServerSocket = ServerSocket()
        tcpServerSocket.reuseAddress = true

        try {
            tcpServerSocket.bind(InetSocketAddress(TCP_PORT))
        } catch (e: Exception) {
            createToast(e.message.toString())
            Log.d("tcp server bind failed", e.message.toString())
        }

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                try {
                    tcpServerSocket.accept()
                } catch (e: Exception) {
                    createToast(e.message.toString())
                    Log.d("tcp server accept failed", e.message.toString())
                }

            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.IO) {
                    tcpSocket.connect(
                        InetSocketAddress(
                            mainActivityViewModel.receiverIP,
                            mainActivityViewModel.receiverPORT
                        )
                    )
                }

                mainActivityViewModel.isConnectionEstablished.postValue(true)

                inputStream = DataInputStream(withContext(Dispatchers.IO) {
                    tcpSocket.getInputStream()
                })

                outputStream = DataOutputStream(withContext(Dispatchers.IO) {
                    tcpSocket.getOutputStream()
                })

                while (true) {
                    // Wait for the STUN response
                    val response = ByteArray(512)
                    val byteRead = withContext(Dispatchers.IO) {
                        inputStream.read(response)
                    }

                    if (byteRead < response.size) {

                        data.append(String(response, 0, byteRead))

                        mainActivityViewModel.chatData.add(
                            ChatModel(
                                1,
                                data.toString(),
                                System.currentTimeMillis()
                            )
                        )
                        mainActivityViewModel.chatList.postValue(mainActivityViewModel.chatData)

                        data.clear()
                    } else {
                        data.append(String(response, 0, byteRead))
                    }
                }
            } catch (e: Exception) {
                createToast(e.message.toString())
                Log.d("tcp server connect failed", e.message.toString())
            }
        }

        binding.mainActivityUDPClientButton.setOnClickListener {
            val data = binding.mainActivityUDPClientEditText.text?.trim().toString()
            mainActivityViewModel.chatData.add(ChatModel(0, data, System.currentTimeMillis()))
            mainActivityViewModel.chatList.postValue(mainActivityViewModel.chatData)
            binding.mainActivityUDPClientEditText.setText("")

            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.IO) {
                    val chunks = data.chunked(512)
                    for (i in chunks) {
                        outputStream.write(i.toByteArray())
                    }
                }
                withContext(Dispatchers.IO) {
                    outputStream.flush()
                }
            }
        }
    }

    private fun displayProgressIndicator() {
        Log.d("displaying progress indicator","display")
        mainActivityViewModel.isProgressBarVisible = true
        CoroutineScope(Dispatchers.Main.immediate).launch {
            binding.mainActivityLinesrProgressIndicator.isVisible = mainActivityViewModel.isProgressBarVisible
            binding.mainActivityLinesrProgressIndicator.isIndeterminate = mainActivityViewModel.isProgressBarVisible
        }
    }

    private fun createToast(msg: String) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val snackBar = Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
            snackBar.setTextMaxLines(5)
            snackBar.show()
        }
    }

    private fun initRecycler() {
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun darkTheme() {
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> {
                setStatusBarColor(R.color.black, R.color.black, false)
            }
            Configuration.UI_MODE_NIGHT_NO -> {
                setStatusBarColor(R.color.white, R.color.white, true)
            }
            Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                Log.d("Night Mode", "Undefined")
            }
        }
    }

    private fun setStatusBarColor(
        statusBarColor: Int,
        navigationBarColor: Int?,
        isLightStatusBar: Boolean
    ) {
        val window: Window = window
        val decorView: View = window.decorView
        val wic = WindowInsetsControllerCompat(window, decorView)
        wic.isAppearanceLightStatusBars = isLightStatusBar
        window.statusBarColor = ContextCompat.getColor(this, statusBarColor)
        if (navigationBarColor != null) {
            window.navigationBarColor = ContextCompat.getColor(this, navigationBarColor)
        }
    }

    private fun initDialog() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun showDialog() {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val bind = ConfigureBinding.inflate(layoutInflater)

        bind.configureUdpButton.setOnClickListener {
            if (bind.configureDialogReceiverIP.text.isNotBlank() && bind.configureDialogReceiverPORT.text.isNotBlank()) {
                initiateConnection(bind.configureDialogReceiverIP.text.toString(),bind.configureDialogReceiverPORT.text.toString(),null)
                dialog.dismiss()
            }
        }

        bind.configureTcpButton.setOnClickListener {
            if (bind.configureDialogReceiverIP.text.isNotBlank() && bind.configureDialogReceiverPORT.text.isNotBlank()) {
                mainActivityViewModel.receiverIP = bind.configureDialogReceiverIP.text.toString()
                mainActivityViewModel.receiverPORT = bind.configureDialogReceiverPORT.text.toString().toInt()
                dialog.dismiss()

                binding.mainActivityConfigureButton.isVisible = false

                CoroutineScope(Dispatchers.IO).launch {
                    sendTcpData()
                }
            }
        }

        dialog.setCancelable(true)
        dialog.setContentView(bind.root)
        dialog.show()
    }

    override fun onItemClick(data: ConnectionData) {
        Log.d("people Item","clicked")
        initiateConnection(data.ip,data.port, data.token)
        mainActivityViewModel.token = data.token
        userDialog.dismiss()
    }

    private fun initiateConnection(ip : String, port : String, token : String?){
        mainActivityViewModel.receiverIP = ip
        mainActivityViewModel.receiverPORT = port.toInt()

        if (mainActivityViewModel.isTimerRunning.value == false) {
            Log.d("starting timer","started")
            mainActivityViewModel.timer(TIMER_TIME)
            mainActivityViewModel.isTimerRunning.postValue(true)
        }

        if(mainActivityViewModel.isConnectionTimerRunning.value == true){
            mainActivityViewModel.connectionTimer.cancel()
            mainActivityViewModel.isConnectionTimerRunning.postValue(false)
        }

        if(mainActivityViewModel.isDisconnectedTimerRunning.value == true){
            mainActivityViewModel.disconnectedTimer.cancel()
            mainActivityViewModel.isDisconnectedTimerRunning.postValue(false)
        }

        mainActivityViewModel.disconnectedTimer()

        if(token != null){
            mainActivityViewModel.transmitTableUpdate(
                SharedPreferences.read("UserId", "null").toString(),
                mainActivityViewModel.stunDataReceived.value?.get(0).toString(),
                mainActivityViewModel.stunDataReceived.value?.get(1).toString(),
                SharedPreferences.read("FcmToken", "null").toString(),
                "/to/$token"
                ,"2"
            )
        }

        displayProgressIndicator()
        mainActivityViewModel.isObserverNeeded = true
    }
}