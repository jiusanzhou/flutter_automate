package im.zoe.flutter_automate.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

/**
 * UI 选择器 - 用于查找界面元素
 * 支持链式调用，类似 jQuery/AutoJS 的选择器语法
 */
class UiSelector {
    
    // 选择条件
    private var textEquals: String? = null
    private var textContains: String? = null
    private var textStartsWith: String? = null
    private var textEndsWith: String? = null
    private var textMatches: Pattern? = null
    
    private var idEquals: String? = null
    private var idContains: String? = null
    private var idMatches: Pattern? = null
    
    private var classNameEquals: String? = null
    private var classNameContains: String? = null
    
    private var descEquals: String? = null
    private var descContains: String? = null
    private var descMatches: Pattern? = null
    
    private var packageNameEquals: String? = null
    
    private var clickable: Boolean? = null
    private var scrollable: Boolean? = null
    private var editable: Boolean? = null
    private var enabled: Boolean? = null
    private var checked: Boolean? = null
    private var selected: Boolean? = null
    private var focusable: Boolean? = null
    private var focused: Boolean? = null
    private var checkable: Boolean? = null
    
    private var boundsInside: Rect? = null
    private var boundsContains: Rect? = null
    
    private var depth: Int? = null
    private var indexInParent: Int? = null
    
    // ==================== 文本选择器 ====================
    
    fun text(value: String): UiSelector {
        textEquals = value
        return this
    }
    
    fun textContains(value: String): UiSelector {
        textContains = value
        return this
    }
    
    fun textStartsWith(value: String): UiSelector {
        textStartsWith = value
        return this
    }
    
    fun textEndsWith(value: String): UiSelector {
        textEndsWith = value
        return this
    }
    
    fun textMatches(regex: String): UiSelector {
        textMatches = Pattern.compile(regex)
        return this
    }
    
    // ==================== ID 选择器 ====================
    
    fun id(resourceId: String): UiSelector {
        idEquals = resourceId
        return this
    }
    
    fun idContains(value: String): UiSelector {
        idContains = value
        return this
    }
    
    fun idMatches(regex: String): UiSelector {
        idMatches = Pattern.compile(regex)
        return this
    }
    
    // ==================== 类名选择器 ====================
    
    fun className(name: String): UiSelector {
        classNameEquals = name
        return this
    }
    
    fun classNameContains(value: String): UiSelector {
        classNameContains = value
        return this
    }
    
    // ==================== 描述选择器 ====================
    
    fun desc(description: String): UiSelector {
        descEquals = description
        return this
    }
    
    fun descContains(value: String): UiSelector {
        descContains = value
        return this
    }
    
    fun descMatches(regex: String): UiSelector {
        descMatches = Pattern.compile(regex)
        return this
    }
    
    // ==================== 包名选择器 ====================
    
    fun packageName(pkg: String): UiSelector {
        packageNameEquals = pkg
        return this
    }
    
    // ==================== 属性选择器 ====================
    
    fun clickable(value: Boolean = true): UiSelector {
        clickable = value
        return this
    }
    
    fun scrollable(value: Boolean = true): UiSelector {
        scrollable = value
        return this
    }
    
    fun editable(value: Boolean = true): UiSelector {
        editable = value
        return this
    }
    
    fun enabled(value: Boolean = true): UiSelector {
        enabled = value
        return this
    }
    
    fun checked(value: Boolean = true): UiSelector {
        checked = value
        return this
    }
    
    fun selected(value: Boolean = true): UiSelector {
        selected = value
        return this
    }
    
    fun focusable(value: Boolean = true): UiSelector {
        focusable = value
        return this
    }
    
    fun focused(value: Boolean = true): UiSelector {
        focused = value
        return this
    }
    
    fun checkable(value: Boolean = true): UiSelector {
        checkable = value
        return this
    }
    
    // ==================== 位置选择器 ====================
    
    fun boundsInside(left: Int, top: Int, right: Int, bottom: Int): UiSelector {
        boundsInside = Rect(left, top, right, bottom)
        return this
    }
    
    fun boundsContains(left: Int, top: Int, right: Int, bottom: Int): UiSelector {
        boundsContains = Rect(left, top, right, bottom)
        return this
    }
    
    // ==================== 层级选择器 ====================
    
    fun depth(d: Int): UiSelector {
        depth = d
        return this
    }
    
    fun index(i: Int): UiSelector {
        indexInParent = i
        return this
    }
    
    // ==================== 查找方法 ====================
    
    /**
     * 查找第一个匹配的节点
     */
    fun findOne(root: AccessibilityNodeInfo? = null): UiObject? {
        val rootNode = root ?: AutomateAccessibilityService.instance?.getRootNode() ?: return null
        return findOneRecursive(rootNode, 0)
    }
    
    /**
     * 查找所有匹配的节点
     */
    fun findAll(root: AccessibilityNodeInfo? = null): List<UiObject> {
        val rootNode = root ?: AutomateAccessibilityService.instance?.getRootNode() ?: return emptyList()
        val results = mutableListOf<UiObject>()
        findAllRecursive(rootNode, 0, results)
        return results
    }
    
