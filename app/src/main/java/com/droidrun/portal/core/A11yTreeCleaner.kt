package com.droidrun.portal.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Accessibility Tree Cleaner
 * 精简无障碍树数据,只保留有用信息
 */
object A11yTreeCleaner {
    
    private const val TAG = "A11yTreeCleaner"
    private const val MAX_ELEMENTS = 100
    private const val MAX_TEXT_LENGTH = 80
    
    /**
     * 清理并精简无障碍树数据
     */
    fun cleanA11yTree(rawJson: String): String {
        return try {
            val root = JSONObject(rawJson)
            val data = root.optString("data", null) ?: return "数据格式错误"
            
            val inner = JSONObject(data)
            val result = StringBuilder()
            
            // 1. 精简 phone_state
            appendPhoneState(inner, result)
            
            // 2. 提取屏幕宽高
            appendScreenSize(inner, result)
            
            // 3. 提取可交互元素
            val elementCount = appendInteractiveElements(inner, result)
            
            Log.i(TAG, "A11y tree cleaned: ${rawJson.length} -> ${result.length} bytes, $elementCount elements")
            
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning A11y tree", e)
            "清理失败: ${e.message}"
        }
    }
    
    private fun appendPhoneState(inner: JSONObject, result: StringBuilder) {
        val phoneState = inner.optJSONObject("phone_state") ?: return
        
        result.append("【手机状态】\n")
        
        phoneState.optString("currentApp")?.let {
            if (it.isNotEmpty()) result.append("APP: $it\n")
        }
        
        phoneState.optString("packageName")?.let {
            if (it.isNotEmpty()) result.append("包名: $it\n")
        }
        
        phoneState.optString("activityName")?.let {
            if (it.isNotEmpty()) result.append("页面: $it\n")
        }
        
        result.append("键盘: ${if (phoneState.optBoolean("keyboardVisible")) "显示" else "隐藏"}\n")
        result.append("可编辑: ${if (phoneState.optBoolean("isEditable")) "是" else "否"}\n")
        
        // 焦点元素
        val focused = phoneState.optJSONObject("focusedElement")
        if (focused != null) {
            val text = focused.optString("text", "")
            val id = focused.optString("resourceId", "")
            if (text.isNotEmpty() || id.isNotEmpty()) {
                result.append("焦点: ")
                if (text.isNotEmpty()) result.append(text)
                if (id.isNotEmpty()) {
                    val shortId = id.substringAfterLast('/')
                    result.append(" #$shortId")
                }
                result.append("\n")
            }
        }
    }
    
    private fun appendScreenSize(inner: JSONObject, result: StringBuilder) {
        val a11yTree = inner.optJSONArray("a11y_tree")
        if (a11yTree != null && a11yTree.length() > 0) {
            val firstNode = a11yTree.getJSONObject(0)
            val bounds = firstNode.optString("bounds", "")
            if (bounds.isNotEmpty()) {
                try {
                    val parts = bounds.split(",")
                    if (parts.size == 4) {
                        val x1 = parts[0].toInt()
                        val y1 = parts[1].toInt()
                        val x2 = parts[2].toInt()
                        val y2 = parts[3].toInt()
                        val width = x2 - x1
                        val height = y2 - y1
                        result.append("屏幕: ${width}x${height}\n\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse bounds: $bounds")
                }
            }
        }
    }
    
    private fun appendInteractiveElements(inner: JSONObject, result: StringBuilder): Int {
        result.append("【可交互元素】\n")
        var count = 0
        
        val a11yTree = inner.optJSONArray("a11y_tree")
        if (a11yTree != null) {
            for (i in 0 until a11yTree.length()) {
                if (count >= MAX_ELEMENTS) break
                extractFlatElements(a11yTree.getJSONObject(i), result, count) { newCount ->
                    count = newCount
                }
            }
        }
        
        if (count == 0) {
            result.append("（无可交互元素）\n")
        } else {
            result.append("共 $count 个元素\n")
        }
        
        return count
    }
    
    private fun extractFlatElements(
        node: JSONObject,
        result: StringBuilder,
        count: Int,
        updateCount: (Int) -> Unit
    ) {
        var currentCount = count
        if (currentCount >= MAX_ELEMENTS) return
        
        if (shouldKeepNode(node)) {
            currentCount++
            updateCount(currentCount)
            
            val text = node.optString("text", "")
            val bounds = node.optString("bounds", "")
            val resourceId = node.optString("resourceId", "")
            val className = node.optString("className", "")
            val clickable = node.optBoolean("clickable", false)
            val contentDesc = node.optString("contentDesc", "")
            
            result.append("$currentCount. ")
            
            // 类型
            if (className.isNotEmpty()) {
                val shortClass = className.substringAfterLast('.')
                result.append("[$shortClass] ")
            }
            
            // 文本
            if (text.isNotEmpty()) {
                result.append(truncateText(text, MAX_TEXT_LENGTH))
            } else if (contentDesc.isNotEmpty()) {
                result.append(truncateText(contentDesc, MAX_TEXT_LENGTH))
            }
            
            // 坐标
            if (bounds.isNotEmpty()) {
                result.append(" @$bounds")
            }
            
            // resourceId
            if (resourceId.isNotEmpty()) {
                val shortId = resourceId.substringAfterLast('/')
                result.append(" #$shortId")
            }
            
            // 交互属性
            if (clickable) {
                result.append(" 可点击")
            }
            
            result.append("\n")
        }
        
        // 递归子节点
        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                if (currentCount >= MAX_ELEMENTS) break
                extractFlatElements(children.getJSONObject(i), result, currentCount, updateCount)
            }
        }
    }
    
    private fun shouldKeepNode(node: JSONObject): Boolean {
        val text = node.optString("text", "")
        val contentDesc = node.optString("contentDesc", "")
        val resourceId = node.optString("resourceId", "")
        val clickable = node.optBoolean("clickable", false)
        val focusable = node.optBoolean("focusable", false)
        val checkable = node.optBoolean("checkable", false)
        
        // 有意义文本
        if (isMeaningfulText(text)) return true
        
        // 有 content-desc
        if (contentDesc.isNotEmpty()) return true
        
        // 有 resourceId
        if (resourceId.isNotEmpty()) return true
        
        // 可交互
        if (clickable || focusable || checkable) return true
        
        return false
    }
    
    private fun isMeaningfulText(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // 过滤纯容器名
        val containerNames = setOf(
            "FrameLayout", "View", "LinearLayout", "ViewPager",
            "RecyclerView", "ViewGroup"
        )
        if (text in containerNames) return false
        
        // 过滤纯符号
        if (text.length <= 2) {
            val allSymbol = text.all { it in setOf('|', '-', '_', '=', '~') }
            if (allSymbol) return false
        }
        
        return true
    }
    
    private fun truncateText(text: String, maxLen: Int): String {
        return if (text.length > maxLen) {
            "${text.substring(0, maxLen)}…"
        } else {
            text
        }
    }
}