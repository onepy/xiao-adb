package com.droidrun.portal.mcp.tools

import android.util.Log
import com.droidrun.portal.api.ApiHandler
import com.droidrun.portal.api.ApiResponse
import com.droidrun.portal.core.A11yTreeCleaner
import com.droidrun.portal.mcp.McpTool
import org.json.JSONObject

/**
 * 基于控件的操作工具集合
 * 这些工具通过无障碍服务直接操作UI控件,比坐标操作更稳定可靠
 */

/**
 * 获取精简的屏幕XML状态
 * 在操作完成后自动调用,提供实时的屏幕状态
 */
private fun getSimplifiedScreenXmlForElement(apiHandler: ApiHandler): JSONObject? {
    return try {
        when (val response = apiHandler.getStateFull(true)) {
            is ApiResponse.Success -> {
                val cleanedData = A11yTreeCleaner.cleanA11yTree(response.data as String)
                JSONObject(cleanedData)
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.e("ScreenXml", "Error getting simplified XML", e)
        null
    }
}

/**
 * 控件查找辅助类
 * 提供通用的控件查找逻辑
 */
object ElementFinder {
    private const val TAG = "ElementFinder"
    
    /**
     * 根据resourceId查找控件
     * 支持智能补全：自动尝试补全包名前缀
     * 注意: 调用者负责回收返回的节点
     */
    fun findByResourceId(resourceId: String): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "AccessibilityService not available")
            return null
        }
        
        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Root node not available")
            return null
        }
        
        // 智能补全逻辑：尝试多种形式的 resourceId
        val idsToTry = mutableListOf<String>()
        
        // 1. 使用原始 resourceId
        idsToTry.add(resourceId)
        
        // 2. 如果不包含包名（没有冒号），尝试从当前应用包名补全
        if (!resourceId.contains(":")) {
            val currentPackage = service.rootInActiveWindow?.packageName?.toString()
            if (!currentPackage.isNullOrEmpty()) {
                // 尝试补全为: package:id/name
                idsToTry.add("$currentPackage:id/$resourceId")
            }
        }
        
        // 3. 如果只有短名称（没有 id/ 前缀），尝试补全
        if (!resourceId.contains("/") && !resourceId.contains(":")) {
            val currentPackage = service.rootInActiveWindow?.packageName?.toString()
            if (!currentPackage.isNullOrEmpty()) {
                idsToTry.add("$currentPackage:id/$resourceId")
            }
        }
        
        // 尝试每种形式
        for (idToTry in idsToTry) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(idToTry)
            if (nodes.isNotEmpty()) {
                // 返回第一个匹配的节点
                val result = nodes[0]
                // 回收其他不需要的节点
                for (i in 1 until nodes.size) {
                    nodes[i].recycle()
                }
                Log.i(TAG, "Found element by resourceId: $idToTry (original: $resourceId), bounds: ${getNodeBounds(result)}")
                return result
            }
        }
        
        // 都没找到才回收rootNode
        Log.w(TAG, "Element not found with resourceId: $resourceId (tried: $idsToTry)")
        rootNode.recycle()
        return null
    }
    
    private fun getNodeBounds(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return "(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom})"
    }
    
    /**
     * 根据文本查找控件
     * 注意: 调用者负责回收返回的节点
     */
    fun findByText(text: String, exact: Boolean = false): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "AccessibilityService not available")
            return null
        }
        
        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Root node not available")
            return null
        }
        
        // 不要在finally中回收rootNode!
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        if (nodes.isEmpty()) {
            rootNode.recycle()
            return null
        }
        
        val result = if (exact) {
            // 查找精确匹配的节点
            val exactMatch = nodes.find { it.text?.toString() == text }
            // 回收不匹配的节点
            nodes.forEach { if (it != exactMatch) it.recycle() }
            exactMatch
        } else {
            // 返回第一个匹配的节点
            val first = nodes[0]
            // 回收其他节点
            for (i in 1 until nodes.size) {
                nodes[i].recycle()
            }
            first
        }
        
        if (result != null) {
            Log.i(TAG, "Found element by text: $text, bounds: ${getNodeBounds(result)}")
        } else {
            rootNode.recycle()
        }
        
        return result
    }
    
    /**
     * 根据contentDescription查找控件
     * 注意: 调用者负责回收返回的节点
     */
    fun findByContentDescription(desc: String): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "AccessibilityService not available")
            return null
        }
        
        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Root node not available")
            return null
        }
        
        // 递归查找,不回收rootNode直到确定是否找到
        val found = findNodeByContentDesc(rootNode, desc)
        if (found != null) {
            Log.i(TAG, "Found element by contentDescription: $desc, bounds: ${getNodeBounds(found)}")
            return found
        }
        
        // 没找到才回收rootNode
        rootNode.recycle()
        return null
    }
    
    /**
     * 递归查找具有指定contentDescription的节点
     * 注意: 不要在这里回收节点!由调用者负责
     */
    private fun findNodeByContentDesc(
        node: android.view.accessibility.AccessibilityNodeInfo,
        desc: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 检查当前节点
        if (node.contentDescription?.toString() == desc) {
            return node
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            
            val found = findNodeByContentDesc(child, desc)
            if (found != null) {
                // 找到了!不回收child因为found可能就是child
                return found
            }
            // 没找到,回收这个子节点
            child.recycle()
        }
        
        return null
    }
    
    /**
     * 根据className查找控件
     * 注意: 调用者负责回收返回的节点
     */
    fun findByClassName(className: String): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "AccessibilityService not available")
            return null
        }
        
        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Root node not available")
            return null
        }
        
        val found = findNodeByClassName(rootNode, className)
        if (found != null) {
            Log.i(TAG, "Found element by className: $className, bounds: ${getNodeBounds(found)}")
            return found
        }
        
        // 没找到才回收rootNode
        rootNode.recycle()
        return null
    }
    
    /**
     * 递归查找具有指定className的节点
     * 注意: 不要在这里回收节点!由调用者负责
     */
    private fun findNodeByClassName(
        node: android.view.accessibility.AccessibilityNodeInfo,
        className: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 检查当前节点
        if (node.className?.toString() == className) {
            return node
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            
            val found = findNodeByClassName(child, className)
            if (found != null) {
                // 找到了!不回收child因为found可能就是child
                return found
            }
            // 没找到,回收这个子节点
            child.recycle()
        }
        
        return null
    }
    
    /**
     * 通用查找方法 - 支持多种查找策略
     * 注意: 调用者负责回收返回的节点
     */
    fun find(
        resourceId: String? = null,
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 优先使用resourceId(最准确)
        if (!resourceId.isNullOrEmpty()) {
            val node = findByResourceId(resourceId)
            if (node != null) {
                Log.i(TAG, "Found element by resourceId: $resourceId")
                return node
            }
        }
        
        // 其次使用text
        if (!text.isNullOrEmpty()) {
            val node = findByText(text)
            if (node != null) {
                Log.i(TAG, "Found element by text: $text")
                return node
            }
        }
        
        // 然后使用contentDescription
        if (!contentDescription.isNullOrEmpty()) {
            val node = findByContentDescription(contentDescription)
            if (node != null) {
                Log.i(TAG, "Found element by contentDescription: $contentDescription")
                return node
            }
        }
        
        // 最后使用className
        if (!className.isNullOrEmpty()) {
            val node = findByClassName(className)
            if (node != null) {
                Log.i(TAG, "Found element by className: $className")
                return node
            }
        }
        
        Log.w(TAG, "Element not found with any search criteria")
        return null
    }
}

