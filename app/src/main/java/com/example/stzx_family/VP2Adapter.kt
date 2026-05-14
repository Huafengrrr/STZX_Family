package com.example.stzx_family

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class VP2Adapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // 页面顺序：实时监控(地图) → 我的设备 → 用户
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MapFragment()
            1 -> DeviceFragment()
            2 -> UserFragment()
            else -> MapFragment()
        }
    }
}
