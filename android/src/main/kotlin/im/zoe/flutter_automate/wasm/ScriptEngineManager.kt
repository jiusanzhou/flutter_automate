package im.zoe.flutter_automate.wasm

import android.content.Context
import android.util.Log
import im.zoe.flutter_automate.core.*
import im.zoe.flutter_automate.quickjs.QuickJSEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 脚本引擎管理器
 * 优先使用 QuickJS JNI，回退到 SimpleJsInterpreter
 */
class ScriptEngineManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ScriptEngineManager"
        
        @Volatile
        private var instance: ScriptEngineManager? = null
        
        fun getInstance(context: Context): ScriptEngineManager {
            return instance ?: synchronized(this) {
                instance ?: ScriptEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // QuickJS 引擎 (高性能 JNI)
    private var quickJSEngine: QuickJSEngine? = null
    private var quickJSAvailable = false
    
    // WASM 运行时 (回退方案)
    private val wasmRuntime: WasmRuntime = ChicoryWasmRuntime()
    
    // 已加载的语言模块
    private val languageModules = ConcurrentHashMap<String, LanguageModule>()
    
    // 执行中的脚本
    private val executions = ConcurrentHashMap<Int, ScriptExecution>()
    private val executionIdGenerator = AtomicInteger(0)
    
    // 全局监听器
    private val globalListeners = CopyOnWriteArrayList<ScriptExecutionListener>()
    
    // ==================== 初始化 ====================
    
    init {
        // 优先尝试初始化 QuickJS JNI
        try {
            quickJSEngine = QuickJSEngine(context)
            if (quickJSEngine!!.init()) {
                quickJSAvailable = true
                Log.i(TAG, "ScriptEngineManager initialized with QuickJS JNI (high performance)")
            } else {
                quickJSEngine = null
                Log.w(TAG, "QuickJS JNI init failed, falling back to interpreter")
            }
        } catch (e: Exception) {
            Log.w(TAG, "QuickJS JNI not available: ${e.message}")
            quickJSEngine = null
        }
        
        // 初始化 WASM 运行时作为回退
        if (!quickJSAvailable) {
            wasmRuntime.init(context)
            Log.i(TAG, "ScriptEngineManager initialized with ${wasmRuntime.name} runtime (fallback)")
        }
    }
    
    /**
     * 加载语言模块
     */
    fun loadLanguage(language: String): LanguageModule {
        return languageModules.getOrPut(language.lowercase()) {
            val module = when (language.lowercase()) {
                "js", "javascript" -> loadJavaScriptModule()
                "python", "py" -> loadPythonModule()
                "lua" -> loadLuaModule()
                else -> throw IllegalArgumentException("Unsupported language: $language")
            }
            Log.i(TAG, "Loaded language module: $language")
            module
        }
    }
    
    private fun loadJavaScriptModule(): LanguageModule {
        // 如果 QuickJS JNI 可用，优先使用
        if (quickJSAvailable && quickJSEngine != null) {
            return QuickJSLanguageModule(quickJSEngine!!, context)
        }
        
        // 回退到 SimpleJsInterpreter
        Log.w(TAG, "Using SimpleJsInterpreter fallback")
        return JavaScriptLanguageModule(null, createHostFunctions(), context)
    }
    
    private fun loadPythonModule(): LanguageModule {
        // 加载 Python WASM (RustPython 或 Pyodide)
        val wasmModule = try {
            wasmRuntime.loadModuleFromAssets(context, "wasm/python.wasm")
        } catch (e: Exception) {
            Log.w(TAG, "Python WASM not found")
            null
        }
        
        return PythonLanguageModule(wasmModule, createHostFunctions())
    }
    
    private fun loadLuaModule(): LanguageModule {
        // 加载 Lua WASM
        val wasmModule = try {
            wasmRuntime.loadModuleFromAssets(context, "wasm/lua.wasm")
        } catch (e: Exception) {
            Log.w(TAG, "Lua WASM not found")
            null
        }
        
        return LuaLanguageModule(wasmModule, createHostFunctions())
    }
    
    /**
     * 创建宿主函数（自动化 API）
     */
    private fun createHostFunctions(): HostFunctions {
        return HostFunctions(context)
    }
    
    // ==================== 脚本执行 ====================
    
    /**
     * 执行代码
     */
    fun execute(
        code: String,
        language: String = "js",
        filename: String = "main"
    ): ScriptExecution {
        Log.i(TAG, "execute() called: language=$language, filename=$filename")
        
        val module = loadLanguage(language)
        Log.i(TAG, "Loaded language module: ${module.name}")
        
        val id = executionIdGenerator.incrementAndGet()
        val execution = ScriptExecution(
            id = id,
            filename = filename,
            language = language,
            code = code
        )
        
        executions[id] = execution
        
        // 通知监听器
        globalListeners.forEach { it.onStart(language, filename) }
        
        // 异步执行
        Thread {
            try {
                Log.i(TAG, "Starting script execution in thread")
                val result = module.execute(code, filename)
                Log.i(TAG, "Script execution completed: result=$result")
                execution.result = ScriptResult.Success(result)
                globalListeners.forEach { it.onSuccess(language, filename, result) }
            } catch (e: Throwable) {
                Log.e(TAG, "Script execution error", e)
                execution.result = ScriptResult.Error(e)
                globalListeners.forEach { it.onError(language, filename, e) }
            } finally {
                execution.isCompleted = true
                globalListeners.forEach { it.onStop(language, filename) }
            }
        }.start()
        
        return execution
    }
    
    /**
     * 执行文件
     */
    fun executeFile(file: File): ScriptExecution {
        val language = when (file.extension.lowercase()) {
            "js", "mjs" -> "js"
            "py" -> "python"
            "lua" -> "lua"
            "ts" -> "js" // TypeScript 需要先编译
            else -> "js"
        }
        
        val code = file.readText()
        return execute(code, language, file.name)
    }
    
    /**
     * 停止所有执行
     */
    fun stopAll(): Int {
        var count = 0
        executions.values.forEach { execution ->
            if (!execution.isCompleted) {
                execution.stop()
                count++
            }
        }
        return count
    }
    
    /**
     * 销毁
     */
    fun destroy() {
        stopAll()
        languageModules.values.forEach { it.close() }
        languageModules.clear()
        wasmRuntime.destroy()
        Log.i(TAG, "ScriptEngineManager destroyed")
    }
    
    // ==================== 监听器 ====================
    
    fun addListener(listener: ScriptExecutionListener) {
        globalListeners.add(listener)
    }
    
    fun removeListener(listener: ScriptExecutionListener) {
        globalListeners.remove(listener)
    }
}

/**
 * 语言模块接口
 */
interface LanguageModule {
    val name: String
    val extensions: List<String>
    
    fun execute(code: String, filename: String = "main"): Any?
    fun stop()
    fun close()
}

/**
 * JavaScript 语言模块
 */
class JavaScriptLanguageModule(
    private val wasmModule: WasmModule?,
    private val hostFunctions: HostFunctions,
    private val context: Context
) : LanguageModule {
    
    override val name = "JavaScript"
    override val extensions = listOf("js", "mjs")
    
    private var instance: WasmInstance? = null
    
    init {
        if (wasmModule != null) {
            val imports = createImports()
            instance = wasmModule.instantiate(imports)
        }
    }
    
    private fun createImports(): WasmImports {
        val imports = WasmImports()
        
        // 注册自动化 API
        hostFunctions.registerAll(imports)
        
        return imports
    }
    
    override fun execute(code: String, filename: String): Any? {
        // 回退到内置简单解释器
        return SimpleJsInterpreter(context).execute(code)
    }
    
    override fun stop() {
        // TODO: 中断执行
    }
    
    override fun close() {
        // No resources to release
    }
}

/**
 * QuickJS JNI 高性能语言模块
 */
class QuickJSLanguageModule(
    private val engine: im.zoe.flutter_automate.quickjs.QuickJSEngine,
    private val context: Context
) : LanguageModule {
    
    override val name = "JavaScript (QuickJS JNI)"
    override val extensions = listOf("js", "mjs")
    
    override fun execute(code: String, filename: String): Any? {
        Log.i("QuickJSLanguageModule", "execute: code.length=${code.length}, filename=$filename")
        val result = engine.eval(code, filename)
        Log.i("QuickJSLanguageModule", "execute: result=$result")
        return result
    }
    
    override fun stop() {
        engine.interrupt()
    }
    
    override fun close() {
        engine.destroy()
    }
}

/**
 * Python 语言模块
 */
class PythonLanguageModule(
    private val wasmModule: WasmModule?,
    private val hostFunctions: HostFunctions
) : LanguageModule {
    
    override val name = "Python"
    override val extensions = listOf("py")
    
    override fun execute(code: String, filename: String): Any? {
        // TODO: 实现 Python WASM 执行
        throw UnsupportedOperationException("Python support coming soon")
    }
    
    override fun stop() {}
    override fun close() {
        wasmModule?.close()
    }
}

/**
 * Lua 语言模块
 */
class LuaLanguageModule(
    private val wasmModule: WasmModule?,
    private val hostFunctions: HostFunctions
) : LanguageModule {
    
    override val name = "Lua"
    override val extensions = listOf("lua")
    
    override fun execute(code: String, filename: String): Any? {
        // TODO: 实现 Lua WASM 执行
        throw UnsupportedOperationException("Lua support coming soon")
    }
    
    override fun stop() {}
    override fun close() {
        wasmModule?.close()
    }
}

/**
 * 脚本执行信息
 */
data class ScriptExecution(
    val id: Int,
    val filename: String,
    val language: String,
    val code: String,
    val startTime: Long = System.currentTimeMillis(),
    var isCompleted: Boolean = false,
    var result: ScriptResult? = null
) {
    @Volatile
    private var shouldStop = false
    
    fun stop() {
        shouldStop = true
    }
    
    fun shouldStop(): Boolean = shouldStop
    
    fun waitFor(timeoutMs: Long = -1): ScriptResult? {
        val startWait = System.currentTimeMillis()
        while (!isCompleted) {
            if (timeoutMs > 0 && System.currentTimeMillis() - startWait > timeoutMs) {
                break
            }
            Thread.sleep(50)
        }
        return result
    }
}

/**
 * 脚本执行结果
 */
sealed class ScriptResult {
    data class Success(val value: Any?) : ScriptResult()
    data class Error(val exception: Throwable) : ScriptResult()
}

/**
 * 脚本执行监听器
 */
interface ScriptExecutionListener {
    fun onStart(language: String, filename: String)
    fun onSuccess(language: String, filename: String, result: Any?)
    fun onError(language: String, filename: String, error: Throwable)
    fun onStop(language: String, filename: String)
}