// 1. FindElementTool - 查找控件
class FindElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "FindElementTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id (例如: 'com.example.app:id/button')")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名 (例如: 'android.widget.Button')")
                    })
                })
            }
            
            return McpTool(
                name = "android.element.find",
                description = """
                    查找屏幕上的控件元素。
                    此工具通过控件的属性(resourceId、text、contentDescription、className)来定位控件。
                    
                    **何时使用:**
                    - 需要获取控件的详细信息(位置、状态等)时
                    - 需要验证某个控件是否存在时
                    - 作为其他控件操作的前置步骤
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id。这是最准确的定位方式。
                    - `text` (string, 可选): 控件显示的文本内容。
                    - `content_description` (string, 可选): 控件的无障碍描述。
                    - `class_name` (string, 可选): 控件的类名。
                    
                    **优先级:** resource_id > text > content_description > class_name
                    
                    **返回:**
                    - 找到控件: 返回控件的详细信息(bounds、text、className等)
                    - 未找到: 返回错误信息
                    
                    **如何使用:**
                    至少提供一个查找参数。建议优先使用resource_id作为定位方式。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            
            // 至少需要一个查找条件
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Finding element with: resourceId=$resourceId, text=$text, " +
                     "contentDescription=$contentDescription, className=$className")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    
                    JSONObject().apply {
                        put("success", true)
                        put("element", JSONObject().apply {
                            put("resource_id", node.viewIdResourceName ?: "")
                            put("text", node.text?.toString() ?: "")
                            put("content_description", node.contentDescription?.toString() ?: "")
                            put("class_name", node.className?.toString() ?: "")
                            put("bounds", JSONObject().apply {
                                put("left", bounds.left)
                                put("top", bounds.top)
                                put("right", bounds.right)
                                put("bottom", bounds.bottom)
                                put("centerX", bounds.centerX())
                                put("centerY", bounds.centerY())
                            })
                            put("clickable", node.isClickable)
                            put("scrollable", node.isScrollable)
                            put("checkable", node.isCheckable)
                            put("checked", node.isChecked)
                            put("enabled", node.isEnabled)
                            put("focusable", node.isFocusable)
                            put("focused", node.isFocused)
                        })
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 2. ClickElementTool - 点击控件  
class ClickElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "ClickElementTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名")
                    })
                })
            }
            
            return McpTool(
                name = "android.element.click",
                description = """
                    通过控件属性定位并点击控件。
                    此工具会查找匹配的控件并执行点击操作,比坐标点击更稳定可靠。
                    
                    **何时使用:**
                    - 需要点击具有特定ID或文本的按钮时
                    - 界面布局可能变化,但控件ID稳定时
                    - 需要更精确的控件操作而不是坐标点击时
                    
                    **优势:**
                    - 不依赖屏幕坐标,对UI变化更精准
                    - 直接操作控件对象,更精确
                    - 自动处理控件的可点击状态检查
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id
                    - `text` (string, 可选): 控件显示的文本
                    - `content_description` (string, 可选): 控件的无障碍描述
                    - `class_name` (string, 可选): 控件的类名
                    
                    至少提供一个查找参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Clicking element with: resourceId=$resourceId, text=$text")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    if (!node.isClickable && !node.isEnabled) {
                        return JSONObject().apply {
                            put("success", false)
                            put("error", "Element is not clickable or enabled")
                        }
                    }
                    
                    val success = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    
                    if (success) {
                        Thread.sleep(400)
                    }
                    val screenXml = if (success) getSimplifiedScreenXmlForElement(apiHandler) else null
                    
                    JSONObject().apply {
                        put("success", success)
                        if (success) {
                            put("message", "Element clicked successfully")
                            if (screenXml != null) {
                                put("screen_state", screenXml)
                            }
                        } else {
                            put("error", "Failed to perform click action")
                        }
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking element", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 3. ScrollElementTool - 滚动控件
class ScrollElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "ScrollElementTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名")
                    })
                    put("direction", JSONObject().apply {
                        put("type", "string")
                        put("enum", org.json.JSONArray().apply {
                            put("forward")
                            put("backward")
                        })
                        put("description", "滚动方向: 'forward'(向下/向右) 或 'backward'(向上/向左)")
                        put("default", "forward")
                    })
                })
            }
            
            return McpTool(
                name = "android.element.scroll",
                description = """
                    滚动可滚动控件(如列表、滚动视图)。
                    此工具通过控件属性定位可滚动元素并执行滚动操作。
                    
                    **何时使用:**
                    - 需要在列表或滚动视图中滚动内容时
                    - 需要查看屏幕外的内容时
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id
                    - `text` (string, 可选): 控件显示的文本
                    - `content_description` (string, 可选): 控件的无障碍描述
                    - `class_name` (string, 可选): 控件的类名
                    - `direction` (string, 可选, 默认: 'forward'): 滚动方向
                      - 'forward': 向下或向右滚动
                      - 'backward': 向上或向左滚动
                    
                    至少提供一个查找参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            val direction = arguments?.optString("direction", "forward") ?: "forward"
            
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Scrolling element: direction=$direction")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    if (!node.isScrollable) {
                        return JSONObject().apply {
                            put("success", false)
                            put("error", "Element is not scrollable")
                        }
                    }
                    
                    val action = when (direction) {
                        "forward" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        "backward" -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        else -> android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    }
                    
                    val success = node.performAction(action)
                    
                    if (success) {
                        Thread.sleep(500)
                    }
                    val screenXml = if (success) getSimplifiedScreenXmlForElement(apiHandler) else null
                    
                    JSONObject().apply {
                        put("success", success)
                        if (success) {
                            put("message", "Element scrolled $direction successfully")
                            if (screenXml != null) {
                                put("screen_state", screenXml)
                            }
                        } else {
                            put("error", "Failed to scroll element")
                        }
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scrolling element", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 4. LongPressElementTool - 长按控件
class LongPressElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "LongPressElementTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名")
                    })
                })
            }
            
            return McpTool(
                name = "android.element.long_press",
                description = """
                    长按控件以触发上下文菜单或特殊操作。
                    此工具通过控件属性定位元素并执行长按操作。
                    
                    **何时使用:**
                    - 需要触发控件的长按菜单时
                    - 需要执行长按特定操作时(如选择文本、拖动等)
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id
                    - `text` (string, 可选): 控件显示的文本
                    - `content_description` (string, 可选): 控件的无障碍描述
                    - `class_name` (string, 可选): 控件的类名
                    
                    至少提供一个查找参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Long pressing element")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    val success = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    
                    if (success) {
                        Thread.sleep(400)
                    }
                    val screenXml = if (success) getSimplifiedScreenXmlForElement(apiHandler) else null
                    
                    JSONObject().apply {
                        put("success", success)
                        if (success) {
                            put("message", "Element long pressed successfully")
                            if (screenXml != null) {
                                put("screen_state", screenXml)
                            }
                        } else {
                            put("error", "Failed to long press element")
                        }
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error long pressing element", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 5. SetTextTool - 设置控件文本
class SetTextTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "SetTextTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容(用于查找)")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名")
                    })
                    put("new_text", JSONObject().apply {
                        put("type", "string")
                        put("description", "要设置的新文本内容")
                    })
                })
                put("required", org.json.JSONArray().put("new_text"))
            }
            
            return McpTool(
                name = "android.element.set_text",
                description = """
                    直接设置可编辑控件的文本内容。
                    此工具通过控件属性定位输入框并设置文本,比输入法更直接。
                    
                    **何时使用:**
                    - 需要在输入框中设置文本时
                    - 需要快速填充表单字段时
                    
                    **优势:**
                    - 直接设置文本,不依赖输入法
                    - 速度更快
                    - 支持特殊字符
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id
                    - `text` (string, 可选): 控件显示的文本(用于查找)
                    - `content_description` (string, 可选): 控件的无障碍描述
                    - `class_name` (string, 可选): 控件的类名
                    - `new_text` (string, 必需): 要设置的新文本内容
                    
                    至少提供一个查找参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            val newText = arguments?.getString("new_text")
                ?: throw IllegalArgumentException("Missing new_text parameter")
            
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Setting text: $newText")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    if (!node.isEditable) {
                        return JSONObject().apply {
                            put("success", false)
                            put("error", "Element is not editable")
                        }
                    }
                    
                    val arguments = android.os.Bundle().apply {
                        putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    }
                    
                    val success = node.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                        arguments
                    )
                    
                    if (success) {
                        Thread.sleep(300)
                    }
                    val screenXml = if (success) getSimplifiedScreenXmlForElement(apiHandler) else null
                    
                    JSONObject().apply {
                        put("success", success)
                        if (success) {
                            put("message", "Text set successfully")
                            if (screenXml != null) {
                                put("screen_state", screenXml)
                            }
                        } else {
                            put("error", "Failed to set text")
                        }
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting text", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 6. DoubleTapElementTool - 双击控件
class DoubleTapElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "DoubleTapElementTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名")
                    })
                })
            }
            
            return McpTool(
                name = "android.element.double_tap",
                description = """
                    双击控件以触发特殊操作。
                    此工具通过控件属性定位元素并执行双击操作。
                    
                    **何时使用:**
                    - 需要触发控件的双击事件时
                    - 某些应用需要双击才能执行操作时
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id
                    - `text` (string, 可选): 控件显示的文本
                    - `content_description` (string, 可选): 控件的无障碍描述
                    - `class_name` (string, 可选): 控件的类名
                    
                    至少提供一个查找参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Double tapping element")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    // 执行两次点击操作来模拟双击
                    val success1 = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    Thread.sleep(100) // 短暂延迟
                    val success2 = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    
                    val success = success1 && success2
                    
                    if (success) {
                        Thread.sleep(400)
                    }
                    val screenXml = if (success) getSimplifiedScreenXmlForElement(apiHandler) else null
                    
                    JSONObject().apply {
                        put("success", success)
                        if (success) {
                            put("message", "Element double tapped successfully")
                            if (screenXml != null) {
                                put("screen_state", screenXml)
                            }
                        } else {
                            put("error", "Failed to double tap element")
                        }
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error double tapping element", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 8. DragElementTool - 拖动控件
class DragElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "DragElementTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("resource_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的resource-id")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件包含的文本内容")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的content-description")
                    })
                    put("class_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "控件的类名")
                    })
                    put("target_x", JSONObject().apply {
                        put("type", "integer")
                        put("description", "拖动目标位置的X坐标")
                    })
                    put("target_y", JSONObject().apply {
                        put("type", "integer")
                        put("description", "拖动目标位置的Y坐标")
                    })
                })
                put("required", org.json.JSONArray().apply {
                    put("target_x")
                    put("target_y")
                })
            }
            
            return McpTool(
                name = "android.element.drag",
                description = """
                    拖动控件到指定位置。
                    此工具通过控件属性定位元素,然后将其拖动到目标坐标。
                    
                    **何时使用:**
                    - 需要拖放控件时
                    - 需要重新排列可拖动元素时
                    - 需要将元素拖到特定位置时
                    
                    **参数:**
                    - `resource_id` (string, 可选): 控件的resource-id
                    - `text` (string, 可选): 控件显示的文本
                    - `content_description` (string, 可选): 控件的无障碍描述
                    - `class_name` (string, 可选): 控件的类名
                    - `target_x` (integer, 必需): 拖动目标位置的X坐标
                    - `target_y` (integer, 必需): 拖动目标位置的Y坐标
                    
                    至少提供一个查找参数,以及目标坐标。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val resourceId = arguments?.optString("resource_id", null)
            val text = arguments?.optString("text", null)
            val contentDescription = arguments?.optString("content_description", null)
            val className = arguments?.optString("class_name", null)
            val targetX = arguments?.getInt("target_x")
                ?: throw IllegalArgumentException("Missing target_x parameter")
            val targetY = arguments?.getInt("target_y")
                ?: throw IllegalArgumentException("Missing target_y parameter")
            
            if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() &&
                contentDescription.isNullOrEmpty() && className.isNullOrEmpty()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "At least one search parameter is required")
                }
            }
            
            Log.i(TAG, "Dragging element to ($targetX, $targetY)")
            
            val node = ElementFinder.find(resourceId, text, contentDescription, className)
            
            if (node != null) {
                try {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    
                    // 使用ApiHandler执行拖动操作(从控件中心到目标位置)
                    val startX = bounds.centerX()
                    val startY = bounds.centerY()
                    
                    when (val response = apiHandler.performSwipe(startX, startY, targetX, targetY, 500)) {
                        is com.droidrun.portal.api.ApiResponse.Success -> {
                            Thread.sleep(500)
                            val screenXml = getSimplifiedScreenXmlForElement(apiHandler)
                            
                            JSONObject().apply {
                                put("success", true)
                                put("message", "Element dragged successfully from ($startX,$startY) to ($targetX,$targetY)")
                                if (screenXml != null) {
                                    put("screen_state", screenXml)
                                }
                            }
                        }
                        is com.droidrun.portal.api.ApiResponse.Error -> {
                            JSONObject().apply {
                                put("success", false)
                                put("error", response.message)
                            }
                        }
                        else -> {
                            JSONObject().apply {
                                put("success", false)
                                put("error", "Unexpected response type")
                            }
                        }
                    }
                } finally {
                    node.recycle()
                }
            } else {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Element not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dragging element", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}