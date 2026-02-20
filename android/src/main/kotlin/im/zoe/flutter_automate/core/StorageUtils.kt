package im.zoe.flutter_automate.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

/**
 * 存储工具类 - Auto.js storages 兼容
 * 支持 storages.create(name) 风格 API
 */
object StorageUtils {
    
    private const val TAG = "StorageUtils"
    private const val PREFIX = "automate_storage_"
    
    private lateinit var context: Context
    private val storages = mutableMapOf<String, Storage>()
    
    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
    
    /**
     * 创建或获取存储实例
     */
    fun create(name: String): Storage {
        return storages.getOrPut(name) {
            Storage(context.getSharedPreferences(PREFIX + name, Context.MODE_PRIVATE))
        }
    }
    
    /**
     * 删除存储
     */
    fun remove(name: String): Boolean {
        storages.remove(name)
        return context.getSharedPreferences(PREFIX + name, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
    
    /**
     * Storage 实例 - Auto.js 兼容
     */
    class Storage(private val prefs: SharedPreferences) {
        
        /**
         * 获取值
         */
        fun get(key: String, defaultValue: Any? = null): Any? {
            if (!prefs.contains(key)) return defaultValue
            
            // 尝试获取不同类型
            return try {
                val jsonStr = prefs.getString(key, null)
                if (jsonStr != null) {
                    parseJsonValue(jsonStr)
                } else {
                    defaultValue
                }
            } catch (e: Exception) {
                Log.e(TAG, "get failed: $key", e)
                defaultValue
            }
        }
        
        /**
         * 设置值
         */
        fun put(key: String, value: Any?): Boolean {
            return try {
                val jsonStr = toJsonString(value)
                prefs.edit().putString(key, jsonStr).apply()
                true
            } catch (e: Exception) {
                Log.e(TAG, "put failed: $key", e)
                false
            }
        }
        
        /**
         * 删除键
         */
        fun remove(key: String): Boolean {
            return prefs.edit().remove(key).commit()
        }
        
        /**
         * 检查键是否存在
         */
        fun contains(key: String): Boolean {
            return prefs.contains(key)
        }
        
        /**
         * 清空所有
         */
        fun clear(): Boolean {
            return prefs.edit().clear().commit()
        }
        
        /**
         * 获取所有键
         */
        fun keys(): Set<String> {
            return prefs.all.keys
        }
        
        // JSON 序列化
        private fun toJsonString(value: Any?): String {
            return when (value) {
                null -> "null"
                is String -> JSONObject().put("_type", "string").put("_value", value).toString()
                is Number -> JSONObject().put("_type", "number").put("_value", value).toString()
                is Boolean -> JSONObject().put("_type", "boolean").put("_value", value).toString()
                is Map<*, *> -> JSONObject().put("_type", "object").put("_value", JSONObject(value as Map<*, *>)).toString()
                is List<*> -> JSONObject().put("_type", "array").put("_value", JSONArray(value)).toString()
                else -> JSONObject().put("_type", "string").put("_value", value.toString()).toString()
            }
        }
        
        // JSON 反序列化
        private fun parseJsonValue(jsonStr: String): Any? {
            return try {
                val json = JSONObject(jsonStr)
                val type = json.optString("_type", "string")
                when (type) {
                    "null" -> null
                    "string" -> json.optString("_value", "")
                    "number" -> {
                        val value = json.opt("_value")
                        when (value) {
                            is Int -> value
                            is Long -> value
                            is Double -> value
                            is Float -> value
                            else -> value?.toString()?.toDoubleOrNull() ?: 0
                        }
                    }
                    "boolean" -> json.optBoolean("_value", false)
                    "object" -> jsonObjectToMap(json.optJSONObject("_value"))
                    "array" -> jsonArrayToList(json.optJSONArray("_value"))
                    else -> json.optString("_value", "")
                }
            } catch (e: Exception) {
                // 旧格式或普通字符串
                jsonStr
            }
        }
        
        private fun jsonObjectToMap(json: JSONObject?): Map<String, Any?> {
            if (json == null) return emptyMap()
            val map = mutableMapOf<String, Any?>()
            json.keys().forEach { key ->
                map[key] = when (val value = json.get(key)) {
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            }
            return map
        }
        
        private fun jsonArrayToList(json: JSONArray?): List<Any?> {
            if (json == null) return emptyList()
            return (0 until json.length()).map { i ->
                when (val value = json.get(i)) {
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            }
        }
        
        /**
         * 转为 JSON 字符串（用于 JS 返回）
         */
        fun toJson(key: String): String {
            val value = get(key)
            return when (value) {
                null -> "null"
                is String -> "\"${value.replace("\"", "\\\"")}\""
                is Number -> value.toString()
                is Boolean -> value.toString()
                is Map<*, *> -> JSONObject(value as Map<*, *>).toString()
                is List<*> -> JSONArray(value).toString()
                else -> "\"${value.toString().replace("\"", "\\\"")}\""
            }
        }
    }
}
