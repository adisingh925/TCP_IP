package com.adreal.tcp_ip

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

const val MAXLINE = 200
const val STUN_METHOD_BINDING_REQUEST = 0x0001
const val STUN_ATTR_MAPPED_ADDRESS = 0x0020
const val STUN_MAGIC_COOKIE = 0x2112A442

class StunClient{
    fun main() {
//    if (args.size != 3) {
//        println("STUN(RFC5389) client demo by Chris <nodexy@gmail>\n")
//        println("usage: ${args[0]} <server_ip> <server_port> <local_port>\n")
//        return
//    }

        println("Main start ... ")

        val returnIpPort = StringBuilder()

        val n = stunXorAddr("74.125.197.127", 19302, 54421, returnIpPort)
        if (n != 0)
            println("STUN req error : $n")
        else
            println("ip:port = $returnIpPort")

        println("Main over.")
    }

    fun stunXorAddr(stunServerIp: String, stunServerPort: Int, localPort: Int, returnIpPort: StringBuilder): Int {
        val servaddr = InetAddress.getByName(stunServerIp)
        val localaddr = InetAddress.getByName("0.0.0.0")

        val socket = DatagramSocket(localPort, localaddr)
        val serverAddress = InetSocketAddress(servaddr, stunServerPort)

        println("Socket opened to $stunServerIp:$stunServerPort at local port $localPort")

        // First bind
        val bindingReq = ByteArray(20)
//        bindingReq[0] = (STUN_METHOD_BINDING_REQUEST shr 8).toByte()
//        bindingReq[1] = STUN_METHOD_BINDING_REQUEST.toByte()
//        bindingReq[4] = (STUN_MAGIC_COOKIE shr 24).toByte()
//        bindingReq[5] = (STUN_MAGIC_COOKIE shr 16).toByte()
//        bindingReq[6] = (STUN_MAGIC_COOKIE shr 8).toByte()
//        bindingReq[7] = STUN_MAGIC_COOKIE.toByte()
//        bindingReq[8] = 0x63
//        bindingReq[9] = 0xc7.toByte()
//        bindingReq[10] = 0x11
//        bindingReq[11] = 0x7e
//        bindingReq[12] = 0x07
//        bindingReq[13] = 0x14
//        bindingReq[14] = 0x27
//        bindingReq[15] = 0x8f.toByte()
//        bindingReq[16] = 0x5d
//        bindingReq[17] = 0xed.toByte()
//        bindingReq[18] = 0x32
//        bindingReq[19] = 0x21

        bindingReq[0] = (0x0001 shr 8 and 0xff).toByte()
        bindingReq[1] = (0x0001 and 0xff).toByte()
        bindingReq[2] = (0x0000 shr 8 and 0xff).toByte()
        bindingReq[3] = (0x0000 and 0xff).toByte()
        bindingReq[4] = (0x2112A442 shr 24 and 0xff).toByte()
        bindingReq[5] = (0x2112A442 shr 16 and 0xff).toByte()
        bindingReq[6] = (0x2112A442 shr 8 and 0xff).toByte()
        bindingReq[7] = (0x2112A442 and 0xff).toByte()
        bindingReq[8] = (0x63c7117e shr 24 and 0xff).toByte()
        bindingReq[9] = (0x63c7117e shr 16 and 0xff).toByte()
        bindingReq[10] = (0x63c7117e shr 8 and 0xff).toByte()
        bindingReq[11] = (0x63c7117e and 0xff).toByte()
        bindingReq[12] = (0x0714278f shr 24 and 0xff).toByte()
        bindingReq[13] = (0x0714278f shr 16 and 0xff).toByte()
        bindingReq[14] = (0x0714278f shr 8 and 0xff).toByte()
        bindingReq[15] = (0x0714278f and 0xff).toByte()
        bindingReq[16] = (0x5ded3221 shr 24 and 0xff).toByte()
        bindingReq[17] = (0x5ded3221 shr 16 and 0xff).toByte()
        bindingReq[18] = (0x5ded3221 shr 8 and 0xff).toByte()
        bindingReq[19] = (0x5ded3221 and 0xff).toByte()

        println("Sending data ... ")
        val packet = DatagramPacket(bindingReq, bindingReq.size, serverAddress)
        socket.send(packet)

        // Wait
        Thread.sleep(1000)

        println("Reading recv ... ")
        val recvPacket = DatagramPacket(ByteArray(MAXLINE), MAXLINE)
        socket.receive(recvPacket)
        val recvBuf = recvPacket.data

        for(i in recvBuf){
            print(String.format("%02x",i) + " ")
        }

        // Check transaction ID
        if (recvBuf[8] != 0x63.toByte() || recvBuf[9] != 0xc7.toByte() || recvBuf[10] != 0x11.toByte() || recvBuf[11] != 0x7e.toByte() || recvBuf[12] != 0x07.toByte() || recvBuf[13] != 0x14.toByte() || recvBuf[14] != 0x27.toByte() || recvBuf[15] != 0x8f.toByte() || recvBuf[16] != 0x5d.toByte() || recvBuf[17] != 0xed.toByte() || recvBuf[18] != 0x32.toByte() || recvBuf[19] != 0x21.toByte()) {
            println("Transaction ID error!")
            return -1
        }

        // Check message length
        val msgLength = ((recvBuf[2].toInt() and 0xff) shl 8) or (recvBuf[3].toInt() and 0xff)
//    if (msgLength != 20) {
//        println("Message length error!")
//        return -1
//    }

        // Parse attribute
        var i = 20
        while (i < recvPacket.length) {
            print("1")
            val attrType = ((recvBuf[i].toInt() and 0xff) shl 8) or (recvBuf[i + 1].toInt() and 0xff)
            val attrLength = ((recvBuf[i + 2].toInt() and 0xff) shl 8) or (recvBuf[i + 3].toInt() and 0xff)

            print(String.format("%02x",attrType) + String.format("%02x",attrLength))

            if (attrType == STUN_ATTR_MAPPED_ADDRESS) {
                print("2\n")
//            if (recvBuf[i + 4].toInt() != 0x00 || recvBuf[i + 6].toInt() != 0x00) {
//                println("Non IPv4 address!")
//                return -1
//            }

                val port = ((recvBuf[i + 5].toInt() and 0xff) shl 8) or (recvBuf[i + 6].toInt() and 0xff)  xor 0x2112
                returnIpPort.append((recvBuf[i + 8].toInt() and 0xff) xor 0x21).append('.').append((recvBuf[i + 9].toInt() and 0xff) xor 0x12).append('.').append((recvBuf[i + 10].toInt() and 0xff) xor 0xa4).append('.').append((recvBuf[i + 11].toInt() and 0xff) xor 0x42).append(':').append(port)
                //print(port)
                break
            }

            i += attrLength + 4
        }

        return 0
    }
}

