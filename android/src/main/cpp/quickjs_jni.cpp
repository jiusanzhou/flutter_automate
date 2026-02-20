#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <android/log.h>
#include <stdio.h>
#include <sys/stat.h>
#include <time.h>
#include <pthread.h>

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
static JavaVM *g_jvm = nullptr;
static jobject g_callback = nullptr;
static volatile int g_interrupt_flag = 0;

// 日志文件相关
static char g_log_dir[512] = {0};
static FILE *g_log_file = nullptr;
static char g_log_date[16] = {0};
static pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;

// 设置日志目录
static void set_log_dir(const char *dir) {
    strncpy(g_log_dir, dir, sizeof(g_log_dir) - 1);
    g_log_dir[sizeof(g_log_dir) - 1] = '\0';
    // 创建目录
    mkdir(g_log_dir, 0755);
    LOGI("Log directory set to: %s", g_log_dir);
}

// 获取当前日期字符串
static void get_current_date(char *buf, size_t len) {
    time_t now = time(NULL);
    struct tm *t = localtime(&now);
    strftime(buf, len, "%Y-%m-%d", t);
}

// 获取当前时间字符串
static void get_current_time(char *buf, size_t len) {
    time_t now = time(NULL);
    struct tm *t = localtime(&now);
    strftime(buf, len, "%H:%M:%S", t);
}

// 确保日志文件打开 (按日期轮转)
static void ensure_log_file() {
    if (g_log_dir[0] == '\0') return;
    
    char today[16];
    get_current_date(today, sizeof(today));
    
    // 日期变化，关闭旧文件
    if (strcmp(today, g_log_date) != 0) {
        if (g_log_file) {
            fclose(g_log_file);
            g_log_file = nullptr;
        }
        strncpy(g_log_date, today, sizeof(g_log_date));
    }
    
    // 打开新文件
    if (!g_log_file) {
        char path[640];
        snprintf(path, sizeof(path), "%s/script_log_%s.log", g_log_dir, today);
        g_log_file = fopen(path, "a");
        if (g_log_file) {
            LOGI("Log file opened: %s", path);
        } else {
            LOGE("Failed to open log file: %s", path);
        }
    }
}

// 写日志到文件
static void write_log(const char *level, const char *msg) {
    pthread_mutex_lock(&g_log_mutex);
    ensure_log_file();
    if (g_log_file) {
        char time_buf[16];
        get_current_time(time_buf, sizeof(time_buf));
        fprintf(g_log_file, "[%s] [%s] %s\n", time_buf, level, msg);
        fflush(g_log_file);
    }
    pthread_mutex_unlock(&g_log_mutex);
}

static int js_interrupt_handler(JSRuntime *rt, void *opaque) {
    return g_interrupt_flag;
}

static JNIEnv* getEnv() {
    JNIEnv *env;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        g_jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

// ==================== Host Function Bridge ====================

static JSValue js_call_host(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1 || !g_callback) return JS_UNDEFINED;
    
    const char *func_name = JS_ToCString(ctx, argv[0]);
    if (!func_name) return JS_UNDEFINED;
    
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

static bool call_host_bool(JSContext *ctx, const char *func, int argc, JSValueConst *argv) {
    JSValue args[10];
    args[0] = JS_NewString(ctx, func);
    for (int i = 0; i < argc && i < 9; i++) {
        args[i + 1] = JS_DupValue(ctx, argv[i]);
    }
    JSValue result = js_call_host(ctx, JS_UNDEFINED, argc + 1, args);
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    for (int i = 0; i <= argc && i < 10; i++) JS_FreeValue(ctx, args[i]);
    return ret;
}

// ==================== Console ====================

// 日志回调函数指针
static jobject g_log_callback = nullptr;
static jmethodID g_log_callback_method = nullptr;

// 设置日志回调
extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeSetLogCallback(
    JNIEnv *env, jobject thiz, jobject callback) {
    
    if (g_log_callback != nullptr) {
        env->DeleteGlobalRef(g_log_callback);
        g_log_callback = nullptr;
    }
    
    if (callback != nullptr) {
        g_log_callback = env->NewGlobalRef(callback);
        jclass cls = env->GetObjectClass(callback);
        g_log_callback_method = env->GetMethodID(cls, "onLog", "(Ljava/lang/String;Ljava/lang/String;)V");
    }
}

// 内部函数：收集所有参数为单个字符串
static char* collect_args_to_string(JSContext *ctx, int argc, JSValueConst *argv) {
    size_t total_len = 0;
    const char **strs = (const char**)malloc(sizeof(char*) * argc);
    
    for (int i = 0; i < argc; i++) {
        strs[i] = JS_ToCString(ctx, argv[i]);
        if (strs[i]) total_len += strlen(strs[i]) + 1; // +1 for space
    }
    
    char *result = (char*)malloc(total_len + 1);
    result[0] = '\0';
    
    for (int i = 0; i < argc; i++) {
        if (strs[i]) {
            if (i > 0) strcat(result, " ");
            strcat(result, strs[i]);
            JS_FreeCString(ctx, strs[i]);
        }
    }
    
    free(strs);
    return result;
}

// 通用 console 输出函数 - 直接写日志，不通过 Kotlin 回调
static JSValue js_console_output(JSContext *ctx, int argc, JSValueConst *argv, const char *level, int android_level) {
    char *msg = collect_args_to_string(ctx, argc, argv);
    
    // 1. 输出到 logcat
    __android_log_print(android_level, LOG_TAG, "[JS] %s", msg);
    
    // 2. 直接写入日志文件
    write_log(level, msg);
    
    // 3. 回调到 Kotlin/Flutter 层
    if (g_log_callback != nullptr && g_log_callback_method != nullptr) {
        JNIEnv *env;
        if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
            jstring jlevel = env->NewStringUTF(level);
            jstring jmsg = env->NewStringUTF(msg);
            env->CallVoidMethod(g_log_callback, g_log_callback_method, jlevel, jmsg);
            env->DeleteLocalRef(jlevel);
            env->DeleteLocalRef(jmsg);
        }
    }
    
    free(msg);
    return JS_UNDEFINED;
}

static JSValue js_console_log(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return js_console_output(ctx, argc, argv, "log", ANDROID_LOG_INFO);
}

static JSValue js_console_warn(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return js_console_output(ctx, argc, argv, "warn", ANDROID_LOG_WARN);
}

static JSValue js_console_error(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return js_console_output(ctx, argc, argv, "error", ANDROID_LOG_ERROR);
}

static JSValue js_console_info(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return js_console_output(ctx, argc, argv, "info", ANDROID_LOG_INFO);
}

static JSValue js_console_debug(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return js_console_output(ctx, argc, argv, "debug", ANDROID_LOG_DEBUG);
}

// ==================== Control Flow ====================

static JSValue js_sleep(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    int64_t ms;
    if (JS_ToInt64(ctx, &ms, argv[0]) < 0) return JS_UNDEFINED;
    usleep(ms * 1000);
    return JS_UNDEFINED;
}

static JSValue js_exit(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    g_interrupt_flag = 1;
    return JS_ThrowInternalError(ctx, "Script exited");
}

static JSValue js_toast(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    call_host_bool(ctx, "toast", argc, argv);
    return JS_UNDEFINED;
}

// ==================== Clipboard ====================

static JSValue js_setClip(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "setClip", argc, argv));
}

static JSValue js_getClip(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "getClip") };
    JSValue r = js_call_host(ctx, this_val, 1, args);
    JS_FreeValue(ctx, args[0]);
    return r;
}

// ==================== Gestures ====================

static JSValue js_click(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "click", argc, argv));
}

static JSValue js_longClick(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "longClick", argc, argv));
}

static JSValue js_press(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 3) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "press", argc, argv));
}

static JSValue js_swipe(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 4) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "swipe", argc, argv));
}

static JSValue js_swipeUp(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "swipeUp", 0, nullptr));
}

static JSValue js_swipeDown(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "swipeDown", 0, nullptr));
}

static JSValue js_swipeLeft(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "swipeLeft", 0, nullptr));
}

static JSValue js_swipeRight(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "swipeRight", 0, nullptr));
}

// ==================== Global Actions ====================

static JSValue js_back(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "back", 0, nullptr));
}

static JSValue js_home(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "home", 0, nullptr));
}

static JSValue js_recents(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "recents", 0, nullptr));
}

static JSValue js_notifications(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "notifications", 0, nullptr));
}

static JSValue js_quickSettings(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "quickSettings", 0, nullptr));
}

// ==================== UI Selector Implementation ====================

