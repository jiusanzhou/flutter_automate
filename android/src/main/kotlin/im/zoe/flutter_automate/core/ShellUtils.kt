package im.zoe.flutter_automate.core

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Shell 命令执行工具
 */
object ShellUtils {
    
    private const val TAG = "ShellUtils"
    
    /**
     * 命令执行结果
     */
    data class Result(
        val code: Int,
        val output: String,
        val error: String
    ) {
        val success: Boolean get() = code == 0
        
        fun toMap(): Map<String, Any> = mapOf(
            "code" to code,
            "output" to output,
            "error" to error,
            "success" to success
        )
    }
    
    /**
     * 执行 Shell 命令
     * @param command 命令
     * @param root 是否使用 root 权限
     * @param timeout 超时时间（毫秒），0 表示不限制
     * @return 执行结果
     */
    fun exec(command: String, root: Boolean = false, timeout: Long = 30000): Result {
        return try {
            if (root) {
                execRoot(command, timeout)
            } else {
                execNormal(command, timeout)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exec failed: $command", e)
            Result(-1, "", e.message ?: "Unknown error")
        }
    }
    
    /**
     * 执行多条命令
     */
    fun execCommands(commands: List<String>, root: Boolean = false): Result {
        val combined = commands.joinToString("\n")
        return exec(combined, root)
    }
    
    /**
     * 普通权限执行命令
     */
    private fun execNormal(command: String, timeout: Long): Result {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        
        return readProcessOutput(process, timeout)
    }
    
    /**
     * Root 权限执行命令
     */
    private fun execRoot(command: String, timeout: Long): Result {
        val process = Runtime.getRuntime().exec("su")
        
        // 写入命令
        DataOutputStream(process.outputStream).use { os ->
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
        }
        
        return readProcessOutput(process, timeout)
    }
    
    /**
     * 读取进程输出
     */
    private fun readProcessOutput(process: Process, timeout: Long): Result {
        val output = StringBuilder()
        val error = StringBuilder()
        
        // 读取标准输出
        Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.appendLine(line)
                    }
                }
            } catch (e: Exception) {
                // 忽略
            }
        }.start()
        
        // 读取错误输出
        Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        error.appendLine(line)
                    }
                }
            } catch (e: Exception) {
                // 忽略
            }
        }.start()
        
        // 等待完成
        val completed = if (timeout > 0) {
            process.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
        } else {
            process.waitFor()
            true
        }
        
        if (!completed) {
            process.destroyForcibly()
            return Result(-1, output.toString().trim(), "Timeout")
        }
        
        // 等待输出读取完成
        Thread.sleep(50)
        
        return Result(
            process.exitValue(),
            output.toString().trim(),
            error.toString().trim()
        )
    }
    
    /**
     * 检查是否有 Root 权限
     */
    fun hasRootAccess(): Boolean {
        return try {
            val result = exec("id", root = true, timeout = 5000)
            result.success && result.output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查命令是否存在
     */
    fun commandExists(command: String): Boolean {
        val result = exec("which $command")
        return result.success && result.output.isNotEmpty()
    }
    
    // ==================== 常用命令封装 ====================
    
    /**
     * 获取属性值
     */
    fun getProp(key: String): String? {
        val result = exec("getprop $key")
        return if (result.success) result.output else null
    }
    
    /**
     * 设置属性值（需要 root）
     */
    fun setProp(key: String, value: String): Boolean {
        val result = exec("setprop $key $value", root = true)
        return result.success
    }
    
    /**
     * 输入文本（需要 root 或 ADB）
     */
    fun inputText(text: String): Boolean {
        // 转义特殊字符
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace(" ", "%s")
            .replace("'", "\\'")
        val result = exec("input text \"$escaped\"")
        return result.success
    }
    
    /**
     * 模拟按键
     */
    fun inputKeyEvent(keyCode: Int): Boolean {
        val result = exec("input keyevent $keyCode")
        return result.success
    }
    
    /**
     * 模拟点击
     */
    fun inputTap(x: Int, y: Int): Boolean {
        val result = exec("input tap $x $y")
        return result.success
    }
    
    /**
     * 模拟滑动
     */
    fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 300): Boolean {
        val result = exec("input swipe $x1 $y1 $x2 $y2 $duration")
        return result.success
    }
    
    /**
     * 截屏（需要权限）
     */
    fun screencap(path: String): Boolean {
        val result = exec("screencap -p $path")
        return result.success
    }
    
    /**
     * 获取当前 Activity
     */
    fun getCurrentActivity(): String? {
        val result = exec("dumpsys activity activities | grep mResumedActivity")
        if (!result.success) return null
        
        // 解析输出
        val regex = Regex("""(\S+/\S+)""")
        return regex.find(result.output)?.groupValues?.get(1)
    }
    
    /**
     * 获取当前包名
     */
    fun getCurrentPackage(): String? {
        val activity = getCurrentActivity() ?: return null
        return activity.split("/").firstOrNull()
    }
    
    /**
     * 启动 Activity
     */
    fun startActivity(component: String): Boolean {
        val result = exec("am start -n $component")
        return result.success
    }
    
    /**
     * 强制停止应用
     */
    fun forceStop(packageName: String): Boolean {
        val result = exec("am force-stop $packageName")
        return result.success
    }
    
    /**
     * 清除应用数据
     */
    fun clearAppData(packageName: String): Boolean {
        val result = exec("pm clear $packageName", root = true)
        return result.success
    }
    
    /**
     * 安装 APK
     */
    fun installApk(apkPath: String): Boolean {
        val result = exec("pm install -r $apkPath")
        return result.success
    }
    
    /**
     * 卸载应用
     */
    fun uninstallApp(packageName: String): Boolean {
        val result = exec("pm uninstall $packageName")
        return result.success
    }
    
    // ==================== 常用 KeyCode ====================
    
    object KeyCode {
        const val HOME = 3
        const val BACK = 4
        const val CALL = 5
        const val ENDCALL = 6
        const val DPAD_UP = 19
        const val DPAD_DOWN = 20
        const val DPAD_LEFT = 21
        const val DPAD_RIGHT = 22
        const val DPAD_CENTER = 23
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val POWER = 26
        const val CAMERA = 27
        const val MENU = 82
        const val SEARCH = 84
        const val ENTER = 66
        const val DEL = 67
        const val TAB = 61
        const val SPACE = 62
        const val APP_SWITCH = 187
        const val SCREENSHOT = 120
    }
}
