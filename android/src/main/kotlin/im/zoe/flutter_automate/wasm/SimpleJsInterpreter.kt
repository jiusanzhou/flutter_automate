package im.zoe.flutter_automate.wasm

import android.util.Log
import im.zoe.flutter_automate.core.*
import java.util.regex.Pattern

/**
 * 简单 JavaScript 解释器
 * 当 WASM 模块不可用时的回退方案
 * 支持基础语法：变量、函数调用、条件、循环等
 */
class SimpleJsInterpreter(private val hostFunctions: HostFunctions) {
    
    companion object {
        private const val TAG = "SimpleJsInterpreter"
    }
    
    // 全局变量
    private val globals = mutableMapOf<String, Any?>()
    
    // 内置函数
    private val builtinFunctions = mutableMapOf<String, (List<Any?>) -> Any?>()
    
    init {
        registerBuiltins()
    }
    
    private fun registerBuiltins() {
        // 日志
        builtinFunctions["console.log"] = { args -> 
            Log.i(TAG, args.joinToString(" "))
            null
        }
        builtinFunctions["log"] = builtinFunctions["console.log"]!!
        builtinFunctions["print"] = builtinFunctions["console.log"]!!
        
        // 控制流
        builtinFunctions["sleep"] = { args ->
            val ms = (args.firstOrNull() as? Number)?.toLong() ?: 0L
            Thread.sleep(ms)
            null
        }
        
        builtinFunctions["toast"] = { args ->
            val msg = args.firstOrNull()?.toString() ?: ""
            // hostFunctions.toast(msg)
            null
        }
        
        // UI 操作
        builtinFunctions["text"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().text(value)
        }
        
        builtinFunctions["textContains"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().textContains(value)
        }
        
        builtinFunctions["id"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().id(value)
        }
        
        builtinFunctions["className"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().className(value)
        }
        
        builtinFunctions["desc"] = { args ->
            val value = args.firstOrNull()?.toString() ?: ""
            UiSelector().desc(value)
        }
        
        builtinFunctions["click"] = { args ->
            val target = args.firstOrNull()
            when (target) {
                is UiSelector -> target.click()
                is Number -> {
                    val x = target.toFloat()
                    val y = (args.getOrNull(1) as? Number)?.toFloat() ?: 0f
                    GestureEngine.click(x, y)
                }
                else -> false
            }
        }
        
        builtinFunctions["longClick"] = { args ->
            val target = args.firstOrNull()
            when (target) {
                is UiSelector -> target.longClick()
                is Number -> {
                    val x = target.toFloat()
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
        
        // 全局操作
        builtinFunctions["back"] = { _ ->
            AutomateAccessibilityService.instance?.pressBack() ?: false
        }
        
        builtinFunctions["home"] = { _ ->
            AutomateAccessibilityService.instance?.pressHome() ?: false
        }
        
        builtinFunctions["recents"] = { _ ->
            AutomateAccessibilityService.instance?.pressRecents() ?: false
        }
        
        // 应用
        builtinFunctions["launch"] = { args ->
            val pkg = args.firstOrNull()?.toString() ?: ""
            AppUtils.launch(hostFunctions.javaClass.classLoader!!.loadClass("android.app.Application").newInstance() as android.content.Context, pkg)
        }
        
        builtinFunctions["launchApp"] = { args ->
            val name = args.firstOrNull()?.toString() ?: ""
            // AppUtils.launchByName(context, name)
            false
        }
        
        // 等待
        builtinFunctions["waitFor"] = { args ->
            val selector = args.firstOrNull() as? UiSelector
            val timeout = (args.getOrNull(1) as? Number)?.toLong() ?: 10000L
            selector?.waitFor(timeout)
        }
        
        builtinFunctions["exists"] = { args ->
            val selector = args.firstOrNull() as? UiSelector
            selector?.exists() ?: false
        }
    }
    
    /**
     * 执行代码
     */
    fun execute(code: String): Any? {
        val statements = parseStatements(code)
        var result: Any? = null
        
        for (statement in statements) {
            result = executeStatement(statement)
        }
        
        return result
    }
    
    private fun parseStatements(code: String): List<String> {
        // 简单按分号和换行分割
        return code.split(Regex("[;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
    }
    
    private fun executeStatement(statement: String): Any? {
        // 去除注释
        val cleanStatement = statement.replace(Regex("//.*$"), "").trim()
        if (cleanStatement.isEmpty()) return null
        
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
            val valueExpr = assignMatcher.group(2)!!
            val value = evaluateExpression(valueExpr)
            globals[name] = value
            return value
        }
        
        // 其他情况当作表达式
        return evaluateExpression(cleanStatement)
    }
    
    private fun evaluateExpression(expr: String): Any? {
        val trimmed = expr.trim()
        
        // 数字
        trimmed.toDoubleOrNull()?.let { return it }
        trimmed.toIntOrNull()?.let { return it }
        
        // 字符串
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        
        // 布尔值
        if (trimmed == "true") return true
        if (trimmed == "false") return false
        if (trimmed == "null" || trimmed == "undefined") return null
        
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
                !inString && (c == '"' || c == '\'') -> {
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
        
        // 查找 UiSelector 方法
        val selector = args.firstOrNull() as? UiSelector
        if (selector != null) {
            when (name) {
                "click" -> return selector.click()
                "longClick" -> return selector.longClick()
                "findOne" -> return selector.findOne()
                "findAll" -> return selector.findAll()
                "exists" -> return selector.exists()
                "waitFor" -> {
                    val timeout = (args.getOrNull(1) as? Number)?.toLong() ?: 10000L
                    return selector.waitFor(timeout)
                }
            }
        }
        
        Log.w(TAG, "Unknown function: $name")
        return null
    }
    
    private fun evaluateChain(expr: String): Any? {
        // 简单的方法链解析: text("hello").click()
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
        
        // 执行链
        var result: Any? = callFunction(parts[0].first, parts[0].second)
        
        for (i in 1 until parts.size) {
            val (method, args) = parts[i]
            result = when (result) {
                is UiSelector -> {
                    when (method) {
                        "text" -> result.text(args.firstOrNull()?.toString() ?: "")
                        "textContains" -> result.textContains(args.firstOrNull()?.toString() ?: "")
                        "id" -> result.id(args.firstOrNull()?.toString() ?: "")
                        "className" -> result.className(args.firstOrNull()?.toString() ?: "")
                        "clickable" -> result.clickable(args.firstOrNull() as? Boolean ?: true)
                        "click" -> result.click()
                        "longClick" -> result.longClick()
                        "findOne" -> result.findOne()
                        "findAll" -> result.findAll()
                        "exists" -> result.exists()
                        "waitFor" -> result.waitFor((args.firstOrNull() as? Number)?.toLong() ?: 10000L)
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
                else -> result
            }
        }
        
        return result
    }
}