    /**
     * 等待匹配的节点出现
     */
    fun waitFor(timeoutMs: Long = 10000, root: AccessibilityNodeInfo? = null): UiObject? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            findOne(root)?.let { return it }
            Thread.sleep(100)
        }
        return null
    }
    
    /**
     * 检查是否存在匹配的节点
     */
    fun exists(root: AccessibilityNodeInfo? = null): Boolean {
        return findOne(root) != null
    }
    
    // ==================== 内部方法 ====================
    
    private fun findOneRecursive(node: AccessibilityNodeInfo, currentDepth: Int): UiObject? {
        if (matches(node, currentDepth)) {
            return UiObject(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findOneRecursive(child, currentDepth + 1)?.let { return it }
        }
        
        return null
    }
    
    private fun findAllRecursive(node: AccessibilityNodeInfo, currentDepth: Int, results: MutableList<UiObject>) {
        if (matches(node, currentDepth)) {
            results.add(UiObject(node))
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllRecursive(child, currentDepth + 1, results)
        }
    }
    
    /**
     * 检查节点是否匹配所有条件
     */
    private fun matches(node: AccessibilityNodeInfo, currentDepth: Int): Boolean {
        // 文本匹配
        val nodeText = node.text?.toString() ?: ""
        textEquals?.let { if (nodeText != it) return false }
        textContains?.let { if (!nodeText.contains(it)) return false }
        textStartsWith?.let { if (!nodeText.startsWith(it)) return false }
        textEndsWith?.let { if (!nodeText.endsWith(it)) return false }
        textMatches?.let { if (!it.matcher(nodeText).matches()) return false }
        
        // ID 匹配
        val nodeId = node.viewIdResourceName ?: ""
        idEquals?.let { if (nodeId != it && !nodeId.endsWith(":id/$it")) return false }
        idContains?.let { if (!nodeId.contains(it)) return false }
        idMatches?.let { if (!it.matcher(nodeId).matches()) return false }
        
        // 类名匹配
        val nodeClassName = node.className?.toString() ?: ""
        classNameEquals?.let { 
            if (nodeClassName != it && !nodeClassName.endsWith(".$it")) return false 
        }
        classNameContains?.let { if (!nodeClassName.contains(it)) return false }
        
        // 描述匹配
        val nodeDesc = node.contentDescription?.toString() ?: ""
        descEquals?.let { if (nodeDesc != it) return false }
        descContains?.let { if (!nodeDesc.contains(it)) return false }
        descMatches?.let { if (!it.matcher(nodeDesc).matches()) return false }
        
        // 包名匹配
        val nodePkg = node.packageName?.toString() ?: ""
        packageNameEquals?.let { if (nodePkg != it) return false }
        
        // 属性匹配
        clickable?.let { if (node.isClickable != it) return false }
        scrollable?.let { if (node.isScrollable != it) return false }
        editable?.let { if (node.isEditable != it) return false }
        enabled?.let { if (node.isEnabled != it) return false }
        checked?.let { if (node.isChecked != it) return false }
        selected?.let { if (node.isSelected != it) return false }
        focusable?.let { if (node.isFocusable != it) return false }
        focused?.let { if (node.isFocused != it) return false }
        checkable?.let { if (node.isCheckable != it) return false }
        
        // 位置匹配
        val nodeBounds = Rect().also { node.getBoundsInScreen(it) }
        boundsInside?.let { 
            if (!it.contains(nodeBounds)) return false 
        }
        boundsContains?.let { 
            if (!nodeBounds.contains(it)) return false 
        }
        
        // 深度匹配
        depth?.let { if (currentDepth != it) return false }
        
        // 索引匹配 (需要父节点信息，暂时跳过)
        // indexInParent?.let { ... }
        
        return true
    }
    
    // ==================== 便捷操作 ====================
    
    /**
     * 找到并点击
     */
    fun click(): Boolean {
        return findOne()?.click() ?: false
    }
    
    /**
     * 找到并长按
     */
    fun longClick(): Boolean {
        return findOne()?.longClick() ?: false
    }
    
    /**
     * 找到并设置文本
     */
    fun setText(text: String): Boolean {
        return findOne()?.setText(text) ?: false
    }
}

// ==================== 全局便捷方法 ====================

/**
 * 创建文本选择器
 */
fun text(value: String): UiSelector = UiSelector().text(value)

/**
 * 创建文本包含选择器
 */
fun textContains(value: String): UiSelector = UiSelector().textContains(value)

/**
 * 创建 ID 选择器
 */
fun id(resourceId: String): UiSelector = UiSelector().id(resourceId)

/**
 * 创建类名选择器
 */
fun className(name: String): UiSelector = UiSelector().className(name)

/**
 * 创建描述选择器
 */
fun desc(description: String): UiSelector = UiSelector().desc(description)

/**
 * 创建可点击选择器
 */
fun clickable(value: Boolean = true): UiSelector = UiSelector().clickable(value)

/**
 * 创建可滚动选择器
 */
fun scrollable(value: Boolean = true): UiSelector = UiSelector().scrollable(value)
