# Flutter Automate API 设计

参考 AutoJS V8 风格，设计一套完整的自动化 API。

## 全局函数

### 控制流

```javascript
sleep(ms)           // 暂停执行 ms 毫秒
exit()              // 退出脚本
setClip(text)       // 设置剪贴板
getClip()           // 获取剪贴板
toast(msg)          // 显示 Toast
log(msg)            // 输出日志
console.log(msg)    // 同上
```

### 手势操作

```javascript
click(x, y)                    // 点击坐标
longClick(x, y)                // 长按坐标
doubleClick(x, y)              // 双击
press(x, y, duration)          // 按压
swipe(x1, y1, x2, y2, duration) // 滑动
gesture(duration, [x1,y1], [x2,y2], ...) // 手势路径
gestures([duration1, [点...]], [duration2, [点...]], ...) // 多指手势
```

### 方向滑动

```javascript
swipeUp(distance?)             // 向上滑
swipeDown(distance?)           // 向下滑
swipeLeft(distance?)           // 向左滑
swipeRight(distance?)          // 向右滑
scrollUp(distance?)            // 滚动向上
scrollDown(distance?)          // 滚动向下
```

### 全局按键

```javascript
back()                         // 返回
home()                         // Home
recents()                      // 最近任务
notifications()                // 通知栏
quickSettings()                // 快速设置
powerDialog()                  // 电源对话框
screenshot(path?)              // 截屏
```

### 应用操作

```javascript
app.launch(packageName)        // 启动应用
app.launchApp(appName)         // 通过名称启动
app.getPackageName(appName)    // 获取包名
app.getAppName(packageName)    // 获取应用名
app.openUrl(url)               // 打开 URL
app.openAppSetting(packageName) // 打开应用设置
app.currentPackage()           // 当前包名
app.currentActivity()          // 当前 Activity
```

## UI 选择器

### 选择器构建

```javascript
// 基础选择器
text(str)                      // 文本匹配
textContains(str)              // 文本包含
textStartsWith(str)            // 文本开头
textEndsWith(str)              // 文本结尾
textMatches(regex)             // 文本正则

desc(str)                      // 描述匹配
descContains(str)              // 描述包含
descStartsWith(str)            // 描述开头
descEndsWith(str)              // 描述结尾
descMatches(regex)             // 描述正则

id(str)                        // 资源 ID
idContains(str)                // ID 包含
idStartsWith(str)              // ID 开头
idEndsWith(str)                // ID 结尾
idMatches(regex)               // ID 正则

className(str)                 // 类名
classNameContains(str)         // 类名包含
packageName(str)               // 包名

// 属性选择器
clickable(true/false)          // 可点击
longClickable(true/false)      // 可长按
scrollable(true/false)         // 可滚动
enabled(true/false)            // 已启用
checked(true/false)            // 已勾选
selected(true/false)           // 已选中
focusable(true/false)          // 可聚焦
focused(true/false)            // 已聚焦
editable(true/false)           // 可编辑

// 位置选择器
bounds(left, top, right, bottom) // 边界
boundsInside(left, top, right, bottom) // 边界内
boundsContains(left, top, right, bottom) // 包含边界

// 层级选择器
depth(n)                       // 深度
drawingOrder(n)                // 绘制顺序

// 链式组合
text("确定").clickable()       // 组合条件
```

### 选择器方法

```javascript
selector.findOne()             // 查找一个（立即返回）
selector.findOne(timeout)      // 查找一个（带超时）
selector.findOnce()            // 查找一个（不等待）
selector.findOnce(index)       // 查找第 N 个
selector.find()                // 查找所有
selector.exists()              // 是否存在
selector.waitFor()             // 等待出现
selector.untilFind()           // 等待直到找到

// 动作
selector.click()               // 点击
selector.longClick()           // 长按
selector.setText(text)         // 设置文本
selector.copy()                // 复制
selector.cut()                 // 剪切
selector.paste()               // 粘贴
selector.scrollUp()            // 向上滚动
selector.scrollDown()          // 向下滚动
selector.scrollLeft()          // 向左滚动
selector.scrollRight()         // 向右滚动
selector.select()              // 选中
selector.collapse()            // 折叠
selector.expand()              // 展开
```

### UiObject 对象

```javascript
obj.click()                    // 点击
obj.longClick()                // 长按
obj.setText(text)              // 设置文本
obj.text()                     // 获取文本
obj.desc()                     // 获取描述
obj.id()                       // 获取 ID
obj.classNam             // 获取类名
obj.packageName()              // 获取包名
obj.bounds()                   // 获取边界 {left,top,right,bottom}
obj.boundsInParent()           // 父容器内边界
obj.drawingOrder()             // 绘制顺序
obj.clickable()                // 是否可点击
obj.scrollable()               // 是否可滚动
obj.enabled()                  // 是否启用
obj.checked()                  // 是否勾选
obj.selected()                 // 是否选中
obj.editable()                 // 是否可编辑
obj.parent()                   // 父节点
obj.child(index)               // 子节点
obj.childCount()               // 子节点数量
obj.children()                 // 所有子节点
obj.find(selector)             // 在子树中查找
obj.findOne(selector)          // 在子树中查找一个
```

