package im.zoe.flutter_automate.wasm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Chicory WASM 运行时实现
 * 
 * Chicory 是一个纯 Java/Kotlin 实现的 WASM 运行时，无需 native 库
 * https://github.com/nicholaswilson/chicory
 * 
 * 优点：
 * - 纯 JVM 实现，无需 NDK
 * - 跨平台
 * - 易于集成
 * 
 * 注：这里提供一个轻量级的自实现 WASM 解释器作为替代
 */
class ChicoryWasmRuntime : WasmRuntime {
    
    companion object {
        private const val TAG = "ChicoryWasmRuntime"
    }
    
    override val name: String = "Chicory"
    
    private var initialized = false
    
    override fun init(context: Context) {
        if (initialized) return
        initialized = true
        Log.i(TAG, "Chicory WASM runtime initialized")
    }
    
    override fun destroy() {
        initialized = false
        Log.i(TAG, "Chicory WASM runtime destroyed")
    }
    
    override fun loadModule(wasmBytes: ByteArray): WasmModule {
        return ChicoryWasmModule(wasmBytes)
    }
}

/**
 * Chicory WASM 模块实现
 */
class ChicoryWasmModule(private val wasmBytes: ByteArray) : WasmModule {
    
    override val name: String = "module"
    
    private val parser = WasmParser(wasmBytes)
    private val moduleInfo = parser.parse()
    
    override fun instantiate(imports: WasmImports): WasmInstance {
        return ChicoryWasmInstance(moduleInfo, imports)
    }
    
    override fun getExports(): List<WasmExport> {
        return moduleInfo.exports.map { export ->
            WasmExport(
                name = export.name,
                kind = when (export.kind.toInt()) {
                    0x00 -> WasmExportKind.FUNCTION
                    0x01 -> WasmExportKind.TABLE
                    0x02 -> WasmExportKind.MEMORY
                    0x03 -> WasmExportKind.GLOBAL
                    else -> WasmExportKind.FUNCTION
                }
            )
        }
    }
    
    override fun close() {
        // 释放资源
    }
}

/**
 * Chicory WASM 实例实现
 */
class ChicoryWasmInstance(
    private val moduleInfo: WasmModuleInfo,
    private val imports: WasmImports
) : WasmInstance {
    
    private val interpreter = WasmInterpreter(moduleInfo, imports)
    private val memory = ChicoryWasmMemory(moduleInfo.memoryPages)
    
    init {
        interpreter.memory = memory
        // 初始化数据段
        moduleInfo.dataSegments.forEach { segment ->
            memory.writeBytes(segment.offset, segment.data)
        }
    }
    
    override fun call(functionName: String, vararg args: Any?): Any? {
        val funcIndex = moduleInfo.exports
            .find { it.name == functionName && it.kind.toInt() == 0x00 }
            ?.index
            ?: throw IllegalArgumentException("Function not found: $functionName")
        
        val wasmArgs = args.map { arg ->
            when (arg) {
                is Int -> arg.toLong()
                is Long -> arg
                is Float -> java.lang.Float.floatToRawIntBits(arg).toLong()
                is Double -> java.lang.Double.doubleToRawLongBits(arg)
                null -> 0L
                else -> arg.toString().toLongOrNull() ?: 0L
            }
        }.toLongArray()
        
        return interpreter.call(funcIndex, wasmArgs)
    }
    
    override fun getMemory(name: String): WasmMemory? = memory
    
    override fun getGlobal(name: String): Any? {
        val globalIndex = moduleInfo.exports
            .find { it.name == name && it.kind.toInt() == 0x03 }
            ?.index
            ?: return null
        return interpreter.getGlobal(globalIndex)
    }
    
    override fun setGlobal(name: String, value: Any?) {
        val globalIndex = moduleInfo.exports
            .find { it.name == name && it.kind.toInt() == 0x03 }
            ?.index
            ?: return
        val longValue = when (value) {
            is Number -> value.toLong()
            else -> 0L
        }
        interpreter.setGlobal(globalIndex, longValue)
    }
    
    override fun close() {
        // 释放资源
    }
}

