# 金铲铲助手 v0.4 · 小米 14

仅适配 **2670×1200 原生横屏截图**。按用户输入顺序自动购买指定卡，支持悬浮窗开始/暂停、自动/手动刷新切换及金币底线。截图和中文 OCR 都在手机本地处理，合并后的清单明确移除 INTERNET 权限。

## 使用

1. 安装 `dist/JccScreenshotProbe.apk`，确认“金铲铲截图验证”无障碍服务已启用；更新后可能需要重新开启。
2. 每行输入一个游戏里显示的完整卡名，越靠前越优先，同名多张从左往右买。
3. 设置保留金币，选择是否自动刷新，点击“保存并显示悬浮窗”。
4. 切到游戏，手动展开商店，点悬浮窗“开始”。开始时尚未展开则等待，不自动打开商店。
5. 开启自动刷新：买完并确认后刷新；关闭：你手动刷新，程序只买目标。可在悬浮窗中即时切换，关闭会取消尚未派发的刷新。
6. 点暂停或关商店停止。离开游戏、画面尺寸变化、持续识别不清或手势失败也会暂停。

悬浮窗开始时自动避开商店；标题和按钮显示运行状态。标题可拖动，点击收起/展开。“仅识别”模式完全不派发游戏点击，适合验证新目标。原单次截图功能仍保留：点按钮 8 秒后截一张并保存到 `Pictures/JccProbe/`。

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

使用捆绑中文模型的 ML Kit 16.0.1，识别原生卡名横条，并分别检查彩色和保留笔画灰度版本。未收录名字要求两者文字一致且各字段置信度不低于 40%；该门槛与旧 Tesseract 分数不可互换。它是一致性检查，不能保证错误结果一定被排除。

用户标注过的五个卡名保留原生文字模板，匹配阈值 0.90；1/2/3 费用字样模板阈值 0.88。其他费用用彩色/灰度 OCR 交叉确认。模板只比较字形，不按推荐标记或整张立绘购买。刷新按钮先比较原生字样模板，失败时精确读取“刷新”。

推荐图标大多在裁剪区外。闪光若进入文字区域仍可能干扰，会触发重读或暂停。暂不适配其他分辨率、其他商店布局、选秀或自动出售棋子。

## 操作确认

每次购买后确认目标格变空且金币准确扣除费用；刷新后确认金币扣除 2，且新卡已经出现。确认前不会重复点击，连续三次不成功就停止。满备战区、经济变化或特殊刷新规则可能导致确认不通过，会暂停而非猜测继续。

点击前再截一帧，对五个名字、金币和刷新区域做局部校验，变化则重新识别。每次截图调用间隔至少 360ms；点击按住 35～65ms，点击前额外等 40～100ms，并加入小幅位置偏移。完整循环耗时还包含 OCR、二次截图与结果确认。暂停会使在途结果和待派发操作失效；已派发的短手势可能完成，但不会继续下一次。

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

- `tools/PolicyCheck.java`：优先级、多张目标、费用确认、金币底线、失败停止、刷新动画空帧不再触发刷新、运行中刷新开关。
- `tools/GuardCheck.java`：旧画面校验拒绝购买后及关闭商店的画面。
- `OcrChecks`：手机只读检查保存样本，不会派发手势。原始三张图片命名 `fixture0.jpg` 至 `fixture2.jpg`，现场失败图命名 `fixture3.png`，放在 `/sdcard/Android/data/local.jcc.probe/files/`，执行 `adb shell am instrument -w local.jcc.probe/.OcrChecks`。
- 四张样本覆盖商店收起、购买前/后、空位、金币 74/73/31、费用 1/2/3，以及卡蜜尔/凯特琳/沃里克/阿利斯塔/远古石甲虫。`result=PASS` 才算全部断言通过。

这不是全卡池准确率测试，也未覆盖蕾欧娜现场截图、单字新目标、4/5 费用、所有金币数字和完整实时购买循环。还需要继续实机验证。参考图中部分字段有字样模板快速路径，因此不能据此宣称 OCR 对任意卡名都准确。

官方接口：https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

中文 OCR 文档：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
