#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <android/log.h>

extern "C" {
#include "quickjs/quickjs.h"
#include "quickjs/quickjs-libc.h"
}

#define LOG_TAG "QuickJSJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==================== Global State ====================

static JSRuntime *g_runtime = nullptr;
static JSContext *g_ctx = nullptr;

// These will be set by Java
static JavaVM *g_jvm = nullptr;
static jobject g_callback = nullptr;

// Helper to get JNI env
static JNIEnv* getEnv() {
    JNIEnv *env;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        g_jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

// Call Java method from JS
static JSValue js_call_host(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1 || !g_callback) {
        return JS_UNDEFINED;
    }
    
    const char *func_name = JS_ToCString(ctx, argv[0]);
    if (!func_name) {
        return JS_UNDEFINED;
    }
    
    // Build args array
    JNIEnv *env = getEnv();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray jargs = env->NewObjectArray(argc - 1, stringClass, nullptr);
    
    for (int i = 1; i < argc; i++) {
        const char *arg = JS_ToCString(ctx, argv[i]);
        if (arg) {
            jstring jarg = env->NewStringUTF(arg);
            env->SetObjectArrayElement(jargs, i - 1, jarg);
            env->DeleteLocalRef(jarg);
            JS_FreeCString(ctx, arg);
        }
    }
    
    // Call Java callback
    jclass callbackClass = env->GetObjectClass(g_callback);
    jmethodID method = env->GetMethodID(callbackClass, "invoke", 
        "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;");
    
    jstring jfunc = env->NewStringUTF(func_name);
    jstring result = static_cast<jstring>(env->CallObjectMethod(g_callback, method, jfunc, jargs));
    
    JS_FreeCString(ctx, func_name);
    env->DeleteLocalRef(jfunc);
    env->DeleteLocalRef(jargs);
    
    if (result) {
        const char *result_str = env->GetStringUTFChars(result, nullptr);
        JSValue ret = JS_NewString(ctx, result_str);
        env->ReleaseStringUTFChars(result, result_str);
        env->DeleteLocalRef(result);
        return ret;
    }
    
    return JS_UNDEFINED;
}

// Console.log
static JSValue js_console_log(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    for (int i = 0; i < argc; i++) {
        const char *str = JS_ToCString(ctx, argv[i]);
        if (str) {
            LOGI("[JS] %s", str);
            JS_FreeCString(ctx, str);
        }
    }
    return JS_UNDEFINED;
}

// Sleep
static JSValue js_sleep(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    
    int64_t ms;
    if (JS_ToInt64(ctx, &ms, argv[0]) < 0) {
        return JS_UNDEFINED;
    }
    
    usleep(ms * 1000);
    return JS_UNDEFINED;
}

// ==================== Automation Host Functions ====================

static JSValue js_click(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[3];
    args[0] = JS_NewString(ctx, "click");
    if (argc >= 2) {
        args[1] = JS_DupValue(ctx, argv[0]); // x
        args[2] = JS_DupValue(ctx, argv[1]); // y
        return js_call_host(ctx, this_val, 3, args);
    }
    return js_call_host(ctx, this_val, 1, args);
}

static JSValue js_swipe(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[6];
    args[0] = JS_NewString(ctx, "swipe");
    for (int i = 0; i < argc && i < 5; i++) {
        args[i + 1] = JS_DupValue(ctx, argv[i]);
    }
    return js_call_host(ctx, this_val, argc + 1, args);
}

static JSValue js_swipe_up(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "swipeUp") };
    return js_call_host(ctx, this_val, 1, args);
}

static JSValue js_swipe_down(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "swipeDown") };
    return js_call_host(ctx, this_val, 1, args);
}

static JSValue js_back(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "back") };
    return js_call_host(ctx, this_val, 1, args);
}

static JSValue js_home(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "home") };
    return js_call_host(ctx, this_val, 1, args);
}

static JSValue js_open_url(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "openUrl");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

static JSValue js_launch(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "launch");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

// UI Selector factory
static JSValue js_text(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "selector_text");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

static JSValue js_text_contains(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "selector_textContains");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

static JSValue js_id(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "selector_id");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

static JSValue js_class_name(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "selector_className");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

static JSValue js_desc(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    JSValue args[2];
    args[0] = JS_NewString(ctx, "selector_desc");
    args[1] = JS_DupValue(ctx, argv[0]);
    return js_call_host(ctx, this_val, 2, args);
}

// ==================== Register Global Functions ====================

