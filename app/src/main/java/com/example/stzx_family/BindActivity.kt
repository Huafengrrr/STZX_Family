package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import java.util.concurrent.TimeUnit

class BindActivity : AppCompatActivity() {

    private val TAG = "BindActivity"
    private val uniCloudUrl = "https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation"
    // 设置超时：连接 10 秒，读取 15 秒，写入 15 秒
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bind)

        val etCode = findViewById<EditText>(R.id.et_pairing_code)
        val btnBind = findViewById<Button>(R.id.btn_bind)
        val prefs = getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val currentPhone = prefs.getString("PHONE", "")

        // 安全检查：未登录则跳回登录页
        if (currentPhone.isNullOrEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 返回按钮逻辑：已登录则回主页，未登录则回登录页
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            val hasPhone = !prefs.getString("PHONE", "").isNullOrEmpty()
            if (hasPhone) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            } else {
                prefs.edit().clear().apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            finish()
        }

        btnBind.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "请输入完整的 6 位配对码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 禁用按钮，防止重复点击
            btnBind.isEnabled = false
            btnBind.text = "绑定中..."

            val json = JSONObject()
            json.put("action", "bindDevice")
            json.put("phone", currentPhone)
            json.put("pairingCode", code)

            Log.d(TAG, "请求绑定: phone=$currentPhone, code=$code")

            val request = Request.Builder()
                .url(uniCloudUrl)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "网络请求失败", e)
                    runOnUiThread {
                        btnBind.isEnabled = true
                        btnBind.text = "立即绑定"
                        Toast.makeText(this@BindActivity, "网络异常: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val resStr = response.body?.string()
                    Log.d(TAG, "响应: code=${response.code}, body=$resStr")
                    runOnUiThread {
                        btnBind.isEnabled = true
                        btnBind.text = "立即绑定"
                        try {
                            val resJson = JSONObject(resStr ?: "{}")
                            if (resJson.optBoolean("success", false)) {
                                Toast.makeText(this@BindActivity, "绑定成功！", Toast.LENGTH_SHORT).show()
                                val deviceId = resJson.optString("deviceId", "")
                                if (deviceId.isNotEmpty()) {
                                    prefs.edit().putString("BOUND_DEVICE_ID", deviceId).apply()
                                }
                                val intent = Intent(this@BindActivity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                val msg = resJson.optString("msg", "绑定失败")
                                Toast.makeText(this@BindActivity, msg, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析响应异常: resStr=$resStr", e)
                            Toast.makeText(this@BindActivity, "服务器返回数据异常", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }
}
