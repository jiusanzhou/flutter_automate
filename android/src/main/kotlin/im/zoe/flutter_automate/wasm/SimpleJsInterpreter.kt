package im.zoe.flutter_automate.wasm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import im.zoe.flutter_automate.core.*
import java.util.regex.Pattern

/**
 * 简单 JavaScript 解释器
 * 当 WASM 模块不可用时的回退方案
 * 支持基础语法：变量、函数调用、条件、循环等
 */
class SimpleJsInterpreter(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleJsInterpreter"
    }
    
    // 全局变量
    private val globals = mutableMapOf<String, Any?>()
    
    // 内置函数
    private val builtinFunctions = mutableMapOf<String, (List<Any?>) -> Any?>()
    
    // 是否停止
    @Volatile
    private var stopped = false
    
    init {
        registerBuiltins()
    }
    
    fun stop() {
        stopped = true
    }
    
    private fun registerBuiltins() {
        // ==================== 日志 ====================
        builtinFunctions["console.log"] = { args -> 
            Log.i(TAG, "[JS] " + args.joinToString(" "))
            null
        }
        builtinFunctions["log"] = builtinFunctions["console.log"]!!
        builtinFunctions["print"] = builtinFunctions["console.log"]!!
        
        // ==================== 控制流 ====================
        builtinFunctions["sleep"] = { args ->
            val ms = (args.firstOrNull() as? Number)?.toLong() ?: 0L
            if (!stopped) Thread.sleep(ms)
            null
        }
        
        builtinFunctions["exit"] = { _ ->
            stopped = true
            null
        }
        
        // ==================== UI 选择器 ====================
        builtinFunctions["text"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().text(value)
        }
        
        builtinFunctions["textContains"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().textContains(value)
        }
        
        builtinFunctions["textStartsWith"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().textStartsWith(value)
        }
        
        builtinFunctions["textMatches"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().textMatches(value)
        }
        
        builtinFunctions["id"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().id(value)
        }
        
        builtinFunctions["idContains"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().idContains(value)
        }
        
        builtinFunctions["className"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().className(value)
        }
        
        builtinFunctions["desc"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().desc(value)
        }
        
        builtinFunctions["descContains"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().descContains(value)
        }
        
        // ==================== 手势操作 ====================
        builtinFunctions["click"] = { args ->
            val first = args.firstOrNull()
            when (first) {
                is UiSelector -> first.click()
                is Number -> {
                    val x = first.toFloat()
                    val y = (args.getOrNull(1) as? Number)?.toFloat() ?: 0f
                    GestureEngine.click(x, y)
                }
                is String -> {
                    // click("搜索") - 按文本点击
                    UiSelector().text(first).click()
                }
                else -> false
            }
        }
        
        builtinFunctions["longClick"] = { args ->
            val first = args.firstOrNull()
            when (first) {
                is UiSelector -> first.longClick()
                is Number -> {
                    val x = first.toFloat()
                    val y = (args.getOrNull(1) as? Number)?.toFloat() ?: 0f
                    val duration = (args.getOrNull(2) as? Number)?.toLong() ?: 500L
                    GestureEngine.longClick(x, y, duration)
                }
                else -> false
            }
        }
        
        builtinFunctions["swipe"] = { args ->
            val x1 = (args.getOrNull(0) as? Number)?.toFloat() ?: 0f
            val y1 = (args.getOrNull(1) as? Number)?.toFloat() ?: 0f
            val x2 = (args.getOrNull(2) as? Number)?.toFloat() ?: 0f
            val y2 = (args.getOrNull(3) as? Number)?.toFloat() ?: 0f
            val duration = (args.getOrNull(4) as? Number)?.toLong() ?: 300L
            GestureEngine.swipe(x1, y1, x2, y2, duration)
        }
        
        builtinFunctions["swipeUp"] = { _ -> GestureEngine.swipeUp() }
        builtinFunctions["swipeDown"] = { _ -> GestureEngine.swipeDown() }
        builtinFunctions["swipeLeft"] = { _ -> GestureEngine.swipeLeft() }
        builtinFunctions["swipeRight"] = { _ -> GestureEngine.swipeRight() }
        
        // ==================== 全局操作 ====================
        builtinFunctions["back"] = { _ ->
            AutomateAccessibilityService.instance?.pressBack() ?: false
        }
        
        builtinFunctions["home"] = { _ ->
            AutomateAccessibilityService.instance?.pressHome() ?: false
        }
        
        builtinFunctions["recents"] = { _ ->
            AutomateAccessibilityService.instance?.pressRecents() ?: false
        }
        
        builtinFunctions["notifications"] = { _ ->
            AutomateAccessibilityService.instance?.openNotifications() ?: false
        }
        
        builtinFunctions["quickSettings"] = { _ ->
            AutomateAccessibilityService.instance?.openQuickSettings() ?: false
        }
        
        // ==================== 应用操作 ====================
        builtinFunctions["launch"] = { args ->
            val pkg = args.firstOrNull()?.toString() ?: ""
            AppUtils.launch(context, pkg)
        }
        
        builtinFunctions["launchApp"] = { args ->
            val name = args.firstOrNull()?.toString() ?: ""
            AppUtils.launchByName(context, name)
        }
        
        builtinFunctions["openUrl"] = { args ->
            val url = args.firstOrNull()?.toString() ?: ""
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "openUrl failed", e)
                false
            }
        }
        
        builtinFunctions["currentPackage"] = { _ ->
            AutomateAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
        }
        
        // ==================== 文本输入 ====================
        builtinFunctions["setText"] = { args ->
            val target = args.firstOrNull()
            val text = args.getOrNull(1)?.toString() ?: args.firstOrNull()?.toString() ?: ""
            
            when (target) {
                is UiSelector -> target.setText(text)
                is String -> {
                    // 直接输入文本到当前焦点
                    val service = AutomateAccessibilityService.instance
                    if (service != null) {
                        val root = service.rootInActiveWindow
                        val focused = root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                        if (focused != null) {
                            val arguments = android.os.Bundle()
                            arguments.putCharSequence(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                target
                            )
                            focused.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                                arguments
                            )
                        }
                    }
                    true
                }
                else -> false
            }
        }
        
        builtinFunctions["input"] = { args ->
            val text = args.firstOrNull()?.toString() ?: ""
            ShellUtils.inputText(text)
        }
        
        // ==================== 等待 ====================
        builtinFunctions["waitFor"] = { args ->
            val target = args.firstOrNull()
            val timeout = (args.getOrNull(1) as? Number)?.toLong() ?: 10000L
            when (target) {
                is UiSelector -> target.waitFor(timeout)
                is String -> UiSelector().text(target).waitFor(timeout)
                else -> null
            }
        }
        
        builtinFunctions["waitForText"] = { args ->
            val text = args.firstOrNull()?.toString() ?: ""
            val timeout = (args.getOrNull(1) as? Number)?.toLong() ?: 10000L
            UiSelector().textContains(text).waitFor(timeout)
        }
        
        builtinFunctions["exists"] = { args ->
            val target = args.firstOrNull()
            when (target) {
                is UiSelector -> target.exists()
                is String -> UiSelector().text(target).exists()
                else -> false
            }
        }
        
        // ==================== 查找 ====================
        builtinFunctions["findOne"] = { args ->
            val target = args.firstOrNull()
            when (target) {
                is UiSelector -> target.findOne()
                is String -> UiSelector().text(target).findOne()
                else -> null
            }
        }
        
        builtinFunctions["findAll"] = { args ->
            val target = args.firstOrNull()
            when (target) {
                is UiSelector -> target.findAll()
                is String -> UiSelector().text(target).findAll()
                else -> emptyList()
            }
        }
        
        // ==================== Shell ====================
        builtinFunctions["shell"] = { args ->
            val cmd = args.firstOrNull()?.toString() ?: ""
            val root = args.getOrNull(1) as? Boolean ?: false
            ShellUtils.exec(cmd, root)
        }
        
        // ==================== 循环控制 ====================
        builtinFunctions["range"] = { args ->
            val start = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            val end = (args.getOrNull(1) as? Number)?.toInt() ?: start
            val step = (args.getOrNull(2) as? Number)?.toInt() ?: 1
            (start until end step step).toList()
        }
    }
    
    /**
     * 执行代码
     */
    fun execute(code: String): Any? {
        stopped = false
        val statements = parseStatements(code)
        var result: Any? = null
        
        for (statement in statements) {
            if (stopped) break
            result = executeStatement(statement)
        }
        
        return result
    }
    
    private fun parseStatements(code: String): List<String> {
        // 按分号和换行分割，保留多行
        val lines = code.split("\n")
        val statements = mutableListOf<String>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue
            
            // 按分号分割
            val parts = trimmed.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            statements.addAll(parts)
        }
        
        return statements
    }
    
    private fun executeStatement(statement: String): Any? {
        val cleanStatement = statement.replace(Regex("//.*$"), "").trim()
        if (cleanStatement.isEmpty()) return null
        
        // for 循环: for (var i = 0; i < 5; i++) { ... }
        val forPattern = Pattern.compile("^for\\s*\\((.+)\\)\\s*\\{(.*)\\}$", Pattern.DOTALL)
        val forMatcher = forPattern.matcher(cleanStatement)
        if (forMatcher.matches()) {
            return executeForLoop(forMatcher.group(1)!!, forMatcher.group(2)!!)
        }
        
        // 变量声明: var/let/const x = value
        val varPattern = Pattern.compile("^(var|let|const)\\s+(\\w+)\\s*=\\s*(.+)$")
        val varMatcher = varPattern.matcher(cleanStatement)
        if (varMatcher.matches()) {
            val name = varMatcher.group(2)!!
            val valueExpr = varMatcher.group(3)!!
            val value = evaluateExpression(valueExpr)
            globals[name] = value
            return value
        }
        
        // 赋值: x = value
        val assignPattern = Pattern.compile("^(\\w+)\\s*=\\s*(.+)$")
        val assignMatcher = assignPattern.matcher(cleanStatement)
        if (assignMatcher.matches()) {
            val name = assignMatcher.group(1)!!
            if (!listOf("var", "let", "const", "for", "if", "while").contains(name)) {
                val valueExpr = assignMatcher.group(2)!!
                val value = evaluateExpression(valueExpr)
                globals[name] = value
                return value
            }
        }
        
        // 其他情况当作表达式
        return evaluateExpression(cleanStatement)
    }
    
    private fun executeForLoop(condition: String, body: String): Any? {
        // 简单的 for 循环: var i = 0; i < 5; i++
        val parts = condition.split(";").map { it.trim() }
        if (parts.size != 3) return null
        
        // 初始化
        executeStatement(parts[0])
        
        // 循环
        var result: Any? = null
        var iterations = 0
        val maxIterations = 1000 // 防止无限循环
        
        while (iterations < maxIterations && !stopped) {
            // 检查条件
            val condResult = evaluateCondition(parts[1])
            if (condResult != true) break
            
            // 执行循环体
            val bodyStatements = parseStatements(body)
            for (stmt in bodyStatements) {
                if (stopped) break
                result = executeStatement(stmt)
            }
            
            // 递增
            executeStatement(parts[2])
            iterations++
        }
        
        return result
    }
    
    private fun evaluateCondition(condition: String): Boolean {
        val trimmed = condition.trim()
        
        // i < 5
        val ltPattern = Pattern.compile("(\\w+)\\s*<\\s*(\\w+)")
        val ltMatcher = ltPattern.matcher(trimmed)
        if (ltMatcher.matches()) {
            val left = getNumericValue(ltMatcher.group(1)!!)
            val right = getNumericValue(ltMatcher.group(2)!!)
            return left < right
        }
        
        // i <= 5
        val lePattern = Pattern.compile("(\\w+)\\s*<=\\s*(\\w+)")
        val leMatcher = lePattern.matcher(trimmed)
        if (leMatcher.matches()) {
            val left = getNumericValue(leMatcher.group(1)!!)
            val right = getNumericValue(leMatcher.group(2)!!)
            return left <= right
        }
        
        return false
    }
    
    private fun getNumericValue(expr: String): Double {
        expr.toDoubleOrNull()?.let { return it }
        (globals[expr] as? Number)?.toDouble()?.let { return it }
        return 0.0
    }
    
    private fun evaluateExpression(expr: String): Any? {
        val trimmed = expr.trim()
        
        // i++ / i--
        if (trimmed.endsWith("++")) {
            val varName = trimmed.dropLast(2)
            val current = (globals[varName] as? Number)?.toInt() ?: 0
            globals[varName] = current + 1
            return current
        }
        if (trimmed.endsWith("--")) {
            val varName = trimmed.dropLast(2)
            val current = (globals[varName] as? Number)?.toInt() ?: 0
            globals[varName] = current - 1
            return current
        }
        
        // 数字
        trimmed.toDoubleOrNull()?.let { return it }
        trimmed.toIntOrNull()?.let { return it }
        
        // 字符串
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        
        // 模板字符串
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            var result = trimmed.substring(1, trimmed.length - 1)
            // 替换 ${var} 变量
            val varPattern = Pattern.compile("\\$\\{(\\w+)\\}")
            val matcher = varPattern.matcher(result)
            val sb = StringBuffer()
            while (matcher.find()) {
                val varName = matcher.group(1)!!
                val value = globals[varName]?.toString() ?: ""
                matcher.appendReplacement(sb, value)
            }
            matcher.appendTail(sb)
            return sb.toString()
        }
        
        // 布尔值
        if (trimmed == "true") return true
        if (trimmed == "false") return false
        if (trimmed == "null" || trimmed == "undefined") return null
        
        // 数组索引: arr[0]
        val indexPattern = Pattern.compile("^(\\w+)\\[(\\d+)\\]$")
        val indexMatcher = indexPattern.matcher(trimmed)
        if (indexMatcher.matches()) {
            val arrName = indexMatcher.group(1)!!
            val index = indexMatcher.group(2)!!.toInt()
            val arr = globals[arrName]
            return when (arr) {
                is List<*> -> arr.getOrNull(index)
                is Array<*> -> arr.getOrNull(index)
                else -> null
            }
        }
        
        // 函数调用: funcName(args)
        val funcPattern = Pattern.compile("^([\\w.]+)\\s*\\((.*)\\)$")
        val funcMatcher = funcPattern.matcher(trimmed)
        if (funcMatcher.matches()) {
            val funcName = funcMatcher.group(1)!!
            val argsStr = funcMatcher.group(2)!!
            val args = parseArguments(argsStr)
            return callFunction(funcName, args)
        }
        
        // 方法链: selector.method()
        if (trimmed.contains(".") && trimmed.contains("(")) {
            return evaluateChain(trimmed)
        }
        
        // 变量引用
        if (globals.containsKey(trimmed)) {
            return globals[trimmed]
        }
        
        return null
    }
    
    private fun parseArguments(argsStr: String): List<Any?> {
        if (argsStr.isBlank()) return emptyList()
        
        val args = mutableListOf<Any?>()
        var current = StringBuilder()
        var depth = 0
        var inString = false
        var stringChar = ' '
        
        for (c in argsStr) {
            when {
                !inString && (c == '"' || c == '\'' || c == '`') -> {
                    inString = true
                    stringChar = c
                    current.append(c)
                }
                inString && c == stringChar -> {
                    inString = false
                    current.append(c)
                }
                !inString && c == '(' -> {
                    depth++
                    current.append(c)
                }
                !inString && c == ')' -> {
                    depth--
                    current.append(c)
                }
                !inString && c == ',' && depth == 0 -> {
                    args.add(evaluateExpression(current.toString()))
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
        }
        
        if (current.isNotEmpty()) {
            args.add(evaluateExpression(current.toString()))
        }
        
        return args
    }
    
    private fun callFunction(name: String, args: List<Any?>): Any? {
        // 查找内置函数
        builtinFunctions[name]?.let { return it(args) }
        
        // UiSelector 链式调用
        val selector = args.firstOrNull() as? UiSelector
        if (selector != null) {
            return when (name) {
                "click" -> selector.click()
                "longClick" -> selector.longClick()
                "findOne" -> selector.findOne()
                "findAll" -> selector.findAll()
                "exists" -> selector.exists()
                "waitFor" -> {
                    val timeout = (args.getOrNull(1) as? Number)?.toLong() ?: 10000L
                    selector.waitFor(timeout)
                }
                "setText" -> {
                    val text = args.getOrNull(1)?.toString() ?: ""
                    selector.setText(text)
                }
                else -> null
            }
        }
        
        // UiObject 操作
        val uiObject = args.firstOrNull() as? UiObject
        if (uiObject != null) {
            return when (name) {
                "click" -> uiObject.click()
                "longClick" -> uiObject.longClick()
                else -> null
            }
        }
        
        Log.w(TAG, "Unknown function: $name")
        return null
    }
    
    private fun evaluateChain(expr: String): Any? {
        val parts = mutableListOf<Pair<String, List<Any?>>>()
        
        var remaining = expr
        while (remaining.isNotEmpty()) {
            val funcPattern = Pattern.compile("^([\\w]+)\\s*\\(([^)]*)\\)")
            val funcMatcher = funcPattern.matcher(remaining)
            
            if (funcMatcher.find()) {
                val funcName = funcMatcher.group(1)!!
                val argsStr = funcMatcher.group(2)!!
                val args = parseArguments(argsStr)
                parts.add(Pair(funcName, args))
                
                remaining = remaining.substring(funcMatcher.end())
                if (remaining.startsWith(".")) {
                    remaining = remaining.substring(1)
                }
            } else {
                break
            }
        }
        
        if (parts.isEmpty()) return null
        
        var result: Any? = callFunction(parts[0].first, parts[0].second)
        
        for (i in 1 until parts.size) {
            if (stopped) break
            val (method, args) = parts[i]
            result = when (result) {
                is UiSelector -> {
                    when (method) {
                        "text" -> result.text(args.firstOrNull()?.toString() ?: "")
                        "textContains" -> result.textContains(args.firstOrNull()?.toString() ?: "")
                        "id" -> result.id(args.firstOrNull()?.toString() ?: "")
                        "idContains" -> result.idContains(args.firstOrNull()?.toString() ?: "")
                        "className" -> result.className(args.firstOrNull()?.toString() ?: "")
                        "desc" -> result.desc(args.firstOrNull()?.toString() ?: "")
                        "descContains" -> result.descContains(args.firstOrNull()?.toString() ?: "")
                        "clickable" -> result.clickable(args.firstOrNull() as? Boolean ?: true)
                        "scrollable" -> result.scrollable(args.firstOrNull() as? Boolean ?: true)
                        "editable" -> result.editable(args.firstOrNull() as? Boolean ?: true)
                        "click" -> result.click()
                        "longClick" -> result.longClick()
                        "findOne" -> result.findOne()
                        "findAll" -> result.findAll()
                        "exists" -> result.exists()
                        "waitFor" -> result.waitFor((args.firstOrNull() as? Number)?.toLong() ?: 10000L)
                        "setText" -> result.setText(args.firstOrNull()?.toString() ?: "")
                        else -> result
                    }
                }
                is UiObject -> {
                    when (method) {
                        "click" -> result.click()
                        "longClick" -> result.longClick()
                        "text" -> result.text()
                        "id" -> result.id()
                        "bounds" -> result.bounds()
                        "parent" -> result.parent()
                        "children" -> result.children()
                        else -> result
                    }
                }
                is List<*> -> {
                    when (method) {
                        "length" -> result.size
                        "size" -> result.size
                        else -> result
                    }
                }
                else -> result
            }
        }
        
        return result
    }
}
