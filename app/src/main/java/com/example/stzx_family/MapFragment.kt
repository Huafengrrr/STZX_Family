package com.example.stzx_family

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import okhttp3.*
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MapFragment : Fragment() {

    private val fallHistory = mutableListOf<JSONObject>()
    private lateinit var viewRedDot: View
    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var tvStatus: TextView
    private lateinit var tvTime: TextView
    private lateinit var layoutDeviceTabs: LinearLayout

    private var blindCaneMarker: Marker? = null
    private val client = OkHttpClient()
    private var pollingTimer: Timer? = null

    private val trackPoints = mutableListOf<LatLng>()
    private var trackPolyline: Polyline? = null

    private var lastTrackTimestamp: Long = 0L
    private var isHistoryMode = false

    // 当前选中的设备ID
    private var currentDeviceId: String = ""
    // 设备列表
    private val deviceList = mutableListOf<DeviceInfo>()

    private val uniCloudUrl = "https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation"
    private val AMAP_WEB_KEY = "66eafa772acc05e7a8e587b4bd69074e"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tv_status)
        tvTime = view.findViewById(R.id.tv_time)
        viewRedDot = view.findViewById(R.id.view_red_dot)
        layoutDeviceTabs = view.findViewById(R.id.layout_device_tabs)

        mapView = view.findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        aMap.uiSettings.isZoomControlsEnabled = false

        // 右上角报警按钮
        view.findViewById<View>(R.id.layout_alarm).setOnClickListener { showFallHistoryDialog() }
        // 右上角历史轨迹按钮：实时模式→直接选日期，历史模式→弹菜单(选新日期/返回实时)
        view.findViewById<View>(R.id.layout_history).setOnClickListener {
            if (isHistoryMode) showHistoryOrExitMenu() else showDatePicker()
        }

        // 拉取设备列表并构建标签栏
        fetchDeviceList()
    }

    fun startPolling() {
        startPollingCloud()
    }

    fun stopPolling() {
        pollingTimer?.cancel()
        pollingTimer = null
    }

    // ========== 设备列表与标签栏 ==========

    private fun fetchDeviceList() {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val phone = prefs.getString("PHONE", "")

        val json = JSONObject()
        json.put("action", "getDeviceList")
        json.put("phone", phone)

        val request = Request.Builder()
            .url(uniCloudUrl)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread { loadDeviceListFromLocal() }
            }
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                activity?.runOnUiThread {
                    try {
                        val resJson = JSONObject(resStr ?: "")
                        if (resJson.getBoolean("success")) {
                            val dataArray = resJson.optJSONArray("data")
                            if (dataArray != null && dataArray.length() > 0) {
                                val newList = mutableListOf<DeviceInfo>()
                                for (i in 0 until dataArray.length()) {
                                    val item = dataArray.getJSONObject(i)
                                    val devId = item.optString("deviceId", "")
                                    newList.add(DeviceInfo(
                                        deviceId = devId,
                                        status = item.optString("status", "offline"),
                                        lastUpdate = item.optString("lastUpdate", "未知"),
                                        name = getDeviceName(devId)
                                    ))
                                }
                                setupDeviceTabs(newList)
                            } else {
                                loadDeviceListFromLocal()
                            }
                        } else {
                            loadDeviceListFromLocal()
                        }
                    } catch (e: Exception) {
                        loadDeviceListFromLocal()
                    }
                }
            }
        })
    }

    // 降级：从 SharedPreferences 读取所有已绑定设备
    private fun loadDeviceListFromLocal() {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        val idsStr = prefs.getString("BOUND_DEVICE_IDS", "") ?: ""
        val idList = idsStr.split(",").filter { it.isNotEmpty() }
        if (idList.isNotEmpty()) {
            val devices = idList.map { devId ->
                DeviceInfo(devId, "online", "本地记录", getDeviceName(devId))
            }
            setupDeviceTabs(devices)
        } else {
            // 兼容旧数据
            val boundId = prefs.getString("BOUND_DEVICE_ID", "")
            if (!boundId.isNullOrEmpty()) {
                prefs.edit().putString("BOUND_DEVICE_IDS", boundId).apply()
                setupDeviceTabs(listOf(DeviceInfo(boundId, "online", "本地记录", getDeviceName(boundId))))
            } else {
                setupDeviceTabs(emptyList())
            }
        }
    }

    // 从 SharedPreferences 读取设备自定义名称
    private fun getDeviceName(deviceId: String): String {
        val prefs = requireActivity().getSharedPreferences("STZX_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("DEVICE_NAME_$deviceId", "") ?: ""
    }

    // 根据设备列表构建底部标签栏
    private fun setupDeviceTabs(devices: List<DeviceInfo>) {
        deviceList.clear()
        deviceList.addAll(devices)
        layoutDeviceTabs.removeAllViews()

        if (devices.isEmpty()) {
            // 无设备时隐藏标签栏
            view?.findViewById<View>(R.id.scroll_device_tabs)?.visibility = View.GONE
            tvStatus.text = "状态：暂无绑定设备"
            tvTime.text = "请先在\"我的设备\"中绑定盲杖"
            return
        }

        view?.findViewById<View>(R.id.scroll_device_tabs)?.visibility = View.VISIBLE

        // 默认选中第一个设备
        currentDeviceId = devices[0].deviceId

        for (i in devices.indices) {
            val tabView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_device_tab, layoutDeviceTabs, false)

            val tvName = tabView.findViewById<TextView>(R.id.tv_tab_device_name)
            val viewDot = tabView.findViewById<View>(R.id.view_tab_status_dot)

            // 显示自定义名称或默认名
            val displayName = if (devices[i].name.isNotEmpty()) devices[i].name else "盲杖 #${i + 1}"
            tvName.text = displayName

            // 状态圆点
            val dotRes = when (devices[i].status) {
                "online" -> R.drawable.shape_status_dot_online
                "alarm" -> R.drawable.shape_status_dot_alarm
                else -> R.drawable.shape_status_dot_offline
            }
            viewDot.setBackgroundResource(dotRes)

            // 选中/未选中样式
            updateTabStyle(tabView, i == 0)

            // 点击切换设备
            val pos = i
            tabView.setOnClickListener {
                if (pos != 0 || currentDeviceId != devices[pos].deviceId) {
                    // 退出历史模式（如果在）
                    if (isHistoryMode) {
                        isHistoryMode = false
                        fallHistory.clear()
                        viewRedDot.visibility = View.GONE
                    }
                    currentDeviceId = devices[pos].deviceId
                    resetMapState()
                    // 更新所有标签样式
                    for (j in 0 until layoutDeviceTabs.childCount) {
                        updateTabStyle(layoutDeviceTabs.getChildAt(j), j == pos)
                    }
                    startPollingCloud()
                }
            }

            layoutDeviceTabs.addView(tabView)
        }

        // 开始轮询第一个设备
        startPollingCloud()
    }

    // 更新单个标签的选中/未选中样式
    private fun updateTabStyle(tabView: View, isSelected: Boolean) {
        val tvName = tabView.findViewById<TextView>(R.id.tv_tab_device_name)
        if (isSelected) {
            tabView.setBackgroundResource(R.drawable.bg_device_tab_selected)
            tvName.setTextColor(requireContext().getColor(R.color.white))
        } else {
            tabView.setBackgroundResource(R.drawable.bg_device_tab_unselected)
            tvName.setTextColor(requireContext().getColor(R.color.text_secondary))
        }
    }

    // 切换设备时重置地图状态
    private fun resetMapState() {
        aMap.clear()
        trackPoints.clear()
        trackPolyline = null
        blindCaneMarker = null
        lastTrackTimestamp = 0L
        tvStatus.text = "状态：初始化中..."
        tvStatus.setTextColor(resources.getColor(R.color.text_primary, null))
        tvTime.text = "最后更新：--"
    }

    // ========== 历史轨迹菜单 ==========

    private fun showHistoryOrExitMenu() {
        val options = arrayOf("📅 选择新日期", "🟢 返回实时监控")
        AlertDialog.Builder(requireContext()).setItems(options) { _, which ->
            if (which == 0) showDatePicker() else exitHistoryMode()
        }.show()
    }

    // ========== 实时轮询 ==========

    private fun startPollingCloud() {
        if (isHistoryMode) return
        if (currentDeviceId.isEmpty()) return
        pollingTimer?.cancel()
        pollingTimer = Timer()
        pollingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() { fetchLatestLocation() }
        }, 0, 5000)
    }

    private fun fetchLatestLocation() {
        if (isHistoryMode) return
        if (currentDeviceId.isEmpty()) return

        val urlWithTime = "$uniCloudUrl?deviceId=$currentDeviceId&t=${System.currentTimeMillis()}"
        val request = Request.Builder().url(urlWithTime).cacheControl(CacheControl.FORCE_NETWORK).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread { if(!isHistoryMode) tvStatus.text = "状态：网络掉线，正在重连..." }
            }
            override fun onResponse(call: Call, response: Response) {
                if (isHistoryMode) return
                val resStr = response.body?.string()

                if (!resStr.isNullOrEmpty()) {
                    try {
                        val json = JSONObject(resStr)
                        if (json.getBoolean("success")) {
                            val data = json.optJSONObject("data") ?: return
                            val lat = data.getDouble("latitude")
                            val lon = data.getDouble("longitude")
                            val status = data.getString("status")
                            val timestamp = data.getLong("create_time")

                            fetchAddressFromAmap(lon, lat) { address ->
                                activity?.runOnUiThread {
                                    if (!isHistoryMode) {
                                        updateUI(lat, lon, status, timestamp, address)
                                        checkFallStatus(data, address)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) { Log.e("FamilyApp", "解析实时数据异常", e) }
                }
            }
        })
    }

    // ========== 历史轨迹 ==========

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
            enterHistoryMode(dateStr)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun enterHistoryMode(dateStr: String) {
        isHistoryMode = true
        pollingTimer?.cancel()

        activity?.runOnUiThread {
            tvStatus.text = "⏳ 正在拉取 $dateStr 历史记录并过滤噪点..."
            tvStatus.setTextColor(resources.getColor(R.color.status_warn, null))
            tvTime.text = "请稍候..."
        }

        val url = "$uniCloudUrl?action=history&deviceId=$currentDeviceId&date=$dateStr&t=${System.currentTimeMillis()}"
        val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread { Toast.makeText(requireContext(), "历史记录拉取失败", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val resStr = response.body?.string()
                try {
                    val json = JSONObject(resStr ?: "")
                    if (json.getBoolean("success")) {
                        val dataArray = json.optJSONArray("data")
                        activity?.runOnUiThread { renderHistoryMap(dateStr, dataArray) }
                    }
                } catch (e: Exception) { Log.e("FamilyApp", "解析历史记录失败", e) }
            }
        })
    }

    // 历史轨迹防漂移渲染算法
    private fun renderHistoryMap(dateStr: String, dataArray: org.json.JSONArray?) {
        aMap.clear()
        trackPoints.clear()
        trackPolyline = null
        blindCaneMarker = null

        if (dataArray == null || dataArray.length() == 0) {
            tvStatus.text = "📅 $dateStr"
            tvTime.text = "当天无任何出行记录"
            return
        }

        val historyPoints = mutableListOf<LatLng>()
        var lastValidPoint: LatLng? = null
        var lastValidTime: Long = 0L
        var droppedCount = 0

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val lat = item.getDouble("latitude")
            val lon = item.getDouble("longitude")
            val status = item.optString("status", "Normal")
            val timestamp = item.getLong("create_time")
            val currentPoint = LatLng(lat, lon)

            var isValid = true

            // 噪点过滤算法
            if (lastValidPoint != null) {
                val distance = AMapUtils.calculateLineDistance(lastValidPoint, currentPoint)
                val timeDiffSec = (timestamp - lastValidTime) / 1000f
                if (timeDiffSec > 0) {
                    val speed = distance / timeDiffSec
                    if (speed > 15f) {
                        isValid = false
                        droppedCount++
                    }
                }
            }

            if (isValid) {
                historyPoints.add(currentPoint)
                lastValidPoint = currentPoint
                lastValidTime = timestamp

                if (status == "FALL" || status == "SOS") {
                    val title = if (status == "SOS") "🆘主动求救" else "💥跌倒警报"
                    aMap.addMarker(MarkerOptions()
                        .position(currentPoint)
                        .title(title)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                }
            }
        }

        if (historyPoints.isNotEmpty()) {
            aMap.addPolyline(PolylineOptions().addAll(historyPoints).width(15f).color(resources.getColor(R.color.accent_dark, null)))
            val lastPoint = historyPoints.last()
            aMap.addMarker(MarkerOptions().position(lastPoint).title("最终位置").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 15f))
        }

        tvStatus.text = "📅 历史回放: $dateStr"
        tvTime.text = "成功绘制 ${historyPoints.size} 个点 (已过滤 $droppedCount 个漂移噪点)"
    }

    private fun exitHistoryMode() {
        isHistoryMode = false
        aMap.clear()
        trackPoints.clear()
        trackPolyline = null
        blindCaneMarker = null
        fallHistory.clear()
        viewRedDot.visibility = View.GONE
        lastTrackTimestamp = 0L
        Toast.makeText(requireContext(), "已返回实时监控", Toast.LENGTH_SHORT).show()
        startPollingCloud()
    }

    // ========== 地址解析 ==========

    private fun fetchAddressFromAmap(lon: Double, lat: Double, callback: (String) -> Unit) {
        val url = "https://restapi.amap.com/v3/geocode/regeo?location=$lon,$lat&key=$AMAP_WEB_KEY"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback("网络异常") }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val json = JSONObject(it.body?.string() ?: "")
                        if (json.getString("status") == "1") {
                            callback(json.getJSONObject("regeocode").getString("formatted_address"))
                        } else callback("解析地点失败")
                    } catch (e: Exception) { callback("地址解析异常") }
                }
            }
        })
    }

    // ========== 跌倒检测 ==========

    private fun checkFallStatus(data: JSONObject, address: String) {
        val status = data.optString("status")
        if (status == "FALL" || status == "SOS") {
            viewRedDot.visibility = View.VISIBLE
            val currentTimestamp = data.getLong("create_time")
            if (fallHistory.isEmpty() || fallHistory.first().getLong("create_time") != currentTimestamp) {
                data.put("address", address)
                fallHistory.add(0, data)
            }
        }
    }

    private fun showFallHistoryDialog() {
        viewRedDot.visibility = View.GONE
        if (fallHistory.isEmpty()) {
            Toast.makeText(requireContext(), "暂无报警记录", Toast.LENGTH_SHORT).show()
            return
        }
        val items = fallHistory.map {
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.getLong("create_time")))
            val type = if (it.optString("status") == "SOS") "🆘 主动求救" else "💥 跌倒警报"
            val address = it.optString("address", "位置解析中...")
            "时间：$time [$type]\n地点：$address"
        }.toTypedArray()

        AlertDialog.Builder(requireContext()).setTitle("⚠️ 最新报警记录").setItems(items) { _, which ->
            val record = fallHistory[which]
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(record.getDouble("latitude"), record.getDouble("longitude")), 18f))
        }.setPositiveButton("关闭", null).show()
    }

    // ========== 实时轨迹渲染 ==========

    private fun updateUI(lat: Double, lon: Double, status: String, timestamp: Long, address: String) {
        val latLng = LatLng(lat, lon)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        var isValidPoint = true

        if (trackPoints.isNotEmpty() && lastTrackTimestamp > 0L) {
            val lastPoint = trackPoints.last()
            val distance = AMapUtils.calculateLineDistance(lastPoint, latLng)
            val timeDiffSec = (timestamp - lastTrackTimestamp) / 1000f

            if (timeDiffSec > 0) {
                val speed = distance / timeDiffSec
                if (speed > 15f) {
                    isValidPoint = false
                }
            }
        }

        if (isValidPoint) {
            tvTime.text = "最后更新：$timeStr\n当前位置：$address"
            trackPoints.add(latLng)
            lastTrackTimestamp = timestamp

            if (trackPolyline == null) {
                trackPolyline = aMap.addPolyline(PolylineOptions().addAll(trackPoints).width(15f).color(resources.getColor(R.color.accent, null)))
            } else trackPolyline?.points = trackPoints
        }

        if (status == "FALL") {
            tvStatus.text = "🚨 检测到跌倒！"; tvStatus.setTextColor(resources.getColor(R.color.status_danger, null))
        } else if (status == "SOS") {
            tvStatus.text = "🆘 盲杖发起主动求救！"; tvStatus.setTextColor(resources.getColor(R.color.status_danger, null))
        } else {
            tvStatus.text = "当前状态：正常行走"; tvStatus.setTextColor(resources.getColor(R.color.status_safe, null))
        }

        if (blindCaneMarker == null) {
            blindCaneMarker = aMap.addMarker(MarkerOptions().position(latLng).title("盲杖当前位置").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } else {
            blindCaneMarker?.position = latLng
            if (isValidPoint) aMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
    }

    // ========== Fragment 生命周期与 MapView 同步 ==========

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // 每次回来时刷新设备列表（可能绑定了新设备）
        fetchDeviceList()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        pollingTimer?.cancel()
        pollingTimer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        pollingTimer?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
