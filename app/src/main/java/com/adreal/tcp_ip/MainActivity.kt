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
import androidx.lifecycle.ViewModelProvider
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
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

    companion object {
        const val PORT = 60001
        const val GOOGLE_STUN_SERVER_IP = "74.125.197.127"
        const val GOOGLE_STUN_SERVER_PORT = 19302
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme()
        setContentView(binding.root)

        initDialog()
        //showDialog()

        CoroutineScope(Dispatchers.IO).launch {
            bindingRequest()
        }

        binding.mainActivityConfigureButton.setOnClickListener {
            showDialog()
        }

        binding.mainActivityPrivateCredentials.text = "${getIPAddress(true)} : ${PORT.toString()}"
        //binding.mainActivitySendingPort.text = PORT.toString()
    }

    private fun bindingRequest() {
        val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        val changeRequest = ChangeRequest()
        sendMH.addMessageAttribute(changeRequest)
        val data = sendMH.bytes
        val s = DatagramSocket(PORT)
        s.reuseAddress = true
        var x = 0

        CoroutineScope(Dispatchers.IO).launch {
            val p = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName(GOOGLE_STUN_SERVER_IP),
                GOOGLE_STUN_SERVER_PORT
            )
            s.send(p)

            CoroutineScope(Dispatchers.IO).launch {
                val rp = DatagramPacket(ByteArray(32), 32)
                s.receive(rp)
                val receiveMH =
                    MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
                receiveMH.parseAttributes(rp.data)
                val ma: MappedAddress =
                    receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress

                CoroutineScope(Dispatchers.Main.immediate).launch {
                    binding.mainActivityPublicCredentials.text =
                        "${ma.address.toString()} : ${ma.port.toString()}"
                    //binding.mainActivityRouterPortTextView.text = ma.port.toString()
                    x++
                }
            }
        }

        binding.mainActivityUDPClientButton.setOnClickListener {
            if (x == 0) {
//                CoroutineScope(Dispatchers.IO).launch {
//                    val p = DatagramPacket(
//                        data,
//                        data.size,
//                        InetAddress.getByName(GOOGLE_STUN_SERVER_IP),
//                        GOOGLE_STUN_SERVER_PORT
//                    )
//                    s.send(p)
//
//                    CoroutineScope(Dispatchers.IO).launch {
//                        val rp = DatagramPacket(ByteArray(32), 32)
//                        s.receive(rp)
//                        val receiveMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
//                        receiveMH.parseAttributes(rp.data)
//                        val ma: MappedAddress = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress
//
//                        CoroutineScope(Dispatchers.Main.immediate).launch {
//                            binding.mainActivityRouterIpTextView.text = ma.address.toString()
//                            binding.mainActivityRouterPortTextView.text = ma.port.toString()
//                            x++
//                        }
//                    }
//                }
            } else {

                val port = mainActivityViewModel.receiverPORT
                val ip = mainActivityViewModel.receiverIP

//                CoroutineScope(Dispatchers.Main.immediate).launch {
//                    binding.mainActivityUDPStatusTextView.text = "UDP is configured on $ip : $port"
//                }

                val data1 = binding.mainActivityUDPClientEditText.text.toString().toByteArray()

                addMessage(binding.mainActivityUDPClientEditText.text.toString(), true)

                binding.mainActivityUDPClientEditText.text.clear()

                CoroutineScope(Dispatchers.IO).launch {
                    val p = DatagramPacket(
                        data1, data1.size,
                        withContext(Dispatchers.IO) {
                            InetAddress.getByName(ip)
                        }, port
                    )
                    withContext(Dispatchers.IO) {
                        s.send(p)
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    while (true) {
                        val rp = DatagramPacket(ByteArray(512), 512)
                        s.receive(rp)
                        val data2 = String(rp.data)
                        Log.d("data", data2)
                        CoroutineScope(Dispatchers.Main.immediate).launch {
//                            binding.mainActivityReceivedMessage.text = data2
//                            binding.mainActivityReceivedMessage.setAutoSizeTextTypeUniformWithConfiguration(
//                                10,
//                                50,
//                                1,
//                                TypedValue.COMPLEX_UNIT_DIP
//                            )

                            addMessage(data2, false)
                        }
                    }
                }
            }
        }
    }

    private fun addMessage(message: String, me: Boolean) {
        if (me) {
            binding.mainActivityChatTextView.append("\n\nYou : $message")
        } else {
            binding.mainActivityChatTextView.append("\n\nFriend : $message")
        }
        binding.mainActivityScrollBar.fullScroll(View.FOCUS_DOWN)
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
        } // for now eat exceptions
        return ""
    }

    private fun initDialog() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun showDialog() {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val bind = ConfigureBinding.inflate(layoutInflater)

        bind.configureDialogDoneButton.setOnClickListener {
            if (bind.configureDialogReceiverIP.text.isNotBlank() && bind.configureDialogReceiverPORT.text.isNotBlank()) {
                mainActivityViewModel.receiverIP = bind.configureDialogReceiverIP.text.toString()
                mainActivityViewModel.receiverPORT =
                    bind.configureDialogReceiverPORT.text.toString().toInt()
                dialog.dismiss()
            }
        }

        //dialog.setCancelable(false)
        dialog.setContentView(bind.root)
        dialog.show()
    }
}