## UiCollection 对象

```javascript
collection.size()              // 数量
collection.get(index)          // 获取第 N 个
collection.each(callback)      // 遍历
collection.empty()             // 是否为空
collection.nonEmpty()          // 是否非空
collection.find(selector)      // 在集合中查找
collection.findOne(selector)   // 在集合中查找一个

// 快捷操作
collection.click()             // 点击所有
collection.setText(text)       // 设置所有文本
```

## 图像处理

### 找图找色

```javascript
images.captureScreen()         // 截图
images.captureScreen(path)     // 截图保存

images.read(path)              // 读取图片
images.load(url)               // 从 URL 加载

images.pixel(img, x, y)        // 获取像素
images.findColor(img, color, options) // 找色
images.findMultiColors(img, firstColor, colorOffsets, options) // 多点找色
images.findImage(img, template, options) // 找图

// 选项
{
  region: [x, y, w, h],        // 搜索区域
  threshold: 0.9,              // 相似度阈值
  level: 1                     // 缩放级别
}

// 返回
{ x, y }                       // 坐标或 null
```

### 图片处理

```javascript
images.save(img, path, format, quality) // 保存
images.clip(img, x, y, w, h)   // 裁剪
images.resize(img, w, h)       // 缩放
images.rotate(img, degree)     // 旋转
images.grayscale(img)          // 灰度化
images.threshold(img, value)   // 二值化
```

## 设备信息

```javascript
device.width                   // 屏幕宽度
device.height                  // 屏幕高度
device.buildId                 // 构建 ID
device.brand                   // 品牌
device.device                  // 设备名
device.model                   // 型号
device.product                 // 产品名
device.hardware                // 硬件
device.serial                  // 序列号
device.sdkInt                  // SDK 版本
device.release                 // Android 版本
device.fingerprint             // 指纹
device.imei                    // IMEI
device.androidId               // Android ID
device.mac                     // MAC 地址

device.vibrate(ms)             // 震动
device.cancelVibration()       // 取消震动
device.getBrightness()         // 获取亮度
device.setBrightness(value)    // 设置亮度
device.setBrightnessMode(mode) // 亮度模式
device.getMusicVolume()        // 获取音量
device.setMusicVolume(value)   // 设置音量
device.getAlarmVolume()        // 闹钟音量
device.setAlarmVolume(value)   // 设置闹钟音量
device.getBattery()            // 电量
device.isCharging()            // 是否充电
device.isScreenOn()            // 屏幕是否亮
device.wakeUp()                // 唤醒屏幕
device.keepScreenOn(duration)  // 保持亮屏
device.keepScreenDim(duration) // 保持屏幕
```

## Shell 命令

```javascript
shell(cmd, root)               // 执行命令
// 返回: { code, result, error }

$shell.setAsRoot(true)         // 设置 root 模式
$shell(cmd)                    // 简写
```

## 文件操作

```javascript
files.read(path)               // 读取文本
files.readBytes(path)          // 读取字节
files.write(path, text)        // 写入文本
files.writeBytes(path, bytes)  // 写入字节
files.append(path, text)       // 追加文本
files.appendBytes(path, bytes) // 追加字节
files.copy(src, dst)           // 复制
files.move(src, dst)           // 移动
files.rename(path, newName)    // 重命名
files.remove(path)             // 删除
files.removeDir(path)          // 删除目录
files.exists(path)             // 是否存在
files.isFile(path)             // 是否文件
files.isDir(path)              // 是否目录
files.isEmptyDir(path)         // 是否空目录
files.create(path)             // 创建文件
files.createWithDirs(path)     // 创建文件和目录
files.ensureDir(path)          // 确保目录存在
files.listDir(path)            // 列出目录
files.getSdcardPath()          // SD卡路径
files.cwd()                    // 当前目录
files.path(relativePath)       // 相对路径转绝对路径
```

## HTTP 请求

```javascript
http.get(url, options, callback) // GET 请求
http.post(url, data, options, callback) // POST 请求
http.postJson(url, json, options, callback) // POST JSON
http.postMultipart(url, files, options, callback) // 上传文件
http.request(url, options, callback) // 通用请求

// options
{
  headers: {},                 // 请求头
  timeout: 30000,              // 超时
  method: "GET",               // 方法
  body: "",                    // 请求体
  contentType: ""              // 内容类型
}

// 返回
{
  statusCode: 200,
  statusMessage: "OK",
  headers: {},
  body: {
    string: () => "",
    bytes: () => [],
    json: () => {}
  }
}
```

