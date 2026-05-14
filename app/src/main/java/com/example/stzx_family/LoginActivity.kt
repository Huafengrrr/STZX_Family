package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val uniCloudUrl = "https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 自动登录检测：如果已经登录过，直接判断去绑定页还是主页
        val prefs = getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val savedPhone = prefs.getString("PHONE", "")
        if (!savedPhone.isNullOrEmpty()) {
            val boundId = prefs.getString("BOUND_DEVICE_ID", "")
            val intent = if (boundId.isNullOrEmpty()) {
                Intent(this, BindActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etPhone = findViewById<EditText>(R.id.et_phone)
        val etPassword = findViewById<EditText>(R.id.et_password)

        findViewById<Button>(R.id.btn_login).setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val pwd = etPassword.text.toString().trim()
            if (phone.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "手机号或密码不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 🌟 新增：手机号格式正则校验 (防手滑错输)
            if (!isValidPhone(phone)) {
                Toast.makeText(this, "请输入正确的11位中国大陆手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestCloud("login", phone, pwd)
        }

        findViewById<Button>(R.id.btn_register).setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val pwd = etPassword.text.toString().trim()
            if (phone.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "请输入手机号和密码进行注册", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 🌟 新增：手机号格式严格正则校验
            if (!isValidPhone(phone)) {
                Toast.makeText(this, "注册失败：请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 🌟 新增：限制密码强度，防止因为过短被轻易盗号
            if (pwd.length < 6) {
                Toast.makeText(this, "注册失败：密码长度不能少于6位", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestCloud("register", phone, pwd)
        }
    }

    // 🌟 新增：利用正则表达式判断是否为合法的中国大陆手机号
    private fun isValidPhone(phone: String): Boolean {
        // 规则：1开头，第二位是3-9，后面跟着9位数字，总共11位
        val regex = "^1[3-9]\\d{9}$".toRegex()
        return regex.matches(phone)
    }

    private fun requestCloud(action: String, phone: String, pwd: String) {
        val json = JSONObject()
        json.put("action", action)
        json.put("phone", phone)
        json.put("password", pwd)

        val request = Request.Builder()
            .url(uniCloudUrl)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@LoginActivity, "网络连接失败", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                runOnUiThread {
                    try {
                        val resJson = JSONObject(resStr ?: "")
                        if (resJson.getBoolean("success")) {
                            Toast.makeText(this@LoginActivity, resJson.getString("msg"), Toast.LENGTH_SHORT).show()
                            if (action == "login") {
                                // 保存登录状态
                                val boundId = resJson.optString("bound_device_id", "")
                                getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE).edit()
                                    .putString("PHONE", phone)
                                    .putString("BOUND_DEVICE_ID", boundId)
                                    .apply()

                                // 路由跳转（清空栈，确保返回键不会回到登录页）
                                val intent = if (boundId.isEmpty()) {
                                    Intent(this@LoginActivity, BindActivity::class.java)
                                } else {
                                    Intent(this@LoginActivity, MainActivity::class.java)
                                }
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            Toast.makeText(this@LoginActivity, resJson.getString("msg"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { Toast.makeText(this@LoginActivity, "解析异常", Toast.LENGTH_SHORT).show() }
                }
            }
        })
    }
}