static void register_automation_api(JSContext *ctx) {
    JSValue global = JS_GetGlobalObject(ctx);
    
    // Console
    JSValue console = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, console, "log", JS_NewCFunction(ctx, js_console_log, "log", 1));
    JS_SetPropertyStr(ctx, global, "console", console);
    
    // Control flow
    JS_SetPropertyStr(ctx, global, "sleep", JS_NewCFunction(ctx, js_sleep, "sleep", 1));
    
    // Navigation
    JS_SetPropertyStr(ctx, global, "click", JS_NewCFunction(ctx, js_click, "click", 2));
    JS_SetPropertyStr(ctx, global, "swipe", JS_NewCFunction(ctx, js_swipe, "swipe", 5));
    JS_SetPropertyStr(ctx, global, "swipeUp", JS_NewCFunction(ctx, js_swipe_up, "swipeUp", 0));
    JS_SetPropertyStr(ctx, global, "swipeDown", JS_NewCFunction(ctx, js_swipe_down, "swipeDown", 0));
    JS_SetPropertyStr(ctx, global, "back", JS_NewCFunction(ctx, js_back, "back", 0));
    JS_SetPropertyStr(ctx, global, "home", JS_NewCFunction(ctx, js_home, "home", 0));
    
    // App operations
    JS_SetPropertyStr(ctx, global, "openUrl", JS_NewCFunction(ctx, js_open_url, "openUrl", 1));
    JS_SetPropertyStr(ctx, global, "launch", JS_NewCFunction(ctx, js_launch, "launch", 1));
    
    // UI Selectors
    JS_SetPropertyStr(ctx, global, "text", JS_NewCFunction(ctx, js_text, "text", 1));
    JS_SetPropertyStr(ctx, global, "textContains", JS_NewCFunction(ctx, js_text_contains, "textContains", 1));
    JS_SetPropertyStr(ctx, global, "id", JS_NewCFunction(ctx, js_id, "id", 1));
    JS_SetPropertyStr(ctx, global, "className", JS_NewCFunction(ctx, js_class_name, "className", 1));
    JS_SetPropertyStr(ctx, global, "desc", JS_NewCFunction(ctx, js_desc, "desc", 1));
    
    // Host function bridge
    JS_SetPropertyStr(ctx, global, "__callHost", JS_NewCFunction(ctx, js_call_host, "__callHost", 10));
    
    JS_FreeValue(ctx, global);
}

// ==================== JNI Methods ====================

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeInit(JNIEnv *env, jobject thiz, jobject callback) {
    LOGI("nativeInit: starting...");
    
    if (g_runtime) {
        LOGI("nativeInit: destroying existing runtime");
        JS_FreeContext(g_ctx);
        JS_FreeRuntime(g_runtime);
    }
    
    g_runtime = JS_NewRuntime();
    if (!g_runtime) {
        LOGE("nativeInit: Failed to create runtime");
        return;
    }
    
    JS_SetMemoryLimit(g_runtime, 256 * 1024 * 1024); // 256MB
    JS_SetMaxStackSize(g_runtime, 0);  // Use default stack (no limit)
    
    // JS_NewContext already includes all standard intrinsics
    g_ctx = JS_NewContext(g_runtime);
    if (!g_ctx) {
        LOGE("nativeInit: Failed to create context");
        JS_FreeRuntime(g_runtime);
        g_runtime = nullptr;
        return;
    }
    
    // Store callback
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
    }
    g_callback = env->NewGlobalRef(callback);
    
    // Register automation API
    register_automation_api(g_ctx);
    
    LOGI("QuickJS engine initialized successfully");
}

extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeDestroy(JNIEnv *env, jobject thiz) {
    if (g_ctx) {
        JS_FreeContext(g_ctx);
        g_ctx = nullptr;
    }
    if (g_runtime) {
        JS_FreeRuntime(g_runtime);
        g_runtime = nullptr;
    }
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
    }
    LOGI("QuickJS engine destroyed");
}

extern "C" JNIEXPORT jstring JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeEval(JNIEnv *env, jobject thiz, jstring code, jstring filename) {
    LOGI("nativeEval: g_runtime=%p, g_ctx=%p", g_runtime, g_ctx);
    
    if (!g_ctx) {
        LOGE("nativeEval: Engine not initialized");
        return env->NewStringUTF("Error: Engine not initialized");
    }
    
    const char *code_str = env->GetStringUTFChars(code, nullptr);
    const char *filename_str = env->GetStringUTFChars(filename, nullptr);
    
    LOGI("nativeEval: code='%s'", code_str);
    
    JSValue result = JS_Eval(g_ctx, code_str, strlen(code_str), filename_str, JS_EVAL_TYPE_GLOBAL);
    
    env->ReleaseStringUTFChars(code, code_str);
    env->ReleaseStringUTFChars(filename, filename_str);
    
    if (JS_IsException(result)) {
        JSValue exception = JS_GetException(g_ctx);
        
        // Print full exception details
        const char *err_msg = JS_ToCString(g_ctx, exception);
        LOGE("nativeEval: Exception message: %s", err_msg ? err_msg : "(null)");
        
        // Check if it's an Error object with stack/message
        JSValue msg_val = JS_GetPropertyStr(g_ctx, exception, "message");
        const char *msg_str = JS_ToCString(g_ctx, msg_val);
        if (msg_str) {
            LOGE("nativeEval: Error.message: %s", msg_str);
            JS_FreeCString(g_ctx, msg_str);
        }
        JS_FreeValue(g_ctx, msg_val);
        
        JSValue stack = JS_GetPropertyStr(g_ctx, exception, "stack");
        const char *stack_str = JS_ToCString(g_ctx, stack);
        if (stack_str) {
            LOGE("nativeEval: Stack: %s", stack_str);
        }
        
        jstring ret = env->NewStringUTF(err_msg ? err_msg : "Unknown error");
        if (err_msg) JS_FreeCString(g_ctx, err_msg);
        if (stack_str) JS_FreeCString(g_ctx, stack_str);
        JS_FreeValue(g_ctx, stack);
        JS_FreeValue(g_ctx, exception);
        JS_FreeValue(g_ctx, result);
        return ret;
    }
    
    const char *result_str = JS_ToCString(g_ctx, result);
    LOGI("nativeEval: Success, result=%s", result_str ? result_str : "null");
    jstring ret = env->NewStringUTF(result_str ? result_str : "undefined");
    if (result_str) JS_FreeCString(g_ctx, result_str);
    JS_FreeValue(g_ctx, result);
    
    return ret;
}

extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeInterrupt(JNIEnv *env, jobject thiz) {
    if (g_runtime) {
        JS_SetInterruptHandler(g_runtime, nullptr, nullptr);
        LOGI("QuickJS execution interrupted");
    }
}
