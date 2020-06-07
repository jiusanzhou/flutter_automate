package im.zoe.flutter_automate

import android.content.Context
import android.view.View
import io.flutter.plugin.common.*
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory



class FlutterLogView(messenger: BinaryMessenger, context: Context?, id: Int, params: Any?, containerView: View?): PlatformView, MethodChannel.MethodCallHandler {

    init {

    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    override fun getView(): View {
        TODO("Not yet implemented")
    }

    override fun onFlutterViewAttached(flutterView: View) {

    }

    override fun onFlutterViewDetached() {

    }

    override fun onInputConnectionLocked() {

    }
}

class FlutterLogViewFactory(messager: BinaryMessenger, containerView: View): PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    private var messager: BinaryMessenger
    private var containerView: View

    init {
        this.messager = messager
        this.containerView = containerView
    }


    override fun create(context: Context?, viewId: Int, args: Any?): PlatformView {
        return FlutterLogView(messager, context, viewId, args, containerView)
    }
}