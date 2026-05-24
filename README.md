# EInkReader - 超轻量墨水屏小说阅读器

> **APK不足1MB** — 比一张照片还小的阅读器

专为老旧墨水屏设备打造的极简小说阅读器，支持 TXT / EPUB 格式，针对低性能Android设备深度优化。

## 核心特性

### 🪶 极致轻量
- **APK体积不足1MB**，安装包比微信聊天记录还小
- 内存占用极低，适配全智A133等低性能处理器
- 零广告、零推送、零后台服务

### 📱 真正的墨水屏全刷
- **硬件级全刷**：通过 `Window.getDecorView().setBackgroundColor()` 走 SurfaceFlinger → Hardware Composer 路径，触发真正的屏幕物理刷新
- **黑→白→正常** 三阶段视觉闪烁，彻底消除残影
- 自动全刷间隔可调（每N页自动全刷）
- 翻页模式可选（局部刷新/全刷），适配不同墨水屏特性

### 📖 阅读功能
- TXT / EPUB 文件导入与阅读
- TXT → EPUB 格式转换
- 智能章节目录解析（支持中文章节名识别）
- EPUB / TXT 解析缓存（秒开已有书籍，缓存重装不丢失）
- 章节目录跳转（按钮翻页，适配墨水屏）

### ⚙️ 丰富的自定义设置
- 字号 / 字体粗细 / 行距 / 段距 / 字距 连续可调
- 阅读区域边距自定义（上/下/左右）
- 翻页重叠行数可调（0~20行）
- 三种触屏翻页模式：左右点击 / 任意点击 / 滑动
- 物理按键映射（支持自定义翻页键）
- 屏幕亮度加减控制
- 全刷延时参数调节

### 🔧 适配能力
- **最低支持 Android 4.4 (API 19)**
- **支持 32位 ARM (armeabi-v7a)** 和 64位 ARM (arm64-v8a)
- 内置文件浏览器（替代被屏蔽的系统文件选择器）
- 二维码日志传输（设备无法导出文件时，扫码即可获取错误日志）
- 全局崩溃捕获与日志记录

## 下载

前往 [Releases](https://github.com/xfl1996/EInkReader/releases) 页面下载最新版本。

### APK 文件说明

| 文件名 | 适用架构 | 说明 |
|--------|----------|------|
| `app-armeabi-v7a-debug.apk` | 32位 ARM | 适配大部分老设备（全智A133等） |
| `app-arm64-v8a-debug.apk` | 64位 ARM | 适配较新的ARM设备 |
| `app-debug.apk` | 全架构通用 | 包含32位+64位ARM，体积略大但兼容性最好 |

## 构建

```bash
# 需要 Android SDK 和 Gradle
./gradlew assembleDebug
```

要求：
- Android SDK (compileSdk 35)
- Gradle 8.14+
- JDK 8+

## 许可证

MIT License