// Forward declarations
static JSValue js_selector_findOne(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_findAll(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_waitFor(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_exists(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_click(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_setText(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);

// Add condition to selector and return the same selector (chainable)
static JSValue selector_add_condition(JSContext *ctx, JSValueConst selector, const char *type, const char *value) {
    JSValue conditions = JS_GetPropertyStr(ctx, selector, "_conditions");
    JSValue lenVal = JS_GetPropertyStr(ctx, conditions, "length");
    int32_t len = 0;
    JS_ToInt32(ctx, &len, lenVal);
    JS_FreeValue(ctx, lenVal);
    
    JSValue cond = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, cond, "type", JS_NewString(ctx, type));
    JS_SetPropertyStr(ctx, cond, "value", JS_NewString(ctx, value));
    JS_SetPropertyUint32(ctx, conditions, len, cond);
    JS_FreeValue(ctx, conditions);
    
    return JS_DupValue(ctx, selector);
}

// Chain method template
#define SELECTOR_CHAIN_METHOD(name, condType) \
static JSValue js_selector_##name(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) { \
    if (argc < 1) return JS_DupValue(ctx, this_val); \
    const char *str = JS_ToCString(ctx, argv[0]); \
    JSValue ret = selector_add_condition(ctx, this_val, condType, str ? str : ""); \
    if (str) JS_FreeCString(ctx, str); \
    return ret; \
}

SELECTOR_CHAIN_METHOD(text_chain, "text")
SELECTOR_CHAIN_METHOD(textContains_chain, "textContains")
SELECTOR_CHAIN_METHOD(textStartsWith_chain, "textStartsWith")
SELECTOR_CHAIN_METHOD(textEndsWith_chain, "textEndsWith")
SELECTOR_CHAIN_METHOD(textMatches_chain, "textMatches")
SELECTOR_CHAIN_METHOD(desc_chain, "desc")
SELECTOR_CHAIN_METHOD(descContains_chain, "descContains")
SELECTOR_CHAIN_METHOD(descStartsWith_chain, "descStartsWith")
SELECTOR_CHAIN_METHOD(descEndsWith_chain, "descEndsWith")
SELECTOR_CHAIN_METHOD(descMatches_chain, "descMatches")
SELECTOR_CHAIN_METHOD(id_chain, "id")
SELECTOR_CHAIN_METHOD(idContains_chain, "idContains")
SELECTOR_CHAIN_METHOD(idStartsWith_chain, "idStartsWith")
SELECTOR_CHAIN_METHOD(idEndsWith_chain, "idEndsWith")
SELECTOR_CHAIN_METHOD(idMatches_chain, "idMatches")
SELECTOR_CHAIN_METHOD(className_chain, "className")
SELECTOR_CHAIN_METHOD(classNameContains_chain, "classNameContains")
SELECTOR_CHAIN_METHOD(classNameStartsWith_chain, "classNameStartsWith")
SELECTOR_CHAIN_METHOD(classNameEndsWith_chain, "classNameEndsWith")
SELECTOR_CHAIN_METHOD(classNameMatches_chain, "classNameMatches")
SELECTOR_CHAIN_METHOD(packageName_chain, "packageName")
SELECTOR_CHAIN_METHOD(packageNameContains_chain, "packageNameContains")
SELECTOR_CHAIN_METHOD(packageNameStartsWith_chain, "packageNameStartsWith")
SELECTOR_CHAIN_METHOD(packageNameEndsWith_chain, "packageNameEndsWith")

static JSValue js_selector_clickable_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "clickable", val ? "true" : "false");
}

static JSValue js_selector_scrollable_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "scrollable", val ? "true" : "false");
}

static JSValue js_selector_enabled_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "enabled", val ? "true" : "false");
}

static JSValue js_selector_checked_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "checked", val ? "true" : "false");
}

static JSValue js_selector_selected_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "selected", val ? "true" : "false");
}

static JSValue js_selector_focusable_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "focusable", val ? "true" : "false");
}

static JSValue js_selector_focused_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "focused", val ? "true" : "false");
}

static JSValue js_selector_longClickable_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "longClickable", val ? "true" : "false");
}

static JSValue js_selector_checkable_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "checkable", val ? "true" : "false");
}

static JSValue js_selector_editable_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "editable", val ? "true" : "false");
}

static JSValue js_selector_visibleToUser_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    return selector_add_condition(ctx, this_val, "visibleToUser", val ? "true" : "false");
}

// Depth selector
static JSValue js_selector_depth_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_DupValue(ctx, this_val);
    int32_t depth = 0;
    JS_ToInt32(ctx, &depth, argv[0]);
    char buf[16];
    snprintf(buf, 16, "%d", depth);
    return selector_add_condition(ctx, this_val, "depth", buf);
}

// drawingOrder selector
static JSValue js_selector_drawingOrder_chain(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_DupValue(ctx, this_val);
    int32_t order = 0;
    JS_ToInt32(ctx, &order, argv[0]);
    char buf[16];
    snprintf(buf, 16, "%d", order);
    return selector_add_condition(ctx, this_val, "drawingOrder", buf);
}

// Forward declarations for selector methods
static JSValue js_selector_findOnce(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_longClick(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_scrollForward(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);
static JSValue js_selector_scrollBackward(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv);

// Create selector object with all methods
static JSValue create_selector_object(JSContext *ctx) {
    JSValue obj = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, obj, "_conditions", JS_NewArray(ctx));
    
    // Text selectors
    JS_SetPropertyStr(ctx, obj, "text", JS_NewCFunction(ctx, js_selector_text_chain, "text", 1));
    JS_SetPropertyStr(ctx, obj, "textContains", JS_NewCFunction(ctx, js_selector_textContains_chain, "textContains", 1));
    JS_SetPropertyStr(ctx, obj, "textStartsWith", JS_NewCFunction(ctx, js_selector_textStartsWith_chain, "textStartsWith", 1));
    JS_SetPropertyStr(ctx, obj, "textEndsWith", JS_NewCFunction(ctx, js_selector_textEndsWith_chain, "textEndsWith", 1));
    JS_SetPropertyStr(ctx, obj, "textMatches", JS_NewCFunction(ctx, js_selector_textMatches_chain, "textMatches", 1));
    
    // Desc selectors
    JS_SetPropertyStr(ctx, obj, "desc", JS_NewCFunction(ctx, js_selector_desc_chain, "desc", 1));
    JS_SetPropertyStr(ctx, obj, "descContains", JS_NewCFunction(ctx, js_selector_descContains_chain, "descContains", 1));
    JS_SetPropertyStr(ctx, obj, "descStartsWith", JS_NewCFunction(ctx, js_selector_descStartsWith_chain, "descStartsWith", 1));
    JS_SetPropertyStr(ctx, obj, "descEndsWith", JS_NewCFunction(ctx, js_selector_descEndsWith_chain, "descEndsWith", 1));
    JS_SetPropertyStr(ctx, obj, "descMatches", JS_NewCFunction(ctx, js_selector_descMatches_chain, "descMatches", 1));
    
    // ID selectors
    JS_SetPropertyStr(ctx, obj, "id", JS_NewCFunction(ctx, js_selector_id_chain, "id", 1));
    JS_SetPropertyStr(ctx, obj, "idContains", JS_NewCFunction(ctx, js_selector_idContains_chain, "idContains", 1));
    JS_SetPropertyStr(ctx, obj, "idStartsWith", JS_NewCFunction(ctx, js_selector_idStartsWith_chain, "idStartsWith", 1));
    JS_SetPropertyStr(ctx, obj, "idEndsWith", JS_NewCFunction(ctx, js_selector_idEndsWith_chain, "idEndsWith", 1));
    JS_SetPropertyStr(ctx, obj, "idMatches", JS_NewCFunction(ctx, js_selector_idMatches_chain, "idMatches", 1));
    
    // ClassName selectors
    JS_SetPropertyStr(ctx, obj, "className", JS_NewCFunction(ctx, js_selector_className_chain, "className", 1));
    JS_SetPropertyStr(ctx, obj, "classNameContains", JS_NewCFunction(ctx, js_selector_classNameContains_chain, "classNameContains", 1));
    JS_SetPropertyStr(ctx, obj, "classNameStartsWith", JS_NewCFunction(ctx, js_selector_classNameStartsWith_chain, "classNameStartsWith", 1));
    JS_SetPropertyStr(ctx, obj, "classNameEndsWith", JS_NewCFunction(ctx, js_selector_classNameEndsWith_chain, "classNameEndsWith", 1));
    JS_SetPropertyStr(ctx, obj, "classNameMatches", JS_NewCFunction(ctx, js_selector_classNameMatches_chain, "classNameMatches", 1));
    
    // PackageName selectors
    JS_SetPropertyStr(ctx, obj, "packageName", JS_NewCFunction(ctx, js_selector_packageName_chain, "packageName", 1));
    JS_SetPropertyStr(ctx, obj, "packageNameContains", JS_NewCFunction(ctx, js_selector_packageNameContains_chain, "packageNameContains", 1));
    JS_SetPropertyStr(ctx, obj, "packageNameStartsWith", JS_NewCFunction(ctx, js_selector_packageNameStartsWith_chain, "packageNameStartsWith", 1));
    JS_SetPropertyStr(ctx, obj, "packageNameEndsWith", JS_NewCFunction(ctx, js_selector_packageNameEndsWith_chain, "packageNameEndsWith", 1));
    
    // Boolean property selectors
    JS_SetPropertyStr(ctx, obj, "clickable", JS_NewCFunction(ctx, js_selector_clickable_chain, "clickable", 0));
    JS_SetPropertyStr(ctx, obj, "scrollable", JS_NewCFunction(ctx, js_selector_scrollable_chain, "scrollable", 0));
    JS_SetPropertyStr(ctx, obj, "enabled", JS_NewCFunction(ctx, js_selector_enabled_chain, "enabled", 0));
    JS_SetPropertyStr(ctx, obj, "checked", JS_NewCFunction(ctx, js_selector_checked_chain, "checked", 0));
    JS_SetPropertyStr(ctx, obj, "selected", JS_NewCFunction(ctx, js_selector_selected_chain, "selected", 0));
    JS_SetPropertyStr(ctx, obj, "focusable", JS_NewCFunction(ctx, js_selector_focusable_chain, "focusable", 0));
    JS_SetPropertyStr(ctx, obj, "focused", JS_NewCFunction(ctx, js_selector_focused_chain, "focused", 0));
    JS_SetPropertyStr(ctx, obj, "longClickable", JS_NewCFunction(ctx, js_selector_longClickable_chain, "longClickable", 0));
    JS_SetPropertyStr(ctx, obj, "checkable", JS_NewCFunction(ctx, js_selector_checkable_chain, "checkable", 0));
    JS_SetPropertyStr(ctx, obj, "editable", JS_NewCFunction(ctx, js_selector_editable_chain, "editable", 0));
    JS_SetPropertyStr(ctx, obj, "visibleToUser", JS_NewCFunction(ctx, js_selector_visibleToUser_chain, "visibleToUser", 0));
    
    // Depth and drawingOrder selectors
    JS_SetPropertyStr(ctx, obj, "depth", JS_NewCFunction(ctx, js_selector_depth_chain, "depth", 1));
    JS_SetPropertyStr(ctx, obj, "drawingOrder", JS_NewCFunction(ctx, js_selector_drawingOrder_chain, "drawingOrder", 1));
    
    // Finder methods
    JS_SetPropertyStr(ctx, obj, "findOne", JS_NewCFunction(ctx, js_selector_findOne, "findOne", 0));
    JS_SetPropertyStr(ctx, obj, "findOnce", JS_NewCFunction(ctx, js_selector_findOnce, "findOnce", 1));
    JS_SetPropertyStr(ctx, obj, "findAll", JS_NewCFunction(ctx, js_selector_findAll, "findAll", 0));
    JS_SetPropertyStr(ctx, obj, "find", JS_NewCFunction(ctx, js_selector_findAll, "find", 0));
    JS_SetPropertyStr(ctx, obj, "waitFor", JS_NewCFunction(ctx, js_selector_waitFor, "waitFor", 1));
    JS_SetPropertyStr(ctx, obj, "exists", JS_NewCFunction(ctx, js_selector_exists, "exists", 0));
    JS_SetPropertyStr(ctx, obj, "click", JS_NewCFunction(ctx, js_selector_click, "click", 0));
    JS_SetPropertyStr(ctx, obj, "longClick", JS_NewCFunction(ctx, js_selector_longClick, "longClick", 0));
    JS_SetPropertyStr(ctx, obj, "setText", JS_NewCFunction(ctx, js_selector_setText, "setText", 1));
    JS_SetPropertyStr(ctx, obj, "scrollForward", JS_NewCFunction(ctx, js_selector_scrollForward, "scrollForward", 0));
    JS_SetPropertyStr(ctx, obj, "scrollBackward", JS_NewCFunction(ctx, js_selector_scrollBackward, "scrollBackward", 0));
    
    return obj;
}

// Serialize conditions to JSON
static char* serialize_conditions(JSContext *ctx, JSValueConst selector) {
    JSValue conditions = JS_GetPropertyStr(ctx, selector, "_conditions");
    JSValue json = JS_JSONStringify(ctx, conditions, JS_UNDEFINED, JS_UNDEFINED);
    const char *str = JS_ToCString(ctx, json);
    char *result = str ? strdup(str) : strdup("[]");
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, json);
    JS_FreeValue(ctx, conditions);
    return result;
}

