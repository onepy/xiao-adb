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
     * @param rawJson 直接的JSON字符串,包含a11y_tree和phone_state
     */
    fun cleanA11yTree(rawJson: String): String {
        return try {
            val root = JSONObject(rawJson)
            val result = JSONObject()
            
            // 1. 提取phone_state (精简)
            val phoneState = extractPhoneState(root)
            result.put("phone_state", phoneState)
            
            // 2. 提取screen_size
            val screenSize = extractScreenSize(root)
            result.put("screen_size", screenSize)
            
            // 3. 提取可交互元素列表(字符串数组格式)
            val elements = extractInteractiveElementsCompact(root)
            
            // 在数组第一行添加格式说明
            val elementsWithHeader = JSONArray()
            elementsWithHeader.put("FORMAT: text|x,y,w,h|id|type|flags (flags: c=clickable,l=long_clickable,e=editable,f=focused,s=selected,k=checked)")
            for (i in 0 until elements.length()) {
                elementsWithHeader.put(elements.getString(i))
            }
            
            result.put("elements", elementsWithHeader)
            result.put("element_count", elements.length())
            
            val resultStr = result.toString()
            Log.i(TAG, "A11y tree cleaned: ${rawJson.length} -> ${resultStr.length} bytes, ${elements.length()} elements")
            
            resultStr
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning A11y tree", e)
            JSONObject().apply {
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }
    
    private fun extractPhoneState(root: JSONObject): JSONObject {
        val phoneState = root.optJSONObject("phone_state") ?: return JSONObject()
        
        return JSONObject().apply {
            put("app", phoneState.optString("currentApp", ""))
            put("package", phoneState.optString("packageName", ""))
            put("activity", phoneState.optString("activityName", ""))
            put("keyboard_visible", phoneState.optBoolean("keyboardVisible", false))
            put("is_editable", phoneState.optBoolean("isEditable", false))
            
            val focused = phoneState.optJSONObject("focusedElement")
            if (focused != null) {
                val focusInfo = JSONObject()
                val className = focused.optString("className", "")
                val resourceId = focused.optString("resourceId", "")
                if (className.isNotEmpty()) focusInfo.put("class", className)
                // 保留完整的 resourceId，方便控件查找
                if (resourceId.isNotEmpty()) focusInfo.put("id", resourceId)
                if (focusInfo.length() > 0) put("focused", focusInfo)
            }
        }
    }
    
    private fun extractScreenSize(root: JSONObject): JSONObject {
        val deviceContext = root.optJSONObject("device_context")
        if (deviceContext != null) {
            val screenBounds = deviceContext.optJSONObject("screen_bounds")
            if (screenBounds != null) {
                return JSONObject().apply {
                    put("width", screenBounds.optInt("width", 0))
                    put("height", screenBounds.optInt("height", 0))
                }
            }
        }
        return JSONObject()
    }
    
    /**
     * 提取可交互元素 - 紧凑字符串格式
     * 格式: text|x,y,w,h|id|type|flags
     * flags: c=clickable, l=long_clickable, e=editable, f=focused, s=selected, k=checked
     */
    private fun extractInteractiveElementsCompact(root: JSONObject): JSONArray {
        val result = JSONArray()
        val a11yTree = root.optJSONObject("a11y_tree")
        
        if (a11yTree != null) {
            collectElementsCompact(a11yTree, result)
        }
        
        return result
    }
    
    private fun collectElementsCompact(node: JSONObject, result: JSONArray) {
        if (result.length() >= MAX_ELEMENTS) return
        
        if (shouldKeepNode(node)) {
            val parts = mutableListOf<String>()
            
            // 1. 文本 (可能为空)
            val text = node.optString("text", "")
            val contentDesc = node.optString("contentDescription", "")
            val displayText = when {
                text.isNotEmpty() -> truncateText(text, MAX_TEXT_LENGTH)
                contentDesc.isNotEmpty() -> truncateText(contentDesc, MAX_TEXT_LENGTH)
                else -> ""
            }
            parts.add(displayText)
            
            // 2. 坐标 x,y,w,h
            val boundsInScreen = node.optJSONObject("boundsInScreen")
            if (boundsInScreen != null) {
                val x = boundsInScreen.optInt("left", 0)
                val y = boundsInScreen.optInt("top", 0)
                val w = boundsInScreen.optInt("right", 0) - x
                val h = boundsInScreen.optInt("bottom", 0) - y
                parts.add("$x,$y,$w,$h")
            } else {
                parts.add("")
            }
            
            // 3. resourceId (保留完整ID，包含包名和前缀)
            val resourceId = node.optString("resourceId", "")
            parts.add(resourceId)
            
            // 4. 类型 (去掉包名前缀)
            val className = node.optString("className", "")
            parts.add(if (className.isNotEmpty()) className.substringAfterLast('.') else "")
            
            // 5. 标志位 (单字母编码)
            val flags = buildString {
                if (node.optBoolean("isClickable", false)) append('c')
                if (node.optBoolean("isLongClickable", false)) append('l')
                if (node.optBoolean("isEditable", false)) append('e')
                if (node.optBoolean("isFocused", false)) append('f')
                if (node.optBoolean("isSelected", false)) append('s')
                if (node.optBoolean("isChecked", false)) append('k')
            }
            parts.add(flags)
            
            // 用 | 分隔各部分
            result.put(parts.joinToString("|"))
        }
        
        // 递归子节点
        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                if (result.length() >= MAX_ELEMENTS) break
                collectElementsCompact(children.getJSONObject(i), result)
            }
        }
    }
    
    private fun shouldKeepNode(node: JSONObject): Boolean {
        val text = node.optString("text", "")
        val contentDesc = node.optString("contentDescription", "")
        val resourceId = node.optString("resourceId", "")
        val clickable = node.optBoolean("isClickable", false)
        val focusable = node.optBoolean("isFocusable", false)
        val checkable = node.optBoolean("isCheckable", false)
        val editable = node.optBoolean("isEditable", false)
        
        // 有意义文本
        if (isMeaningfulText(text)) return true
        
        // 有 content-desc
        if (contentDesc.isNotEmpty()) return true
        
        // 有 resourceId
        if (resourceId.isNotEmpty()) return true
        
        // 可交互
        if (clickable || focusable || checkable || editable) return true
        
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