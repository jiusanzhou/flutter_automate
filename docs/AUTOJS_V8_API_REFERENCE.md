# Auto.js Pro V8 API 参考文档

> 抓取自 http://autojs.cc/v8

## 目录

1. [全局变量与函数 (globals)](#全局变量与函数-globals)
2. [自动化 - 坐标操作](#自动化---坐标操作)
3. [app - 应用](#app---应用)
4. [device - 设备](#device---设备)

---

## 全局变量与函数 (globals)

全局变量和函数在所有模块中均可使用。

### sleep(n)
- `n` {number} 毫秒数

暂停运行n**毫秒**的时间。1秒等于1000毫秒。

```js
// 暂停5秒
sleep(5000);
```

### setClip(text)
- `text` {string} 文本

设置剪贴板内容。此剪贴板即系统剪贴板，在一般应用的输入框中"粘贴"既可使用。

```js
setClip("剪贴板文本");
```

### getClip()
- 返回 {string}

返回系统剪贴板的内容。

```js
toast("剪贴板内容为:" + getClip());
```

### toast(message)
- `message` {string} 要显示的信息

以气泡显示信息message几秒。(具体时间取决于安卓系统，一般都是2秒)

注意，信息的显示是"异步"执行的，并且，不会等待信息消失程序才继续执行。

```js
for(var i = 0; i < 100; i++){
    toast(i);
    sleep(2000);
}
```

### toastLog(message)
- `message` {string} 要显示的信息

相当于`toast(message);log(message)`。显示信息message并在控制台中输出。

### exit()

立即停止脚本运行。

立即停止是通过抛出`ScriptInterruptedException`来实现的，因此如果用`try...catch`把exit()函数的异常捕捉，则脚本不会立即停止。

### random(min, max)
- `min` {number} 随机数产生的区间下界
- `max` {number} 随机数产生的区间上界
- 返回 {number}

返回一个在[min...max]之间的随机数。例如random(0, 2)可能产生0, 1, 2。

### random()
- 返回 {number}

返回在[0, 1)的随机浮点数。

### requiresApi(api)
- `api` {number} Android版本号

表示此脚本需要Android API版本达到指定版本才能运行。

平台版本与API级别对照表：
- Android 7.0：24
- Android 6.0：23
- Android 5.1：22
- Android 5.0：21
- Android 4.4：19

### context

全局变量。一个android.content.Context对象。
注意该对象为ApplicationContext，因此不能用于界面、对话框等的创建。

### currentPackage()
- 返回 {string} 当前正在运行的应用的包名

获取应用包名。返回最近一次监测到的正在运行的应用的包名。
此函数依赖于无障碍服务，如果服务未启动，则抛出异常并提示用户启动。

```js
// 获取某音包名
// com.ss.android.ugc.aweme
log(currentPackage());
```

### currentActivity()
- 返回 {string} 当前正在运行的Activity的名称

获取应用Activity。此函数依赖于无障碍服务。

```js
// 某音会话页
// com.ss.android.ugc.aweme.im.sdk.chat.ChatRoomActivity
log(currentActivity())
```

---

## 自动化 - 坐标操作

> Stability: 2 - Stable

本章节介绍了一些使用坐标进行点击、滑动的函数。这些函数有的需要安卓7.0以上，有的需要root权限。

### setScreenMetrics(width, height)
- `width` {number} 屏幕宽度，单位像素
- `height` {number} 屏幕高度，单位像素

设置脚本坐标点击所适合的屏幕宽高。如果脚本运行时，屏幕宽度不一致会自动放缩坐标。

```js
setScreenMetrics(1080, 1920);
click(800, 200);
longClick(300, 500);
```

在其他设备上AutoJs会自动放缩坐标以便脚本仍然有效。例如在540 * 960的屏幕中`click(800, 200)`实际上会点击位置(400, 100)。

### 安卓7.0以上的触摸和手势模拟

**注意以下命令只有Android7.0及以上才有效**

#### click(x, y)
- `x` {number} 要点击的坐标的x值
- `y` {number} 要点击的坐标的y值
- 返回 {boolean}

模拟点击坐标(x, y)，并返回是否点击成功。只有在点击执行完成后脚本才继续执行。

一般而言，只有点击过程(大约150毫秒)中被其他事件中断(例如用户自行点击)才会点击失败。

使用该函数模拟连续点击时可能有点击速度过慢的问题，这时可以用`press()`函数代替。

#### longClick(x, y)
- `x` {number} 要长按的坐标的x值
- `y` {number} 要长按的坐标的y值
- 返回 {boolean}

模拟长按坐标(x, y), 并返回是否成功。只有在长按执行完成（大约600毫秒）时脚本才会继续执行。

#### press(x, y, duration)
- `x` {number} 要按住的坐标的x值
- `y` {number} 要按住的坐标的y值
- `duration` {number} 按住时长，单位毫秒
- 返回 {boolean}

模拟按住坐标(x, y), 并返回是否成功。只有按住操作执行完成时脚本才会继续执行。

如果按住时间过短，那么会被系统认为是点击；如果时长超过500毫秒，则认为是长按。

连点器示例：
```js
// 循环100次
for(var i = 0; i < 100; i++){
    // 点击位置(500, 1000), 每次用时1毫秒
    press(500, 1000, 1);
}
```

#### swipe(x1, y1, x2, y2, duration)
- `x1` {number} 滑动的起始坐标的x值
- `y1` {number} 滑动的起始坐标的y值
- `x2` {number} 滑动的结束坐标的x值
- `y2` {number} 滑动的结束坐标的y值
- `duration` {number} 滑动时长，单位毫秒
- 返回 {boolean}

模拟从坐标(x1, y1)滑动到坐标(x2, y2)，并返回是否成功。

#### gesture(duration, [x1, y1], [x2, y2], ...)
- `duration` {number} 手势的时长
- `[x, y]` {...} 手势滑动路径的一系列坐标

模拟手势操作。例如`gesture(1000, [0, 0], [500, 500], [500, 1000])`为模拟一个从(0, 0)到(500, 500)到(500, 100)的手势操作，时长为2秒。

#### gestures([delay1, duration1, [x1, y1], [x2, y2], ...], ...)

同时模拟多个手势。每个手势的参数为[delay, duration, 坐标]，delay为延迟多久(毫秒)才执行该手势；duration为手势执行时长；坐标为手势经过的点的坐标。

手指捏合示例：
```js
gestures([0, 500, [800, 300], [500, 1000]],
         [0, 500, [300, 1500], [500, 1000]]);
```

### 使用root权限点击和滑动的简单命令

> Stability: 1 - Experimental

以下函数均需要root权限，可以实现任意位置的点击、滑动等。

#### Tap(x, y)
- `x` {number} 要点击的x坐标
- `y` {number} 要点击的y坐标

点击位置(x, y)。需要root权限。

#### Swipe(x1, y1, x2, y2, [duration])
- `x1` {number} 滑动起点的x坐标
- `y1` {number} 滑动起点的y坐标
- `x2` {number} 滑动终点的x坐标
- `y2` {number} 滑动终点的y坐标
- `duration` {number} 滑动动作所用的时间

滑动。从(x1, y1)位置滑动到(x2, y2)位置。

### RootAutomator

> Stability: 2 - Stable

RootAutomator是一个使用root权限来模拟触摸的对象，用它可以完成触摸与多点触摸，并且这些动作的执行没有延迟。

```js
var ra = new RootAutomator();
events.on('exit', function(){
    ra.exit();
});
// 执行一些点击操作
```

#### new RootAutomator([options])
- `options` {object} 可选参数，包括:
  - `adb` {boolean} 是否使用adb权限，默认为false
  - `inputDevice` {string} 指定RootAutomator操作的设备

#### RootAutomator.tap(x, y[, id])
- `x` {number} 横坐标
- `y` {number} 纵坐标
- `id` {number} 多点触摸id，可选，默认为1

点击位置(x, y)。其中id是一个整数值，用于区分多点触摸，不同的id表示不同的"手指"。

```js
var ra = new RootAutomator();
// 让"手指1"点击位置(100, 100)
ra.tap(100, 100, 1);
// 让"手指2"点击位置(200, 200);
ra.tap(200, 200, 2);
ra.exit();
```

#### RootAutomator.swipe(x1, x2, y1, y2[, duration, id])
模拟一次从(x1, y1)到(x2, y2)的时间为duration毫秒的滑动。

#### RootAutomator.press(x, y, duration[, id])
模拟按下位置(x, y)，时长为duration毫秒。

#### RootAutomator.longPress(x, y[, id])
模拟长按位置(x, y)。

#### RootAutomator.touchDown(x, y[, id])
模拟手指按下位置(x, y)。

#### RootAutomator.touchMove(x, y[, id])
模拟移动手指到位置(x, y)。

#### RootAutomator.touchUp([id])
模拟手指弹起。

### RootAutomator2

RootAutomator2用于基于root或者adb权限，模拟点击、手势、长按等操作。相比RootAutomator，RootAutomator2的兼容性更佳。

```js
let screenWidth = $device.width;
let screenHeight = $device.height;

// 使用root权限执行
const ra = new RootAutomator2({ root: true });

// 点击(200, 200)的位置
ra.tap(200, 200);
sleep(1000);

// 按住屏幕中点持续500毫秒
ra.press(screenWidth / 2, screenHeight / 2, 500);
sleep(1000);

// 从(500, 200)滑动到(500, 1000)，滑动时长300毫秒
ra.swipe(500, 200, 500, 1000, 300);

// 退出RootAutomator
ra.exit();
```

#### new RootAutomator2([options])
- `options` {object} 选项:
  - `adb` {boolean} 是否使用adb权限，默认为false
  - `root` {boolean} 是否使用root权限，默认为true

#### RootAutomator2.tap(x, y)
点击位置(x, y)，时长为5毫秒。

#### RootAutomator2.longPress(x, y)
长按(x, y)位置。

#### RootAutomator2.press(x, y, duration)
按下(x, y)位置持续duration时长，然后抬起手指。

#### RootAutomator2.swipe(x1, y1, x2, y2, duration)
在给定的duration时长从(x1, y1)位置滑动到(x2, y2)位置。

#### RootAutomator2.touchDown(x, y, [id])
按下(x, y)位置。

#### RootAutomator2.touchDown(pointers)
模拟多指按下事件。
```js
ra.touchDown([
    { x: 100, y: 100, id: 0 },
    { x: 200, y: 200, id: 1 }
]);
```

#### RootAutomator2.touchMove(x, y, [id])
将手指移动到(x, y)位置。

#### RootAutomator2.touchMove(pointers)
模拟多指移动事件。

#### RootAutomator2.touchUp([id])
抬起手指。若不指定id则抬起所有手指。

#### RootAutomator2.touchUp(pointers)
模拟多指抬起事件。

#### RootAutomator2.flush()
等待所有操作完成。

#### RootAutomator2.exit([forced])
退出RootAutomator2。

---

## app - 应用

app模块提供一系列函数，用于使用其他应用、与其他应用交互。例如发送意图、打开文件、发送邮件等。

### app.versionCode
- {number}

当前软件版本号，整数值。例如160, 256等。

### app.versionName
- {string}

当前软件的版本名称，例如"3.0.0 Beta"。

### app.autojs.versionCode
- {number}

Auto.js版本号，整数值。

### app.autojs.versionName
- {string}

Auto.js版本名称，例如"3.0.0 Beta"。

### app.launchApp(appName)
- `appName` {string} 应用名称
- 返回 {boolean}

通过应用名称启动应用。如果该名称对应的应用不存在，则返回false; 否则返回true。

该函数也可以作为全局函数使用。

```js
launchApp("Auto.js");
```

### app.launch(packageName)
- `packageName` {string} 应用包名
- 返回 {boolean}

通过应用包名启动应用。

```js
// 启动微信
launch("com.tencent.mm");
```

### app.launchPackage(packageName)
相当于`app.launch(packageName)`。

### app.getPackageName(appName)
- `appName` {string} 应用名称
- 返回 {string}

获取应用名称对应的已安装的应用的包名。

```js
var name = getPackageName("QQ"); // 返回"com.tencent.mobileqq"
```

### app.getAppName(packageName)
- `packageName` {string} 应用包名
- 返回 {string}

获取应用包名对应的已安装的应用的名称。

```js
var name = getAppName("com.tencent.mobileqq"); // 返回"QQ"
```

### app.openAppSetting(packageName)
- `packageName` {string} 应用包名
- 返回 {boolean}

打开应用的详情页(设置页)。

### app.viewFile(path)
- `path` {string} 文件路径

用其他应用查看文件。

```js
// 查看文本文件
app.viewFile("/sdcard/1.txt");
```

### app.editFile(path)
- `path` {string} 文件路径

用其他应用编辑文件。

### app.uninstall(packageName)
- `packageName` {string} 应用包名

卸载应用。

```js
// 卸载QQ
app.uninstall("com.tencent.mobileqq");
```

### app.openUrl(url)
- `url` {string} 网站的Url

用浏览器打开网站url。如果不以"http://"或"https://"开头则默认是"http://"。

### app.sendEmail(options)
- `options` {Object} 发送邮件的参数：
  - `email` {string} | {Array} 收件人的邮件地址
  - `cc` {string} | {Array} 抄送收件人
  - `bcc` {string} | {Array} 密送收件人
  - `subject` {string} 邮件主题(标题)
  - `text` {string} 邮件正文
  - `attachment` {string} 附件的路径

```js
app.sendEmail({
    email: ["10086@qq.com", "10001@qq.com"],
    subject: "这是一个邮件标题",
    text: "这是邮件正文"
});
```

### app.startActivity(name)
- `name` {string} 活动名称，可选的值为:
  - `console` 日志界面
  - `settings` 设置界面

启动Auto.js的特定界面。

### app.intent(options)
- `options` {Object} 选项，包括：
  - `action` {string} 意图的Action
  - `type` {string} 意图的MimeType
  - `data` {string} 意图的Data (Uri)
  - `category` {Array} 意图的类别
  - `packageName` {string} 目标包名
  - `className` {string} 目标Activity或Service等组件的名称
  - `extras` {Object} Intent的Extras(额外信息)
  - `flags` {Array} intent的标识
  - `root` {Boolean} 是否以root权限启动

根据选项，构造一个意图Intent对象。

```js
// 打开应用来查看图片文件
var i = app.intent({
    action: "VIEW",
    type: "image/png",
    data: "file:///sdcard/1.png"
});
context.startActivity(i);
```

使用root权限跳转示例：
```js
app.startActivity({
    packageName: "org.autojs.autojs",
    className: "org.autojs.autojs.ui.settings.SettingsActivity_",
    root: true
});
```

### app.startActivity(options)
根据选项构造一个Intent，并启动该Activity。

### app.sendBroadcast(options)
根据选项构造一个Intent，并发送该广播。

### app.startService(options)
根据选项构造一个Intent，并启动该服务。

### app.sendBroadcast(name)
- `name` {string} 特定的广播名称：
  - `inspect_layout_hierarchy` 布局层次分析
  - `inspect_layout_bounds` 布局范围

发送特定名称的广播可以触发Auto.js的布局分析。

### app.intentToShell(options)
根据选项构造一个Intent，转换为对应的shell的intent命令的参数。

```js
shell("am start " + app.intentToShell({
    packageName: "org.autojs.autojs",
    className: "org.autojs.autojs.ui.settings.SettingsActivity_"
}), true);
```

### app.parseUri(uri)
- `uri` {string} 代表Uri的字符串
- 返回 {Uri}

解析uri字符串并返回相应的Uri对象。

### app.getUriForFile(path)
- `path` {string} 文件路径
- 返回 {Uri}

从一个文件路径创建一个uri对象。

### app.getInstalledApps([options])
- `options` {Object} 选项
- 返回 {Array<ApplicationInfo>}

返回为当前用户安装的所有应用程序包的列表。

```js
// 获取系统app
let apps = $app.getInstalledApps({
    get: ['meta_data'],
    match: ['system_only']
});
console.log(apps);
```

---

## device - 设备

> Stability: 2 - Stable

device模块提供了与设备有关的信息与操作，例如获取设备宽高，内存使用率，IMEI，调整设备亮度、音量等。

### 设备信息属性

#### device.width
- {number}
设备屏幕分辨率宽度。例如1080。

#### device.height
- {number}
设备屏幕分辨率高度。例如1920。

#### device.buildId
- {string}
修订版本号，或者诸如"M4-rc20"的标识。

#### device.broad
- {string}
设备的主板型号。

#### device.brand
- {string}
与产品或硬件相关的厂商品牌，如"Xiaomi", "Huawei"等。

#### device.device
- {string}
设备在工业设计中的名称。

#### device.model
- {string}
设备型号。

#### device.product
- {string}
整个产品的名称。

#### device.bootloader
- {string}
设备Bootloader的版本。

#### device.hardware
- {string}
设备的硬件名称(来自内核命令行或/proc)。

#### device.fingerprint
- {string}
构建(build)的唯一标识码。

#### device.serial
- {string}
硬件序列号。

#### device.sdkInt
- {number}
安卓系统API版本。例如安卓4.4的sdkInt为19。

#### device.incremental
- {string}
源代码控制的内部值。

#### device.release
- {string}
Android系统版本号。例如"5.0", "7.1.1"。

#### device.baseOS
- {string}
产品所基于的基础操作系统构建。

#### device.securityPatch
- {string}
安全补丁程序级别。

#### device.codename
- {string}
开发代号，例如发行版是"REL"。

### 设备信息方法

#### device.getIMEI()
- 返回 {string}
返回设备的IMEI。

#### device.getAndroidId()
- 返回 {string}
返回设备的Android ID。Android ID为一个用16进制字符串表示的64位整数。

#### device.getMacAddress()
- 返回 {string}
返回设备的Mac地址。该函数需要在有WLAN连接的情况下才能获取。

### 亮度控制

#### device.getBrightness()
- 返回 {number}
返回当前的(手动)亮度。范围为0~255。

#### device.getBrightnessMode()
- 返回 {number}
返回当前亮度模式，0为手动亮度，1为自动亮度。

#### device.setBrightness(b)
- `b` {number} 亮度，范围0~255

设置当前手动亮度。

#### device.setBrightnessMode(mode)
- `mode` {number} 亮度模式，0为手动亮度，1为自动亮度

设置当前亮度模式。

### 音量控制

#### device.getMusicVolume()
- 返回 {number}
返回当前媒体音量。

#### device.getNotificationVolume()
- 返回 {number}
返回当前通知音量。

#### device.getAlarmVolume()
- 返回 {number}
返回当前闹钟音量。

#### device.getMusicMaxVolume()
- 返回 {number}
返回媒体音量的最大值。

#### device.getNotificationMaxVolume()
- 返回 {number}
返回通知音量的最大值。

#### device.getAlarmMaxVolume()
- 返回 {number}
返回闹钟音量的最大值。

#### device.setMusicVolume(volume)
设置当前媒体音量。

#### device.setNotificationVolume(volume)
设置当前通知音量。

#### device.setAlarmVolume(volume)
设置当前闹钟音量。

### 电池与内存

#### device.getBattery()
- 返回 {number} 0.0~100.0的浮点数
返回当前电量百分比。

#### device.isCharging()
- 返回 {boolean}
返回设备是否正在充电。

#### device.getTotalMem()
- 返回 {number}
返回设备内存总量，单位字节(B)。

#### device.getAvailMem()
- 返回 {number}
返回设备当前可用的内存，单位字节(B)。

### 屏幕控制

#### device.isScreenOn()
- 返回 {boolean}
返回设备屏幕是否是亮着的。

#### device.wakeUp()
唤醒设备。包括唤醒设备CPU、屏幕等。可以用来点亮屏幕。

#### device.wakeUpIfNeeded()
如果屏幕没有点亮，则唤醒设备。

#### device.keepScreenOn([timeout])
- `timeout` {number} 屏幕保持常亮的时间, 单位毫秒

保持屏幕常亮。

```js
// 一直保持屏幕常亮
device.keepScreenOn()
```

建议使用比较长的时长来代替"一直保持屏幕常亮"：
```js
device.keepScreenOn(3600 * 1000)
```

#### device.keepScreenDim([timeout])
保持屏幕常亮，但允许屏幕变暗来节省电量。

#### device.cancelKeepingAwake()
取消设备保持唤醒状态。

### 其他

#### device.vibrate(ms)
- `ms` {number} 震动时间，单位毫秒

使设备震动一段时间。

```js
// 震动两秒
device.vibrate(2000);
```

#### device.cancelVibration()
如果设备处于震动状态，则取消震动。

---

## 其他模块

完整的 Auto.js Pro V8 API 还包括以下模块（可在 http://autojs.cc/v8 查看）：

- **自动化 - 控件操作** - 基于无障碍服务的UI控件操作
- **base64** - Base64编解码
- **colors - 颜色** - 颜色处理
- **canvas - 画布** - 画布绘制
- **console - 控制台** - 日志输出
- **crypto - 加解密与消息摘要** - 加密解密
- **debug - 调试工具** - 调试
- **dialogs - 对话框** - 对话框
- **engines - 脚本引擎** - 脚本引擎管理
- **events - 事件与监听** - 事件监听
- **floaty - 悬浮窗** - 悬浮窗
- **files - 文件系统** - 文件操作
- **http - HTTP网络请求** - HTTP请求
- **images - 图片处理** - 图片处理与找图找色
- **keys - 按键模拟** - 按键模拟
- **media - 多媒体** - 多媒体
- **module - 模块** - 模块系统
- **ocr - 文字识别** - OCR
- **plugins - 插件** - 插件
- **power_manager - 电源管理** - 电源管理
- **sensors - 传感器** - 传感器
- **shell - Shell命令** - Shell命令
- **storages - 本地存储** - 本地存储
- **settings - 设置** - 设置
- **threads - 多线程** - 多线程
- **timers - 定时器** - 定时器
- **ui - 用户界面** - UI界面
- **util - 工具** - 工具函数
- **WebSocket** - WebSocket
- **zip - 压缩与解压** - 压缩解压

---

> 文档来源: http://autojs.cc/v8
> 抓取日期: 2026-02-16
