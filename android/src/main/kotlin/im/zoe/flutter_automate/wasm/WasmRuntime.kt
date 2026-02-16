package im.zoe.flutter_automate.wasm

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * WASM 运行时接口
 * 抽象层，支持不同的 WASM 运行时实现（Wasmer, WasmEdge, Wasmtime）
 */
interface WasmRuntime {
    
    /**
     * 运行时名称
     */
    val name: String
    
    /**
     * 初始化运行时
     */
    fun init(context: Context)
    
    /**
     * 销毁运行时
     */
    fun destroy()
    
    /**
     * 加载 WASM 模块
     */
    fun loadModule(wasmBytes: ByteArray): WasmModule
    
    /**
     * 从文件加载 WASM 模块
     */
    fun loadModule(file: File): WasmModule {
        return loadModule(file.readBytes())
    }
    
    /**
     * 从 assets 加载 WASM 模块
     */
    fun loadModuleFromAssets(context: Context, assetPath: String): WasmModule {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        return loadModule(bytes)
    }
}

/**
 * WASM 模块
 */
interface WasmModule {
    
    /**
     * 模块名称
     */
    val name: String
    
    /**
     * 创建实例
     */
    fun instantiate(imports: WasmImports = WasmImports()): WasmInstance
    
    /**
     * 获取导出函数列表
     */
    fun getExports(): List<WasmExport>
    
    /**
     * 释放模块
     */
    fun close()
}

/**
 * WASM 实例
 */
interface WasmInstance {
    
    /**
     * 调用导出函数
     */
    fun call(functionName: String, vararg args: Any?): Any?
    
    /**
     * 获取导出的内存
     */
    fun getMemory(name: String = "memory"): WasmMemory?
    
    /**
     * 获取导出的全局变量
     */
    fun getGlobal(name: String): Any?
    
    /**
     * 设置全局变量
     */
    fun setGlobal(name: String, value: Any?)
    
    /**
     * 释放实例
     */
    fun close()
}

/**
 * WASM 内存
 */
interface WasmMemory {
    
    /**
     * 内存大小（页数，每页 64KB）
     */
    val pages: Int
    
    /**
     * 内存大小（字节）
     */
    val size: Int get() = pages * 65536
    
    /**
     * 读取字节
     */
    fun readBytes(offset: Int, length: Int): ByteArray
    
    /**
     * 写入字节
     */
    fun writeBytes(offset: Int, data: ByteArray)
    
    /**
     * 读取字符串（UTF-8）
     */
    fun readString(offset: Int, length: Int): String {
        return String(readBytes(offset, length), Charsets.UTF_8)
    }
    
    /**
     * 读取以 null 结尾的字符串
     */
    fun readCString(offset: Int): String {
        val bytes = mutableListOf<Byte>()
        var i = offset
        while (true) {
            val b = readBytes(i, 1)[0]
            if (b == 0.toByte()) break
            bytes.add(b)
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
    
    /**
     * 写入字符串，返回写入的字节数
     */
    fun writeString(offset: Int, str: String): Int {
        val bytes = str.toByteArray(Charsets.UTF_8)
        writeBytes(offset, bytes)
        return bytes.size
    }
    
    /**
     * 读取 Int32
     */
    fun readInt32(offset: Int): Int {
        val bytes = readBytes(offset, 4)
        return ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
    }
    
    /**
     * 写入 Int32
     */
    fun writeInt32(offset: Int, value: Int) {
        val buffer = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(value)
        writeBytes(offset, buffer.array())
    }
    
    /**
     * 读取 Float64
     */
    fun readFloat64(offset: Int): Double {
        val bytes = readBytes(offset, 8)
        return ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).double
    }
    
    /**
     * 写入 Float64
     */
    fun writeFloat64(offset: Int, value: Double) {
        val buffer = ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putDouble(value)
        writeBytes(offset, buffer.array())
    }
    
    /**
     * 扩展内存
     */
    fun grow(deltaPages: Int): Int
}

/**
 * WASM 导入
 */
class WasmImports {
    
    private val functions = mutableMapOf<String, MutableMap<String, WasmHostFunction>>()
    
    /**
     * 注册宿主函数
     */
    fun addFunction(
        moduleName: String, 
        functionName: String, 
        function: WasmHostFunction
    ): WasmImports {
        functions.getOrPut(moduleName) { mutableMapOf() }[functionName] = function
        return this
    }
    
    /**
     * 简化注册
     */
    fun addFunction(
        moduleName: String, 
        functionName: String, 
        handler: (args: LongArray) -> Long
    ): WasmImports {
        return addFunction(moduleName, functionName, object : WasmHostFunction {
            override fun call(args: LongArray): Long = handler(args)
        })
    }
    
    /**
     * 获取所有导入
     */
    fun getAll(): Map<String, Map<String, WasmHostFunction>> = functions
    
    /**
     * 获取指定模块的导入
     */
    fun getModule(moduleName: String): Map<String, WasmHostFunction>? = functions[moduleName]
}

/**
 * WASM 宿主函数
 */
interface WasmHostFunction {
    /**
     * 调用函数
     * 参数和返回值都用 Long 表示（可以是 i32, i64, f32, f64）
     */
    fun call(args: LongArray): Long
}

/**
 * WASM 导出项
 */
data class WasmExport(
    val name: String,
    val kind: WasmExportKind,
    val paramTypes: List<WasmValueType> = emptyList(),
    val returnTypes: List<WasmValueType> = emptyList()
)

enum class WasmExportKind {
    FUNCTION,
    MEMORY,
    GLOBAL,
    TABLE
}

enum class WasmValueType {
    I32,
    I64,
    F32,
    F64,
    EXTERNREF,
    FUNCREF
}
