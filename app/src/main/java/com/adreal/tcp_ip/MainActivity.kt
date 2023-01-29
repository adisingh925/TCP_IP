package com.adreal.tcp_ip

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings.Secure
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
import com.adreal.tcp_ip.Encryption.Encryption
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


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
        const val EXIT_CHAT = "EXIT_CHAT"
        const val TERMINATION_MESSAGE = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme()
        setContentView(binding.root)

        SharedPreferences.init(this)

//        val keyPair = Encryption(this).getAsymmetricKeyPair()
//        val encryptedData = Encryption(this).encrypt("hello",keyPair.public)
//        Log.d("MainActivity Encrypted Data",encryptedData)
//        val decryptedData = Encryption(this).decrypt(encryptedData,keyPair.private)
//        Log.d("MainActivity Decrypted Data",decryptedData)

        mainActivityViewModel.receiverData()

        setLocalIp("${mainActivityViewModel.getIPAddress(true)} : $PORT")

        if (mainActivityViewModel.isDataInitialized == 0) {
            connectionLiveData.observe(this) {

                mainActivityViewModel.isConnectionEstablished.postValue(false)
                handleConfigurationButton(false)
                handleSendViews(false)

                if (it) {
                    Log.d("Main Activity", "Online")
                    mainActivityViewModel.sendBindingRequest()
                    setLocalIp("${mainActivityViewModel.getIPAddress(true)} : $PORT")
                } else {
                    Log.d("Main Activity", "Offline")
                }
            }

            mainActivityViewModel.isDataInitialized = 1
        }

        mainActivityViewModel.isConnectionTimerFinished.observe(this) {
            if (it) {
                Log.d("Connection Timer", "finished")
                mainActivityViewModel.isConnectionEstablished.postValue(false)

                handleProgressBar(
                    visible = mainActivityViewModel.isProgressBarVisible,
                    indeterminate = mainActivityViewModel.isProgressBarVisible
                )

                mainActivityViewModel.disconnectedTimer()
            }
        }

        mainActivityViewModel.isDisconnectedTimerFinished.observe(this) {
            if (it) {
                Toast.makeText(this, "Connection Terminated...", Toast.LENGTH_SHORT).show()

                mainActivityViewModel.isConnectionEstablished.postValue(false)

                mainActivityViewModel.isProgressBarVisible = false

                handleProgressBar(
                    mainActivityViewModel.isProgressBarVisible,
                    mainActivityViewModel.isProgressBarVisible
                )

                handleTimersCancellation(
                    isDisconnectedTimerFinished = false,
                    cancelMainTimer = true,
                    cancelConnectionTimer = false,
                    cancelDisconnectedTimer = false
                )

                handleSendViews(false)
            }
        }

        mainActivityViewModel.isUdpRetryTimerFinished.observe(this) {
            mainActivityViewModel.sendBindingRequest()
            binding.mainActivityPrivateCredentials.text =
                "${mainActivityViewModel.getIPAddress(true)} : $PORT"
        }

        mainActivityViewModel.isTcpRetryTimerFinished.observe(this) {
            mainActivityViewModel.sendTcpBindingRequest()
            binding.mainActivityPrivateCredentials.text =
                "${mainActivityViewModel.getIPAddress(true)} : $PORT"
        }

        databaseViewModel.readAllData.observe(this) {
            Log.d("people data activity", it.toString())
            for (i in it) {
                if (i.status == "2") {
                    initiateConnection(i.ip, i.port, null, false)
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

        handleSendViews(mainActivityViewModel.isEditTextEnabled)
        handleProgressBar(mainActivityViewModel.isProgressBarVisible, true)

        initDialog()
        initUserDialog()
        initRecycler()

        mainActivityViewModel.isConnectionEstablished.observe(this@MainActivity) {
            if (it) {
                Log.d("connection", "established")
                Toast.makeText(this, "Connection Established", Toast.LENGTH_SHORT).show()

                handleProgressBar(
                    mainActivityViewModel.isProgressBarVisible,
                    !(mainActivityViewModel.isProgressBarVisible)
                )

                handleSendViews(true)
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

            Log.d("stun", "response received")

            configureLocalIpAndPort(data[0], data[1])

            handleConfigurationButton(true)

            binding.mainActivityPublicCredentials.text = "${data[0]} : ${data[1]}"

            if (SharedPreferences.read("isSubscribed", "null") == "null") {
                FirebaseMessaging.getInstance().subscribeToTopic(Constants.FCM_TOPIC)
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            SharedPreferences.write("isSubscribed", "y")
                            Log.d("Fcm subscribe", "success")
                            sendTableUpdate(data[0], data[1], "1", TOPIC_DESTINATION)
                        } else {
                            Log.d("Fcm subscribe", "failed")
                        }
                    }
            } else {
                sendTableUpdate(data[0], data[1], "1", TOPIC_DESTINATION)
            }
        }

        binding.mainActivityUDPClientButton.setOnClickListener {
            if (mainActivityViewModel.mode == 1) {
                val text = binding.mainActivityUDPClientEditText.text.toString().trim()

                if (text == EXIT_CHAT) {

                    handleTimersCancellation(
                        isDisconnectedTimerFinished = true,
                        cancelMainTimer = true,
                        cancelConnectionTimer = true,
                        cancelDisconnectedTimer = true
                    )

                    mainActivityViewModel.isConnectionEstablished.postValue(false)
                }

                if (text.isNotBlank()) {
                    mainActivityViewModel.sendData(text)
                    binding.mainActivityUDPClientEditText.setText("")
                }
            } else {
                //yet to code
            }
        }
    }

    private fun showUserDialog() {
        userDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val bind = PeopleLayoutBinding.inflate(layoutInflater)

        val peopleAdapter = PeopleAdapter(this, this)
        val recyclerView = bind.peopleLayoutRecyclerView
        recyclerView.adapter = peopleAdapter
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        databaseViewModel.readAllData.observe(this) {
            Log.d("Dialog live data", "running")
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
        mainActivityViewModel.isProgressBarVisible = true

        handleProgressBar(
            mainActivityViewModel.isProgressBarVisible,
            mainActivityViewModel.isProgressBarVisible
        )

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
                initiateConnection(
                    bind.configureDialogReceiverIP.text.toString(),
                    bind.configureDialogReceiverPORT.text.toString(),
                    null,
                    true
                )
                dialog.dismiss()
            }
        }

        bind.configureTcpButton.setOnClickListener {
            if (bind.configureDialogReceiverIP.text.isNotBlank() && bind.configureDialogReceiverPORT.text.isNotBlank()) {
                mainActivityViewModel.receiverIP = bind.configureDialogReceiverIP.text.toString()
                mainActivityViewModel.receiverPORT =
                    bind.configureDialogReceiverPORT.text.toString().toInt()
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
        Log.d("people Item", "clicked")
        initiateConnection(data.ip, data.port, data.token, true)
        mainActivityViewModel.token = data.token
        userDialog.dismiss()
    }

    private fun initiateConnection(ip: String, port: String, token: String?, exitChat: Boolean) {

        if (mainActivityViewModel.isConnectionEstablished.value == true) {
            if (exitChat && mainActivityViewModel.receiverIP != "" && mainActivityViewModel.receiverPORT != 0) {
                if (ip != mainActivityViewModel.receiverIP) {
                    mainActivityViewModel.sendData(EXIT_CHAT)
                }
            }
        }

        mainActivityViewModel.receiverIP = ip
        mainActivityViewModel.receiverPORT = port.toInt()

        mainActivityViewModel.isConnectionEstablished.postValue(false)

        if (mainActivityViewModel.isTimerRunning.value == false) {
            Log.d("starting timer", "started")
            mainActivityViewModel.timer(TIMER_TIME)
        }

        if (mainActivityViewModel.isDisconnectedTimerRunning.value == true) {
            mainActivityViewModel.disconnectedTimer.cancel()
        }

        mainActivityViewModel.disconnectedTimer()

        if (token != null) {
            sendTableUpdate(
                mainActivityViewModel.stunDataReceived.value?.get(0).toString(),
                mainActivityViewModel.stunDataReceived.value?.get(1).toString(),
                "2",
                "/to/$token"
            )
        }

        mainActivityViewModel.isProgressBarVisible = true

        handleProgressBar(
            mainActivityViewModel.isProgressBarVisible,
            mainActivityViewModel.isProgressBarVisible
        )
    }

    private fun sendTableUpdate(myIp: String, myPort: String, status: String, destination: String) {
        mainActivityViewModel.transmitTableUpdate(
            SharedPreferences.read("UserId", "null").toString(),
            myIp,
            myPort,
            SharedPreferences.read("FcmToken", "null").toString(),
            destination,
            status
        )
    }

    private fun handleProgressBar(visible: Boolean, indeterminate: Boolean) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            binding.mainActivityLinesrProgressIndicator.isVisible = visible
            binding.mainActivityLinesrProgressIndicator.isIndeterminate = indeterminate

            if (!indeterminate) {
                binding.mainActivityLinesrProgressIndicator.setProgressCompat(100, true)
            }
        }
    }

    private fun configureLocalIpAndPort(ip: String, port: String) {
        if (SharedPreferences.read("myIp", "null") == "null") {
            SharedPreferences.write("myIp", ip)
        } else if (SharedPreferences.read("myIp", "null") != ip) {
            SharedPreferences.write("myIp", ip)
        }

        if (SharedPreferences.read("myPort", "null") == "null") {
            SharedPreferences.write("myPort", port)
        } else if (SharedPreferences.read("myPort", "null") != port) {
            SharedPreferences.write("myPort", port)
        }
    }

    private fun handleSendViews(isEnabled: Boolean) {
        mainActivityViewModel.isEditTextEnabled = isEnabled
        mainActivityViewModel.isButtonEnabled = isEnabled
        binding.mainActivityUDPClientEditText.isEnabled = mainActivityViewModel.isEditTextEnabled
        binding.mainActivityUDPClientButton.isEnabled = mainActivityViewModel.isButtonEnabled
    }

    private fun handleConfigurationButton(isEnabled: Boolean) {
        binding.mainActivityPeopleButton.isEnabled = isEnabled
        binding.mainActivityConfigureButton.isEnabled = isEnabled
    }

    private fun setLocalIp(text: String) {
        binding.mainActivityPrivateCredentials.text = text
    }

    fun handleTimersCancellation(
        isDisconnectedTimerFinished: Boolean,
        cancelMainTimer: Boolean,
        cancelConnectionTimer: Boolean,
        cancelDisconnectedTimer: Boolean
    ) {

        if (cancelMainTimer) {
            if (mainActivityViewModel.isTimerRunning.value == true) {
                mainActivityViewModel.timer.cancel()
                mainActivityViewModel.isTimerRunning.postValue(false)
            }
        }

        if (cancelConnectionTimer) {
            if (mainActivityViewModel.isConnectionTimerRunning.value == true) {
                mainActivityViewModel.connectionTimer.cancel()
                mainActivityViewModel.isConnectionTimerRunning.postValue(false)
            }
        }

        if (cancelDisconnectedTimer) {
            if (mainActivityViewModel.isDisconnectedTimerRunning.value == true) {
                mainActivityViewModel.disconnectedTimer.cancel()
                mainActivityViewModel.isDisconnectedTimerRunning.postValue(false)
            }
        }

        if (isDisconnectedTimerFinished) {
            mainActivityViewModel.isDisconnectedTimerFinished.postValue(isDisconnectedTimerFinished)
        }
    }
}