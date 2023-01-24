package com.adreal.tcp_ip.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adreal.tcp_ip.DataClass.ConnectionData
import com.adreal.tcp_ip.databinding.ItemLayoutBinding

class PeopleAdapter(private val context: Context,private val onItemClickListener : OnItemClickListener) : RecyclerView.Adapter<PeopleAdapter.MyViewHolder>() {

    lateinit var binding: ItemLayoutBinding

    private var peopleList = emptyList<ConnectionData>()

    interface OnItemClickListener
    {
        fun onItemClick(data : ConnectionData)
    }

    class MyViewHolder(binding: ItemLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        var userConnectionDetail = binding.itemLayoutTextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        binding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return peopleList.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.userConnectionDetail.text = "${peopleList[position].ip} : ${peopleList[position].port}"

        holder.itemView.setOnClickListener {
            onItemClickListener.onItemClick(peopleList[position])
        }
    }

    fun setData(data : List<ConnectionData>){
        this.peopleList = data
        notifyDataSetChanged()
    }
}