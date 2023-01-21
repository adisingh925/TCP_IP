package com.adreal.tcp_ip

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.adreal.tcp_ip.Adapter.ChatAdapter
import com.adreal.tcp_ip.DataClass.ChatModel
import com.adreal.tcp_ip.ViewModel.MainActivityViewModel
import com.adreal.tcp_ip.databinding.ActivityMainBinding
import com.adreal.tcp_ip.databinding.ConfigureBinding
import de.javawi.jstun.attribute.ChangeRequest
import de.javawi.jstun.attribute.MappedAddress
import de.javawi.jstun.attribute.MessageAttributeInterface
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.*

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

    private val socket by lazy {
        DatagramSocket(PORT)
    }

    private val tcpSocket by lazy {
        Socket()
    }

//    private val serverSocket by lazy {
//        ServerSocket()
//    }

    private val adapter by lazy {
        ChatAdapter(this)
    }

    private val recyclerView by lazy {
        binding.recyclerView
    }

    lateinit var inputStream : DataInputStream
    lateinit var outputStream : DataOutputStream

    private val stunDataReceived = MutableLiveData<Boolean>()
    private val tcpStunDataReceived = MutableLiveData<Boolean>()

    companion object {
        const val PORT = 60001
        const val TCP_PORT = 50008
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

        mainActivityViewModel.isConnectionEstablished.postValue(false)

        initDialog()
        initRecycler()

        CoroutineScope(Dispatchers.IO).launch {
            sendBindingRequest()
        }

        mainActivityViewModel.chatList.observe(this) {
            adapter.setData(it)
            recyclerView.scrollToPosition(it.size - 1)
        }

        binding.mainActivityConfigureButton.setOnClickListener {
            showDialog()
        }

        CoroutineScope(Dispatchers.IO).launch {
            sendTcpBindingRequest()
        }

        stunDataReceived.observe(this) {
//            sendData()
        }

        tcpStunDataReceived.observe(this){
//            CoroutineScope(Dispatchers.IO).launch {
//                sendTcpData()
//            }
        }

        mainActivityViewModel.tick.observe(this) {
            if (it != 0.toLong()) {
                Log.d("tick", it.toString())
                CoroutineScope(Dispatchers.IO).launch {
                    val p = DatagramPacket(
                        CONNECTION_ESTABLISH_STRING.toByteArray(),
                        CONNECTION_ESTABLISH_STRING.toByteArray().size,
                        withContext(Dispatchers.IO) {
                            InetAddress.getByName(mainActivityViewModel.receiverIP)
                        },
                        mainActivityViewModel.receiverPORT
                    )

                    withContext(Dispatchers.IO) {
                        socket.send(p)
                    }
                }
            } else {
                mainActivityViewModel.timer.cancel()
            }
        }

        binding.mainActivityPrivateCredentials.text = "${getIPAddress(true)} : $PORT"
    }

    private fun sendTcpData() {
        val data = java.lang.StringBuilder()

        val tcpSocket = Socket()
        tcpSocket.reuseAddress = true
        tcpSocket.bind(InetSocketAddress(TCP_PORT))

        val tcpServerSocket = ServerSocket(TCP_PORT)
        tcpServerSocket.reuseAddress = true

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                tcpServerSocket.accept()
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                tcpSocket.connect(
                    InetSocketAddress(
                        mainActivityViewModel.receiverIP,
                        mainActivityViewModel.receiverPORT
                    )
                )

                inputStream = DataInputStream(tcpSocket.getInputStream())
                outputStream = DataOutputStream(tcpSocket.getOutputStream())

                while(true){
                    // Wait for the STUN response
                    val response = ByteArray(512)
                    val byteRead = inputStream.read(response)

                    if(byteRead < response.size){

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
                    }else{
                        data.append(String(response, 0, byteRead))
                    }
                }
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
                    for(i in chunks){
                        outputStream.write(i.toByteArray())
                    }
                }
                withContext(Dispatchers.IO) {
                    outputStream.flush()
                }
            }
        }
    }

    private fun sendTcpBindingRequest() {
        val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        val changeRequest = ChangeRequest()
        sendMH.addMessageAttribute(changeRequest)
        val data = sendMH.bytes

        // Create a socket to connect to the STUN server
        tcpSocket.reuseAddress = true
        tcpSocket.bind(InetSocketAddress(TCP_PORT))
        tcpSocket.connect(InetSocketAddress(STUNTMAN_STUN_SERVER_IP, STUNTMAN_STUN_SERVER_PORT_TCP))

        val inputStream = DataInputStream(tcpSocket.getInputStream())
        val outputStream = DataOutputStream(tcpSocket.getOutputStream())

        // Send the STUN request using TCP
        outputStream.write(data)
        outputStream.flush()

        // Wait for the STUN response
        val response = ByteArray(1024)
        inputStream.read(response)

        val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        receiveMH.parseAttributes(response)
        val ma: MappedAddress = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

        // Process the response
        CoroutineScope(Dispatchers.Main.immediate).launch {
            binding.mainActivityTCPPublicCredentials.text = "${ma.address} : ${ma.port}"
            tcpStunDataReceived.postValue(true)
        }

        // Close the socket
        tcpSocket.close()
    }

    private fun initRecycler() {
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun sendBindingRequest() {
        val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        val changeRequest = ChangeRequest()
        sendMH.addMessageAttribute(changeRequest)
        val data = sendMH.bytes

        CoroutineScope(Dispatchers.IO).launch {
            val p = DatagramPacket(
                data,
                data.size,
                withContext(Dispatchers.IO) {
                    InetAddress.getByName(GOOGLE_STUN_SERVER_IP)
                },
                GOOGLE_STUN_SERVER_PORT
            )
            withContext(Dispatchers.IO) {
                socket.send(p)
            }

            val rp = DatagramPacket(ByteArray(32), 32)

            withContext(Dispatchers.IO) {
                socket.receive(rp)
            }

            val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
            receiveMH.parseAttributes(rp.data)
            val ma: MappedAddress = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

            CoroutineScope(Dispatchers.Main.immediate).launch {
                binding.mainActivityPublicCredentials.text = "${ma.address} : ${ma.port}"
                stunDataReceived.postValue(true)
            }
        }
    }

    private fun sendData() {
        binding.mainActivityUDPClientButton.setOnClickListener {

            val port = mainActivityViewModel.receiverPORT
            val ip = mainActivityViewModel.receiverIP

            val text = binding.mainActivityUDPClientEditText.text.toString().trim()

            mainActivityViewModel.chatData.add(ChatModel(0, text, System.currentTimeMillis()))
            mainActivityViewModel.chatList.postValue(mainActivityViewModel.chatData)

            val data = text.toByteArray()

            binding.mainActivityUDPClientEditText.text?.clear()

            CoroutineScope(Dispatchers.IO).launch {
                val p = DatagramPacket(
                    data, data.size,
                    withContext(Dispatchers.IO) {
                        InetAddress.getByName(ip)
                    }, port
                )
                withContext(Dispatchers.IO) {
                    socket.send(p)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val rp = DatagramPacket(ByteArray(60000), 60000)
                withContext(Dispatchers.IO) {
                    socket.receive(rp)
                }

                val data = String(rp.data, 0, rp.data.indexOf(0))

                Log.d("data received", data)

                if (data != CONNECTION_ESTABLISH_STRING) {
                    mainActivityViewModel.chatData.add(
                        ChatModel(
                            1,
                            data,
                            System.currentTimeMillis()
                        )
                    )
                    mainActivityViewModel.chatList.postValue(mainActivityViewModel.chatData)
                } else {
                    mainActivityViewModel.timer.cancel()
                }
            }
        }
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

    private fun getIPAddress(useIPv4: Boolean): String {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr: String = addr.hostAddress as String
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(
                                    0,
                                    delim
                                ).uppercase(
                                    Locale.getDefault()
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {

        }
        return ""
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

                mainActivityViewModel.timer()
                sendData()
            }
        }

        bind.configureTcpButton.setOnClickListener {
            if (bind.configureDialogReceiverIP.text.isNotBlank() && bind.configureDialogReceiverPORT.text.isNotBlank()) {
                mainActivityViewModel.receiverIP = bind.configureDialogReceiverIP.text.toString()
                mainActivityViewModel.receiverPORT = bind.configureDialogReceiverPORT.text.toString().toInt()
                dialog.dismiss()

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