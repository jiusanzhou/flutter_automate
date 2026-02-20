package im.zoe.flutter_automate.core

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * UI 对象 - 封装 AccessibilityNodeInfo
 * 提供便捷的属性访问和操作方法
 */
class UiObject(private val node: AccessibilityNodeInfo) {
    
    // ==================== 属性获取 ====================
    
    /**
     * 获取文本
     */
    fun text(): String = node.text?.toString() ?: ""
    
    /**
     * 获取资源 ID
     */
    fun id(): String = node.viewIdResourceName ?: ""
    
    /**
     * 获取类名
     */
    fun className(): String = node.className?.toString() ?: ""
    
    /**
     * 获取内容描述
     */
    fun desc(): String = node.contentDescription?.toString() ?: ""
    
    /**
     * 获取内容 (desc || text)
     */
    fun content(): String {
        val d = desc()
        return if (d.isNotEmpty()) d else text()
    }
    
    /**
     * 获取包名
     */
    fun packageName(): String = node.packageName?.toString() ?: ""
    
    /**
     * 获取边界
     */
    fun bounds(): Rect = Rect().also { node.getBoundsInScreen(it) }
    
    /**
     * 获取父节点边界内的位置
     */
    fun boundsInParent(): Rect = Rect().also { node.getBoundsInParent(it) }
    
    /**
     * 获取子节点数量
     */
    fun childCount(): Int = node.childCount
    
    /**
     * 获取中心点 X
     */
    fun centerX(): Int {
        val bounds = bounds()
        return (bounds.left + bounds.right) / 2
    }
    
    /**
     * 获取中心点 Y
     */
    fun centerY(): Int {
        val bounds = bounds()
        return (bounds.top + bounds.bottom) / 2
    }
    
    /**
     * 边界便捷方法
     */
    fun left(): Int = bounds().left
    fun top(): Int = bounds().top
    fun right(): Int = bounds().right
    fun bottom(): Int = bounds().bottom
    fun width(): Int = bounds().width()
    fun height(): Int = bounds().height()
    