## 对话框

```javascript
dialogs.alert(title, content, callback) // 警告框
dialogs.confirm(title, content, callback) // 确认框
dialogs.prompt(title, prefill, callback) // 输入框
dialogs.input(title, prefill, callback) // 输入（带回调）
dialogs.select(title, items, callback) // 单选
dialogs.multiChoice(title, items, indices, callback) // 多选
dialogs.singleChoice(title, items, index, callback) // 单选列表

// 构建器模式
dialogs.build({
  title: "",
  content: "",
  positive: "确定",
  negative: "取消",
  neutral: "其他",
  items: [],
  itemsSelectMode: "select|single|multi",
  itemsSelectedIndex: 0,
  itemsSelectedIndices: [],
  inputHint: "",
  inputPrefill: "",
  progress: { max: 100, showMinMax: true },
  checkBoxPrompt: "",
  checkBoxChecked: false,
  cancelable: true,
  canceledOnTouchOutside: true
}).show()
```

## 悬浮窗

```javascript
floaty.window(layout)          // 创建悬浮窗
floaty.rawWindow(layout)       // 创建原始悬浮窗
floaty.closeAll()              // 关闭所有

window.setPosition(x, y)       // 设置位置
window.getX()                  // 获取 X
window.getY()                  // 获取 Y
window.setSize(w, h)           // 设置大小
window.getWidth()              // 获取宽度
window.getHeight()             // 获取高度
window.close()                 // 关闭
window.exitOnClose()           // 关闭时退出

window.setAdjustSoftInput(mode) // 软键盘模式
window.setTouchable(touchable) // 是否可触摸
```

## 传感器

```javascript
sensors.register(name, listener) // 注册监听
sensors.unregister(listener)   // 取消监听
sensors.unregisterAll()        // 取消所有

// 传感器类型
"accelerometer"                // 加速度
"orientation"                  // 方向
"gyroscope"                    // 陀螺仪
"magnetic_field"               // 磁场
"gravity"                      // 重力
"linear_acceleration"          // 线性加速度
"ambient_temperature"          // 环境温度
"light"                        // 光线
"pressure"                     // 压力
"proximity"                    // 接近
"relative_humidity"            // 相对湿度
```

## 线程

```javascript
threads.start(callback)        // 启动线程
threads.currentThread()        // 当前线程
threads.disposable()           // 一次性线程
threads.atomic(value)          // 原子变量
threads.lock()                 // 锁

thread.interrupt()             // 中断
thread.join()                  // 等待结束
thread.isAlive()               // 是否存活
thread.waitFor()               // 等待启动
thread.setTimeout(callback, ms) // 定时器
thread.setInterval(callback, ms) // 循环定时器
thread.setImmediate(callback)  // 立即执行
thread.clearTimeout(id)        // 清除定时器
thread.clearInterval(id)       // 清除循环定时器
thread.clearImmediate(id)      // 清除立即执行
```

## 定时器

```javascript
setTimeout(callback, ms)       // 延迟执行
setInterval(callback, ms)      // 循环执行
setImmediate(callback)         // 立即执行
clearTimeout(id)               // 清除
clearInterval(id)              // 清除
clearImmediate(id)             // 清除
```

## 事件监听

```javascript
events.on(name, listener)      // 监听事件
events.off(name, listener)     // 取消监听
events.emitter()               // 创建发射器
events.broadcast.emit(name, data) // 广播

// 按键监听
events.onKeyDown(keyName, listener)
events.onKeyUp(keyName, listener)
events.onceKeyDown(keyName, listener)
events.onceKeyUp(keyName, listener)
events.removeAllKeyDownListeners(keyName)
events.removeAllKeyUpListeners(keyName)

// 音量键
events.observeKey()
events.setKeyInterceptionEnabled(key, enabled)
```

## 存储

```javascript
storages.create(name)          // 创建存储
storage.get(key, defaultValue) // 获取
storage.put(key, value)        // 存储
storage.remove(key)            // 删除
storage.contains(key)          // 是否存在
storage.clear()                // 清空
```

## 实现优先级

### Phase 1 - 核心（已完成 ✅）
- [x] 基础手势: click, longClick, swipe, swipeUp/Down
- [x] 全局按键: back, home
- [x] 控制流: sleep, console.log
- [x] 应用: openUrl

### Phase 2 - UI 自动化
- [ ] UI 选择器完整实现
- [ ] UiObject 对象
- [ ] UiCollection
- [ ] 等待机制

### Phase 3 - 扩展功能
- [ ] 找图找色
- [ ] 文件操作
- [ ] HTTP 请求
- [ ] 对话框
- [ ] 悬浮窗

### Phase 4 - 高级功能
- [ ] 多线程
- [ ] 事件监听
- [ ] 传感器
- [ ] 存储
