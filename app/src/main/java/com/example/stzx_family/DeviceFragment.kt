package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class DeviceFragment : Fragment() {

    private val uniCloudUrl = "https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation"
    private val client = OkHttpClient()

    private lateinit var rvDeviceList: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var tvDeviceCount: TextView
    private lateinit var btnAddDevice: Button
    private lateinit var adapter: DeviceAdapter

    private val deviceList = mutableListOf<DeviceInfo>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_device, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvDeviceList = view.findViewById(R.id.rv_device_list)
        layoutEmpty = view.findViewById(R.id.layout_empty)
        tvDeviceCount = view.findViewById(R.id.tv_device_count)
        btnAddDevice = view.findViewById(R.id.btn_add_device)

        // 设置 RecyclerView
        adapter = DeviceAdapter(deviceList,
            onUnbindClick = { device, position ->
                // 点击解绑
                AlertDialog.Builder(requireContext())
                    .setTitle("解除绑定")
                    .setMessage("确定要解除设备 ${device.deviceId} 的绑定吗？")
                    .setPositiveButton("确定") { _, _ ->
                        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
                        val phone = prefs.getString("PHONE", "")
                        unbindDevice(phone ?: "", device.deviceId, position)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onRenameClick = { device, position ->
                showRenameDialog(device, position)
            }
        )
        rvDeviceList.layoutManager = LinearLayoutManager(requireContext())
        rvDeviceList.adapter = adapter

        // 空状态 → 去绑定按钮
        view.findViewById<Button>(R.id.btn_go_bind).setOnClickListener {
            val intent = Intent(requireContext(), BindActivity::class.java)
            startActivity(intent)
        }

        // 添加新设备按钮
        btnAddDevice.setOnClickListener {
            val intent = Intent(requireContext(), BindActivity::class.java)
            startActivity(intent)
        }

        // 拉取设备列表
        fetchDeviceList()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到该页面时刷新列表（可能从BindActivity绑定了新设备回来）
        fetchDeviceList()
    }

    private fun fetchDeviceList() {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val phone = prefs.getString("PHONE", "")

        // 构造请求：获取该手机号下所有已绑定设备
        val json = JSONObject()
        json.put("action", "getDeviceList")
        json.put("phone", phone)

        val request = Request.Builder()
            .url(uniCloudUrl)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    // 云端接口不可用时，降级为本地 SharedPreferences 读取
                    loadFromLocal()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                activity?.runOnUiThread {
                    try {
                        val resJson = JSONObject(resStr ?: "")
                        if (resJson.getBoolean("success")) {
                            val dataArray = resJson.optJSONArray("data")
                            if (dataArray != null && dataArray.length() > 0) {
                                // 读取本地已绑定设备列表，用于过滤已本地解绑的设备
                                val localIds = (prefs.getString("BOUND_DEVICE_IDS", "") ?: "")
                                    .split(",").filter { it.isNotEmpty() }.toSet()

                                val newList = mutableListOf<DeviceInfo>()
                                val serverIds = mutableListOf<String>()
                                for (i in 0 until dataArray.length()) {
                                    val item = dataArray.getJSONObject(i)
                                    val devId = item.optString("deviceId", "")
                                    if (devId.isNotEmpty()) serverIds.add(devId)
                                    // 仅显示本地列表中仍存在的设备（过滤掉已本地解绑的）
                                    if (devId.isNotEmpty() && (localIds.isEmpty() || devId in localIds)) {
                                        newList.add(DeviceInfo(
                                            deviceId = devId,
                                            status = item.optString("status", "offline"),
                                            lastUpdate = item.optString("lastUpdate", "未知"),
                                            name = getDeviceName(devId)
                                        ))
                                    }
                                }

                                // 首次同步：如果本地列表为空但服务端有设备，将服务端设备写入本地
                                if (localIds.isEmpty() && serverIds.isNotEmpty()) {
                                    prefs.edit()
                                        .putString("BOUND_DEVICE_IDS", serverIds.joinToString(","))
                                        .putString("BOUND_DEVICE_ID", serverIds.first())
                                        .apply()
                                    // 重新构建列表（包含所有服务端设备）
                                    newList.clear()
                                    for (i in 0 until dataArray.length()) {
                                        val item = dataArray.getJSONObject(i)
                                        val devId = item.optString("deviceId", "")
                                        if (devId.isNotEmpty()) {
                                            newList.add(DeviceInfo(
                                                deviceId = devId,
                                                status = item.optString("status", "offline"),
                                                lastUpdate = item.optString("lastUpdate", "未知"),
                                                name = getDeviceName(devId)
                                            ))
                                        }
                                    }
                                }

                                updateUI(newList)
                            } else {
                                // 接口成功但无数据，尝试本地
                                loadFromLocal()
                            }
                        } else {
                            loadFromLocal()
                        }
                    } catch (e: Exception) {
                        loadFromLocal()
                    }
                }
            }
        })
    }

    // 降级方案：从 SharedPreferences 读取所有已绑定设备
    private fun loadFromLocal() {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val idsStr = prefs.getString("BOUND_DEVICE_IDS", "") ?: ""
        val idList = idsStr.split(",").filter { it.isNotEmpty() }

        if (idList.isNotEmpty()) {
            val newList = idList.map { devId ->
                DeviceInfo(
                    deviceId = devId,
                    status = "online",
                    lastUpdate = "本地记录",
                    name = getDeviceName(devId)
                )
            }
            updateUI(newList)
        } else {
            // 兼容旧数据：如果 BOUND_DEVICE_IDS 为空但有 BOUND_DEVICE_ID
            val boundId = prefs.getString("BOUND_DEVICE_ID", "")
            if (!boundId.isNullOrEmpty()) {
                // 迁移旧数据到多设备列表
                prefs.edit().putString("BOUND_DEVICE_IDS", boundId).apply()
                updateUI(listOf(DeviceInfo(
                    deviceId = boundId,
                    status = "online",
                    lastUpdate = "本地记录",
                    name = getDeviceName(boundId)
                )))
            } else {
                updateUI(emptyList())
            }
        }
    }

    private fun updateUI(newList: List<DeviceInfo>) {
        adapter.replaceAll(newList)

        if (newList.isEmpty()) {
            rvDeviceList.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            btnAddDevice.visibility = View.GONE
            tvDeviceCount.text = "暂无绑定设备"
        } else {
            rvDeviceList.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            btnAddDevice.visibility = View.VISIBLE
            tvDeviceCount.text = "已绑定 ${newList.size} 台设备"
        }
    }

    private fun unbindDevice(phone: String, deviceId: String, position: Int) {
        // 先执行本地解绑（移除SharedPreferences记录），确保即使云端不支持解绑也能正常使用
        removeDeviceLocal(deviceId)
        // 从列表中移除该设备（带动画）
        adapter.removeAt(position)
        // 检查是否全部移除
        if (deviceList.isEmpty()) {
            updateUI(emptyList())
        } else {
            tvDeviceCount.text = "已绑定 ${deviceList.size} 台设备"
        }
        Toast.makeText(requireContext(), "解绑成功", Toast.LENGTH_SHORT).show()

        // 尝试云端解绑（最佳努力，成功更好，失败不影响本地结果）
        tryCloudUnbind(phone, deviceId)
    }

    // 本地解绑：从SharedPreferences中移除设备记录
    private fun removeDeviceLocal(deviceId: String) {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val idsStr = prefs.getString("BOUND_DEVICE_IDS", "") ?: ""
        val idSet = idsStr.split(",").filter { it.isNotEmpty() }.toMutableSet()
        idSet.remove(deviceId)
        val editor = prefs.edit()
        if (idSet.isEmpty()) {
            editor.remove("BOUND_DEVICE_IDS").remove("BOUND_DEVICE_ID")
        } else {
            editor.putString("BOUND_DEVICE_IDS", idSet.joinToString(","))
            // 如果移除的是当前主设备，更新主设备为列表中的第一个
            if (prefs.getString("BOUND_DEVICE_ID", "") == deviceId) {
                editor.putString("BOUND_DEVICE_ID", idSet.first())
            }
        }
        // 同时清除该设备的自定义名称
        editor.remove("DEVICE_NAME_$deviceId")
        editor.apply()
    }

    // 尝试云端解绑（最佳努力）
    private fun tryCloudUnbind(phone: String, deviceId: String) {
        val json = JSONObject()
        json.put("action", "unbindDevice")
        json.put("phone", phone)
        json.put("deviceId", deviceId)

        val request = Request.Builder()
            .url(uniCloudUrl)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 云端解绑失败不影响本地结果，仅打印日志
                android.util.Log.w("DeviceFragment", "云端解绑失败（网络异常）: $deviceId", e)
            }
            override fun onResponse(call: Call, response: Response) {
                // 云端解绑结果不影响本地，仅打印日志
                android.util.Log.d("DeviceFragment", "云端解绑响应: ${response.body?.string()}")
            }
        })
    }

    // 重命名弹窗
    private fun showRenameDialog(device: DeviceInfo, position: Int) {
        val editText = EditText(requireContext()).apply {
            hint = "给盲杖取个名字"
            text = android.text.Editable.Factory.getInstance().newEditable(device.name)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("重命名设备")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                // 存入 SharedPreferences
                val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
                if (newName.isEmpty()) {
                    // 名字为空则删除自定义名，回退到默认
                    prefs.edit().remove("DEVICE_NAME_${device.deviceId}").apply()
                } else {
                    prefs.edit().putString("DEVICE_NAME_${device.deviceId}", newName).apply()
                }
                // 更新列表中的设备名
                deviceList[position] = device.copy(name = newName)
                adapter.notifyItemChanged(position)
                Toast.makeText(requireContext(), "已重命名", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 从 SharedPreferences 读取设备自定义名称
    private fun getDeviceName(deviceId: String): String {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("DEVICE_NAME_$deviceId", "") ?: ""
    }
}
