package com.adreal.tcp_ip

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
import com.adreal.tcp_ip.DataClass.ChatModel
import com.adreal.tcp_ip.ViewModel.MainActivityViewModel
import com.adreal.tcp_ip.databinding.ActivityMainBinding
import com.adreal.tcp_ip.databinding.ConfigureBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {

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

    lateinit var inputStream: DataInputStream
    lateinit var outputStream: DataOutputStream

    companion object {
        const val PORT = 60001
        const val TCP_PORT = 50001
        const val GOOGLE_STUN_SERVER_IP = "74.125.197.127"
        const val GOOGLE_STUN_SERVER_PORT = 19302
        const val CONNECTION_ESTABLISH_STRING = "$@6%9*4!&2#0"
        const val STUNTMAN_STUN_SERVER_IP = "18.191.223.12"
        const val STUNTMAN_STUN_SERVER_PORT_TCP = 3478
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme()
        setContentView(binding.root)

        binding.mainActivityUDPClientEditText.isEnabled = mainActivityViewModel.isEditTextEnabled
        binding.mainActivityUDPClientButton.isEnabled = mainActivityViewModel.isButtonEnabled
        binding.mainActivityLinesrProgressIndicator.isVisible = mainActivityViewModel.isProgressBarVisible

        initDialog()
        initRecycler()

        if (mainActivityViewModel.isDataInitialized == 0) {
            mainActivityViewModel.sendTcpBindingRequest()
            mainActivityViewModel.sendBindingRequest()
            mainActivityViewModel.isDataInitialized = 1
        }

        mainActivityViewModel.isConnectionEstablished.observe(this@MainActivity) {
            if (mainActivityViewModel.isObserverNeeded) {
                if (it) {
                    Log.d("connection", "established")
//                    mainActivityViewModel.timer.cancel()
//                    mainActivityViewModel.timer(5000)
                    Toast.makeText(this, "Connection Established", Toast.LENGTH_SHORT).show()

                    mainActivityViewModel.isProgressBarVisible = false
                    mainActivityViewModel.isButtonEnabled = true
                    mainActivityViewModel.isEditTextEnabled = true

                    binding.mainActivityLinesrProgressIndicator.isVisible =
                        mainActivityViewModel.isProgressBarVisible
                    binding.mainActivityUDPClientEditText.isEnabled =
                        mainActivityViewModel.isEditTextEnabled
                    binding.mainActivityUDPClientButton.isEnabled =
                        mainActivityViewModel.isButtonEnabled
                }

                mainActivityViewModel.isObserverNeeded = false
            }
        }

        binding.mainActivityPrivateCredentials.text =
            "${mainActivityViewModel.getIPAddress(true)} : $PORT"

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

        mainActivityViewModel.stunDataReceived.observe(this) {
            binding.mainActivityPublicCredentials.text = it
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
        mainActivityViewModel.isProgressBarVisible = true
        CoroutineScope(Dispatchers.Main.immediate).launch {
            binding.mainActivityLinesrProgressIndicator.isVisible =
                mainActivityViewModel.isProgressBarVisible
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
                mainActivityViewModel.receiverIP = bind.configureDialogReceiverIP.text.toString()
                mainActivityViewModel.receiverPORT = bind.configureDialogReceiverPORT.text.toString().toInt()
                dialog.dismiss()

                mainActivityViewModel.timer(3600000)
                displayProgressIndicator()
                mainActivityViewModel.receiverData()
                mainActivityViewModel.isObserverNeeded = true
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
}