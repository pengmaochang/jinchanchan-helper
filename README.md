# 金铲铲截图验证（Android 11+）

本阶段只验证 `AccessibilityService.takeScreenshot()` 能否读取真实游戏画面。每次由用户点击按钮触发，8 秒后截取一张，不读取控件树，不自动点击，不联网。

## 手机测试

1. 安装 `dist/JccScreenshotProbe.apk`（本地测试签名）。
2. 打开“金铲铲截图验证”，点击“打开无障碍设置”，在已下载/已安装的服务中启用同名服务。
3. 返回应用，确认显示“服务：已连接”。
4. 点击“8 秒后截取一张”，在 8 秒内切到手机桌面，先验证普通画面。
5. 返回应用查看状态和预览。原图保存到相册 `Pictures/JccProbe/`。
6. 重复一次，切到金铲铲的游戏画面，对照截图是否含正确的卡牌、金币和棋盘。
7. 测试后可关闭无障碍服务。

如果系统提示限制开启服务，请按手机系统界面为该应用允许受限设置后再尝试。各版本菜单可能不同，以实际提示为准。

系统 API 返回成功不代表画面正常：全黑或局部空白仍算本次游戏画面读取验证未通过。错误码会显示在应用中；受保护内容可能被系统拒绝或遮黑。

## PC 安装与取回截图

手机打开 USB 调试并授权这台电脑后，在项目目录的 PowerShell 中执行：

```powershell
& .tools/platform-tools/adb.exe devices -l
& .tools/platform-tools/adb.exe install -r dist/JccScreenshotProbe.apk
& .tools/platform-tools/adb.exe shell am start -n local.jcc.probe/.MainActivity
# 完成截图后，将相册中的验证图片取回 PC：
& .tools/platform-tools/adb.exe pull /sdcard/Pictures/JccProbe ./captures
```

`unauthorized` 表示需要在手机确认调试授权。列表为空时先检查数据线、USB 模式或驱动。首次验证使用 USB；同一 Wi-Fi 不是无障碍截图本身的必要条件，后续再接无线调试。

## 构建

源码无第三方运行时依赖。当前 APK 使用官方 Android SDK 35 构建，最低 Android 11，目标 Android 15。

```powershell
python tools/prepare_sdk.py
# 按本机情况指定 Java 17；此变量只用于本构建脚本。
$env:PROBE_JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2023.1\jbr'
python tools/build_apk.py
```

`prepare_sdk.py` 从 Google 下载 SDK 平台与构建工具，并对照官方仓库 SHA-1 校验。`build_apk.py` 使用 aapt2、javac、D8、zipalign、apksigner 生成 APK 并验证签名。SDK、测试签名私钥和构建结果不会加入 Git。

也保留了 AGP 8.7.3 的 Gradle 工程配置（需要 Gradle 8.9、JDK 17 和本机 SDK）。直接导入 Android Studio 时请使用它自带的 SDK 配置。

## 本次验证状态

- 已完成：Java 编译、资源打包、DEX 生成、APK 签名验证。
- 待完成：手机安装、用户启用服务、普通画面截图、游戏画面截图并人工核对。
- 未验证金铲铲的实际截图兼容性。

接口文档：https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
