# STZX_Family（声途智行 · 亲友端）

「声途智行」AI 无障碍导航系统的**亲友端 App**：家属可实时查看盲杖用户的位置、历史轨迹，并在跌倒/主动求救时收到语音与音效警报。

> 配套盲杖端为独立设备端项目，负责 GPS 采集与位置上报。

## 功能概览

| 功能 | 说明 |
|------|------|
| 登录 / 注册 | 手机号注册，云端验证（uniCloud 云函数） |
| 设备绑定 | 通过盲杖端生成的**配对码**绑定，支持多设备绑定与解绑、自定义设备名称 |
| 实时定位 | 每 5 秒轮询云端最新位置，地图 Marker + 轨迹线实时渲染 |
| 历史轨迹 | 按日期查询当天所有上报点，自动过滤速度 >15 m/s 的漂移噪点 |
| 跌倒 / SOS 警报 | 盲杖上报 `FALL`/`SOS` 状态时：状态栏变红 + 红点提醒 + TTS 语音播报 + 差异化警报音效（可点右上角查看历史报警记录并跳转位置） |
| 逆地理编码 | 调用高德 Web API 将经纬度转为详细地址展示 |
| 模拟行走 | 地图右下角按钮，内置约 1km 北京预设路线（3 秒/点），同步上传云端，用于无硬件时测试完整链路 |
| 审图号 | 通过高德 SDK `mapContentApprovalNumber` 动态获取并显示在地图右下角（合规要求） |

## 技术栈

- **语言 / UI**：Kotlin + XML（FrameLayout 层叠布局 + ViewPager2 + BottomNavigationView）
- **地图**：高德地图 Android SDK
- **网络**：OkHttp（POST JSON，`action` 字段区分操作）
- **后端**：uniCloud 云函数（部署在 DCloud uniCloud 控制台，源码不在本仓库）
- **本地存储**：SharedPreferences（`STZX_PREFS`）

## 云端接口（action）

统一入口 URL：`https://fc-mp-6aceaf7c-21e7-4eb1-a4f8-8e2bbdb8d479.next.bspapp.com/uploadLocation`

| action | 方式 | 参数 | 说明 |
|--------|------|------|------|
| `login` / `register` | POST | phone, password | 用户登录注册 |
| `bindDevice` | POST | phone, pairingCode | 绑定设备（配对码云端验证，**必须走云端**） |
| `uploadLocation` | POST | deviceId, latitude, longitude, status | 位置上报（盲杖端与模拟模式共用） |
| `fetchLocation` | POST | deviceId | 查询设备最新位置 |
| `history` | GET | deviceId, date | 查询某天历史轨迹 |

> 设备列表与解绑为**本地优先**：直接读写 SharedPreferences，云端最佳努力。

## 构建运行

### 环境要求

- Android Studio（需支持 AGP 8.13.2）
- Android SDK、JDK 17+
- Gradle 8.13（由 gradle-wrapper 自动下载）
- Kotlin 2.0.21（见 `gradle/libs.versions.toml` 依赖清单）

### 步骤

1. 克隆项目：
   ```bash
   git clone https://github.com/Huafengrrr/STZX_Family.git
   ```
2. 用 Android Studio 打开项目，等待 Gradle Sync 自动下载依赖
3. `local.properties` 由 IDE 自动生成（本机 SDK 路径，不入库）
4. 连接手机或启动模拟器，点击 Run 运行

### 使用流程

1. 注册 / 登录账号
2. 在「我的设备」页输入盲杖端显示的**配对码**绑定设备（支持绑定多根盲杖）
3. 切换到地图页即可实时查看位置；点右上角日历图标可回放历史轨迹
4. 无盲杖硬件时，点地图右下角 ▶ 按钮开启**模拟行走**，验证全部功能（含随机跌倒/SOS 警报）

## 注意事项

- 高德 Key 与云函数 URL 硬编码在 `MapFragment.kt` 中，如需更换请修改对应常量
- 云函数源码在 DCloud uniCloud 控制台维护，不在本仓库
- 报警音效位于 `app/src/main/res/raw/`（`alarm_fall.wav` / `alarm_sos.wav`）
- `build/`、`.gradle/`、`.idea/` 等均为本地生成物，不要提交
