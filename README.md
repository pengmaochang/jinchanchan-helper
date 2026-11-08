# 金铲铲助手（Android 11+，arm64）

v0.3 支持中文目标名字输入、按优先级自动购买、自动/手动刷新切换。全部截图和 OCR 在手机本地执行，不联网。原来的单次截图功能继续保留。当前适配小米 14 的 2670×1200 横屏游戏布局。

## 自动购买

1. 更新后重新确认无障碍服务启用。本版增加读取窗口包名和执行手势能力，需要系统重新绑定服务。
2. 每行输入一个完整中文卡名，顺序代表优先级，同屏同名卡从左向右购买。
3. 设置保留金币；打开“自动刷新”表示买完目标并确认后刷新，关闭时由你手动刷新、程序只自动购买。默认关闭自动刷新。
4. 可勾选“仅识别”验证新目标是否可读；该模式不点击游戏。
5. 点击“保存并显示悬浮窗”，切到游戏，手动展开商店，在悬浮窗点击“开始”。若开始时未展开，等待你手动打开，不会自己打开商店。
6. 悬浮窗也提供可即时切换的自动刷新开关；关闭会取消尚未派发的刷新。
7. 拖动标题移动，点标题收起/展开。悬浮窗请放在商店下方，遮住商店会暂停。
8. 识别到商店关闭、离开游戏、屏幕方向变化、识别连续不清或手势失败时停止。点暂停使在途识别、校验和待派发点击失效；已派发的单次短手势可能完成，但不会继续下一次。

购买后要求该卡位变空且金币准确扣除费用；刷新后要求金币准确扣除 2。确认前不重复点击，连续 3 次未确认则停止。满备战区等导致购买失败的情况也会按未确认处理，不自动出售棋子。目标存在但金币低于保留底线时停止，不跳过该目标去刷新。

## 识别与点击

使用 Tesseract4Android 4.9.0：中文模型读卡名和“刷新”文字，英文模型读金币与费用。以底部名字区域为主，避开左上角推荐图标和大部分闪光。另保留参考截图中五张卡的文字模板作为快速路径，匹配阈值 0.86。未收录卡走中文 OCR，低置信度连续三帧会停止；当前参考样本中部分中文名字的直接 OCR 置信度不足，靠模板补足，不能据此承诺任意新卡名都准确。

按 Android 默认显示屏截图，将 2670×1200 画面映射到 2048×920 参考坐标。每次截图调用至少相隔 360ms；原始截图 OCR 在已收录模板路径上的实机检查约 40～60ms，完整循环还包含截图节流、二次画面校验和确认。点击按住 35～65ms，点击前额外延时 40～100ms，并在卡牌/刷新按钮安全区域内小幅偏移。

每次点击前再截图，对五个名字区域、金币和刷新文字逐一校验。局部文字变动时取消旧点击、重新识别；持续变化则停止。前台检查读取无障碍窗口根节点的包名，不遍历控件树，允许 `com.tencent.jkchess` 和 `com.tencent.jk`。不自动适配其他布局，也不支持自动售卖、选秀或特殊商店。

## 离线识别验证

`tools/VisionCheck.java` 与手机直接共用 `ShopVision.java`，以三个原始截图为输入（收起、购买前、购买后），生成卡名模板并断言：收起时不判为商店、目标命中、其他四张不命中、买后第一格为空位。

```powershell
New-Item -ItemType Directory -Force .tools/vision-check | Out-Null
& "$env:PROBE_JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d .tools/vision-check app/src/main/java/local/jcc/probe/ShopVision.java tools/VisionCheck.java
& "$env:PROBE_JAVA_HOME/bin/java.exe" -cp .tools/vision-check VisionCheck '收起截图.jpg' '购买前截图.jpg' '购买后截图.jpg'
```

其他验证入口：

- `tools/PolicyCheck.java`：优先级、多张目标、购买/刷新确认、失败停止、金币底线和运行中刷新开关。
- `tools/GuardCheck.java`：旧画面校验拒绝购买后及关闭商店的画面。
- `OcrChecks`：手机只读检查参考图片，不执行手势。将三张原图依次推送到应用外部目录的 `fixture0.jpg`、`fixture1.jpg`、`fixture2.jpg`，执行 `adb shell am instrument -w local.jcc.probe/.OcrChecks`。返回 `result=PASS` 才算通过；还会输出直接 OCR 名字和置信度。

参考验证不是泛化准确率或完整自动购买测试。仍需实机验证新卡名、卡位变化、闪光、金币变化和商店切换动画。

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

使用官方 Android SDK 35 构建，最低 Android 11，目标 Android 15，版本 0.3，仅打包 arm64 原生 OCR。OCR 二进制及模型由准备脚本校验 SHA-256，开源许可证随 APK 打包，见 `THIRD_PARTY_NOTICES.md`。

```powershell
python tools/prepare_sdk.py
python tools/prepare_ocr.py
# 按本机情况指定 Java 17；此变量只用于本构建脚本。
$env:PROBE_JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2023.1\jbr'
python tools/build_apk.py
```

`prepare_sdk.py` 从 Google 下载 SDK 平台与构建工具，并对照官方仓库 SHA-1 校验。`build_apk.py` 使用 aapt2、javac、D8、zipalign、apksigner 生成 APK 并验证签名。SDK、测试签名私钥和构建结果不会加入 Git。

也保留了 AGP 8.7.3 的 Gradle 工程配置（需要 Gradle 8.9、JDK 17 和本机 SDK），同样先执行 OCR 准备脚本，Gradle 引用本地 AAR 和模型。直接导入 Android Studio 时请使用它自带的 SDK 配置。

## 本次验证状态

- 已完成：手机截图正常；修正实际游戏包名；参考截图 OCR 实机检查通过（74→73 金币、五张费用、空位）；策略与点击前画面校验检查通过；APK 构建和签名验证通过。
- 已实现：目标名字列表、按优先级购买、自动刷新开关、仅识别模式、悬浮窗和暂停控制。
- 待完成：更新后重新启用无障碍服务，实机验证前台判断、自动购买、刷新和停止流程。尚未宣称完整自动循环实测通过。

接口文档：https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
