package im.zoe.flutter_automate.core

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 对话框和交互工具
 */
object DialogUtils {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 显示 Toast
     */
    fun toast(context: Context, message: String, long: Boolean = false) {
        mainHandler.post {
            Toast.makeText(
                context, 
                message, 
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * 显示警告对话框
     * @return 用户是否点击了确定
     */
    fun alert(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "确定"
    ): Boolean {
        val result = AtomicReference(false)
        val latch = CountDownLatch(1)
        
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(confirmText) { dialog, _ ->
                    result.set(true)
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener {
                    latch.countDown()
                }
                .show()
        }
        
        latch.await(60, TimeUnit.SECONDS)
        return result.get()
    }
    
    /**
     * 显示确认对话框
     * @return 用户是否点击了确定
     */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "确定",
        cancelText: String = "取消"
    ): Boolean {
        val result = AtomicReference(false)
        val latch = CountDownLatch(1)
        
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(confirmText) { dialog, _ ->
                    result.set(true)
                    dialog.dismiss()
                    latch.countDown()
                }
                .setNegativeButton(cancelText) { dialog, _ ->
                    result.set(false)
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener {
                    latch.countDown()
                }
                .show()
        }
        
        latch.await(60, TimeUnit.SECONDS)
        return result.get()
    }
    
    /**
     * 显示输入对话框
     * @return 用户输入的内容，取消返回 null
     */
    fun input(
        context: Context,
        title: String,
        message: String? = null,
        defaultValue: String = "",
        hint: String = "",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        confirmText: String = "确定",
        cancelText: String = "取消"
    ): String? {
        val result = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        
        mainHandler.post {
            val editText = EditText(context).apply {
                setText(defaultValue)
                this.hint = hint
                this.inputType = inputType
                setPadding(50, 30, 50, 30)
            }
            
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 20)
                addView(editText)
            }
            
            val builder = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton(confirmText) { dialog, _ ->
                    result.set(editText.text.toString())
                    dialog.dismiss()
                    latch.countDown()
                }
                .setNegativeButton(cancelText) { dialog, _ ->
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener {
                    latch.countDown()
                }
            
            if (message != null) {
                builder.setMessage(message)
            }
            
            builder.show()
        }
        
        latch.await(300, TimeUnit.SECONDS)
        return result.get()
    }
    
    /**
     * 显示单选对话框
     * @return 选中的索引，取消返回 -1
     */
    fun singleChoice(
        context: Context,
        title: String,
        items: List<String>,
        selectedIndex: Int = -1
    ): Int {
        val result = AtomicReference(-1)
        val latch = CountDownLatch(1)
        var currentSelection = selectedIndex
        
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(items.toTypedArray(), selectedIndex) { _, which ->
                    currentSelection = which
                }
                .setPositiveButton("确定") { dialog, _ ->
                    result.set(currentSelection)
                    dialog.dismiss()
                    latch.countDown()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener {
                    latch.countDown()
                }
                .show()
        }
        
        latch.await(300, TimeUnit.SECONDS)
        return result.get()
    }
    
    /**
     * 显示多选对话框
     * @return 选中的索引列表，取消返回空列表
     */
    fun multiChoice(
        context: Context,
        title: String,
        items: List<String>,
        selectedIndices: List<Int> = emptyList()
    ): List<Int> {
        val result = AtomicReference<List<Int>>(emptyList())
        val latch = CountDownLatch(1)
        val checkedItems = BooleanArray(items.size) { selectedIndices.contains(it) }
        
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMultiChoiceItems(items.toTypedArray(), checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("确定") { dialog, _ ->
                    result.set(checkedItems.indices.filter { checkedItems[it] })
                    dialog.dismiss()
                    latch.countDown()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener {
                    latch.countDown()
                }
                .show()
        }
        
        latch.await(300, TimeUnit.SECONDS)
        return result.get()
    }
    
    /**
     * 显示列表选择对话框（点击即选中）
     * @return 选中的索引，取消返回 -1
     */
    fun select(
        context: Context,
        title: String,
        items: List<String>
    ): Int {
        val result = AtomicReference(-1)
        val latch = CountDownLatch(1)
        
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(items.toTypedArray()) { dialog, which ->
                    result.set(which)
                    dialog.dismiss()
                    latch.countDown()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                    latch.countDown()
                }
                .setOnCancelListener {
                    latch.countDown()
                }
                .show()
        }
        
        latch.await(300, TimeUnit.SECONDS)
        return result.get()
    }
}
