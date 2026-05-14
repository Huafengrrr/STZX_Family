package com.example.stzx_family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class UserFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_user, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val phone = prefs.getString("PHONE", "")

        // 显示手机号
        val tvPhone = view.findViewById<TextView>(R.id.tv_user_phone)
        if (!phone.isNullOrEmpty()) {
            val masked = if (phone.length == 11) {
                phone.substring(0, 3) + "****" + phone.substring(7)
            } else phone
            tvPhone.text = "+86 $masked"
        }

        // 我的设备 → 切换到设备页
        view.findViewById<android.widget.LinearLayout>(R.id.layout_my_device).setOnClickListener {
            // 通过 MainActivity 切换 ViewPager2 页面
            (activity as? MainActivity)?.switchToDevicePage()
        }

        // 历史轨迹 → 切换到地图页
        view.findViewById<android.widget.LinearLayout>(R.id.layout_history).setOnClickListener {
            (activity as? MainActivity)?.switchToMapPage()
        }

        // 退出登录
        view.findViewById<Button>(R.id.btn_logout).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定") { _, _ ->
                    prefs.edit().clear().apply()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