    /**
     * 获取在父节点中的索引
     */
    fun indexInParent(): Int {
        val parent = node.parent ?: return -1
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i)
            if (child == node) return i
        }
        return -1
    }
    
    /**
     * 获取节点深度
     */
    fun depth(): Int {
        var d = 0
        var p = node.parent
        while (p != null) {
            d++
            p = p.parent
        }
        return d
    }
    
    /**
     * 获取绘制顺序
     */
    fun drawingOrder(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            node.drawingOrder
        } else 0
    }
    
    /**
     * 点击控件边界 (支持偏移)
     */
    fun clickBounds(offsetX: Int = 0, offsetY: Int = 0): Boolean {
        val service = AutomateAccessibilityService.instance ?: return false
        return service.click((centerX() + offsetX).toFloat(), (centerY() + offsetY).toFloat())
    }
    
    /**
     * 获取指定索引的兄弟节点
     */
    fun sibling(index: Int): UiObject? {
        val parent = node.parent ?: return null
        val actualIndex = if (index < 0) parent.childCount + index else index
        if (actualIndex < 0 || actualIndex >= parent.childCount) return null
        return parent.getChild(actualIndex)?.let { UiObject(it) }
    }
    
    /**
     * 获取前一个兄弟节点
     */
    fun previousSibling(): UiObject? {
        val idx = indexInParent()
        if (idx <= 0) return null
        return sibling(idx - 1)
    }
    
    /**
     * 获取后一个兄弟节点
     */
    fun nextSibling(): UiObject? {
        val idx = indexInParent()
        val parent = node.parent ?: return null
        if (idx < 0 || idx >= parent.childCount - 1) return null
        return sibling(idx + 1)
    }
    
    /**
     * 获取第一个子节点
     */
    fun firstChild(): UiObject? = child(0)
    
    /**
     * 获取最后一个子节点
     */
    fun lastChild(): UiObject? = child(childCount() - 1)
    
    // ==================== 状态检查 ====================
    
    fun isClickable(): Boolean = node.isClickable
    fun isLongClickable(): Boolean = node.isLongClickable
    fun isScrollable(): Boolean = node.isScrollable
    fun isEditable(): Boolean = node.isEditable
    fun isEnabled(): Boolean = node.isEnabled
    fun isChecked(): Boolean = node.isChecked
    fun isCheckable(): Boolean = node.isCheckable
    fun isSelected(): Boolean = node.isSelected
    fun isFocusable(): Boolean = node.isFocusable
    fun isFocused(): Boolean = node.isFocused
    fun isVisibleToUser(): Boolean = node.isVisibleToUser
    
    // ==================== 节点操作 ====================
    
    /**
     * 点击节点
     */
    fun click(): Boolean {
        // 优先使用节点自身的点击
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        
        // 尝试点击可点击的父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        
        // 使用手势点击
        val service = AutomateAccessibilityService.instance ?: return false
        return service.click(centerX().toFloat(), centerY().toFloat())
    }
    
    /**
     * 长按节点
     */
    fun longClick(): Boolean {
        // 优先使用节点自身的长按
        if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return true
        }
        
        // 尝试长按可长按的父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isLongClickable && parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        
        // 使用手势长按
        val service = AutomateAccessibilityService.instance ?: return false
        return service.longClick(centerX().toFloat(), centerY().toFloat())
    }
    
    /**
     * 双击节点
     */
    fun doubleClick(): Boolean {
        val service = AutomateAccessibilityService.instance ?: return false
        val x = centerX().toFloat()
        val y = centerY().toFloat()
        service.click(x, y, 50)
        Thread.sleep(50)
        return service.click(x, y, 50)
    }
    
    /**
     * 设置文本
     */
    fun setText(text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        
        // 兼容旧版本：先清空再粘贴
        // 需要配合剪贴板使用，这里简化处理
        return false
    }
    
    /**
     * 追加文本
     */
    fun appendText(text: String): Boolean {
        val currentText = text()
        return setText(currentText + text)
    }
    
    /**
     * 清空文本
     */
    fun clearText(): Boolean {
        return setText("")
    }
    
    /**
     * 获取焦点
     */
    fun focus(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }
    
    /**
     * 清除焦点
     */
    fun clearFocus(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
    }
    
    /**
     * 选中
     */
    fun select(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
    }
    
    /**
     * 取消选中
     */
    fun clearSelection(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION)
    }
    
    /**
     * 向前滚动
     */
    fun scrollForward(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
    
    /**
     * 向后滚动
     */
    fun scrollBackward(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
    
    /**
     * 向上滚动
     */
    fun scrollUp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)
        }
        return scrollBackward()
    }
    
    /**
     * 向下滚动
     */
    fun scrollDown(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
        }
        return scrollForward()
    }
    
    /**
     * 向左滚动
     */
    fun scrollLeft(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id)
        }
        return false
    }
    
    /**
     * 向右滚动
     */
    fun scrollRight(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id)
        }
        return false
    }
    
    /**
     * 折叠
     */
    fun collapse(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            node.performAction(AccessibilityNodeInfo.ACTION_COLLAPSE)
        } else false
    }
    
    /**
     * 展开
     */
    fun expand(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            node.performAction(AccessibilityNodeInfo.ACTION_EXPAND)
        } else false
    }
    
    /**
     * 复制
     */
    fun copy(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }
    
    /**
     * 粘贴
     */
    fun paste(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
    
    /**
     * 剪切
     */
    fun cut(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CUT)
    }
    
    // ==================== 层级遍历 ====================
    
    /**
     * 获取父节点
     */
    fun parent(): UiObject? {
        return node.parent?.let { UiObject(it) }
    }
    
    /**
     * 获取指定索引的子节点
     */
    fun child(index: Int): UiObject? {
        if (index < 0 || index >= node.childCount) return null
        return node.getChild(index)?.let { UiObject(it) }
    }
    
    /**
     * 获取所有子节点
     */
    fun children(): List<UiObject> {
        return (0 until node.childCount).mapNotNull { 
            node.getChild(it)?.let { child -> UiObject(child) }
        }
    }
    
    /**
     * 获取所有兄弟节点
     */
    fun siblings(): List<UiObject> {
        val parent = node.parent ?: return emptyList()
        return (0 until parent.childCount).mapNotNull {
            val sibling = parent.getChild(it)
            if (sibling != null && sibling != node) UiObject(sibling) else null
        }
    }
    
    /**
     * 在子节点中查找
     */
    fun find(selector: UiSelector): UiObject? {
        return selector.findOne(node)
    }
    
    /**
     * 在子节点中查找所有
     */
    fun findAll(selector: UiSelector): List<UiObject> {
        return selector.findAll(node)
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取原始节点
     */
    fun getNode(): AccessibilityNodeInfo = node
    
    /**
     * 刷新节点信息
     */
    fun refresh(): Boolean {
        return node.refresh()
    }
    
    /**
     * 回收节点
     */
    fun recycle() {
        node.recycle()
    }
    
    override fun toString(): String {
        return "UiObject(text='${text()}', id='${id()}', class='${className()}', bounds=${bounds()})"
    }
}
