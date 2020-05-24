package im.zoe.flutter_automate

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.annotation.NonNull;
import com.stardust.app.GlobalAppContext
import im.zoe.flutter_automate.automate.AutoMate

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.*
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.lang.Exception

public class FlutterAutomatePlugin:
        FlutterPlugin,
        MethodCallHandler,
        EventChannel.StreamHandler,
        ActivityAware
{

  private lateinit var channel : MethodChannel

  // call handler

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.i(LOG_TAG, "===========> onAttachedToEngine")

    channel = MethodChannel(flutterPluginBinding.flutterEngine.dartExecutor, METHOD_CHANNEL_NAME)
    channel.setMethodCallHandler(this)

    application = flutterPluginBinding.applicationContext as Application

    try {
      // take automate instance
      automate = AutoMate.initInstance(application)
      // set application
      GlobalAppContext.set(application)
    } catch (e: Throwable) {
      Log.e(LOG_TAG, "===========> ${e.toString()}")
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  // method call implement

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {

    when (call.method) {
      "init" -> {
        minit(call, result)
        result.success(true)
        return
      }
      "execute" -> {
        // check accessibility service
        execute(call, result)
      }
      "stopAll" -> {
        stopAll(call, result)
      }
      "waitForAccessibilityServiceEnabled" -> {
        // waitForAccessibilityServiceEnabled(call, result)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  // stream handler implement

  override fun onListen(arguments: Any, events: EventChannel.EventSink) {

  }

  override fun onCancel(arguments: Any) {

  }

  // activity

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    val activity = binding.activity
    application = activity.application
    setup(application)
    Log.i(LOG_TAG, "======> onAttachedToActivity")
  }

  override fun onDetachedFromActivity() {
  }

  override fun onDetachedFromActivityForConfigChanges() {
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
  }

  private fun setup(app: Application) {

    Log.i(LOG_TAG, "AutoMate Plugin try to setup")

    // take automate instance
    automate = AutoMate.initInstance(app)

    // set application
    GlobalAppContext.set(app)

    Log.i(LOG_TAG, "AutoMate Plugin setup success")
  }

  // static

  companion object {

    private val LOG_TAG = "AutoMate"
    private val METHOD_CHANNEL_NAME = "im.zoe.labs/flutter_automate/method"
    private val EVENTS_CHANNEL_NAME = "im.zoe.labs/flutter_automate/events"

    @SuppressLint("StaticFieldLeak")
    lateinit var application: Application private set

    @SuppressLint("StaticFieldLeak")
    lateinit var automate: AutoMate private set

    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val instance = FlutterAutomatePlugin()

      val methodChannel = MethodChannel(registrar.messenger(), METHOD_CHANNEL_NAME)
      methodChannel.setMethodCallHandler(instance)

      val eventsChannel = EventChannel(registrar.messenger(), EVENTS_CHANNEL_NAME)
      eventsChannel.setStreamHandler(instance)

      application = registrar.activity().application

      // take automate instance
      automate = AutoMate.initInstance(application)

      // set application
      GlobalAppContext.set(application)

      Log.i(LOG_TAG, "AutoMate Plugin setup success")
    }
  }

  // ensure accessible

  private fun minit(call: MethodCall, result: Result) {
    try {
      automate.waitForAccessibilityServiceEnabled()
      result.success(true)
    }catch (e: Exception) {
      result.error("WAIT_ACCESSIBILITY", e.toString(), null)
    }
  }


  private fun execute(call: MethodCall, result: Result) {
    var name: String? = call.argument("name")
    var code: String? = call.argument("code")
    var workDir: String? = call.argument("workDir")

    try {
      val r = automate.execute(name = name, code = code, workDir = workDir)
      result.success(r.id)
    }catch (e: Exception) {
      Log.e(LOG_TAG, "execute $name error: $e")
      result.error("EXECUTE_ERROR", e.toString(), null)
    }
  }

  private fun stopAll(call: MethodCall, result: Result) {
    var count = automate.stopAll()
    result.success(count)
  }

  private fun waitForAccessibilityServiceEnabled(call: MethodCall, result: Result) {

    try {
      automate.waitForAccessibilityServiceEnabled()
      result.success(true)
    } catch (e: Exception) {
      result.error("AS_ERROR", e.toString(), null)
    }
  }
}
