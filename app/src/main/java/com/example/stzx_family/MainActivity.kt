package com.example.stzx_family

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    // 导航项ID与ViewPager2页码的映射
    private val navItemIdToPosition = mapOf(
        R.id.nav_map to 0,
        R.id.nav_device to 1,
        R.id.nav_user to 2
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true)
        com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_nav)

        // 设置 ViewPager2 适配器
        viewPager.adapter = VP2Adapter(this)
        // 关闭左右越界滑动效果
        viewPager.isUserInputEnabled = true

        // ViewPager2 页面切换 → 同步底部导航栏选中状态
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val navId = when (position) {
                    0 -> R.id.nav_map
                    1 -> R.id.nav_device
                    2 -> R.id.nav_user
                    else -> R.id.nav_map
                }
                bottomNav.menu.findItem(navId)?.isChecked = true
            }
        })

        // 底部导航栏点击 → 切换 ViewPager2 页面
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    viewPager.currentItem = 0
                    true
                }
                R.id.nav_device -> {
                    viewPager.currentItem = 1
                    true
                }
                R.id.nav_user -> {
                    viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }

        // 处理从用户页跳转过来的历史轨迹请求
        if (intent?.getBooleanExtra("open_history", false) == true) {
            viewPager.currentItem = 0
        }
    }

    // 供 UserFragment 调用：切换到设备页
    fun switchToDevicePage() {
        viewPager.currentItem = 1
    }

    // 供 UserFragment 调用：切换到地图页
    fun switchToMapPage() {
        viewPager.currentItem = 0
    }
}
