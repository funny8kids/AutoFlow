<div align="center">

# AutoFlow

**把手机变成你自己的自动化平台 · Android Automation, Reimagined**

[![Build APK](https://github.com/funny8kids/AutoFlow/actions/workflows/android.yml/badge.svg)](https://github.com/funny8kids/AutoFlow/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**93 个触发器 · 146 个动作 · 58 个约束 · AI 生成宏与模板**

</div>

---

## ✨ 这是什么

AutoFlow 是一个 Android 自动化工具：用「**触发器 + 条件 + 动作**」把手机上的重复操作变成自动流程。

和同类工具相比，它有三个不同：

| | AutoFlow | 常见方案 |
|---|---|---|
| **界面** | Material 3 · 渐变头图 · 卡片流 · 底部抽屉 | 传统列表 + 弹窗 |
| **AI** | 内置 DeepSeek：一句话生成宏，或生成**整套模板包** | 无 / 仅云端 |
| **能力分级** | 公开 API → 通知 → 无障碍 → Shizuku → Root，逐级降级提示 | 权限失败静默跳过 |

> 项目目标是对标 MacroDroid 的能力，并做现代化重写。架构参考来自对官方 APK 的逆向分析（见 [docs/](../docs)），实现为独立代码。

## 🚀 功能一览

### 触发器（93）

| 分类 | 能力 |
|---|---|
| 时间 | 定时循环、每日定时、整点、每分钟、日期变化 |
| 电源 | 电量阈值、充满、低电量、电池温度/电压/健康、AC/USB/无线充电、低电耗 |
| 设备状态 | 屏幕开关、解锁、锁屏、开机、关机、飞行模式、底座、屏保、存储不足、壁纸 |
| 传感器 | 摇晃、接近、光线、走动、步数、气压、环境温度 |
| 外设 | 耳机插拔、蓝牙连接/断开/A2DP、USB、媒体按键、NFC 标签 |
| 网络 | WiFi 开关、指定 SSID、信号弱、扫描完成、网络变化 |
| 应用 | 安装、卸载、数据清除、更新、应用切换（UsageStats）、通知接收/移除/含文字 |
| 通信 | 短信接收/发送、通话状态、SIM 状态 |
| 通用 | **自定义广播**：填任意 Action 即可监听 |

### 动作（146）

界面 · 设备控制 · 网络 · 变量 · 文本处理 · 文件 · 应用 · UI 交互（无障碍）· 流程控制 · **动作块** · Root/Shell 特权

### 约束（58）

时间/星期/日期 · 电量/充电/温度 · 网络/WiFi · 屏幕/锁屏/横竖屏 · 音量/亮度 · 存储/内存 · 前台应用 · 权限可用性（Root/Shizuku/通知使用权）

### AI（默认 DeepSeek）

- **单宏生成**：一句话 → 一个可运行的宏
- **模板包生成**：一句话 → 3–5 个配套宏，可单个/全部安装
- 模型名**不写死**：填入 API Key 后从 `GET /models` 拉取可选模型
- 提示词由注册表动态生成，AI 只能用真实存在的能力

### 其他

- 🎨 Material 3 设计系统，浅色/深色，每宏独立强调色
- 🧩 动作块（可复用子流程，支持 JSON 参数注入）
- 💾 AES-GCM 加密备份（PBKDF2 派生密钥）
- 🖼️ 桌面小组件 + 应用内快捷面板
- 🔍 分类与搜索
- ⚡ 前台服务保活，开机自恢复
- 🧠 变量系统：局部/全局、字典/数组、魔法文本 `{v=名}` / `{lv=名}`

## 📦 下载 / 构建

### GitHub Actions 自动构建

推送到 `main` 或打 tag 会触发 [android.yml](.github/workflows/android.yml)：

- 每次提交 → 产出 `AutoFlow-debug-apk` 构建产物（Actions → Artifacts）
- 打 `v*` tag → 自动创建 Release 并附加 APK

### 本地构建

```bash
# 需要 JDK 17+ 与 Android SDK 35
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 🏗️ 架构

```
app/src/main/java/com/autoflow/app/
├── core/
│   ├── Model.kt          Macro / TriggerSpec / ActionSpec / ConstraintSpec / Variable
│   ├── Registry.kt       83+131+50 的元数据注册表（驱动全部 UI 与 AI 提示词）
│   ├── MacroEngine.kt    执行引擎：控制流跳转、动作块递归、守卫
│   ├── MacroStore.kt     一宏一 JSON + 原子写（.new → rename → .backup）
│   ├── VarStore.kt       全局变量持久化
│   ├── Backup.kt         AES-GCM 备份编解码
│   ├── Capability.kt     能力分级模型
│   └── ai/               AiSettings · DeepSeekClient
├── platform/
│   ├── ActionExecutors.kt        131 动作 + 50 约束
│   ├── TriggerManager.kt         触发器调度（广播/闹钟/传感器/观察者）
│   ├── AutoFlowService.kt        前台服务（保活 + 前台应用/音乐/WiFi 轮询）
│   ├── AutoAccessibilityService.kt  UI 交互（点击/滑动/输入/读屏）
│   ├── AutoNotificationListener.kt  通知接收/移除/文本
│   ├── ShellRunner.kt            Root > Shell
│   └── QuickWidgetProvider.kt    桌面小组件
└── ui/                    Compose 界面（列表 / 编辑器 / AI / 设置 / 备份 / 模板）
```

**核心设计**：`Registry` 里每条能力都是一个 `ItemSpec`（名称/描述/图标/分类/参数定义）。编辑界面、AI 提示词、参数表单全部由它渲染 —— 新增一个动作只需「注册表加一条 + 执行器加一个分支」，界面零改动。

## 🎯 能力分级

```
公开 API  <  通知使用权  <  无障碍  <  Shizuku(ADB)  <  Root
```

每个动作声明最低要求，运行时探测当前可用级别；不满足时**明确提示去开启**，而不是静默失败。

## 🗺️ 路线图

- [x] v0.1 架构跑通、可安装 APK
- [x] v0.4 变量系统（局部/全局、字典/数组）
- [x] v0.5 无障碍 UI 交互
- [x] v0.6 AES-GCM 加密备份
- [x] v0.7 桌面小组件 + 快捷面板
- [x] v0.8 能力分级 + 特权 Shell
- [x] v0.9 Action Block
- [x] v1.0 前台服务自愈
- [x] v1.3 AI 模板包
- [x] v2.0 覆盖度 71/109/38
- [x] v2.1 覆盖度 83/131/50
- [x] **v2.2 覆盖度 93/146/58**
- [ ] v2.3 触发器补到 100+（日历、通知点击、蓝牙扫描）
- [ ] v2.3 动作补到 160+（通知样式、图片处理、剪贴板历史）
- [ ] v3.0 模板市场 / 宏分享 / 多设备同步

## ⚠️ 说明

- 本项目是**独立实现**，与 MacroDroid（Arlosoft）无关联，未包含其代码或资源。
- 部分能力（如 Root/Shizuku 命令、通知使用权）需要用户自行授权。
- 应用内 AI 使用的 API Key 只保存在本机 SharedPreferences，不会上传。

## 📄 License

[MIT](LICENSE)