package com.adreal.tcp_ip.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import com.adreal.tcp_ip.DataClass.ChatModel
import com.adreal.tcp_ip.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ChatAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messageList = ArrayList<ChatModel>()

    private val senderView = 1

    private val receiverView = 2

    private inner class ViewHolder1(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var senderTextView: TextView = itemView.findViewById(R.id.senderMessage)
        val senderTime: TextView = itemView.findViewById(R.id.senderTime)

        fun bind(position: Int) {
            val time = getDate(messageList[position].id.toString().toLong(), "hh:mm aa")
            senderTextView.text = messageList[position].msg
            senderTime.text = time
        }
    }

    private inner class ViewHolder2(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val receiverTextView: TextView = itemView.findViewById(R.id.receiverMessage)
        val receiverTime: TextView = itemView.findViewById(R.id.receiverTime)

        fun bind(position: Int) {
            val time = getDate(messageList[position].id.toString().toLong(), "hh:mm aa")
            receiverTextView.text = messageList[position].msg
            receiverTime.text = time
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            1 -> return ViewHolder1(
                LayoutInflater.from(context).inflate(R.layout.sender, parent, false)
            )
            2 -> return ViewHolder2(
                LayoutInflater.from(context).inflate(R.layout.receiver, parent, false)
            )
        }

        return ViewHolder2(LayoutInflater.from(context).inflate(R.layout.receiver, parent, false))
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (messageList[position].isReceived) {
            0 -> senderView
            1 -> receiverView
            else -> senderView
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (messageList[position].isReceived) {
            0 -> (holder as ViewHolder1).bind(position)
            1 -> (holder as ViewHolder2).bind(position)
        }
    }

    fun getDate(milliSeconds: Long, dateFormat: String?): String? {
        val formatter = SimpleDateFormat(dateFormat)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return formatter.format(calendar.time)
    }

    fun setData(data: ChatModel) {
        this.messageList.add(data)
        notifyDataSetChanged()
    }
}