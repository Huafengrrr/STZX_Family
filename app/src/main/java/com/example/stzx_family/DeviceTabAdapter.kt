package com.example.stzx_family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 地图页底部设备切换标签的适配器
 */
class DeviceTabAdapter(
    private val devices: MutableList<DeviceInfo> = mutableListOf(),
    private var selectedPosition: Int = 0,
    private val onTabClick: (DeviceInfo, Int) -> Unit
) : RecyclerView.Adapter<DeviceTabAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceName: TextView = itemView.findViewById(R.id.tv_tab_device_name)
        val viewStatusDot: View = itemView.findViewById(R.id.view_tab_status_dot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        val isSelected = position == selectedPosition

        // 设备名称
        holder.tvDeviceName.text = "盲杖 #${position + 1}"

        // 选中/未选中样式
        if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_device_tab_selected)
            holder.tvDeviceName.setTextColor(holder.itemView.context.getColor(R.color.white))
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_device_tab_unselected)
            holder.tvDeviceName.setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
        }

        // 状态圆点
        val dotRes = when (device.status) {
            "online" -> R.drawable.shape_status_dot_online
            "alarm" -> R.drawable.shape_status_dot_alarm
            else -> R.drawable.shape_status_dot_offline
        }
        holder.viewStatusDot.setBackgroundResource(dotRes)

        // 点击切换
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos != selectedPosition) {
                val oldPos = selectedPosition
                selectedPosition = pos
                notifyItemChanged(oldPos)
                notifyItemChanged(pos)
                onTabClick(devices[pos], pos)
            }
        }
    }

    override fun getItemCount(): Int = devices.size

    // 全部替换设备列表
    fun replaceAll(newList: List<DeviceInfo>) {
        devices.clear()
        devices.addAll(newList)
        selectedPosition = if (newList.isNotEmpty()) 0 else -1
        notifyDataSetChanged()
    }

    // 选中指定位置
    fun setSelectedPosition(pos: Int) {
        if (pos == selectedPosition) return
        val oldPos = selectedPosition
        selectedPosition = pos
        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (pos >= 0) notifyItemChanged(pos)
    }

    // 获取当前选中设备
    fun getSelectedDevice(): DeviceInfo? {
        return if (selectedPosition in devices.indices) devices[selectedPosition] else null
    }
}
