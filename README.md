# 金铲铲助手 v0.5 · 小米 14

仅适配 **2670×1200 原生横屏截图**。使用一个开启/暂停切换按钮控制待命：开启期间，商店打开自动买卡，关闭后等下一次打开继续。支持自动/手动刷新和金币底线。截图和单次中文 OCR 都在手机本地处理，合并清单移除 INTERNET 权限。

## 使用

1. 安装 `dist/JccScreenshotProbe.apk`，确认“金铲铲截图验证”无障碍服务已启用；更新后可能需要重新开启。
2. 每行输入一个游戏里显示的完整卡名，越靠前越优先，同名多张从左往右买。
3. 设置保留金币，选择是否自动刷新，点击“保存并显示悬浮窗”。
4. 点击悬浮窗“开启”。可以在商店收起时或进入游戏之前开启，程序保持待命，等游戏商店展开后开始。
5. 开启自动刷新：买完并确认后刷新；关闭：你手动刷新，程序只买目标。可在悬浮窗中即时切换，关闭会取消尚未派发的刷新。
6. 关商店只停止当前操作，开启状态不变；再打开会继续。点同一按钮“暂停”才关闭待命。离开游戏、横屏切换、暂时读不清、金币不足等都会等待，不自动切成暂停。权限失效、服务中断或系统拒绝派发手势时仍会停止并提示原因。

小悬浮窗宽 205dp，保留一个开启/暂停按钮、自动刷新开关和关闭按钮；标题可拖动，点标题隐藏/显示一行状态，控制按钮始终保留。开启时自动避开商店。“仅识别”不派发游戏点击。原单次截图功能仍保留，结果保存到 `Pictures/JccProbe/`。

## 固定识别区域

所有坐标均为手机原图像素，不将名字缩小到参考分辨率，不对 OCR 输入做硬二值化。

| 区域 | 左上角 (x, y) | 宽×高 |
|---|---|---|
| 第 1 格卡名 | 488, 489 | 274×55 |
| 第 2 格卡名 | 918, 489 | 274×55 |
| 第 3 格卡名 | 1348, 489 | 274×55 |
| 第 4 格卡名 | 1777, 489 | 274×55 |
| 第 5 格卡名 | 2207, 489 | 274×55 |
| 各格费用 | 对应卡名 x+350, 489 | 35×57 |
| 金币 | 2470, 1029 | 112×76 |
| 刷新文字 | 2444, 793 | 107×57 |

坐标定义在 `PhoneLayout.java`。`tools/RegionPreview.java` 可在原图副本上绘制这些区域，不改变原始图片。

使用捆绑中文模型的 ML Kit 16.0.1，识别原生卡名横条。按用户要求移除彩色/灰度交叉验证及置信度阻断，直接使用单次读取的非空卡名精确匹配目标列表，不做模糊纠错。费用和金币直接解析单次结果；字段暂时为空则跳过本帧并继续识别。

用户标注过的五个卡名保留原生文字模板，匹配阈值 0.90；1/2/3 费用字样模板阈值 0.88。其他费用使用单次 OCR 结果。模板只比较字形，不按推荐标记或整张立绘购买。刷新按钮先比较原生字样模板，失败时精确读取“刷新”。

推荐图标大多在裁剪区外。闪光若进入文字区域仍可能干扰，会等待下一帧。暂不适配其他分辨率、其他商店布局、选秀或自动出售棋子。

## 操作确认

购买后以目标格变空确认；刷新后以卡名变化或金币扣除 2 确认，新卡尚未出现时等待动画结束。八个可读帧仍未确认且金币完全未变化时，重新判断并重试无效点击。缺金币或读不到目标费用时继续待命，金币恢复后可继续。关店会清除上次待确认操作，避免重开时继续用旧状态。

点击前保留一帧局部像素变化检查，不再对卡名做第二次 OCR；画面变化则重新识别，保持开启。每次截图调用间隔至少 360ms；点击按住 35～65ms，点击前额外等 40～100ms，并加入小幅位置偏移。完整循环耗时还包含 OCR、点击前截图与结果确认。暂停使在途结果和待派发操作失效；已派发的短手势可能完成，但不会继续下一次。

前台检查读取窗口根节点包名，不遍历控件树。接受 `com.tencent.jkchess` 和 `com.tencent.jk`。

## 构建

Windows、Python 3.8+、完整 JDK 17、Gradle 8.9、Android SDK 35 / Build Tools 35.0.0。依赖通过 Gradle 从官方 Maven 仓库解析，OCR 模型直接打进 APK，不要求手机安装 Google Play 服务。

```powershell
python tools/prepare_build.py
python tools/build_apk.py
```

准备脚本将经校验的 SDK、Gradle 和完整 JDK 解压到项目的 `.tools/`。构建默认发现这里的完整 JDK，也支持 `PROBE_JAVA_HOME` 和 `ANDROID_HOME`。IDE 自带的精简运行时若没有 `jlink` 不能用于本构建。签名沿用 `.tools/probe-debug.jks`，SDK、签名私钥、APK、模型缓存和调试截图均不加入 Git。第三方信息见 `THIRD_PARTY_NOTICES.md`。

```powershell
& .tools/platform-tools/adb.exe -d install -r dist/JccScreenshotProbe.apk
& .tools/platform-tools/adb.exe -d shell am start -n local.jcc.probe/.MainActivity
& .tools/platform-tools/adb.exe -d pull /sdcard/Pictures/JccProbe ./captures
```

## 验证

- `tools/PolicyCheck.java`：优先级、多张目标、购买确认、缺金币等待及恢复、识别失败持续等待、关店重开自动恢复、无效点击延迟重试、刷新动画空帧等待、运行中刷新开关。
- `tools/GuardCheck.java`：旧画面校验拒绝购买后及关闭商店的画面。
- `OcrChecks`：手机只读检查保存样本，不会派发手势。原始三张图片命名 `fixture0.jpg` 至 `fixture2.jpg`，现场失败图命名 `fixture3.png`，放在 `/sdcard/Android/data/local.jcc.probe/files/`，执行 `adb shell am instrument -w local.jcc.probe/.OcrChecks`。
- 四张样本覆盖商店收起、购买前/后、空位、金币 74/73/31、费用 1/2/3，以及卡蜜尔/凯特琳/沃里克/阿利斯塔/远古石甲虫。`result=PASS` 才算全部断言通过。

这不是全卡池准确率测试，也未覆盖蕾欧娜现场截图、单字新目标、4/5 费用、所有金币数字和完整实时购买循环。还需要继续实机验证。参考图中部分字段有字样模板快速路径，因此不能据此宣称 OCR 对任意卡名都准确。

官方接口：https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

中文 OCR 文档：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
