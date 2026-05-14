package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class DeviceActivity : AppCompatActivity() {

    private val uniCloudUrl = "https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        val prefs = getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val boundId = prefs.getString("BOUND_DEVICE_ID", "")
        val phone = prefs.getString("PHONE", "")

        val cardDeviceInfo = findViewById<android.widget.LinearLayout>(R.id.card_device_info)
        val cardNoDevice = findViewById<android.widget.LinearLayout>(R.id.card_no_device)
        val tvDeviceId = findViewById<TextView>(R.id.tv_device_id)
        val tvBindStatus = findViewById<TextView>(R.id.tv_bind_status)

        // 根据绑定状态切换卡片显示
        if (!boundId.isNullOrEmpty()) {
            cardDeviceInfo.visibility = android.view.View.VISIBLE
            cardNoDevice.visibility = android.view.View.GONE
            tvDeviceId.text = "设备ID：$boundId"
        } else {
            cardDeviceInfo.visibility = android.view.View.GONE
            cardNoDevice.visibility = android.view.View.VISIBLE
        }

        // 解绑按钮
        findViewById<Button>(R.id.btn_unbind).setOnClickListener {
            if (boundId.isNullOrEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("解除绑定")
                .setMessage("确定要解除该设备的绑定吗？")
                .setPositiveButton("确定") { _, _ ->
                    unbindDevice(phone ?: "", boundId)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 未绑定时"去绑定"按钮 → 跳转到 BindActivity
        findViewById<Button>(R.id.btn_go_bind).setOnClickListener {
            startActivity(Intent(this, BindActivity::class.java))
            finish()
        }
    }

    private fun unbindDevice(phone: String, deviceId: String) {
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
                runOnUiThread { Toast.makeText(this@DeviceActivity, "网络异常", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                runOnUiThread {
                    try {
                        val resJson = JSONObject(resStr ?: "")
                        if (resJson.getBoolean("success")) {
                            Toast.makeText(this@DeviceActivity, "解绑成功", Toast.LENGTH_SHORT).show()
                            // 清除本地绑定信息
                            getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE).edit()
                                .remove("BOUND_DEVICE_ID")
                                .apply()
                            // 刷新页面
                            recreate()
                        } else {
                            Toast.makeText(this@DeviceActivity, resJson.optString("msg", "解绑失败"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@DeviceActivity, "数据异常", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