// Forward declarations
static void add_uiobject_methods(JSContext *ctx, JSValue obj);
static JSValue create_uiobject(JSContext *ctx, const char *json);

// UiObject.click()
static JSValue js_uiobject_click(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    if (!JS_IsObject(bounds)) { JS_FreeValue(ctx, bounds); return JS_FALSE; }
    
    JSValue left = JS_GetPropertyStr(ctx, bounds, "left");
    JSValue right = JS_GetPropertyStr(ctx, bounds, "right");
    JSValue top = JS_GetPropertyStr(ctx, bounds, "top");
    JSValue bottom = JS_GetPropertyStr(ctx, bounds, "bottom");
    
    int32_t l, r, t, b;
    JS_ToInt32(ctx, &l, left); JS_ToInt32(ctx, &r, right);
    JS_ToInt32(ctx, &t, top); JS_ToInt32(ctx, &b, bottom);
    
    JS_FreeValue(ctx, left); JS_FreeValue(ctx, right);
    JS_FreeValue(ctx, top); JS_FreeValue(ctx, bottom);
    JS_FreeValue(ctx, bounds);
    
    int cx = (l + r) / 2, cy = (t + b) / 2;
    char cx_str[16], cy_str[16];
    snprintf(cx_str, 16, "%d", cx);
    snprintf(cy_str, 16, "%d", cy);
    
    JSValue args[2] = { JS_NewString(ctx, cx_str), JS_NewString(ctx, cy_str) };
    bool ret = call_host_bool(ctx, "click", 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    return JS_NewBool(ctx, ret);
}

// UiObject.setText()
static JSValue js_uiobject_setText(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    js_uiobject_click(ctx, this_val, 0, nullptr);
    usleep(100000);
    return JS_NewBool(ctx, call_host_bool(ctx, "setText", argc, argv));
}

// UiObject.text()
static JSValue js_uiobject_text(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "_text");
}

// UiObject.id()
static JSValue js_uiobject_id(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "_id");
}

// UiObject.className()
static JSValue js_uiobject_className(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "_className");
}

// UiObject.desc()
static JSValue js_uiobject_desc(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "_desc");
}

// UiObject.packageName()
static JSValue js_uiobject_packageName(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "_packageName");
}

// UiObject.bounds()
static JSValue js_uiobject_bounds(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "bounds");
}

