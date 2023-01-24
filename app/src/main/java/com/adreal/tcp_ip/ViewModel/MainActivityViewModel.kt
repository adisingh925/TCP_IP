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
import io.ktor.util.reflect.*
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
import kotlin.collections.ArrayList

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
    val stunDataReceived = MutableLiveData<String>()
    var isDataInitialized = 0

    var isEditTextEnabled : Boolean = false
    var isButtonEnabled = false
    var isProgressBarVisible = false
    var isObserverNeeded = false

    val isTimerFinished = MutableLiveData<Boolean>()

    private val socket by lazy {
        DatagramSocket(MainActivity.PORT)
    }

    fun transmitTableUpdate(userId : String, publicIp : String, publicPort : String){
        val jsonObject = JSONObject()
        val jsonObject1 = JSONObject()

        jsonObject.put("to", "/topics/${Constants.FCM_TOPIC}")
        jsonObject.put("data", jsonObject1)

        jsonObject1.put("userId", userId)
        jsonObject1.put("publicIp", publicIp)
        jsonObject1.put("publicPort", publicPort)

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
                        socket.send(p)
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
        } catch (_: Exception) {

        }
        return ""
    }

    fun sendTcpBindingRequest() {
        CoroutineScope(Dispatchers.IO).launch {
            val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
            val changeRequest = ChangeRequest()
            sendMH.addMessageAttribute(changeRequest)
            val data = sendMH.bytes

            // Create a socket to connect to the STUN server
            val tcpSocket = Socket()
            tcpSocket.reuseAddress = true
            tcpSocket.bind(InetSocketAddress(MainActivity.TCP_PORT))
            tcpSocket.connect(
                InetSocketAddress(
                    MainActivity.STUNTMAN_STUN_SERVER_IP,
                    MainActivity.STUNTMAN_STUN_SERVER_PORT_TCP
                )
            )

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
            val ma: MappedAddress =
                receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

            // Process the response
            tcpStunDataReceived.postValue("${ma.address} : ${ma.port}")

            // Close the socket
            tcpSocket.close()
        }
    }

    fun sendBindingRequest() {
        CoroutineScope(Dispatchers.IO).launch {
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
                    socket.send(p)
                }

                val rp = DatagramPacket(ByteArray(32), 32)

                withContext(Dispatchers.IO) {
                    socket.receive(rp)
                }

                val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
                receiveMH.parseAttributes(rp.data)
                val ma: MappedAddress = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

                stunDataReceived.postValue("${ma.address} : ${ma.port}")
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

                Log.d("data", receivedData)

                if (receivedData.toByteArray().size < 256) {

                    if (receivedData != MainActivity.CONNECTION_ESTABLISH_STRING) {

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
                        isConnectionEstablished.postValue(true)
                    }
                } else {
                    udpReceiverData.append(String(rp.data, 0, rp.data.indexOf(0)))
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
}