package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class BindActivity : AppCompatActivity() {

    private val uniCloudUrl = "https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bind)

        val etCode = findViewById<EditText>(R.id.et_pairing_code)
        val prefs = getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val currentPhone = prefs.getString("PHONE", "")

        // 🌟 返回按钮逻辑：已登录则回主页，未登录则回登录页
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            val hasPhone = !prefs.getString("PHONE", "").isNullOrEmpty()
            if (hasPhone) {
                // 已登录状态 → 返回主页（清空栈上方的Activity）
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            } else {
                // 未登录状态 → 返回登录页
                prefs.edit().clear().apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            finish()
        }

        findViewById<Button>(R.id.btn_bind).setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "请输入完整的 6 位配对码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject()
            json.put("action", "bindDevice")
            json.put("phone", currentPhone)
            json.put("pairingCode", code)

            val request = Request.Builder().url(uniCloudUrl).post(json.toString().toRequestBody("application/json".toMediaType())).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { Toast.makeText(this@BindActivity, "网络异常", Toast.LENGTH_SHORT).show() }
                }
                override fun onResponse(call: Call, response: Response) {
                    val resStr = response.body?.string()
                    runOnUiThread {
                        try {
                            val resJson = JSONObject(resStr ?: "")
                            if (resJson.getBoolean("success")) {
                                Toast.makeText(this@BindActivity, "绑定成功！", Toast.LENGTH_SHORT).show()
                                val deviceId = resJson.getString("deviceId")
                                // 保存绑定结果
                                prefs.edit().putString("BOUND_DEVICE_ID", deviceId).apply()
                                // 跳转到地图监控主页（清空栈，确保返回键不会回到登录页）
                                val intent = Intent(this@BindActivity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@BindActivity, resJson.getString("msg"), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) { Toast.makeText(this@BindActivity, "数据异常", Toast.LENGTH_SHORT).show() }
                    }
                }
            })
        }
    }
}