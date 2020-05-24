package im.zoe.flutter_automate.automate

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.autojs.execution.ScriptExecution
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.api.AppUtils
import com.stardust.autojs.runtime.exception.ScriptException
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.autojs.script.JavaScriptSource
import com.stardust.autojs.script.ScriptSource
import com.stardust.autojs.script.StringScriptSource
import com.stardust.view.accessibility.AccessibilityService
import com.stardust.view.accessibility.AccessibilityServiceUtils
import java.lang.Exception


class AutoMate private constructor(application: Application) : com.stardust.autojs.AutoJs(application) {

    // 初始化配置来源

    private lateinit var globalListener: GlobalListener

    init {
        // 注册全局监听器
        scriptEngineService.registerGlobalScriptExecutionListener(GlobalListener())
    }

    override fun createAppUtils(context: Context): AppUtils {
        return AppUtils(context, context.packageName + ".fileprovider")
    }

    override fun ensureAccessibilityServiceEnabled() {

        var errMsg: String ? = mustAccessibilityServiceEnabled()
        if (errMsg != null) {
            throw ScriptException(errMsg)
            // if (!AccessibilityService.waitForEnabled(-1)) {
            //     throw ScriptInterruptedException()
            // }
        }
    }

    override fun waitForAccessibilityServiceEnabled() {

        var errMsg: String ? = mustAccessibilityServiceEnabled()

        if (errMsg != null) {
            if (!AccessibilityService.waitForEnabled(-1)) {
                throw ScriptInterruptedException()
            }
        }
    }

    override fun initScriptEngineManager() {
        super.initScriptEngineManager()

        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE) {
            val engine = JavaScriptEngine(application)
            engine.runtime = createRuntime()
            engine
        }
    }

    override fun createRuntime(): ScriptRuntime {
        val runtime =  super.createRuntime()

        return runtime
    }

    private fun mustAccessibilityServiceEnabled(): String? {
        var errMsg: String ? = null

        if (AccessibilityService.instance != null) return null

        if (AccessibilityServiceUtils.isAccessibilityServiceEnabled(application, AccessibilityService::class.java)) {
            errMsg = "无障碍服务已启动但未运行，请重新启动或重启手机"
        } else {
            // TODO: root 自动开启
            errMsg = "无障碍服务未启动"
        }

        if (errMsg != null) AccessibilityServiceTool.goToAccessibilitySetting()

        return errMsg
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: AutoMate private set

        @SuppressLint("StaticFieldLeak")
        lateinit var mWorkDir: String private set

        fun initInstance(application: Application): AutoMate {
            instance = AutoMate(application)

            mWorkDir = application.applicationContext.filesDir.path + "/automate"

            return instance
        }
    }

    // public method

    fun execute(name: String? = "main", code: String?, workDir: String?): ScriptExecution {

        val source: ScriptSource = StringScriptSource(name, code)
        val config: ExecutionConfig = ExecutionConfig(
                workingDirectory = if (workDir!=null) {
                    workDir
                } else {
                    mWorkDir
                }
        )

        try {
            return scriptEngineService.execute(source, config)
        } catch (e: Exception) {
            globalConsole.error(e)
            throw e
        }
    }

    fun stopAll(): Int {
        return scriptEngineService.stopAll()
    }
}