package com.adreal.tcp_ip.ViewModel

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.adreal.tcp_ip.Constants.Constants
import com.adreal.tcp_ip.DataClass.ChatModel
import com.adreal.tcp_ip.DataClass.FcmResponse
import com.adreal.tcp_ip.MainActivity
import com.adreal.tcp_ip.Retrofit.SendFcmSignalObject
import de.javawi.jstun.attribute.ChangeRequest
import de.javawi.jstun.attribute.MappedAddress
import de.javawi.jstun.attribute.MessageAttributeInterface
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.*

class MainActivityViewModel : ViewModel() {

    var receiverIP: String = ""
    var receiverPORT: Int = 0
    var isConnectionEstablished = MutableLiveData<Boolean>()
    lateinit var timer: CountDownTimer
    var tick = MutableLiveData<Long>()
    val chatData = ArrayList<ChatModel>()
    val chatList = MutableLiveData<ArrayList<ChatModel>>()
    var mode: Int = 1
    val tcpStunDataReceived = MutableLiveData<String>()
    val stunDataReceived = MutableLiveData<List<String>>()
    var isDataInitialized = 0

    var isEditTextEnabled : Boolean = false
    var isButtonEnabled = false
    var isProgressBarVisible = false
    var isObserverNeeded = true

    val isTimerFinished = MutableLiveData<Boolean>()
    val isTimerRunning = MutableLiveData(false)
    val isDisconnectedTimerRunning = MutableLiveData(false)
    val isConnectionTimerRunning = MutableLiveData(false)

    private lateinit var udpRetryTimer: CountDownTimer
    private lateinit var tcpRetryTimer : CountDownTimer
    lateinit var connectionTimer : CountDownTimer
    val isUdpRetryTimerFinished = MutableLiveData<Boolean>()
    val isTcpRetryTimerFinished = MutableLiveData<Boolean>()
    val isConnectionTimerFinished = MutableLiveData<Boolean>()
    val isDisconnectedTimerFinished = MutableLiveData<Boolean>()
    var isConnectionReestablished = MutableLiveData<Boolean>()
    lateinit var disconnectedTimer : CountDownTimer
    var isTcpRetryTimerInitialized = 0
    var isUdpRetryTimerInitialized = 0

    private lateinit var outputStream: DataOutputStream
    private lateinit var inputStream: DataInputStream
    private lateinit var ma : MappedAddress

    var isBindingRequestInit = 0

    var token : String? = null

    private val socket by lazy {
        DatagramSocket(MainActivity.PORT)
    }

