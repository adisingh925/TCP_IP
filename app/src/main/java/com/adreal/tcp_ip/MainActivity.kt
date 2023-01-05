package com.adreal.tcp_ip

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.adreal.tcp_ip.databinding.ActivityMainBinding
import de.javawi.jstun.attribute.ChangeRequest
import de.javawi.jstun.attribute.MappedAddress
import de.javawi.jstun.attribute.MessageAttributeInterface
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    companion object {
        const val PORT = 60001
        const val IP_ADDRESS = "127.0.0.1"
        const val UDP_IP_ADDRESS = "192.168.74.219"
        const val GOOGLE_STUN_SERVER_IP = "74.125.197.127"
        const val GOOGLE_STUN_SERVER_PORT = 19302
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.mainActivityUDPServerButton.isEnabled = false
        binding.mainActivityUDPClientButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            bindingRequest()
        }

        CoroutineScope(Dispatchers.IO).launch {
            //udpServer()
        }

        binding.mainActivityConfigureUDPButton.setOnClickListener {
            if(binding.mainActivityUDPIpAddressEditText.text.isNotBlank() && binding.mainActivityUDPPortEditText.text.isNotBlank()){

                binding.mainActivityUDPClientButton.isEnabled = true

//                CoroutineScope(Dispatchers.IO).launch {
//                    udpServer()
//                }

                CoroutineScope(Dispatchers.IO).launch {
                    udpClient()
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            server()
        }

        CoroutineScope(Dispatchers.IO).launch {
            client()
        }

        binding.mainActivityIPAddress.text = getIPAddress(true)
        binding.mainActivitySendingPort.text = PORT.toString()
    }

    private fun bindingRequest() {
        val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        val changeRequest = ChangeRequest()
        sendMH.addMessageAttribute(changeRequest)
        val data = sendMH.bytes
        val s = DatagramSocket()
        s.reuseAddress = true

        CoroutineScope(Dispatchers.IO).launch {
            val p = DatagramPacket(data, data.size,
                withContext(Dispatchers.IO) {
                    InetAddress.getByName(GOOGLE_STUN_SERVER_IP)
                }, GOOGLE_STUN_SERVER_PORT
            )
            withContext(Dispatchers.IO) {
                s.send(p)
            }
        }

        val rp = DatagramPacket(ByteArray(32), 32)
        s.receive(rp)

        val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        receiveMH.parseAttributes(rp.data)
        val ma: MappedAddress = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

        CoroutineScope(Dispatchers.Main.immediate).launch {
            binding.mainActivityRouterIpTextView.text = ma.address.toString()
            binding.mainActivityRouterPortTextView.text = ma.port.toString()
        }
    }

    private fun udpServer() {
        //val port = binding.mainActivityUDPPortEditText.text.toString().toInt()
        val socket = DatagramSocket(PORT)
        var buffer = ByteArray(512)

        var clientAddress = InetAddress.getByName("localhost")
        var clientPort = 0

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {

                val request = DatagramPacket(buffer, buffer.size)
                withContext(Dispatchers.IO) {
                    socket.receive(request)
                }

                val data = String(request.data)
                buffer = ByteArray(512)
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    binding.mainActivityUDPServerTextView.text = "UDP Server : $data"
                }

                clientAddress = request.address
                clientPort = request.port

                Log.d("address port","$clientAddress + $clientPort")
            }
        }

        binding.mainActivityUDPServerButton.setOnClickListener {
            val data = binding.mainActivityUDPServerEditText.text.toString().toByteArray()
            CoroutineScope(Dispatchers.IO).launch {
                val response = DatagramPacket(data, data.size, clientAddress, clientPort)
                withContext(Dispatchers.IO) {
                    socket.send(response)
                }
            }
        }
    }

    private fun udpClient() {

        val port = binding.mainActivityUDPPortEditText.text.toString().toInt()
        val ip = binding.mainActivityUDPIpAddressEditText.text.toString()

        CoroutineScope(Dispatchers.Main.immediate).launch {
            binding.mainActivityUDPStatusTextView.text = "UDP is running on $ip : $port"
        }

        val address = InetAddress.getByName(ip)
        val socket = DatagramSocket(PORT)
        var buffer = ByteArray(512)

        binding.mainActivityUDPClientButton.setOnClickListener {
            binding.mainActivityUDPServerButton.isEnabled = true
            val data = binding.mainActivityUDPClientEditText.text.toString().toByteArray()
            CoroutineScope(Dispatchers.IO).launch {
                val request = DatagramPacket(data, data.size, address, port)
                withContext(Dispatchers.IO) {
                    socket.send(request)
                }
            }
        }

        while (true) {
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val data = String(response.data)
            buffer = ByteArray(256)
            CoroutineScope(Dispatchers.Main.immediate).launch {
                binding.mainActivityUDPClientTextView.text = "UDP Client : $data"
            }
        }
    }

    private fun server() {
        val server = ServerSocket(PORT)
        val client = server.accept()
        val output = PrintWriter(client.getOutputStream(), true)
        val input = BufferedReader(InputStreamReader(client.inputStream))

        binding.mainActivityServerButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                output.println(binding.mainActivityServerEditText.text.toString())
            }
        }

        while (true) {
            val receivedData = input.readLine()
            println("Server receiving [${receivedData}]")
            CoroutineScope(Dispatchers.Main.immediate).launch {
                binding.mainActivityServerTextView.text = "TCP Server : $receivedData"
            }
        }
    }

    private fun client() {
        val client = Socket(IP_ADDRESS, PORT)
        val output = PrintWriter(client.getOutputStream(), true)
        val input = BufferedReader(InputStreamReader(client.inputStream))

        binding.mainActivityClientButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                output.println(binding.mainActivityClientEditText.text.toString())
            }
        }

        while (true) {
            val receivedData = input.readLine()
            println("Client receiving [${receivedData}]")
            CoroutineScope(Dispatchers.Main.immediate).launch {
                binding.mainActivityClientTextView.text = "TCP Client : $receivedData"
            }
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
        } // for now eat exceptions
        return ""
    }
}