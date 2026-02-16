package im.zoe.flutter_automate.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * 无障碍服务工具类
 */
object AccessibilityServiceHelper {
    
    /**
     * 检查无障碍服务是否已启用
     */
    fun isServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val serviceName = "${context.packageName}/${serviceClass.name}"
        
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            if (TextUtils.isEmpty(enabledServices)) {
                return false
            }
            
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(serviceName, ignoreCase = true)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 检查 AutomateAccessibilityService 是否已启用
     */
    fun isEnabled(context: Context): Boolean {
        return isServiceEnabled(context, AutomateAccessibilityService::class.java)
    }
    
    /**
     * 打开无障碍设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 通过 Root 启用无障碍服务
     */
    fun enableByRoot(context: Context, serviceClass: Class<*>): Boolean {
        val serviceName = "${context.packageName}/${serviceClass.name}"
        
        return try {
            val cmd = """
                enabled=$(settings get secure enabled_accessibility_services)
                pkg=$serviceName
                if [[ ${'$'}enabled == *${'$'}pkg* ]]
                then
                    echo already_enabled
                else
                    enabled=${'$'}pkg:${'$'}enabled
                    settings put secure enabled_accessibility_services ${'$'}enabled
                    settings put secure accessibility_enabled 1
                fi
            """.trimIndent()
            
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 通过 Root 启用 AutomateAccessibilityServicn     */
    fun enableByRoot(context: Context): Boolean {
        return enableByRoot(context, AutomateAccessibilityService::class.java)
    }
    
    /**
     * 检查是否有 Root 权限
     */
    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 执行 Shell 命令
     */
    fun execShell(command: String, root: Boolean = false): ShellResult {
        return try {
            val process = if (root) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            ShellResult(exitCode, output, error)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }
}

/**
 * Shell 命令执行结果
 */
data class ShellResult(
    val code: Int,
    val output: String,
    val error: String
) {
    val isSuccess: Boolean get() = code == 0
}
