package im.zoe.flutter_automate.core

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 通知监听服务
 * 需要在 AndroidManifest 中声明并获取权限
 */
class NotificationListener : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
        
        // 单例实例
        private var instance: NotificationListener? = null
        
        // 通知回调
        private val listeners = CopyOnWriteArrayList<NotificationCallback>()
        
        // 最近的通知缓存
        private val recentNotifications = ConcurrentHashMap<String, NotificationInfo>()
        private const val MAX_CACHE_SIZE = 100
        
        /**
         * 检查服务是否运行
         */
        fun isRunning(): Boolean = instance != null
        
        /**
         * 添加通知监听器
         */
        fun addListener(callback: NotificationCallback) {
            listeners.add(callback)
        }
        
        /**
         * 移除通知监听器
         */
        fun removeListener(callback: NotificationCallback) {
            listeners.remove(callback)
        }
        
        /**
         * 清除所有监听器
         */
        fun clearListeners() {
            listeners.clear()
        }
        
        /**
         * 获取最近的通知
         */
        fun getRecentNotifications(): List<NotificationInfo> {
            return recentNotifications.values.toList()
        }
        
        /**
         * 清除通知缓存
         */
        fun clearCache() {
            recentNotifications.clear()
        }
        
        /**
         * 获取当前活跃的通知
         */
        fun getActiveNotifications(): List<NotificationInfo> {
            return instance?.activeNotifications?.map { 
                parseNotification(it) 
            } ?: emptyList()
        }
        
        /**
         * 取消通知
         */
        fun cancelNotification(key: String): Boolean {
            return try {
                instance?.cancelNotification(key)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Cancel notification failed", e)
                false
            }
        }
        
        /**
         * 取消所有通知
         */
        fun cancelAllNotifications(): Boolean {
            return try {
                instance?.cancelAllNotifications()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Cancel all notifications failed", e)
                false
            }
        }
        
        /**
         * 解析通知信息
         */
        private fun parseNotification(sbn: StatusBarNotification): NotificationInfo {
            val notification = sbn.notification
            val extras = notification.extras
            
            return NotificationInfo(
                key = sbn.key,
                id = sbn.id,
                packageName = sbn.packageName,
                postTime = sbn.postTime,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
                category = notification.category,
                channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notification.channelId
                } else null,
                flags = notification.flags,
                isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
                isClearable = sbn.isClearable
            )
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "NotificationListener created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "NotificationListener destroyed")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val info = parseNotification(sbn)
            
            // 缓存通知
            if (recentNotifications.size >= MAX_CACHE_SIZE) {
                // 移除最旧的
                val oldest = recentNotifications.values.minByOrNull { it.postTime }
                oldest?.let { recentNotifications.remove(it.key) }
            }
            recentNotifications[info.key] = info
            
            // 通知监听器
            listeners.forEach { callback ->
                try {
                    callback.onNotificationPosted(info)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback error", e)
                }
            }
            
            Log.d(TAG, "Notification posted: ${info.packageName} - ${info.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Parse notification failed", e)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        try {
            val info = parseNotification(sbn)
            recentNotifications.remove(info.key)
            
            listeners.forEach { callback ->
                try {
                    callback.onNotificationRemoved(info)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback error", e)
                }
            }
            
            Log.d(TAG, "Notification removed: ${info.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Parse notification failed", e)
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Listener disconnected")
    }
}

/**
 * 通知信息
 */
data class NotificationInfo(
    val key: String,
    val id: Int,
    val packageName: String,
    val postTime: Long,
    val title: String,
    val text: String,
    val subText: String?,
    val bigText: String?,
    val infoText: String?,
    val category: String?,
    val channelId: String?,
    val flags: Int,
    val isOngoing: Boolean,
    val isClearable: Boolean
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "id" to id,
        "packageName" to packageName,
        "postTime" to postTime,
        "title" to title,
        "text" to text,
        "subText" to subText,
        "bigText" to bigText,
        "infoText" to infoText,
        "category" to category,
        "channelId" to channelId,
        "flags" to flags,
        "isOngoing" to isOngoing,
        "isClearable" to isClearable
    )
}

/**
 * 通知回调接口
 */
interface NotificationCallback {
    fun onNotificationPosted(notification: NotificationInfo)
    fun onNotificationRemoved(notification: NotificationInfo)
}
