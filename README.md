# 金铲铲截图与商店识别验证（Android 11+）

v0.2 增加目标选择和可拖动、收起的无障碍悬浮窗，支持用户开始/暂停的连续卡名模板匹配。不读取控件树、不自动点击、不联网。原来的单次截图功能继续保留。

## 悬浮窗识别

1. 更新 APK 后确认无障碍服务已启用（系统可能会关闭更新应用的服务）。
2. 在主界面选择目标（默认可酷伯），点击“显示识别悬浮窗”。
3. 切到金铲铲，手动展开商店，在悬浮窗点击“开始”。
4. 悬浮窗显示五格结果、目标匹配分数和截图+识别耗时；目标标记为 `★目标`。
5. 拖动标题可以移动，点击标题可以收起/展开。请放在商店下方；遮住商店时会暂停。
6. 离开游戏、屏幕方向变化或截图失败会暂停。点击“暂停”会使未返回的识别结果失效。

连续识别只在内存处理截图，每次完成后间隔 400ms 继续，不向相册写入连续截图。没有开启手势能力，也没有购买、刷新或打开商店的自动点击功能。

当前模板来自用户提供的 2670×1200 截图，识别时映射到 2048×920 参考坐标。裁取卡牌底部名字区域，按亮色文字二值化，允许小幅位移后计算 Dice 相似度，阈值为 0.86。模板仅收录该组截图的可酷伯、维迦、洛、韦鲁斯、芸阿娜；不是通用中文 OCR。其他卡显示“未收录”，不能据此判断卡名。

商店候选检测基于名字区域亮色像素数量，仅用于提示；它没有经过不同场景的大量验证，不能作为未来自动点击的唯一依据。推荐图标和多数立绘特效在裁剪区域外，若闪光进入名字区域，仍可能影响分数，需要实机验证。不同布局、比例或字体需要重新标定。

## 离线识别验证

`tools/VisionCheck.java` 与手机直接共用 `ShopVision.java`，以三个原始截图为输入（收起、购买前、购买后），生成卡名模板并断言：收起时不判为商店、目标命中、其他四张不命中、买后第一格为空位。

```powershell
New-Item -ItemType Directory -Force .tools/vision-check | Out-Null
& "$env:PROBE_JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d .tools/vision-check app/src/main/java/local/jcc/probe/ShopVision.java tools/VisionCheck.java
& "$env:PROBE_JAVA_HOME/bin/java.exe" -cp .tools/vision-check VisionCheck '收起截图.jpg' '购买前截图.jpg' '购买后截图.jpg'
```

这组参考验证不是泛化准确率测试。待补充：相同卡出现在其他卡位、实时闪光、不同卡名、不同金币值和商店切换动画。

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

源码无第三方运行时依赖。当前 APK 使用官方 Android SDK 35 构建，最低 Android 11，目标 Android 15，版本为 0.2。卡名二值模板随 APK 打包。

```powershell
python tools/prepare_sdk.py
# 按本机情况指定 Java 17；此变量只用于本构建脚本。
$env:PROBE_JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2023.1\jbr'
python tools/build_apk.py
```

`prepare_sdk.py` 从 Google 下载 SDK 平台与构建工具，并对照官方仓库 SHA-1 校验。`build_apk.py` 使用 aapt2、javac、D8、zipalign、apksigner 生成 APK 并验证签名。SDK、测试签名私钥和构建结果不会加入 Git。

也保留了 AGP 8.7.3 的 Gradle 工程配置（需要 Gradle 8.9、JDK 17 和本机 SDK）。直接导入 Android Studio 时请使用它自带的 SDK 配置。

## 本次验证状态

- 已完成：用户确认手机游戏截图正常；三张参考截图的匹配断言通过；Java 编译、资源打包、DEX 生成、APK 签名验证。
- 待完成：v0.2 悬浮窗实机交互及实时识别验证，含闪光和推荐标记。
- 尚未实现自动购买和自动刷新。

接口文档：https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
