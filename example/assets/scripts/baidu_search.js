// 百度搜索测试脚本
// 打开浏览器 -> 百度 -> 搜索"新年快乐" -> 打开前5条结果

console.log("开始执行百度搜索脚本...")

// 1. 打开浏览器访问百度
openUrl("https://www.baidu.com")
sleep(3000)

// 2. 等待搜索框出现
console.log("等待百度页面加载...")
waitForText("百度", 10000)
sleep(1000)

// 3. 点击搜索框
console.log("点击搜索框...")
id("kw").click()
sleep(500)

// 4. 输入搜索关键词
console.log("输入关键词: 新年快乐")
id("kw").setText("新年快乐")
sleep(500)

// 5. 点击搜索按钮
console.log("点击搜索按钮...")
id("su").click()
sleep(3000)

// 6. 等待搜索结果
console.log("等待搜索结果...")
waitForText("新年快乐", 10000)
sleep(1000)

// 7. 打开前5条结果
console.log("打开前5条搜索结果...")
for (var i = 0; i < 5; i++) {
    // 找到搜索结果链接
    var results = className("android.widget.TextView").clickable().findAll()
    if (results.length > i) {
        console.log(`打开第${i+1}条结果`)
        results[i].click()
        sleep(3000)
        
        // 返回搜索结果页
        back()
        sleep(2000)
    }
}

console.log("脚本执行完成!")