    fun disconnectedTimer(){
        isDisconnectedTimerRunning.postValue(true)

        disconnectedTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {

            }

            override fun onFinish() {
                Log.d("Disconnected Timer","Finished")
                isDisconnectedTimerFinished.postValue(true)
                isDisconnectedTimerRunning.postValue(false)
            }
        }
        disconnectedTimer.start()
    }

    private fun connectionTimer(){
        isConnectionTimerRunning.postValue(true)

        connectionTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {

            }

            override fun onFinish() {
                Log.d("connection Timer","Finished")
                isConnectionTimerRunning.postValue(false)
                isConnectionTimerFinished.postValue(true)
            }
        }
        connectionTimer.start()
    }

    private fun udpRetryTimer(){
        udpRetryTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
//                Log.d("Udp Retry Timer","running")
            }

            override fun onFinish() {
                Log.d("Udp Retry Timer","Finished")
                isUdpRetryTimerFinished.postValue(true)
            }
        }
        udpRetryTimer.start()
    }

    private fun tcpRetryTimer(){
        tcpRetryTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
//                Log.d("Tcp Retry Timer","running")
            }

            override fun onFinish() {
                Log.d("Tcp Retry Timer","Finished")
                isTcpRetryTimerFinished.postValue(true)
            }
        }
        tcpRetryTimer.start()
    }

    fun transmitTableUpdate(userId : String, publicIp : String, publicPort : String, token : String, destination : String,status : String){
        val jsonObject = JSONObject()
        val jsonObject1 = JSONObject()

        jsonObject.put("to", destination)
        jsonObject.put("data", jsonObject1)

        jsonObject1.put("userId", userId)
        jsonObject1.put("publicIp", publicIp)
        jsonObject1.put("publicPort", publicPort)
        jsonObject1.put("token", token)
        jsonObject1.put("status",status)

        val json = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonObject.toString().toRequestBody(json)

        SendFcmSignalObject.sendFcmSignal.sendSignal(
            "key=${Constants.FCM_API_KEY}",
            body
        ).enqueue(object : Callback<FcmResponse>{
            override fun onResponse(call: Call<FcmResponse>, response: Response<FcmResponse>) {
                Log.d("Fcm Message","send")
            }

            override fun onFailure(call: Call<FcmResponse>, t: Throwable) {
                Log.d("Fcm Message","failed")
            }
        })
    }

    fun timer(time: Long) {
        isTimerRunning.postValue(true)

        timer = object : CountDownTimer(time, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d("Time left",millisUntilFinished.toString())
                CoroutineScope(Dispatchers.IO).launch {
                    val p = DatagramPacket(
                        MainActivity.CONNECTION_ESTABLISH_STRING.toByteArray(),
                        MainActivity.CONNECTION_ESTABLISH_STRING.toByteArray().size,
                        withContext(Dispatchers.IO) {
                            InetAddress.getByName(receiverIP)
                        },
                        receiverPORT
                    )

                    withContext(Dispatchers.IO) {
                        Log.d("sending...",p.data.toString())
                        try {
                            socket.send(p)
                        }catch (e : Exception){
                            Log.d("timer send exception",e.message.toString())
                        }
                    }
                }
            }

            override fun onFinish() {
                Log.d("Timer","Finished")
                isTimerFinished.postValue(true)
            }
        }
        timer.start()
    }

    fun getIPAddress(useIPv4: Boolean): String {
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
        } catch (e : Exception) {
            Log.d("ip address exception",e.message.toString())
        }
        return ""
    }

    fun sendTcpBindingRequest() {
        CoroutineScope(Dispatchers.IO).launch {
            isTcpRetryTimerInitialized = 0
            val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
            val changeRequest = ChangeRequest()
            sendMH.addMessageAttribute(changeRequest)
            val data = sendMH.bytes

            // Create a socket to connect to the STUN server
            val tcpSocket = Socket()
            tcpSocket.reuseAddress = true
            withContext(Dispatchers.IO) {
                tcpSocket.bind(InetSocketAddress(MainActivity.TCP_PORT))
            }

            withContext(Dispatchers.IO) {
                try {
                    tcpSocket.connect(
                        InetSocketAddress(
                            MainActivity.STUNTMAN_STUN_SERVER_IP,
                            MainActivity.STUNTMAN_STUN_SERVER_PORT_TCP
                        )
                    )
                }catch (e : Exception){
                    Log.d("tcp binding request connect failed",e.message.toString())
                    initTcpRetryTimer()
                }
            }

            try {
                inputStream = DataInputStream(withContext(Dispatchers.IO) {
                    tcpSocket.getInputStream()
                })
            }catch (e : Exception){
                Log.d("tcp binding request input stream failed",e.message.toString())
                initTcpRetryTimer()
            }

            try {
                outputStream = DataOutputStream(withContext(Dispatchers.IO) {
                    tcpSocket.getOutputStream()
                })
            }catch (e : Exception){
                Log.d("tcp binding request output stream failed",e.message.toString())
                initTcpRetryTimer()
            }

            // Send the STUN request using TCP
            withContext(Dispatchers.IO) {
                try {
                    outputStream.write(data)
                }catch (e : Exception){
                    Log.d("tcp binding request output write stream failed",e.message.toString())
                    initTcpRetryTimer()
                }
            }
            withContext(Dispatchers.IO) {
                try {
                    outputStream.flush()
                }catch (e : Exception){
                    Log.d("tcp binding request output flush failed",e.message.toString())
                    initTcpRetryTimer()
                }

            }

            // Wait for the STUN response
            val response = ByteArray(1024)
            withContext(Dispatchers.IO) {
                try {
                    inputStream.read(response)
                }catch (e : Exception){
                    Log.d("tcp binding request input stream read failed",e.message.toString())
                    initTcpRetryTimer()
                }
            }

            try {
                val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
                receiveMH.parseAttributes(response)
                ma = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress
                // Process the response
                tcpStunDataReceived.postValue("${ma.address} : ${ma.port}")
            }catch (e : Exception){
                Log.d("tcp binding request parsing failed",e.message.toString())
                initTcpRetryTimer()
            }

            // Close the socket
            withContext(Dispatchers.IO) {
                tcpSocket.close()
            }
        }
    }

    fun sendBindingRequest() {
        CoroutineScope(Dispatchers.IO).launch {
            isUdpRetryTimerInitialized = 0
            val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
            val changeRequest = ChangeRequest()
            sendMH.addMessageAttribute(changeRequest)
            val data = sendMH.bytes

            CoroutineScope(Dispatchers.IO).launch {
                val p = DatagramPacket(
                    data,
                    data.size,
                    withContext(Dispatchers.IO) {
                        InetAddress.getByName(MainActivity.GOOGLE_STUN_SERVER_IP)
                    },
                    MainActivity.GOOGLE_STUN_SERVER_PORT
                )
                withContext(Dispatchers.IO) {
                    try {
                        socket.send(p)
                    }catch (e : Exception){
                        Log.d("udp binding request send failed",e.message.toString())
                        initUdpRetryTimer()
                    }
                }

//                val rp = DatagramPacket(ByteArray(32), 32)
//
//                withContext(Dispatchers.IO) {
//                    try {
//                        socket.receive(rp)
//                    }catch (e : Exception){
//                        Log.d("udp binding request receive failed",e.message.toString())
//                        initUdpRetryTimer()
//                    }
//                }
//
//                try {
//                    val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
//                    receiveMH.parseAttributes(rp.data)
//                    ma = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress
//                }catch (e : Exception){
//                    Log.d("udp binding parsing error",e.message.toString())
//                    initUdpRetryTimer()
//                }
//
//                val list = kotlin.collections.ArrayList<String>()
//                list.add(ma.address.toString())
//                list.add(ma.port.toString())
//
//                stunDataReceived.postValue(list)
            }
        }
    }

    fun receiverData() {
        CoroutineScope(Dispatchers.IO).launch {

            val udpReceiverData = java.lang.StringBuilder()

            while (true) {
                val rp = DatagramPacket(ByteArray(1024), 1024)
                withContext(Dispatchers.IO) {
                    socket.receive(rp)
                }

                val receivedData = String(rp.data, 0, rp.data.indexOf(0))

                Log.d("received data, Length", receivedData + " " + receivedData.length)

                val messageType = ((rp.data[0].toInt() shl 8) or rp.data[1].toInt()).toShort()

                if(messageType == 0x0101.toShort()){
                    try {
                        val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
                        receiveMH.parseAttributes(rp.data)
                        ma = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

                        val list = kotlin.collections.ArrayList<String>()
                        list.add(ma.address.toString())
                        list.add(ma.port.toString())

                        stunDataReceived.postValue(list)
                    }catch (e : Exception){
                        Log.d("udp binding parsing error",e.message.toString())
                        initUdpRetryTimer()
                    }
                }else{
                    if (receivedData.toByteArray().size < 256) {

                        if (receivedData != MainActivity.CONNECTION_ESTABLISH_STRING && receivedData != MainActivity.EXIT_CHAT) {

                            udpReceiverData.append(String(rp.data, 0, rp.data.indexOf(0)))

                            chatData.add(
                                ChatModel(
                                    1,
                                    udpReceiverData.toString(),
                                    System.currentTimeMillis()
                                )
                            )
                            chatList.postValue(chatData)

                            udpReceiverData.clear()
                        } else {
                            if(receivedData != MainActivity.EXIT_CHAT){

                                if(isConnectionEstablished.value == false){
                                    isConnectionEstablished.postValue(true)
                                }

                                if(isConnectionTimerRunning.value == true){
                                    connectionTimer.cancel()
                                }

                                CoroutineScope(Dispatchers.Main.immediate).launch {
                                    connectionTimer()
                                }

                                if(isDisconnectedTimerRunning.value == true){
                                    disconnectedTimer.cancel()
                                    isDisconnectedTimerRunning.postValue(false)
                                }
                            }else{
                                udpReceiverData.append("Person has left the chat.")

                                chatData.add(
                                    ChatModel(
                                        1,
                                        udpReceiverData.toString(),
                                        System.currentTimeMillis()
                                    )
                                )

                                chatList.postValue(chatData)

                                udpReceiverData.clear()

                                isConnectionEstablished.postValue(false)

                                if(isTimerRunning.value == true){
                                    timer.cancel()
                                    isTimerRunning.postValue(false)
                                }

                                if(isConnectionTimerRunning.value == true){
                                    connectionTimer.cancel()
                                    isConnectionTimerRunning.postValue(false)
                                }

                                if(isDisconnectedTimerRunning.value == true){
                                    disconnectedTimer.cancel()
                                    isDisconnectedTimerRunning.postValue(false)
                                }

                                isDisconnectedTimerFinished.postValue(true)
                            }
                        }
                    } else {
                        udpReceiverData.append(String(rp.data, 0, rp.data.indexOf(0)))
                    }
                }
            }
        }
    }

    fun sendData(text: String) {
        val port = receiverPORT
        val ip = receiverIP

        Log.d("sending...",text)

        chatData.add(ChatModel(0, text, System.currentTimeMillis()))
        chatList.postValue(chatData)

        val chunks = text.chunked(256)

        for (chunk in chunks) {
            CoroutineScope(Dispatchers.IO).launch {
                val p = DatagramPacket(
                    chunk.toByteArray(), chunk.toByteArray().size,
                    withContext(Dispatchers.IO) {
                        InetAddress.getByName(ip)
                    }, port
                )
                withContext(Dispatchers.IO) {
                    socket.send(p)
                }
            }

            Log.d("chunk size", chunk.toByteArray().size.toString())
        }
    }

    private fun initTcpRetryTimer(){
        if(isTcpRetryTimerInitialized == 0){
            Log.d("tcp retry called","init")
            CoroutineScope(Dispatchers.Main.immediate).launch {
                tcpRetryTimer()
            }
            isTcpRetryTimerInitialized = 1
        }
    }

    private fun initUdpRetryTimer(){
        if(isUdpRetryTimerInitialized == 0){
            Log.d("udp retry called","init")
            CoroutineScope(Dispatchers.Main.immediate).launch {
                udpRetryTimer()
            }
            isUdpRetryTimerInitialized = 1
        }
    }
}