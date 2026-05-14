package com.example.stzx_family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DeviceInfo(
    val deviceId: String,
    val status: String,      // "online" / "offline" / "alarm"
    val lastUpdate: String,  // 最后更新时间文本
    val name: String = ""    // 用户自定义设备名称，为空时显示默认名
)

class DeviceAdapter(
    private val devices: MutableList<DeviceInfo>,
    private val onUnbindClick: (DeviceInfo, Int) -> Unit,
    private val onRenameClick: (DeviceInfo, Int) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceName: TextView = itemView.findViewById(R.id.tv_item_device_name)
        val tvDeviceId: TextView = itemView.findViewById(R.id.tv_item_device_id)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_item_status)
        val tvLastUpdate: TextView = itemView.findViewById(R.id.tv_item_last_update)
        val btnUnbind: TextView = itemView.findViewById(R.id.btn_item_unbind)
        val ivRename: View = itemView.findViewById(R.id.iv_item_rename)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]

        // 设备名称：有自定义名则显示自定义名，否则显示默认名
        val displayName = if (device.name.isNotEmpty()) device.name else "盲杖设备 #${position + 1}"
        holder.tvDeviceName.text = displayName
        holder.tvDeviceId.text = "设备ID：${device.deviceId}"
        holder.tvLastUpdate.text = device.lastUpdate

        // 状态标签
        when (device.status) {
            "online" -> {
                holder.tvStatus.text = "在线"
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.status_safe))
            }
            "alarm" -> {
                holder.tvStatus.text = "警报"
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.status_danger))
            }
            else -> {
                holder.tvStatus.text = "离线"
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.text_hint))
            }
        }

        // 解绑按钮
        holder.btnUnbind.setOnClickListener {
            onUnbindClick(device, holder.adapterPosition)
        }

        // 重命名按钮
        holder.ivRename.setOnClickListener {
            onRenameClick(device, holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = devices.size

    // 删除指定位置的设备（解绑后调用）
    fun removeAt(position: Int) {
        devices.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, devices.size)
    }

    // 全部替换
    fun replaceAll(newList: List<DeviceInfo>) {
        devices.clear()
        devices.addAll(newList)
        notifyDataSetChanged()
    }
}