/**
 * Chicory WASM 内存实现
 */
class ChicoryWasmMemory(initialPages: Int) : WasmMemory {
    
    private var data = ByteArray(initialPages * 65536)
    override var pages: Int = initialPages
        private set
    
    override fun readBytes(offset: Int, length: Int): ByteArray {
        if (offset < 0 || offset + length > data.size) {
            throw IndexOutOfBoundsException("Memory access out of bounds: $offset + $length > ${data.size}")
        }
        return data.copyOfRange(offset, offset + length)
    }
    
    override fun writeBytes(offset: Int, data: ByteArray) {
        if (offset < 0 || offset + data.size > this.data.size) {
            throw IndexOutOfBoundsException("Memory access out of bounds")
        }
        System.arraycopy(data, 0, this.data, offset, data.size)
    }
    
    override fun grow(deltaPages: Int): Int {
        val oldPages = pages
        val newPages = pages + deltaPages
        if (newPages > 65536) { // 最大 4GB
            return -1
        }
        
        val newData = ByteArray(newPages * 65536)
        System.arraycopy(data, 0, newData, 0, data.size)
        data = newData
        pages = newPages
        
        return oldPages
    }
}

// ==================== WASM 解析器 ====================

/**
 * WASM 模块信息
 */
data class WasmModuleInfo(
    val types: List<WasmFuncType>,
    val imports: List<WasmImport>,
    val functions: List<Int>, // 类型索引
    val tables: List<WasmTable>,
    val memoryPages: Int,
    val globals: List<WasmGlobal>,
    val exports: List<WasmExportInfo>,
    val start: Int?,
    val elements: List<WasmElement>,
    val code: List<WasmCode>,
    val dataSegments: List<WasmDataSegment>
)

data class WasmFuncType(
    val params: List<Byte>,
    val results: List<Byte>
)

data class WasmImport(
    val module: String,
    val name: String,
    val kind: Byte,
    val typeIndex: Int
)

data class WasmTable(
    val elemType: Byte,
    val min: Int,
    val max: Int?
)

data class WasmGlobal(
    val type: Byte,
    val mutable: Boolean,
    val initExpr: ByteArray
)

data class WasmExportInfo(
    val name: String,
    val kind: Byte,
    val index: Int
)

data class WasmElement(
    val tableIndex: Int,
    val offset: Int,
    val funcIndices: List<Int>
)

data class WasmCode(
    val locals: List<Pair<Int, Byte>>,
    val body: ByteArray
)

data class WasmDataSegment(
    val memoryIndex: Int,
    val offset: Int,
    val data: ByteArray
)

/**
 * WASM 二进制解析器
 */
class WasmParser(private val bytes: ByteArray) {
    
    private var pos = 0
    
    fun parse(): WasmModuleInfo {
        // 检查魔数和版本
        val magic = readBytes(4)
        if (!magic.contentEquals(byteArrayOf(0x00, 0x61, 0x73, 0x6D))) {
            throw IllegalArgumentException("Invalid WASM magic number")
        }
        
        val version = readU32()
        if (version != 1) {
            throw IllegalArgumentException("Unsupported WASM version: $version")
        }
        
        var types = listOf<WasmFuncType>()
        var imports = listOf<WasmImport>()
        var functions = listOf<Int>()
        var tables = listOf<WasmTable>()
        var memoryPages = 1
        var globals = listOf<WasmGlobal>()
        var exports = listOf<WasmExportInfo>()
        var start: Int? = null
        var elements = listOf<WasmElement>()
        var code = listOf<WasmCode>()
        var dataSegments = listOf<WasmDataSegment>()
        
        // 解析各个 section
        while (pos < bytes.size) {
            val sectionId = readByte()
            val sectionSize = readU32()
            val sectionEnd = pos + sectionSize
            
            when (sectionId.toInt()) {
                1 -> types = parseTypeSection()
                2 -> imports = parseImportSection()
                3 -> functions = parseFunctionSection()
                4 -> tables = parseTableSection()
                5 -> memoryPages = parseMemorySection()
                6 -> globals = parseGlobalSection()
                7 -> exports = parseExportSection()
                8 -> start = parseStartSection()
                9 -> elements = parseElementSection()
                10 -> code = parseCodeSection()
                11 -> dataSegments = parseDataSection()
                else -> pos = sectionEnd // 跳过未知 section
            }
            
            pos = sectionEnd
        }
        
        return WasmModuleInfo(
            types, imports, functions, tables, memoryPages,
            globals, exports, start, elements, code, dataSegments
        )
    }
    
