package im.zoe.flutter_automate.core

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 脚本日志管理器
 * 负责收集、存储和分发 JS console 输出
 */
object ScriptLogManager {
    
    private const val TAG = "ScriptLogManager"
    private const val MAX_MEMORY_LOGS = 1000
    private const val LOG_FILE_PREFIX = "script_log_"
    
    // 内存中的日志缓存
    private val logs = CopyOnWriteArrayList<LogEntry>()
    
    // 日志监听器
    private val listeners = CopyOnWriteArrayList<LogListener>()
    
    // 日志文件目录
    private var logDir: File? = null
    
    // 当前日志文件
    private var currentLogFile: File? = null
    private var currentDate: String = ""
    
    /**
     * 初始化日志管理器
     */
    fun init(context: Context) {
        logDir = File(context.filesDir, "logs")
        if (!logDir!!.exists()) {
            logDir!!.mkdirs()
        }
        Log.i(TAG, "ScriptLogManager initialized, logDir=${logDir?.absolutePath}")
    }
    
    /**
     * 添加日志
     */
    fun addLog(level: String, message: String, timestamp: Long = System.currentTimeMillis()) {
        val entry = LogEntry(level, message, timestamp)
        
        // 添加到内存缓存
        logs.add(entry)
        
        // 限制内存中的日志数量
        while (logs.size > MAX_MEMORY_LOGS) {
            logs.removeAt(0)
        }
        
        // 写入文件
        writeToFile(entry)
        
        // 通知监听器
        listeners.forEach { listener ->
            try {
                listener.onLog(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying log listener", e)
            }
        }
        
        Log.d(TAG, "[${entry.levelTag}] ${entry.message}")
    }
    
    /**
     * 获取所有内存中的日志
     */
    fun getLogs(): List<LogEntry> = logs.toList()
    
    /**
     * 获取最近 n 条日志
     */
    fun getRecentLogs(count: Int): List<LogEntry> {
        val size = logs.size
        return if (size <= count) {
            logs.toList()
        } else {
            logs.subList(size - count, size).toList()
        }
    }
    
    /**
     * 清空内存日志
     */
    fun clearMemoryLogs() {
        logs.clear()
    }
    
    /**
     * 添加监听器
     */
    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }
    
    /**
     * 移除监听器
     */
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
    
    /**
     * 获取今日日志文件路径
     */
    fun getTodayLogFile(): File? {
        ensureLogFile()
        return currentLogFile
    }
    
    /**
     * 获取所有日志文件
     */
    fun getLogFiles(): List<File> {
        return logDir?.listFiles()
            ?.filter { it.name.startsWith(LOG_FILE_PREFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }
    
    /**
     * 读取指定日志文件内容
     */
    fun readLogFile(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file", e)
            ""
        }
    }
    
    /**
     * 删除旧日志文件 (保留最近 n 天)
     */
    fun cleanOldLogs(keepDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L
        logDir?.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
                Log.i(TAG, "Deleted old log file: ${file.name}")
            }
        }
    }
    
    // ==================== 私有方法 ====================
    
    private fun ensureLogFile() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (today != currentDate || currentLogFile == null) {
            currentDate = today
            currentLogFile = File(logDir, "${LOG_FILE_PREFIX}$today.log")
            if (!currentLogFile!!.exists()) {
                currentLogFile!!.createNewFile()
            }
        }
    }
    
    private fun writeToFile(entry: LogEntry) {
        try {
            ensureLogFile()
            currentLogFile?.appendText("${entry.formattedLine}\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log to file", e)
        }
    }
}

/**
 * 日志条目
 */
data class LogEntry(
    val level: String,
    val message: String,
    val timestamp: Long
) {
    val levelTag: String get() = level.uppercase()
    
    val formattedTime: String get() {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestamp))
    }
    
    val formattedLine: String get() = "[$formattedTime] [$levelTag] $message"
}

/**
 * 日志监听器接口
 */
interface LogListener {
    fun onLog(entry: LogEntry)
}