// UiObject.parent()
static JSValue js_uiobject_parent(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue boundsJson = JS_JSONStringify(ctx, bounds, JS_UNDEFINED, JS_UNDEFINED);
    const char *boundsStr = JS_ToCString(ctx, boundsJson);
    JS_FreeValue(ctx, boundsJson);
    JS_FreeValue(ctx, bounds);
    
    JSValue args[2] = { JS_NewString(ctx, "uiobject.parent"), JS_NewString(ctx, boundsStr ? boundsStr : "{}") };
    if (boundsStr) JS_FreeCString(ctx, boundsStr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *str = JS_ToCString(ctx, result);
    JSValue uiobj = create_uiobject(ctx, str);
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return uiobj;
}

// UiObject.children()
static JSValue js_uiobject_children(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue boundsJson = JS_JSONStringify(ctx, bounds, JS_UNDEFINED, JS_UNDEFINED);
    const char *boundsStr = JS_ToCString(ctx, boundsJson);
    JS_FreeValue(ctx, boundsJson);
    JS_FreeValue(ctx, bounds);
    
    JSValue args[2] = { JS_NewString(ctx, "uiobject.children"), JS_NewString(ctx, boundsStr ? boundsStr : "{}") };
    if (boundsStr) JS_FreeCString(ctx, boundsStr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *str = JS_ToCString(ctx, result);
    JS_FreeValue(ctx, result);
    if (!str || !*str) return JS_NewArray(ctx);
    
    JSValue arr = JS_ParseJSON(ctx, str, strlen(str), "<children>");
    JS_FreeCString(ctx, str);
    if (!JS_IsArray(ctx, arr)) return JS_NewArray(ctx);
    
    // Add methods to each child
    JSValue lenVal = JS_GetPropertyStr(ctx, arr, "length");
    int32_t len = 0; JS_ToInt32(ctx, &len, lenVal);
    JS_FreeValue(ctx, lenVal);
    for (int i = 0; i < len; i++) {
        JSValue item = JS_GetPropertyUint32(ctx, arr, i);
        add_uiobject_methods(ctx, item);
        JS_SetPropertyUint32(ctx, arr, i, item);
    }
    return arr;
}

// UiObject.find() - find in subtree
static JSValue js_uiobject_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue boundsJson = JS_JSONStringify(ctx, bounds, JS_UNDEFINED, JS_UNDEFINED);
    const char *boundsStr = JS_ToCString(ctx, boundsJson);
    JS_FreeValue(ctx, boundsJson);
    JS_FreeValue(ctx, bounds);
    
    // Get selector conditions from argument
    const char *selectorJson = "[]";
    if (argc > 0) {
        JSValue conditions = JS_GetPropertyStr(ctx, argv[0], "_conditions");
        if (!JS_IsUndefined(conditions)) {
            JSValue json = JS_JSONStringify(ctx, conditions, JS_UNDEFINED, JS_UNDEFINED);
            selectorJson = JS_ToCString(ctx, json);
            JS_FreeValue(ctx, json);
        }
        JS_FreeValue(ctx, conditions);
    }
    
    JSValue args[3] = { 
        JS_NewString(ctx, "uiobject.find"), 
        JS_NewString(ctx, boundsStr ? boundsStr : "{}"),
        JS_NewString(ctx, selectorJson ? selectorJson : "[]")
    };
    if (boundsStr) JS_FreeCString(ctx, boundsStr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    
    const char *str = JS_ToCString(ctx, result);
    JS_FreeValue(ctx, result);
    if (!str || !*str) return JS_NewArray(ctx);
    
    JSValue arr = JS_ParseJSON(ctx, str, strlen(str), "<find>");
    JS_FreeCString(ctx, str);
    if (!JS_IsArray(ctx, arr)) return JS_NewArray(ctx);
    
    // Add methods to each item
    JSValue lenVal = JS_GetPropertyStr(ctx, arr, "length");
    int32_t len = 0; JS_ToInt32(ctx, &len, lenVal);
    JS_FreeValue(ctx, lenVal);
    for (int i = 0; i < len; i++) {
        JSValue item = JS_GetPropertyUint32(ctx, arr, i);
        add_uiobject_methods(ctx, item);
        JS_SetPropertyUint32(ctx, arr, i, item);
    }
    return arr;
}

// UiObject.content() - desc() || text()
static JSValue js_uiobject_content(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue desc = JS_GetPropertyStr(ctx, this_val, "_desc");
    const char *descStr = JS_ToCString(ctx, desc);
    if (descStr && strlen(descStr) > 0) {
        JS_FreeValue(ctx, desc);
        return JS_NewString(ctx, descStr);
    }
    if (descStr) JS_FreeCString(ctx, descStr);
    JS_FreeValue(ctx, desc);
    return JS_GetPropertyStr(ctx, this_val, "_text");
}

// UiObject.childCount()
static JSValue js_uiobject_childCount(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_GetPropertyStr(ctx, this_val, "childCount");
}

// UiObject.indexInParent()
static JSValue js_uiobject_indexInParent(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue idx = JS_GetPropertyStr(ctx, this_val, "_indexInParent");
    if (JS_IsUndefined(idx)) return JS_NewInt32(ctx, -1);
    return idx;
}

// UiObject.depth()
static JSValue js_uiobject_depth(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue d = JS_GetPropertyStr(ctx, this_val, "_depth");
    if (JS_IsUndefined(d)) return JS_NewInt32(ctx, 0);
    return d;
}

// UiObject.drawingOrder()
static JSValue js_uiobject_drawingOrder(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue d = JS_GetPropertyStr(ctx, this_val, "_drawingOrder");
    if (JS_IsUndefined(d)) return JS_NewInt32(ctx, 0);
    return d;
}

// UiObject boolean property getters
#define UIOBJECT_BOOL_GETTER(name, propName) \
static JSValue js_uiobject_##name(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) { \
    JSValue val = JS_GetPropertyStr(ctx, this_val, propName); \
    if (JS_IsUndefined(val)) return JS_FALSE; \
    return val; \
}

UIOBJECT_BOOL_GETTER(clickable, "clickable")
UIOBJECT_BOOL_GETTER(longClickable, "longClickable")
UIOBJECT_BOOL_GETTER(scrollable, "scrollable")
UIOBJECT_BOOL_GETTER(enabled, "enabled")
UIOBJECT_BOOL_GETTER(checked, "checked")
UIOBJECT_BOOL_GETTER(selected, "selected")
UIOBJECT_BOOL_GETTER(focusable, "focusable")
UIOBJECT_BOOL_GETTER(focused, "focused")
UIOBJECT_BOOL_GETTER(checkable, "checkable")
UIOBJECT_BOOL_GETTER(editable, "editable")
UIOBJECT_BOOL_GETTER(visibleToUser, "visibleToUser")

// UiObject.boundsLeft/Top/Right/Bottom/Width/Height/CenterX/CenterY
static JSValue js_uiobject_boundsLeft(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue left = JS_GetPropertyStr(ctx, bounds, "left");
    JS_FreeValue(ctx, bounds);
    return left;
}
static JSValue js_uiobject_boundsTop(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue top = JS_GetPropertyStr(ctx, bounds, "top");
    JS_FreeValue(ctx, bounds);
    return top;
}
static JSValue js_uiobject_boundsRight(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue right = JS_GetPropertyStr(ctx, bounds, "right");
    JS_FreeValue(ctx, bounds);
    return right;
}
static JSValue js_uiobject_boundsBottom(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue bottom = JS_GetPropertyStr(ctx, bounds, "bottom");
    JS_FreeValue(ctx, bounds);
    return bottom;
}
static JSValue js_uiobject_boundsWidth(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue left = JS_GetPropertyStr(ctx, bounds, "left");
    JSValue right = JS_GetPropertyStr(ctx, bounds, "right");
    int32_t l, r;
    JS_ToInt32(ctx, &l, left); JS_ToInt32(ctx, &r, right);
    JS_FreeValue(ctx, left); JS_FreeValue(ctx, right); JS_FreeValue(ctx, bounds);
    return JS_NewInt32(ctx, r - l);
}
static JSValue js_uiobject_boundsHeight(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue top = JS_GetPropertyStr(ctx, bounds, "top");
    JSValue bottom = JS_GetPropertyStr(ctx, bounds, "bottom");
    int32_t t, b;
    JS_ToInt32(ctx, &t, top); JS_ToInt32(ctx, &b, bottom);
    JS_FreeValue(ctx, top); JS_FreeValue(ctx, bottom); JS_FreeValue(ctx, bounds);
    return JS_NewInt32(ctx, b - t);
}
static JSValue js_uiobject_boundsCenterX(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue cx = JS_GetPropertyStr(ctx, bounds, "centerX");
    JS_FreeValue(ctx, bounds);
    return cx;
}
static JSValue js_uiobject_boundsCenterY(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue cy = JS_GetPropertyStr(ctx, bounds, "centerY");
    JS_FreeValue(ctx, bounds);
    return cy;
}

// UiObject.longClick() - click with long duration
static JSValue js_uiobject_longClick(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    if (!JS_IsObject(bounds)) { JS_FreeValue(ctx, bounds); return JS_FALSE; }
    
    JSValue left = JS_GetPropertyStr(ctx, bounds, "left");
    JSValue right = JS_GetPropertyStr(ctx, bounds, "right");
    JSValue top = JS_GetPropertyStr(ctx, bounds, "top");
    JSValue bottom = JS_GetPropertyStr(ctx, bounds, "bottom");
    
    int32_t l, r, t, b;
    JS_ToInt32(ctx, &l, left); JS_ToInt32(ctx, &r, right);
    JS_ToInt32(ctx, &t, top); JS_ToInt32(ctx, &b, bottom);
    
    JS_FreeValue(ctx, left); JS_FreeValue(ctx, right);
    JS_FreeValue(ctx, top); JS_FreeValue(ctx, bottom);
    JS_FreeValue(ctx, bounds);
    
    int cx = (l + r) / 2, cy = (t + b) / 2;
    char cx_str[16], cy_str[16];
    snprintf(cx_str, 16, "%d", cx);
    snprintf(cy_str, 16, "%d", cy);
    
    JSValue args[2] = { JS_NewString(ctx, cx_str), JS_NewString(ctx, cy_str) };
    bool ret = call_host_bool(ctx, "longClick", 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    return JS_NewBool(ctx, ret);
}

// UiObject.clickBounds(offsetX, offsetY) - click with offset
static JSValue js_uiobject_clickBounds(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    if (!JS_IsObject(bounds)) { JS_FreeValue(ctx, bounds); return JS_FALSE; }
    
    JSValue left = JS_GetPropertyStr(ctx, bounds, "left");
    JSValue right = JS_GetPropertyStr(ctx, bounds, "right");
    JSValue top = JS_GetPropertyStr(ctx, bounds, "top");
    JSValue bottom = JS_GetPropertyStr(ctx, bounds, "bottom");
    
    int32_t l, r, t, b;
    JS_ToInt32(ctx, &l, left); JS_ToInt32(ctx, &r, right);
    JS_ToInt32(ctx, &t, top); JS_ToInt32(ctx, &b, bottom);
    
    JS_FreeValue(ctx, left); JS_FreeValue(ctx, right);
    JS_FreeValue(ctx, top); JS_FreeValue(ctx, bottom);
    JS_FreeValue(ctx, bounds);
    
    int cx = (l + r) / 2, cy = (t + b) / 2;
    
    // Apply offsets
    if (argc >= 1) {
        double offsetX = 0;
        JS_ToFloat64(ctx, &offsetX, argv[0]);
        cx += (int)offsetX;
    }
    if (argc >= 2) {
        double offsetY = 0;
        JS_ToFloat64(ctx, &offsetY, argv[1]);
        cy += (int)offsetY;
    }
    
    char cx_str[16], cy_str[16];
    snprintf(cx_str, 16, "%d", cx);
    snprintf(cy_str, 16, "%d", cy);
    
    JSValue args[2] = { JS_NewString(ctx, cx_str), JS_NewString(ctx, cy_str) };
    bool ret = call_host_bool(ctx, "click", 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    return JS_NewBool(ctx, ret);
}

// UiObject sibling methods
static JSValue js_uiobject_sibling(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_NULL;
    int32_t idx = 0;
    JS_ToInt32(ctx, &idx, argv[0]);
    
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue boundsJson = JS_JSONStringify(ctx, bounds, JS_UNDEFINED, JS_UNDEFINED);
    const char *boundsStr = JS_ToCString(ctx, boundsJson);
    JS_FreeValue(ctx, boundsJson);
    JS_FreeValue(ctx, bounds);
    
    char idxStr[16];
    snprintf(idxStr, 16, "%d", idx);
    
    JSValue args[3] = { JS_NewString(ctx, "uiobject.sibling"), JS_NewString(ctx, boundsStr ? boundsStr : "{}"), JS_NewString(ctx, idxStr) };
    if (boundsStr) JS_FreeCString(ctx, boundsStr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    
    const char *str = JS_ToCString(ctx, result);
    JSValue uiobj = create_uiobject(ctx, str);
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return uiobj;
}

// UiObject.scrollForward/scrollBackward
static JSValue js_uiobject_scrollForward(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue boundsJson = JS_JSONStringify(ctx, bounds, JS_UNDEFINED, JS_UNDEFINED);
    const char *boundsStr = JS_ToCString(ctx, boundsJson);
    JS_FreeValue(ctx, boundsJson);
    JS_FreeValue(ctx, bounds);
    
    JSValue args[2] = { JS_NewString(ctx, "uiobject.scrollForward"), JS_NewString(ctx, boundsStr ? boundsStr : "{}") };
    if (boundsStr) JS_FreeCString(ctx, boundsStr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue js_uiobject_scrollBackward(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue bounds = JS_GetPropertyStr(ctx, this_val, "bounds");
    JSValue boundsJson = JS_JSONStringify(ctx, bounds, JS_UNDEFINED, JS_UNDEFINED);
    const char *boundsStr = JS_ToCString(ctx, boundsJson);
    JS_FreeValue(ctx, boundsJson);
    JS_FreeValue(ctx, bounds);
    
    JSValue args[2] = { JS_NewString(ctx, "uiobject.scrollBackward"), JS_NewString(ctx, boundsStr ? boundsStr : "{}") };
    if (boundsStr) JS_FreeCString(ctx, boundsStr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static void add_uiobject_methods(JSContext *ctx, JSValue obj) {
    // Basic getters
    JS_SetPropertyStr(ctx, obj, "click", JS_NewCFunction(ctx, js_uiobject_click, "click", 0));
    JS_SetPropertyStr(ctx, obj, "longClick", JS_NewCFunction(ctx, js_uiobject_longClick, "longClick", 0));
    JS_SetPropertyStr(ctx, obj, "clickBounds", JS_NewCFunction(ctx, js_uiobject_clickBounds, "clickBounds", 2));
    JS_SetPropertyStr(ctx, obj, "setText", JS_NewCFunction(ctx, js_uiobject_setText, "setText", 1));
    JS_SetPropertyStr(ctx, obj, "text", JS_NewCFunction(ctx, js_uiobject_text, "text", 0));
    JS_SetPropertyStr(ctx, obj, "id", JS_NewCFunction(ctx, js_uiobject_id, "id", 0));
    JS_SetPropertyStr(ctx, obj, "className", JS_NewCFunction(ctx, js_uiobject_className, "className", 0));
    JS_SetPropertyStr(ctx, obj, "desc", JS_NewCFunction(ctx, js_uiobject_desc, "desc", 0));
    JS_SetPropertyStr(ctx, obj, "content", JS_NewCFunction(ctx, js_uiobject_content, "content", 0));
    JS_SetPropertyStr(ctx, obj, "packageName", JS_NewCFunction(ctx, js_uiobject_packageName, "packageName", 0));
    JS_SetPropertyStr(ctx, obj, "getBounds", JS_NewCFunction(ctx, js_uiobject_bounds, "getBounds", 0));
    JS_SetPropertyStr(ctx, obj, "bounds", JS_NewCFunction(ctx, js_uiobject_bounds, "bounds", 0));
    
    // Tree navigation
    JS_SetPropertyStr(ctx, obj, "parent", JS_NewCFunction(ctx, js_uiobject_parent, "parent", 0));
    JS_SetPropertyStr(ctx, obj, "children", JS_NewCFunction(ctx, js_uiobject_children, "children", 0));
    JS_SetPropertyStr(ctx, obj, "find", JS_NewCFunction(ctx, js_uiobject_find, "find", 1));
    JS_SetPropertyStr(ctx, obj, "sibling", JS_NewCFunction(ctx, js_uiobject_sibling, "sibling", 1));
    
    // Counts and indices
    JS_SetPropertyStr(ctx, obj, "childCount", JS_NewCFunction(ctx, js_uiobject_childCount, "childCount", 0));
    JS_SetPropertyStr(ctx, obj, "indexInParent", JS_NewCFunction(ctx, js_uiobject_indexInParent, "indexInParent", 0));
    JS_SetPropertyStr(ctx, obj, "depth", JS_NewCFunction(ctx, js_uiobject_depth, "depth", 0));
    JS_SetPropertyStr(ctx, obj, "drawingOrder", JS_NewCFunction(ctx, js_uiobject_drawingOrder, "drawingOrder", 0));
    
    // Boolean properties
    JS_SetPropertyStr(ctx, obj, "clickable", JS_NewCFunction(ctx, js_uiobject_clickable, "clickable", 0));
    JS_SetPropertyStr(ctx, obj, "longClickable", JS_NewCFunction(ctx, js_uiobject_longClickable, "longClickable", 0));
    JS_SetPropertyStr(ctx, obj, "scrollable", JS_NewCFunction(ctx, js_uiobject_scrollable, "scrollable", 0));
    JS_SetPropertyStr(ctx, obj, "enabled", JS_NewCFunction(ctx, js_uiobject_enabled, "enabled", 0));
    JS_SetPropertyStr(ctx, obj, "checked", JS_NewCFunction(ctx, js_uiobject_checked, "checked", 0));
    JS_SetPropertyStr(ctx, obj, "selected", JS_NewCFunction(ctx, js_uiobject_selected, "selected", 0));
    JS_SetPropertyStr(ctx, obj, "focusable", JS_NewCFunction(ctx, js_uiobject_focusable, "focusable", 0));
    JS_SetPropertyStr(ctx, obj, "focused", JS_NewCFunction(ctx, js_uiobject_focused, "focused", 0));
    JS_SetPropertyStr(ctx, obj, "checkable", JS_NewCFunction(ctx, js_uiobject_checkable, "checkable", 0));
    JS_SetPropertyStr(ctx, obj, "editable", JS_NewCFunction(ctx, js_uiobject_editable, "editable", 0));
    JS_SetPropertyStr(ctx, obj, "visibleToUser", JS_NewCFunction(ctx, js_uiobject_visibleToUser, "visibleToUser", 0));
    
    // Bounds convenience methods
    JS_SetPropertyStr(ctx, obj, "left", JS_NewCFunction(ctx, js_uiobject_boundsLeft, "left", 0));
    JS_SetPropertyStr(ctx, obj, "top", JS_NewCFunction(ctx, js_uiobject_boundsTop, "top", 0));
    JS_SetPropertyStr(ctx, obj, "right", JS_NewCFunction(ctx, js_uiobject_boundsRight, "right", 0));
    JS_SetPropertyStr(ctx, obj, "bottom", JS_NewCFunction(ctx, js_uiobject_boundsBottom, "bottom", 0));
    JS_SetPropertyStr(ctx, obj, "width", JS_NewCFunction(ctx, js_uiobject_boundsWidth, "width", 0));
    JS_SetPropertyStr(ctx, obj, "height", JS_NewCFunction(ctx, js_uiobject_boundsHeight, "height", 0));
    JS_SetPropertyStr(ctx, obj, "centerX", JS_NewCFunction(ctx, js_uiobject_boundsCenterX, "centerX", 0));
    JS_SetPropertyStr(ctx, obj, "centerY", JS_NewCFunction(ctx, js_uiobject_boundsCenterY, "centerY", 0));
    JS_SetPropertyStr(ctx, obj, "boundsLeft", JS_NewCFunction(ctx, js_uiobject_boundsLeft, "boundsLeft", 0));
    JS_SetPropertyStr(ctx, obj, "boundsTop", JS_NewCFunction(ctx, js_uiobject_boundsTop, "boundsTop", 0));
    JS_SetPropertyStr(ctx, obj, "boundsRight", JS_NewCFunction(ctx, js_uiobject_boundsRight, "boundsRight", 0));
    JS_SetPropertyStr(ctx, obj, "boundsBottom", JS_NewCFunction(ctx, js_uiobject_boundsBottom, "boundsBottom", 0));
    JS_SetPropertyStr(ctx, obj, "boundsWidth", JS_NewCFunction(ctx, js_uiobject_boundsWidth, "boundsWidth", 0));
    JS_SetPropertyStr(ctx, obj, "boundsHeight", JS_NewCFunction(ctx, js_uiobject_boundsHeight, "boundsHeight", 0));
    JS_SetPropertyStr(ctx, obj, "boundsCenterX", JS_NewCFunction(ctx, js_uiobject_boundsCenterX, "boundsCenterX", 0));
    JS_SetPropertyStr(ctx, obj, "boundsCenterY", JS_NewCFunction(ctx, js_uiobject_boundsCenterY, "boundsCenterY", 0));
    
    // Scroll methods
    JS_SetPropertyStr(ctx, obj, "scrollForward", JS_NewCFunction(ctx, js_uiobject_scrollForward, "scrollForward", 0));
    JS_SetPropertyStr(ctx, obj, "scrollBackward", JS_NewCFunction(ctx, js_uiobject_scrollBackward, "scrollBackward", 0));
}

// Create UiObject from JSON
static JSValue create_uiobject(JSContext *ctx, const char *json) {
    if (!json || !*json || strcmp(json, "null") == 0) return JS_NULL;
    JSValue obj = JS_ParseJSON(ctx, json, strlen(json), "<uiobject>");
    if (JS_IsException(obj)) return JS_NULL;
    add_uiobject_methods(ctx, obj);
    return obj;
}

// Selector.findOnce(i) - find one with index
static JSValue js_selector_findOnce(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    int32_t index = 0;
    if (argc > 0) JS_ToInt32(ctx, &index, argv[0]);
    char indexStr[16];
    snprintf(indexStr, 16, "%d", index);
    
    JSValue args[3] = { JS_NewString(ctx, "selector.findOnce"), JS_NewString(ctx, json), JS_NewString(ctx, indexStr) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    const char *str = JS_ToCString(ctx, result);
    JSValue uiobj = create_uiobject(ctx, str);
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return uiobj;
}

// Selector.longClick()
static JSValue js_selector_longClick(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.longClick"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    bool success = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, success);
}

// Selector.scrollForward()
static JSValue js_selector_scrollForward(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.scrollForward"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    bool success = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, success);
}

// Selector.scrollBackward()
static JSValue js_selector_scrollBackward(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.scrollBackward"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    bool success = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, success);
}

// Selector.findOne()
static JSValue js_selector_findOne(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.findOne"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    JSValue uiobj = create_uiobject(ctx, str);
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return uiobj;
}

// Selector.findAll()
static JSValue js_selector_findAll(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.findAll"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    JS_FreeValue(ctx, result);
    if (!str || !*str) return JS_NewArray(ctx);
    JSValue arr = JS_ParseJSON(ctx, str, strlen(str), "<findAll>");
    JS_FreeCString(ctx, str);
    if (!JS_IsArray(ctx, arr)) return JS_NewArray(ctx);
    
    // Add methods to each object
    JSValue lenVal = JS_GetPropertyStr(ctx, arr, "length");
    int32_t len = 0; JS_ToInt32(ctx, &len, lenVal);
    JS_FreeValue(ctx, lenVal);
    for (int i = 0; i < len; i++) {
        JSValue item = JS_GetPropertyUint32(ctx, arr, i);
        add_uiobject_methods(ctx, item);
        JS_SetPropertyUint32(ctx, arr, i, item);
    }
    return arr;
}

// Selector.waitFor()
static JSValue js_selector_waitFor(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    int64_t timeout = 10000;
    if (argc > 0) JS_ToInt64(ctx, &timeout, argv[0]);
    char timeout_str[32]; snprintf(timeout_str, 32, "%lld", (long long)timeout);
    
    JSValue args[3] = { JS_NewString(ctx, "selector.waitFor"), JS_NewString(ctx, json), JS_NewString(ctx, timeout_str) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    const char *str = JS_ToCString(ctx, result);
    JSValue uiobj = create_uiobject(ctx, str);
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return uiobj;
}

// Selector.exists()
static JSValue js_selector_exists(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.exists"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    bool exists = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, exists);
}

// Selector.click()
static JSValue js_selector_click(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    char *json = serialize_conditions(ctx, this_val);
    JSValue args[2] = { JS_NewString(ctx, "selector.click"), JS_NewString(ctx, json) };
    free(json);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *str = JS_ToCString(ctx, result);
    bool success = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, success);
}

// Selector.setText()
static JSValue js_selector_setText(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    char *json = serialize_conditions(ctx, this_val);
    const char *text = JS_ToCString(ctx, argv[0]);
    JSValue args[3] = { JS_NewString(ctx, "selector.setText"), JS_NewString(ctx, json), JS_NewString(ctx, text ? text : "") };
    free(json);
    if (text) JS_FreeCString(ctx, text);
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    const char *str = JS_ToCString(ctx, result);
    bool success = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, success);
}

// Global selector functions
#define GLOBAL_SELECTOR(name, condType) \
static JSValue js_##name(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) { \
    JSValue sel = create_selector_object(ctx); \
    if (argc > 0) { \
        const char *str = JS_ToCString(ctx, argv[0]); \
        JSValue ret = selector_add_condition(ctx, sel, condType, str ? str : ""); \
        if (str) JS_FreeCString(ctx, str); \
        JS_FreeValue(ctx, sel); \
        return ret; \
    } \
    return sel; \
}

GLOBAL_SELECTOR(text, "text")
GLOBAL_SELECTOR(textContains, "textContains")
GLOBAL_SELECTOR(textStartsWith, "textStartsWith")
GLOBAL_SELECTOR(textEndsWith, "textEndsWith")
GLOBAL_SELECTOR(textMatches, "textMatches")
GLOBAL_SELECTOR(desc, "desc")
GLOBAL_SELECTOR(descContains, "descContains")
GLOBAL_SELECTOR(descStartsWith, "descStartsWith")
GLOBAL_SELECTOR(descEndsWith, "descEndsWith")
GLOBAL_SELECTOR(descMatches, "descMatches")
GLOBAL_SELECTOR(id, "id")
GLOBAL_SELECTOR(idContains, "idContains")
GLOBAL_SELECTOR(idStartsWith, "idStartsWith")
GLOBAL_SELECTOR(idEndsWith, "idEndsWith")
GLOBAL_SELECTOR(idMatches, "idMatches")
GLOBAL_SELECTOR(className, "className")
GLOBAL_SELECTOR(classNameContains, "classNameContains")
GLOBAL_SELECTOR(classNameStartsWith, "classNameStartsWith")
GLOBAL_SELECTOR(classNameEndsWith, "classNameEndsWith")
GLOBAL_SELECTOR(classNameMatches, "classNameMatches")
GLOBAL_SELECTOR(packageName, "packageName")
GLOBAL_SELECTOR(packageNameContains, "packageNameContains")
GLOBAL_SELECTOR(packageNameStartsWith, "packageNameStartsWith")
GLOBAL_SELECTOR(packageNameEndsWith, "packageNameEndsWith")

static JSValue js_clickable(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "clickable", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_scrollable(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "scrollable", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_enabled(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "enabled", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_checked(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "checked", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_selected(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "selected", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_focusable(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "focusable", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_focused(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "focused", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_longClickable(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "longClickable", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_checkable(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "checkable", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_editable(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "editable", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_visibleToUser(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    bool val = argc < 1 || JS_ToBool(ctx, argv[0]);
    JSValue ret = selector_add_condition(ctx, sel, "visibleToUser", val ? "true" : "false");
    JS_FreeValue(ctx, sel);
    return ret;
}

static JSValue js_depth(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue sel = create_selector_object(ctx);
    if (argc > 0) {
        int32_t depth = 0;
        JS_ToInt32(ctx, &depth, argv[0]);
        char buf[16];
        snprintf(buf, 16, "%d", depth);
        JSValue ret = selector_add_condition(ctx, sel, "depth", buf);
        JS_FreeValue(ctx, sel);
        return ret;
    }
    return sel;
}

// ==================== Gesture: gesture() and gestures() ====================

static JSValue js_gesture(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    // gesture(duration, [x1,y1], [x2,y2], ...)
    if (argc < 2) return JS_FALSE;
    
    // Build args array as JSON and call host
    JSValue jsonArr = JS_NewArray(ctx);
    for (int i = 0; i < argc; i++) {
        JS_SetPropertyUint32(ctx, jsonArr, i, JS_DupValue(ctx, argv[i]));
    }
    JSValue jsonStr = JS_JSONStringify(ctx, jsonArr, JS_UNDEFINED, JS_UNDEFINED);
    const char *str = JS_ToCString(ctx, jsonStr);
    
    JSValue args[2] = { JS_NewString(ctx, "gesture"), JS_NewString(ctx, str ? str : "[]") };
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, jsonStr);
    JS_FreeValue(ctx, jsonArr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *resStr = JS_ToCString(ctx, result);
    bool ret = resStr && strcmp(resStr, "true") == 0;
    if (resStr) JS_FreeCString(ctx, resStr);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue js_gestures(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    // gestures([delay1, duration1, points...], [delay2, duration2, points...], ...)
    JSValue jsonArr = JS_NewArray(ctx);
    for (int i = 0; i < argc; i++) {
        JS_SetPropertyUint32(ctx, jsonArr, i, JS_DupValue(ctx, argv[i]));
    }
    JSValue jsonStr = JS_JSONStringify(ctx, jsonArr, JS_UNDEFINED, JS_UNDEFINED);
    const char *str = JS_ToCString(ctx, jsonStr);
    
    JSValue args[2] = { JS_NewString(ctx, "gestures"), JS_NewString(ctx, str ? str : "[]") };
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, jsonStr);
    JS_FreeValue(ctx, jsonArr);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *resStr = JS_ToCString(ctx, result);
    bool ret = resStr && strcmp(resStr, "true") == 0;
    if (resStr) JS_FreeCString(ctx, resStr);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

// setScreenMetrics(width, height)
static int g_screen_metrics_width = 0;
static int g_screen_metrics_height = 0;

static JSValue js_setScreenMetrics(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc >= 2) {
        JS_ToInt32(ctx, &g_screen_metrics_width, argv[0]);
        JS_ToInt32(ctx, &g_screen_metrics_height, argv[1]);
    }
    return JS_UNDEFINED;
}

// ==================== App Module ====================

static JSValue js_app_launch(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "app.launch", argc, argv));
}

static JSValue js_app_openUrl(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "openUrl", argc, argv));
}

static JSValue js_app_currentPackage(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "app.currentPackage") };
    JSValue r = js_call_host(ctx, this_val, 1, args);
    JS_FreeValue(ctx, args[0]);
    return r;
}

// ==================== Device Module ====================

static JSValue js_device_getBattery(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "device.getBattery") };
    JSValue result = js_call_host(ctx, this_val, 1, args);
    JS_FreeValue(ctx, args[0]);
    const char *str = JS_ToCString(ctx, result);
    int battery = str ? atoi(str) : 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewInt32(ctx, battery);
}

static JSValue js_device_wakeUp(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    return JS_NewBool(ctx, call_host_bool(ctx, "device.wakeUp", 0, nullptr));
}

// ==================== Shell/Files/HTTP ====================

static JSValue js_shell(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_UNDEFINED;
    JSValue args[3] = { JS_NewString(ctx, "shell"), JS_DupValue(ctx, argv[0]), argc > 1 ? JS_DupValue(ctx, argv[1]) : JS_UNDEFINED };
    JSValue result = js_call_host(ctx, this_val, argc > 1 ? 3 : 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); if (argc > 1) JS_FreeValue(ctx, args[2]);
    const char *json = JS_ToCString(ctx, result);
    if (json) {
        JSValue parsed = JS_ParseJSON(ctx, json, strlen(json), "<shell>");
        JS_FreeCString(ctx, json);
        JS_FreeValue(ctx, result);
        return parsed;
    }
    return result;
}

static JSValue js_files_read(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_NULL;
    JSValue args[2] = { JS_NewString(ctx, "files.read"), JS_DupValue(ctx, argv[0]) };
    JSValue r = js_call_host(ctx, this_val, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    return r;
}

static JSValue js_files_write(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_FALSE;
    JSValue args[3] = { JS_NewString(ctx, "files.write"), JS_DupValue(ctx, argv[0]), JS_DupValue(ctx, argv[1]) };
    JSValue result = js_call_host(ctx, this_val, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue js_http_get(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_NULL;
    JSValue args[2] = { JS_NewString(ctx, "http.get"), JS_DupValue(ctx, argv[0]) };
    JSValue result = js_call_host(ctx, this_val, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    const char *json = JS_ToCString(ctx, result);
    if (json) {
        JSValue parsed = JS_ParseJSON(ctx, json, strlen(json), "<http>");
        JS_FreeCString(ctx, json);
        JS_FreeValue(ctx, result);
        return parsed;
    }
    return result;
}

// ==================== HTTP POST ====================

static JSValue js_http_post(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_NULL;
    
    // Serialize data to JSON
    const char *url = JS_ToCString(ctx, argv[0]);
    JSValue dataJson = JS_JSONStringify(ctx, argv[1], JS_UNDEFINED, JS_UNDEFINED);
    const char *dataStr = JS_ToCString(ctx, dataJson);
    
    JSValue args[3] = { JS_NewString(ctx, "http.postForm"), JS_NewString(ctx, url ? url : ""), JS_NewString(ctx, dataStr ? dataStr : "{}") };
    if (url) JS_FreeCString(ctx, url);
    if (dataStr) JS_FreeCString(ctx, dataStr);
    JS_FreeValue(ctx, dataJson);
    
    JSValue result = js_call_host(ctx, this_val, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    
    const char *json = JS_ToCString(ctx, result);
    if (json) {
        JSValue parsed = JS_ParseJSON(ctx, json, strlen(json), "<http.post>");
        JS_FreeCString(ctx, json);
        JS_FreeValue(ctx, result);
        
        // Add body.string() method
        JSValue body = JS_GetPropertyStr(ctx, parsed, "body");
        if (JS_IsObject(body)) {
            // body._bodyString contains the actual response
            // Add string() method that returns _bodyString
            const char *bodyStringCode = "(function() { return this._bodyString || ''; })";
            JSValue stringFn = JS_Eval(ctx, bodyStringCode, strlen(bodyStringCode), "<body.string>", JS_EVAL_TYPE_GLOBAL);
            JS_SetPropertyStr(ctx, body, "string", stringFn);
        }
        JS_FreeValue(ctx, body);
        return parsed;
    }
    return result;
}

// ==================== Storages ====================

// Storage object prototype
static JSClassID js_storage_class_id;

typedef struct {
    char name[128];
} JSStorage;

static void js_storage_finalizer(JSRuntime *rt, JSValue val) {
    // Nothing to free, name is inline
}

static JSClassDef js_storage_class = {
    "Storage",
    .finalizer = js_storage_finalizer,
};

static JSValue js_storage_get(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSStorage *s = (JSStorage *)JS_GetOpaque(this_val, js_storage_class_id);
    if (!s || argc < 1) return JS_UNDEFINED;
    
    const char *key = JS_ToCString(ctx, argv[0]);
    JSValue args[3] = { JS_NewString(ctx, "storage.get"), JS_NewString(ctx, s->name), JS_NewString(ctx, key ? key : "") };
    if (key) JS_FreeCString(ctx, key);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    
    const char *str = JS_ToCString(ctx, result);
    JS_FreeValue(ctx, result);
    
    if (!str || strcmp(str, "null") == 0) {
        if (str) JS_FreeCString(ctx, str);
        // Return default value if provided
        if (argc > 1) return JS_DupValue(ctx, argv[1]);
        return JS_UNDEFINED;
    }
    
    // Parse JSON value
    JSValue parsed = JS_ParseJSON(ctx, str, strlen(str), "<storage.get>");
    JS_FreeCString(ctx, str);
    return parsed;
}

static JSValue js_storage_put(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSStorage *s = (JSStorage *)JS_GetOpaque(this_val, js_storage_class_id);
    if (!s || argc < 2) return JS_FALSE;
    
    const char *key = JS_ToCString(ctx, argv[0]);
    JSValue valueJson = JS_JSONStringify(ctx, argv[1], JS_UNDEFINED, JS_UNDEFINED);
    const char *valueStr = JS_ToCString(ctx, valueJson);
    
    JSValue args[4] = { 
        JS_NewString(ctx, "storage.put"), 
        JS_NewString(ctx, s->name), 
        JS_NewString(ctx, key ? key : ""),
        JS_NewString(ctx, valueStr ? valueStr : "null")
    };
    if (key) JS_FreeCString(ctx, key);
    if (valueStr) JS_FreeCString(ctx, valueStr);
    JS_FreeValue(ctx, valueJson);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 4, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]); JS_FreeValue(ctx, args[3]);
    
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue js_storage_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSStorage *s = (JSStorage *)JS_GetOpaque(this_val, js_storage_class_id);
    if (!s || argc < 1) return JS_FALSE;
    
    const char *key = JS_ToCString(ctx, argv[0]);
    JSValue args[3] = { JS_NewString(ctx, "storage.remove"), JS_NewString(ctx, s->name), JS_NewString(ctx, key ? key : "") };
    if (key) JS_FreeCString(ctx, key);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue js_storage_contains(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSStorage *s = (JSStorage *)JS_GetOpaque(this_val, js_storage_class_id);
    if (!s || argc < 1) return JS_FALSE;
    
    const char *key = JS_ToCString(ctx, argv[0]);
    JSValue args[3] = { JS_NewString(ctx, "storage.contains"), JS_NewString(ctx, s->name), JS_NewString(ctx, key ? key : "") };
    if (key) JS_FreeCString(ctx, key);
    
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 3, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]); JS_FreeValue(ctx, args[2]);
    
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue js_storage_clear(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSStorage *s = (JSStorage *)JS_GetOpaque(this_val, js_storage_class_id);
    if (!s) return JS_FALSE;
    
    JSValue args[2] = { JS_NewString(ctx, "storage.clear"), JS_NewString(ctx, s->name) };
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 2, args);
    JS_FreeValue(ctx, args[0]); JS_FreeValue(ctx, args[1]);
    
    const char *str = JS_ToCString(ctx, result);
    bool ret = str && strcmp(str, "true") == 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewBool(ctx, ret);
}

static JSValue create_storage_object(JSContext *ctx, const char *name) {
    JSValue obj = JS_NewObjectClass(ctx, js_storage_class_id);
    JSStorage *s = (JSStorage *)js_malloc(ctx, sizeof(JSStorage));
    strncpy(s->name, name, sizeof(s->name) - 1);
    s->name[sizeof(s->name) - 1] = '\0';
    JS_SetOpaque(obj, s);
    
    JS_SetPropertyStr(ctx, obj, "get", JS_NewCFunction(ctx, js_storage_get, "get", 2));
    JS_SetPropertyStr(ctx, obj, "put", JS_NewCFunction(ctx, js_storage_put, "put", 2));
    JS_SetPropertyStr(ctx, obj, "remove", JS_NewCFunction(ctx, js_storage_remove, "remove", 1));
    JS_SetPropertyStr(ctx, obj, "contains", JS_NewCFunction(ctx, js_storage_contains, "contains", 1));
    JS_SetPropertyStr(ctx, obj, "clear", JS_NewCFunction(ctx, js_storage_clear, "clear", 0));
    
    return obj;
}

static JSValue js_storages_create(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    const char *name = argc > 0 ? JS_ToCString(ctx, argv[0]) : "default";
    JSValue storage = create_storage_object(ctx, name ? name : "default");
    if (name) JS_FreeCString(ctx, name);
    return storage;
}

// ==================== Device width/height ====================

static JSValue js_device_width(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "device.width") };
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 1, args);
    JS_FreeValue(ctx, args[0]);
    const char *str = JS_ToCString(ctx, result);
    int width = str ? atoi(str) : 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewInt32(ctx, width);
}

static JSValue js_device_height(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "device.height") };
    JSValue result = js_call_host(ctx, JS_UNDEFINED, 1, args);
    JS_FreeValue(ctx, args[0]);
    const char *str = JS_ToCString(ctx, result);
    int height = str ? atoi(str) : 0;
    if (str) JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, result);
    return JS_NewInt32(ctx, height);
}

// ==================== currentPackage global ====================

static JSValue js_currentPackage(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    JSValue args[1] = { JS_NewString(ctx, "currentPackage") };
    JSValue r = js_call_host(ctx, this_val, 1, args);
    JS_FreeValue(ctx, args[0]);
    return r;
}

// ==================== launchApp ====================

static JSValue js_launchApp(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 1) return JS_FALSE;
    return JS_NewBool(ctx, call_host_bool(ctx, "app.launchApp", argc, argv));
}

// ==================== Register All APIs ====================

static void register_automation_api(JSContext *ctx) {
    JSValue global = JS_GetGlobalObject(ctx);
    
    // Console
    JSValue console = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, console, "log", JS_NewCFunction(ctx, js_console_log, "log", 1));
    JS_SetPropertyStr(ctx, console, "warn", JS_NewCFunction(ctx, js_console_warn, "warn", 1));
    JS_SetPropertyStr(ctx, console, "error", JS_NewCFunction(ctx, js_console_error, "error", 1));
    JS_SetPropertyStr(ctx, console, "info", JS_NewCFunction(ctx, js_console_info, "info", 1));
    JS_SetPropertyStr(ctx, console, "debug", JS_NewCFunction(ctx, js_console_debug, "debug", 1));
    JS_SetPropertyStr(ctx, global, "console", console);
    
    // Control flow
    JS_SetPropertyStr(ctx, global, "sleep", JS_NewCFunction(ctx, js_sleep, "sleep", 1));
    JS_SetPropertyStr(ctx, global, "exit", JS_NewCFunction(ctx, js_exit, "exit", 0));
    JS_SetPropertyStr(ctx, global, "toast", JS_NewCFunction(ctx, js_toast, "toast", 1));
    JS_SetPropertyStr(ctx, global, "log", JS_NewCFunction(ctx, js_console_log, "log", 1));
    JS_SetPropertyStr(ctx, global, "setClip", JS_NewCFunction(ctx, js_setClip, "setClip", 1));
    JS_SetPropertyStr(ctx, global, "getClip", JS_NewCFunction(ctx, js_getClip, "getClip", 0));
    
    // Gestures
    JS_SetPropertyStr(ctx, global, "click", JS_NewCFunction(ctx, js_click, "click", 2));
    JS_SetPropertyStr(ctx, global, "longClick", JS_NewCFunction(ctx, js_longClick, "longClick", 2));
    JS_SetPropertyStr(ctx, global, "press", JS_NewCFunction(ctx, js_press, "press", 3));
    JS_SetPropertyStr(ctx, global, "swipe", JS_NewCFunction(ctx, js_swipe, "swipe", 5));
    JS_SetPropertyStr(ctx, global, "swipeUp", JS_NewCFunction(ctx, js_swipeUp, "swipeUp", 0));
    JS_SetPropertyStr(ctx, global, "swipeDown", JS_NewCFunction(ctx, js_swipeDown, "swipeDown", 0));
    JS_SetPropertyStr(ctx, global, "swipeLeft", JS_NewCFunction(ctx, js_swipeLeft, "swipeLeft", 0));
    JS_SetPropertyStr(ctx, global, "swipeRight", JS_NewCFunction(ctx, js_swipeRight, "swipeRight", 0));
    
    // Global actions
    JS_SetPropertyStr(ctx, global, "back", JS_NewCFunction(ctx, js_back, "back", 0));
    JS_SetPropertyStr(ctx, global, "home", JS_NewCFunction(ctx, js_home, "home", 0));
    JS_SetPropertyStr(ctx, global, "recents", JS_NewCFunction(ctx, js_recents, "recents", 0));
    JS_SetPropertyStr(ctx, global, "notifications", JS_NewCFunction(ctx, js_notifications, "notifications", 0));
    JS_SetPropertyStr(ctx, global, "quickSettings", JS_NewCFunction(ctx, js_quickSettings, "quickSettings", 0));
    
    // UI Selectors (AutoJS style) - text
    JS_SetPropertyStr(ctx, global, "text", JS_NewCFunction(ctx, js_text, "text", 1));
    JS_SetPropertyStr(ctx, global, "textContains", JS_NewCFunction(ctx, js_textContains, "textContains", 1));
    JS_SetPropertyStr(ctx, global, "textStartsWith", JS_NewCFunction(ctx, js_textStartsWith, "textStartsWith", 1));
    JS_SetPropertyStr(ctx, global, "textEndsWith", JS_NewCFunction(ctx, js_textEndsWith, "textEndsWith", 1));
    JS_SetPropertyStr(ctx, global, "textMatches", JS_NewCFunction(ctx, js_textMatches, "textMatches", 1));
    
    // UI Selectors - desc
    JS_SetPropertyStr(ctx, global, "desc", JS_NewCFunction(ctx, js_desc, "desc", 1));
    JS_SetPropertyStr(ctx, global, "descContains", JS_NewCFunction(ctx, js_descContains, "descContains", 1));
    JS_SetPropertyStr(ctx, global, "descStartsWith", JS_NewCFunction(ctx, js_descStartsWith, "descStartsWith", 1));
    JS_SetPropertyStr(ctx, global, "descEndsWith", JS_NewCFunction(ctx, js_descEndsWith, "descEndsWith", 1));
    JS_SetPropertyStr(ctx, global, "descMatches", JS_NewCFunction(ctx, js_descMatches, "descMatches", 1));
    
    // UI Selectors - id
    JS_SetPropertyStr(ctx, global, "id", JS_NewCFunction(ctx, js_id, "id", 1));
    JS_SetPropertyStr(ctx, global, "idContains", JS_NewCFunction(ctx, js_idContains, "idContains", 1));
    JS_SetPropertyStr(ctx, global, "idStartsWith", JS_NewCFunction(ctx, js_idStartsWith, "idStartsWith", 1));
    JS_SetPropertyStr(ctx, global, "idEndsWith", JS_NewCFunction(ctx, js_idEndsWith, "idEndsWith", 1));
    JS_SetPropertyStr(ctx, global, "idMatches", JS_NewCFunction(ctx, js_idMatches, "idMatches", 1));
    
    // UI Selectors - className
    JS_SetPropertyStr(ctx, global, "className", JS_NewCFunction(ctx, js_className, "className", 1));
    JS_SetPropertyStr(ctx, global, "classNameContains", JS_NewCFunction(ctx, js_classNameContains, "classNameContains", 1));
    JS_SetPropertyStr(ctx, global, "classNameStartsWith", JS_NewCFunction(ctx, js_classNameStartsWith, "classNameStartsWith", 1));
    JS_SetPropertyStr(ctx, global, "classNameEndsWith", JS_NewCFunction(ctx, js_classNameEndsWith, "classNameEndsWith", 1));
    JS_SetPropertyStr(ctx, global, "classNameMatches", JS_NewCFunction(ctx, js_classNameMatches, "classNameMatches", 1));
    
    // UI Selectors - packageName
    JS_SetPropertyStr(ctx, global, "packageName", JS_NewCFunction(ctx, js_packageName, "packageName", 1));
    JS_SetPropertyStr(ctx, global, "packageNameContains", JS_NewCFunction(ctx, js_packageNameContains, "packageNameContains", 1));
    JS_SetPropertyStr(ctx, global, "packageNameStartsWith", JS_NewCFunction(ctx, js_packageNameStartsWith, "packageNameStartsWith", 1));
    JS_SetPropertyStr(ctx, global, "packageNameEndsWith", JS_NewCFunction(ctx, js_packageNameEndsWith, "packageNameEndsWith", 1));
    
    // UI Selectors - boolean properties
    JS_SetPropertyStr(ctx, global, "clickable", JS_NewCFunction(ctx, js_clickable, "clickable", 0));
    JS_SetPropertyStr(ctx, global, "scrollable", JS_NewCFunction(ctx, js_scrollable, "scrollable", 0));
    JS_SetPropertyStr(ctx, global, "enabled", JS_NewCFunction(ctx, js_enabled, "enabled", 0));
    JS_SetPropertyStr(ctx, global, "checked", JS_NewCFunction(ctx, js_checked, "checked", 0));
    JS_SetPropertyStr(ctx, global, "selected", JS_NewCFunction(ctx, js_selected, "selected", 0));
    JS_SetPropertyStr(ctx, global, "focusable", JS_NewCFunction(ctx, js_focusable, "focusable", 0));
    JS_SetPropertyStr(ctx, global, "focused", JS_NewCFunction(ctx, js_focused, "focused", 0));
    JS_SetPropertyStr(ctx, global, "longClickable", JS_NewCFunction(ctx, js_longClickable, "longClickable", 0));
    JS_SetPropertyStr(ctx, global, "checkable", JS_NewCFunction(ctx, js_checkable, "checkable", 0));
    JS_SetPropertyStr(ctx, global, "editable", JS_NewCFunction(ctx, js_editable, "editable", 0));
    JS_SetPropertyStr(ctx, global, "visibleToUser", JS_NewCFunction(ctx, js_visibleToUser, "visibleToUser", 0));
    JS_SetPropertyStr(ctx, global, "depth", JS_NewCFunction(ctx, js_depth, "depth", 1));
    
    // Gesture APIs
    JS_SetPropertyStr(ctx, global, "gesture", JS_NewCFunction(ctx, js_gesture, "gesture", 10));
    JS_SetPropertyStr(ctx, global, "gestures", JS_NewCFunction(ctx, js_gestures, "gestures", 10));
    JS_SetPropertyStr(ctx, global, "setScreenMetrics", JS_NewCFunction(ctx, js_setScreenMetrics, "setScreenMetrics", 2));
    
    // App module
    JSValue app = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, app, "launch", JS_NewCFunction(ctx, js_app_launch, "launch", 1));
    JS_SetPropertyStr(ctx, app, "openUrl", JS_NewCFunction(ctx, js_app_openUrl, "openUrl", 1));
    JS_SetPropertyStr(ctx, app, "currentPackage", JS_NewCFunction(ctx, js_app_currentPackage, "currentPackage", 0));
    JS_SetPropertyStr(ctx, global, "app", app);
    JS_SetPropertyStr(ctx, global, "openUrl", JS_NewCFunction(ctx, js_app_openUrl, "openUrl", 1));
    JS_SetPropertyStr(ctx, global, "launch", JS_NewCFunction(ctx, js_app_launch, "launch", 1));
    
    // Device module
    JSValue device = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, device, "getBattery", JS_NewCFunction(ctx, js_device_getBattery, "getBattery", 0));
    JS_SetPropertyStr(ctx, device, "wakeUp", JS_NewCFunction(ctx, js_device_wakeUp, "wakeUp", 0));
    // Add width/height as functions (call to get value)
    JS_SetPropertyStr(ctx, device, "getWidth", JS_NewCFunction(ctx, js_device_width, "getWidth", 0));
    JS_SetPropertyStr(ctx, device, "getHeight", JS_NewCFunction(ctx, js_device_height, "getHeight", 0));
    JS_SetPropertyStr(ctx, global, "device", device);
    
    // Shell
    JS_SetPropertyStr(ctx, global, "shell", JS_NewCFunction(ctx, js_shell, "shell", 2));
    
    // Files module
    JSValue files = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, files, "read", JS_NewCFunction(ctx, js_files_read, "read", 1));
    JS_SetPropertyStr(ctx, files, "write", JS_NewCFunction(ctx, js_files_write, "write", 2));
    JS_SetPropertyStr(ctx, global, "files", files);
    
    // HTTP module
    JSValue http = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, http, "get", JS_NewCFunction(ctx, js_http_get, "get", 1));
    JS_SetPropertyStr(ctx, http, "post", JS_NewCFunction(ctx, js_http_post, "post", 2));
    JS_SetPropertyStr(ctx, global, "http", http);
    
    // Storages module
    JS_NewClassID(&js_storage_class_id);
    JS_NewClass(JS_GetRuntime(ctx), js_storage_class_id, &js_storage_class);
    JSValue storages = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, storages, "create", JS_NewCFunction(ctx, js_storages_create, "create", 1));
    JS_SetPropertyStr(ctx, global, "storages", storages);
    
    // currentPackage global
    JS_SetPropertyStr(ctx, global, "currentPackage", JS_NewCFunction(ctx, js_currentPackage, "currentPackage", 0));
    
    // launchApp global
    JS_SetPropertyStr(ctx, global, "launchApp", JS_NewCFunction(ctx, js_launchApp, "launchApp", 1));
    
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
        JS_FreeContext(g_ctx);
        JS_FreeRuntime(g_runtime);
    }
    
    g_runtime = JS_NewRuntime();
    if (!g_runtime) { LOGE("Failed to create runtime"); return; }
    
    JS_SetMemoryLimit(g_runtime, 256 * 1024 * 1024);
    JS_SetMaxStackSize(g_runtime, 0);
    
    g_ctx = JS_NewContext(g_runtime);
    if (!g_ctx) { JS_FreeRuntime(g_runtime); g_runtime = nullptr; return; }
    
    if (g_callback) env->DeleteGlobalRef(g_callback);
    g_callback = env->NewGlobalRef(callback);
    
    register_automation_api(g_ctx);
    LOGI("QuickJS engine initialized");
}

extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeSetLogDir(JNIEnv *env, jobject thiz, jstring log_dir) {
    const char *dir = env->GetStringUTFChars(log_dir, nullptr);
    set_log_dir(dir);
    env->ReleaseStringUTFChars(log_dir, dir);
}

extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeDestroy(JNIEnv *env, jobject thiz) {
    if (g_ctx) { JS_FreeContext(g_ctx); g_ctx = nullptr; }
    if (g_runtime) { JS_FreeRuntime(g_runtime); g_runtime = nullptr; }
    if (g_callback) { env->DeleteGlobalRef(g_callback); g_callback = nullptr; }
    LOGI("QuickJS engine destroyed");
}

extern "C" JNIEXPORT jstring JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeEval(JNIEnv *env, jobject thiz, jstring code, jstring filename) {
    if (!g_ctx) return env->NewStringUTF("Error: Engine not initialized");
    
    const char *code_str = env->GetStringUTFChars(code, nullptr);
    const char *filename_str = env->GetStringUTFChars(filename, nullptr);
    
    JS_SetInterruptHandler(g_runtime, js_interrupt_handler, nullptr);
    g_interrupt_flag = 0;
    
    JSValue result = JS_Eval(g_ctx, code_str, strlen(code_str), filename_str, JS_EVAL_TYPE_GLOBAL);
    
    env->ReleaseStringUTFChars(code, code_str);
    env->ReleaseStringUTFChars(filename, filename_str);
    
    if (JS_IsException(result)) {
        JSValue exception = JS_GetException(g_ctx);
        const char *err = JS_ToCString(g_ctx, exception);
        jstring ret = env->NewStringUTF(err ? err : "Unknown error");
        if (err) JS_FreeCString(g_ctx, err);
        JS_FreeValue(g_ctx, exception);
        JS_FreeValue(g_ctx, result);
        return ret;
    }
    
    const char *result_str = JS_ToCString(g_ctx, result);
    LOGI("nativeEval: result=%s", result_str ? result_str : "null");
    jstring ret = env->NewStringUTF(result_str ? result_str : "undefined");
    if (result_str) JS_FreeCString(g_ctx, result_str);
    JS_FreeValue(g_ctx, result);
    return ret;
}

extern "C" JNIEXPORT void JNICALL
Java_im_zoe_flutter_1automate_quickjs_QuickJSEngine_nativeInterrupt(JNIEnv *env, jobject thiz) {
    g_interrupt_flag = 1;
    LOGI("Interrupt requested");
}
