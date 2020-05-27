package im.zoe.flutter_automate_example

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import com.lzf.easyfloat.interfaces.OnInvokeView
import com.lzf.easyfloat.interfaces.OnPermissionResult
import com.lzf.easyfloat.permission.PermissionUtils
import im.zoe.flutter_automate_example.utils.StorageDirectoryMapper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.util.PathUtils


class MainActivity: FlutterActivity(), MethodChannel.MethodCallHandler {

    private val CHANNEL_METHOD = "im.zoe.labs/flutter_automate_example"

    private val FLOAT_TAG = "FLOATING_AUTOMATE"

    private lateinit var _channel: MethodChannel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(LOG_TAG, "=========> onCreate")

        // 初始化调用
        _channel = MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, CHANNEL_METHOD)
        // 设置回调
        _channel.setMethodCallHandler(this)

        // create the new floating view and attach

        // 检查浮动窗口权限
        if (!PermissionUtils.checkPermission(this)) {
            requestPermission()
        }

        // TODO: 检查辅助功能权限

        // 显示浮动窗口
        addFloating()
    }

    private lateinit var _floatView: View

    private fun addFloating() {
        EasyFloat.with(this)
                .setTag(FLOAT_TAG)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setSidePattern(SidePattern.RESULT_SIDE)
                .setGravity(Gravity.CENTER_VERTICAL)
                .setLayout(R.layout.float_view, OnInvokeView {
                    _floatView = it

                    // 给按钮添加绑定事件
                    it.findViewById<TextView>(R.id.ctrl_btn).setOnClickListener{
                        _channel.invokeMethod("onFloatClick", null)
                    }
                }).show()
    }

    private fun requestPermission() {
        PermissionUtils.requestPermission(this, object : OnPermissionResult {
            override fun permissionResult(isOpen: Boolean) {
                // 权限申请完成, 这里去添加窗口
                if (isOpen) {
                    Log.i(LOG_TAG, "=========> 窗口权限已获得")
                }
            }
        })
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "updateFloat" -> {
                updateFloat(call, result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun updateFloat(call: MethodCall, result: MethodChannel.Result) {
        // 显示或隐藏
        val display = call.argument<Boolean?>("display")
        if (display != null) {
            if (display) { EasyFloat.showAppFloat(FLOAT_TAG) } else {EasyFloat.hideAppFloat(FLOAT_TAG) }
        }

        // 更新文本
        val text = call.argument<String?>("text")
        if (text!=null) {
            _floatView.findViewById<TextView>(R.id.ctrl_btn)?.setText(text)
        }
    }

    companion object {
        val LOG_TAG = "AutoMate:MainActivity"
    }
}