    private fun parseTypeSection(): List<WasmFuncType> {
        val count = readU32()
        return (0 until count).map {
            val form = readByte() // 0x60 for func
            val paramCount = readU32()
            val params = (0 until paramCount).map { readByte() }
            val resultCount = readU32()
            val results = (0 until resultCount).map { readByte() }
            WasmFuncType(params, results)
        }
    }
    
    private fun parseImportSection(): List<WasmImport> {
        val count = readU32()
        return (0 until count).map {
            val module = readString()
            val name = readString()
            val kind = readByte()
            val typeIndex = readU32()
            WasmImport(module, name, kind, typeIndex)
        }
    }
    
    private fun parseFunctionSection(): List<Int> {
        val count = readU32()
        return (0 until count).map { readU32() }
    }
    
    private fun parseTableSection(): List<WasmTable> {
        val count = readU32()
        return (0 until count).map {
            val elemType = readByte()
            val flags = readByte()
            val min = readU32()
            val max = if (flags.toInt() and 1 != 0) readU32() else null
            WasmTable(elemType, min, max)
        }
    }
    
    private fun parseMemorySection(): Int {
        val count = readU32()
        if (count == 0) return 1
        val flags = readByte()
        val min = readU32()
        if (flags.toInt() and 1 != 0) readU32() // max
        return min
    }
    
    private fun parseGlobalSection(): List<WasmGlobal> {
        val count = readU32()
        return (0 until count).map {
            val type = readByte()
            val mutable = readByte() == 0x01.toByte()
            val initExpr = readInitExpr()
            WasmGlobal(type, mutable, initExpr)
        }
    }
    
    private fun parseExportSection(): List<WasmExportInfo> {
        val count = readU32()
        return (0 until count).map {
            val name = readString()
            val kind = readByte()
            val index = readU32()
            WasmExportInfo(name, kind, index)
        }
    }
    
    private fun parseStartSection(): Int {
        return readU32()
    }
    
    private fun parseElementSection(): List<WasmElement> {
        val count = readU32()
        return (0 until count).map {
            val tableIndex = readU32()
            val offset = readConstI32()
            val funcCount = readU32()
            val funcIndices = (0 until funcCount).map { readU32() }
            WasmElement(tableIndex, offset, funcIndices)
        }
    }
    
    private fun parseCodeSection(): List<WasmCode> {
        val count = readU32()
        return (0 until count).map {
            val size = readU32()
            val codeEnd = pos + size
            val localCount = readU32()
            val locals = (0 until localCount).map {
                val n = readU32()
                val t = readByte()
                Pair(n, t)
            }
            val bodySize = codeEnd - pos
            val body = readBytes(bodySize)
            WasmCode(locals, body)
        }
    }
    
    private fun parseDataSection(): List<WasmDataSegment> {
        val count = readU32()
        return (0 until count).map {
            val memoryIndex = readU32()
            val offset = readConstI32()
            val dataSize = readU32()
            val data = readBytes(dataSize)
            WasmDataSegment(memoryIndex, offset, data)
        }
    }
    
    private fun readByte(): Byte = bytes[pos++]
    
