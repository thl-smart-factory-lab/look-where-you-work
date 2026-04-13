package com.example.lookwhereyouwork.ui.mqtt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lookwhereyouwork.R
import com.example.lookwhereyouwork.model.TopicMessage
import com.example.lookwhereyouwork.mqtt.MqttConfig

class TopicAdapter : RecyclerView.Adapter<TopicAdapter.VH>() {

    private var data: List<Pair<String, TopicMessage?>> = emptyList()

    fun submit(lastByTopic: Map<String, TopicMessage>) {
        data = lastByTopic.entries
            .sortedBy { it.key }
            .map { it.key to it.value }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_topic, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (topic, msg) = data[position]
        holder.txtTopic.text = topic

        val payload = msg?.payload ?: "<no message>"
        holder.txtPayload.text = if (payload.length > 100) payload.take(100) + "…" else payload
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val txtTopic: TextView = v.findViewById(R.id.txtTopic)
        val txtPayload: TextView = v.findViewById(R.id.txtPayload)
    }
}
