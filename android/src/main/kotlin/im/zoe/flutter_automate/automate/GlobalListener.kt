package im.zoe.flutter_automate.automate

import android.annotation.SuppressLint
import com.stardust.autojs.execution.ScriptExecution
import com.stardust.autojs.execution.ScriptExecutionListener

class GlobalListener : ScriptExecutionListener {

    override fun onStart(execution: ScriptExecution) {
        execution.engine.setTag(ENGINE_TAG_START_TIME, System.currentTimeMillis())
    }

    override fun onSuccess(execution: ScriptExecution, result: Any?) {
        onFinish(execution)
    }

    override fun onException(execution: ScriptExecution, e: Throwable) {
        onFinish(execution)
    }

    private fun onFinish(execution: ScriptExecution) {
        val millis = execution.engine.getTag(ENGINE_TAG_START_TIME) as Long? ?: return
        val seconds = (System.currentTimeMillis() - millis) / 1000.0

        // TODO: update global console

        // TODO: send event to
    }

    companion object {
        private const val ENGINE_TAG_START_TIME = "start_time"


        @SuppressLint("StaticFieldLeak")
        lateinit var instance: GlobalListener private set
    }
}