    private fun readBytes(n: Int): ByteArray {
        val result = bytes.copyOfRange(pos, pos + n)
        pos += n
        return result
    }
    
    private fun readU32(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = readByte().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result
    }
    
    private fun readString(): String {
        val len = readU32()
        return String(readBytes(len), Charsets.UTF_8)
    }
    
    private fun readInitExpr(): ByteArray {
        val start = pos
        while (bytes[pos++] != 0x0B.toByte()) { }
        return bytes.copyOfRange(start, pos)
    }
    
    private fun readConstI32(): Int {
        val opcode = readByte()
        val value = when (opcode.toInt()) {
            0x41 -> readU32() // i32.const
            else -> 0
        }
        readByte() // 0x0B end
        return value
    }
}

// ==================== WASM 解释器 ====================

/**
 * WASM 解释器
 */
class WasmInterpreter(
    private val module: WasmModuleInfo,
    private val imports: WasmImports
) {
    
    lateinit var memory: WasmMemory
    
    private val globals = mutableListOf<Long>()
    private val stack = ArrayDeque<Long>()
    private val callStack = ArrayDeque<CallFrame>()
    
    init {
        // 初始化全局变量
        module.globals.forEach { global ->
            globals.add(evalInitExpr(global.initExpr))
        }
    }
    
    fun call(funcIndex: Int, args: LongArray): Long {
        val importCount = module.imports.count { it.kind == 0x00.toByte() }
        
        return if (funcIndex < importCount) {
            // 调用导入函数
            val imp = module.imports.filter { it.kind == 0x00.toByte() }[funcIndex]
            val hostFunc = imports.getModule(imp.module)?.get(imp.name)
                ?: throw IllegalStateException("Import not found: ${imp.module}.${imp.name}")
            hostFunc.call(args)
        } else {
            // 调用本地函数
            val localIndex = funcIndex - importCount
            val code = module.code[localIndex]
            val typeIndex = module.functions[localIndex]
            val funcType = module.types[typeIndex]
            
            // 设置局部变量
            val locals = mutableListOf<Long>()
            args.forEach { locals.add(it) }
            code.locals.forEach { (count, _) ->
                repeat(count) { locals.add(0L) }
            }
            
            // 执行代码
            executeCode(code.body, locals, funcType.results.size)
        }
    }
    
    fun getGlobal(index: Int): Long = globals.getOrElse(index) { 0L }
    
    fun setGlobal(index: Int, value: Long) {
        if (index < globals.size) {
            globals[index] = value
        }
    }
    
    private fun executeCode(body: ByteArray, locals: MutableList<Long>, resultCount: Int): Long {
        var pc = 0
        
        while (pc < body.size) {
            val opcode = body[pc++].toInt() and 0xFF
            
            when (opcode) {
                // Control
                0x00 -> { } // unreachable
                0x01 -> { } // nop
                0x0F -> break // return
                0x0B -> break // end
                
                // Parametric
                0x1A -> stack.removeLast() // drop
                0x1B -> { // select
                    val c = stack.removeLast()
                    val v2 = stack.removeLast()
                    val v1 = stack.removeLast()
                    stack.addLast(if (c != 0L) v1 else v2)
                }
                
                // Variable
                0x20 -> { // local.get
                    val idx = readLeb128(body, pc).also { pc = it.second }
                    stack.addLast(locals[idx.first])
                }
                0x21 -> { // local.set
                    val idx = readLeb128(body, pc).also { pc = it.second }
                    locals[idx.first] = stack.removeLast()
                }
                0x22 -> { // local.tee
                    val idx = readLeb128(body, pc).also { pc = it.second }
                    locals[idx.first] = stack.last()
                }
                0x23 -> { // global.get
                    val idx = readLeb128(body, pc).also { pc = it.second }
                    stack.addLast(getGlobal(idx.first))
                }
                0x24 -> { // global.set
                    val idx = readLeb128(body, pc).also { pc = it.second }
                    setGlobal(idx.first, stack.removeLast())
                }
                
                // Memory
                0x28 -> { // i32.load
                    readLeb128(body, pc).also { pc = it.second } // align
                    val offset = readLeb128(body, pc).also { pc = it.second }
                    val addr = stack.removeLast().toInt() + offset.first
                    stack.addLast(memory.readInt32(addr).toLong())
                }
                0x36 -> { // i32.store
                    readLeb128(body, pc).also { pc = it.second } // align
                    val offset = readLeb128(body, pc).also { pc = it.second }
                    val value = stack.removeLast().toInt()
                    val addr = stack.removeLast().toInt() + offset.first
                    memory.writeInt32(addr, value)
                }
                
                // Constants
                0x41 -> { // i32.const
                    val value = readLeb128Signed(body, pc).also { pc = it.second }
                    stack.addLast(value.first.toLong())
                }
                0x42 -> { // i64.const
                    val value = readLeb128Signed64(body, pc).also { pc = it.second }
                    stack.addLast(value.first)
                }
                
                // i32 operations
                0x45 -> stack.addLast(if (stack.removeLast() == 0L) 1L else 0L) // i32.eqz
                0x46 -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a == b) 1L else 0L) } // i32.eq
                0x47 -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a != b) 1L else 0L) } // i32.ne
                0x48 -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a < b) 1L else 0L) } // i32.lt_s
                0x6A -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast((a.toInt() + b.toInt()).toLong()) } // i32.add
                0x6B -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast((a.toInt() - b.toInt()).toLong()) } // i32.sub
                0x6C -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast((a.toInt() * b.toInt()).toLong()) } // i32.mul
                0x6D -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (b != 0) (a / b).toLong() else 0L) } // i32.div_s
                
                // Call
                0x10 -> { // call
                    val funcIdx = readLeb128(body, pc).also { pc = it.second }
                    val importCount = module.imports.count { it.kind == 0x00.toByte() }
                    val typeIndex = if (funcIdx.first < importCount) {
                        module.imports.filter { it.kind == 0x00.toByte() }[funcIdx.first].typeIndex
                    } else {
                        module.functions[funcIdx.first - importCount]
                    }
                    val funcType = module.types[typeIndex]
                    val args = LongArray(funcType.params.size) { stack.removeLast() }.reversedArray()
                    val result = call(funcIdx.first, args)
                    if (funcType.results.isNotEmpty()) {
                        stack.addLast(result)
                    }
                }
                
                else -> {
                    // 跳过未实现的指令
                    Log.w("WasmInterpreter", "Unimplemented opcode: 0x${opcode.toString(16)}")
                }
            }
        }
        
        return if (resultCount > 0 && stack.isNotEmpty()) stack.removeLast() else 0L
    }
    
    private fun evalInitExpr(expr: ByteArray): Long {
        var pc = 0
        val opcode = expr[pc++].toInt() and 0xFF
        return when (opcode) {
            0x41 -> readLeb128Signed(expr, pc).first.toLong() // i32.const
            0x42 -> readLeb128Signed64(expr, pc).first // i64.const
            else -> 0L
        }
    }
    
    private fun readLeb128(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = start
        while (true) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, pos)
    }
    
    private fun readLeb128Signed(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = start
        var b: Int
        do {
            b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            shift += 7
        } while (b and 0x80 != 0)
        
        if (shift < 32 && b and 0x40 != 0) {
            result = result or ((-1) shl shift)
        }
        return Pair(result, pos)
    }
    
    private fun readLeb128Signed64(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        var b: Int
        do {
            b = bytes[pos++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            shift += 7
        } while (b and 0x80 != 0)
        
        if (shift < 64 && b and 0x40 != 0) {
            result = result or ((-1L) shl shift)
        }
        return Pair(result, pos)
    }
}

data class CallFrame(
    val funcIndex: Int,
    val locals: MutableList<Long>,
    val returnPc: Int
)
