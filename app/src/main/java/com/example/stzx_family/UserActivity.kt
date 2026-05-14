package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class UserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        val prefs = getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val phone = prefs.getString("PHONE", "")

        // 显示手机号
        val tvPhone = findViewById<TextView>(R.id.tv_user_phone)
        if (!phone.isNullOrEmpty()) {
            val masked = if (phone.length == 11) {
                phone.substring(0, 3) + "****" + phone.substring(7)
            } else phone
            tvPhone.text = "+86 $masked"
        }

        // 我的设备 → 跳转到 DeviceActivity
        findViewById<android.widget.LinearLayout>(R.id.layout_my_device).setOnClickListener {
            startActivity(Intent(this, DeviceActivity::class.java))
        }

        // 历史轨迹 → 返回 MainActivity 并触发历史选择
        findViewById<android.widget.LinearLayout>(R.id.layout_history).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("open_history", true)
            startActivity(intent)
            finish()
        }

        // 退出登录
        findViewById<Button>(R.id.btn_logout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定") { _, _ ->
                    prefs.edit().clear().apply()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
