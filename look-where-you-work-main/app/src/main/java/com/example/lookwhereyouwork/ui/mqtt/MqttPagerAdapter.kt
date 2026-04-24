package com.example.lookwhereyouwork.ui.mqtt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lookwhereyouwork.R

class MqttPagerAdapter(
    private val onMapPageBound: (View) -> Unit,
    private val onPrinterPageBound: (View) -> Unit
) : RecyclerView.Adapter<MqttPagerAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val layoutId = if (viewType == PAGE_MAP) {
            R.layout.page_mqtt_map
        } else {
            R.layout.page_printer_topic
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return PageViewHolder(view)
    }

    override fun getItemCount(): Int = 2

    override fun getItemViewType(position: Int): Int = position

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        if (position == PAGE_MAP) {
            onMapPageBound(holder.itemView)
        } else {
            onPrinterPageBound(holder.itemView)
        }
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private companion object {
        const val PAGE_MAP = 0
    }
}
