package com.droidrun.portal.mcp.tools

import android.content.Context
import android.util.Base64
import android.util.Log
import com.droidrun.portal.api.ApiHandler
import com.droidrun.portal.api.ApiResponse
import com.droidrun.portal.core.A11yTreeCleaner
import com.droidrun.portal.mcp.McpTool
import org.json.JSONObject

/**
 * Collection of Android operation MCP tools
 * These tools expose existing Android functionality via MCP protocol
 */

// 1. GetStateTool - 获取屏幕XML数据
class GetStateTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "GetStateTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("filter", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Whether to filter out off-screen elements (default: true)")
                        put("default", true)
                    })
                })
            }
            
            return McpTool(
                name = "android.screen.dump",
                description = """
                    快速获取当前屏幕的可交互元素列表（文本、按钮、输入框等的坐标和ID）。
                    
                    **重要:** 此工具仅返回元素的结构化数据，不包含视觉内容。无法理解界面的实际内容和布局。
                    
                    **使用场景:**
                    - 在已知界面内容后，快速定位可交互元素的坐标
                    - 重复查找特定元素进行操作（如循环点击）
                    - 获取元素的resource-id等属性用于精确定位
                    
                    **不适用场景:**
                    - 初次了解界面内容 → 请先使用截图工具(screenshot)识别界面
                    - 理解界面布局和视觉设计 → 请使用截图工具
                    
                    **典型工作流:**
                    1. 使用截图工具了解界面内容
                    2. 使用本工具获取元素坐标和ID
                    3. 执行点击、输入等操作
                    
                    **参数:**
                    - `filter` (boolean, 可选, 默认: `true`):
                      - `true`: 仅返回屏幕可见元素
                      - `false`: 返回所有元素（包括屏幕外）
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val filter = arguments?.optBoolean("filter", true) ?: true
            
            Log.i(TAG, "Getting state with filter=$filter")
            
            when (val response = apiHandler.getStateFull(filter)) {
                is ApiResponse.Success -> {
                    // 启用数据精简,返回结构化JSON
                    val cleanedData = A11yTreeCleaner.cleanA11yTree(response.data as String)
                    
                    JSONObject().apply {
                        put("success", true)
                        put("data", JSONObject(cleanedData))
                    }
                }
                is ApiResponse.Error -> {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error getting state", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 2. GetPackagesTool - 获取已安装应用包名
class GetPackagesTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "GetPackagesTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("filter", JSONObject().apply {
                        put("type", "string")
                        put("enum", org.json.JSONArray().apply {
                            put("user")
                            put("system")
                        })
                        put("description", "过滤类型: 'user'(仅用户应用,默认) 或 'system'(仅系统应用)")
                        put("default", "user")
                    })
                })
            }
            
            return McpTool(
                name = "android.packages.list",
                description = """
                    获取 Android 设备上已安装的应用列表。
                    此工具返回一个精简的应用信息列表，包含每个应用的包名和应用名称。
                    
                    **何时使用:**
                    - 需要查找特定应用的包名以进行启动、卸载等操作时。
                    - 需要获取设备上所有用户应用或系统应用的列表时。
                    
                    **参数:**
                    - `filter` (string, 可选, 默认: `user`):
                      - `user`: 仅返回用户安装的应用。
                      - `system`: 仅返回系统应用。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val filterType = arguments?.optString("type", "user") ?: "user"
            
            Log.i(TAG, "Getting packages with filter=$filterType")
            
            when (val response = apiHandler.getPackages()) {
                is ApiResponse.Raw -> {
                    val packages = response.json.getJSONArray("packages")
                    
                    // Filter based on type and create simplified package strings
                    val filteredPackages = org.json.JSONArray()
                    for (i in 0 until packages.length()) {
                        val pkg = packages.getJSONObject(i)
                        val isSystem = pkg.getBoolean("isSystemApp")
                        
                        // Apply filter
                        val shouldInclude = when (filterType) {
                            "user" -> !isSystem
                            "system" -> isSystem
                            else -> true
                        }
                        
                        if (shouldInclude) {
                            // Create simplified format: "packageName|label"
                            val packageName = pkg.getString("packageName")
                            val label = pkg.getString("label")
                            val simplifiedFormat = "$packageName|$label"
                            filteredPackages.put(simplifiedFormat)
                        }
                    }
                    
                    JSONObject().apply {
                        put("success", true)
                        put("count", filteredPackages.length())
                        put("packages", filteredPackages)
                    }
                }
                is ApiResponse.Error -> {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error getting packages", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 3. LaunchAppTool - 启动应用
class LaunchAppTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "LaunchAppTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("package", JSONObject().apply {
                        put("type", "string")
                        put("description", "Package name of the app to launch (e.g., 'com.android.settings')")
                    })
                    put("activity", JSONObject().apply {
                        put("type", "string")
                        put("description", "Optional: Specific activity to launch")
                    })
                })
                put("required", org.json.JSONArray().put("package"))
            }
            
            return McpTool(
                name = "android.launch_app",
                description = """
                    启动指定的 Android 应用。
                    此工具允许通过应用的包名启动应用，也可以选择指定一个特定的 Activity。
                    
                    **何时使用:**
                    - 需要打开某个应用时。
                    - 需要从当前应用切换到另一个应用时。
                    
                    **参数:**
                    - `package` (string, 必需): 要启动应用的包名 (例如: `com.android.settings`, `com.taobao.taobao`)。
                    - `activity` (string, 可选): 要启动的特定 Activity 的完整类名 (例如: `com.android.settings.Settings`)。如果未提供，将启动应用的默认 Activity。
                    
                    **如何使用:**
                    调用此工具并提供 `package` 参数。如果需要启动应用的特定界面，可以同时提供 `activity` 参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val packageName = arguments?.getString("package")
                ?: throw IllegalArgumentException("Missing package parameter")
            val activity = arguments.optString("activity", null)
            
            Log.i(TAG, "Launching app: $packageName, activity: $activity")
            
            when (val response = apiHandler.startApp(packageName, activity)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认应用是否成功启动并显示正确界面")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 4. InputTextTool - 文本输入(自动Base64转换)
class InputTextTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "InputTextTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "Text to input (will be automatically Base64 encoded)")
                    })
                    put("clear", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Clear existing text before input (default: true)")
                        put("default", true)
                    })
                })
                put("required", org.json.JSONArray().put("text"))
            }
            
            return McpTool(
                name = "android.text.input",
                description = """
                    在当前聚焦的输入框中输入文本。
                    此工具会自动对输入的文本进行 Base64 编码，以确保特殊字符的正确传输。
                    
                    **何时使用:**
                    - 需要在文本输入框中输入文字、数字或符号时。
                    - 需要填充表单字段或搜索框时。
                    
                    **参数:**
                    - `text` (string, 必需): 要输入的文本内容。
                    - `clear` (boolean, 可选, 默认: `true`):
                      - `true`: 在输入新文本之前，清除输入框中已有的文本。
                      - `false`: 在现有文本的末尾追加新文本。
                      
                    **如何使用:**
                    调用此工具并提供 `text` 参数。如果需要先清空输入框，可以保持 `clear` 为默认值 `true`。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val text = arguments?.getString("text")
                ?: throw IllegalArgumentException("Missing text parameter")
            val clear = arguments.optBoolean("clear", true)
            
            // Convert text to Base64
            val base64Text = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            
            Log.i(TAG, "Inputting text (length: ${text.length}), clear=$clear")
            
            when (val response = apiHandler.keyboardInput(base64Text, clear)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认文本是否成功输入")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inputting text", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 5. ClearTextTool - 清除文本
class ClearTextTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "ClearTextTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
            
            return McpTool(
                name = "android.input.clear",
                description = """
                    清除 Android 设备上当前聚焦的输入框中的所有文本。
                    
                    **何时使用:**
                    - 需要清空输入框内容，例如在输入新文本之前。
                    - 需要撤销之前的文本输入操作时。
                    
                    **参数:**
                    此工具不需要任何参数。
                    
                    **如何使用:**
                    直接调用此工具即可清除当前聚焦的输入框内容。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            Log.i(TAG, "Clearing text")
            
            when (val response = apiHandler.keyboardClear()) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认文本是否已清除")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing text", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 6. PressKeyTool - 按键模拟
class PressKeyTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "PressKeyTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("key_code", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Android KeyEvent key code (e.g., 66=ENTER, 67=BACKSPACE, 4=BACK)")
                    })
                })
                put("required", org.json.JSONArray().put("key_code"))
            }
            
            return McpTool(
                name = "android.key.send",
                description = """
                    模拟发送 Android 设备的物理按键事件。
                    此工具允许通过 Android KeyEvent 的键码来模拟按下各种按键，例如返回键、Home 键、音量键等。
                    
                    **何时使用:**
                    - 需要模拟用户按下物理按键时。
                    - 需要执行系统级别的操作，如返回、回到主页、调整音量等。
                    
                    **参数:**
                    - `key_code` (integer, 必需): Android KeyEvent 的键码。
                      
                    **常用键码参考:**
                    - `3`: HOME (主页键)
                    - `4`: BACK (返回键)
                    - `24`: VOLUME_UP (音量+)
                    - `25`: VOLUME_DOWN (音量-)
                    - `26`: POWER (电源键)
                    - `66`: ENTER (回车键)
                    - `67`: DEL (删除键)
                    - `82`: MENU (菜单键)
                    - `187`: APP_SWITCH (最近应用键)
                    
                    **如何使用:**
                    调用此工具并提供相应的 `key_code`。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val keyCode = arguments?.getInt("key_code")
                ?: throw IllegalArgumentException("Missing key_code parameter")
            
            Log.i(TAG, "Pressing key: $keyCode")
            
            when (val response = apiHandler.keyboardKey(keyCode)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认按键操作是否生效")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing key", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 7. TapTool - 单击
class TapTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "TapTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().apply {
                        put("type", "integer")
                        put("description", "X coordinate in pixels")
                    })
                    put("y", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Y coordinate in pixels")
                    })
                })
                put("required", org.json.JSONArray().apply {
                    put("x")
                    put("y")
                })
            }
            
            return McpTool(
                name = "android.tap",
                description = """
                    在 Android 设备的屏幕上执行单击操作。
                    此工具允许通过提供屏幕的 X 和 Y 坐标来模拟用户的点击行为。
                    
                    **何时使用:**
                    - 需要点击屏幕上的按钮、链接、图标或任何可交互元素时。
                    - 需要确认或选择某个选项时。
                    
                    **参数:**
                    - `x` (integer, 必需): 点击位置的 X 坐标 (像素)。
                    - `y` (integer, 必需): 点击位置的 Y 坐标 (像素)。
                    
                    **如何使用:**
                    调用此工具并提供要点击位置的 `x` 和 `y` 坐标。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val x = arguments?.getInt("x") ?: throw IllegalArgumentException("Missing x parameter")
            val y = arguments?.getInt("y") ?: throw IllegalArgumentException("Missing y parameter")
            
            Log.i(TAG, "Tapping at ($x, $y)")
            
            when (val response = apiHandler.performTap(x, y)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认点击操作是否生效")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 8. DoubleTapTool - 双击
class DoubleTapTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "DoubleTapTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().apply {
                        put("type", "integer")
                        put("description", "X coordinate in pixels")
                    })
                    put("y", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Y coordinate in pixels")
                    })
                })
                put("required", org.json.JSONArray().apply {
                    put("x")
                    put("y")
                })
            }
            
            return McpTool(
                name = "android.double_tap",
                description = """
                    在 Android 设备的屏幕上执行双击操作。
                    此工具允许通过提供屏幕的 X 和 Y 坐标来模拟用户的双击行为。
                    
                    **何时使用:**
                    - 需要双击屏幕上的元素以触发特定功能时。
                    - 某些应用或界面需要双击才能进行操作时。
                    
                    **参数:**
                    - `x` (integer, 必需): 双击位置的 X 坐标 (像素)。
                    - `y` (integer, 必需): 双击位置的 Y 坐标 (像素)。
                    
                    **如何使用:**
                    调用此工具并提供要双击位置的 `x` 和 `y` 坐标。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val x = arguments?.getInt("x") ?: throw IllegalArgumentException("Missing x parameter")
            val y = arguments?.getInt("y") ?: throw IllegalArgumentException("Missing y parameter")
            
            Log.i(TAG, "Double tapping at ($x, $y)")
            
            when (val response = apiHandler.performDoubleTap(x, y)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认双击操作是否生效")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error double tapping", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 9. LongPressTool - 长按
class LongPressTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "LongPressTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().apply {
                        put("type", "integer")
                        put("description", "X coordinate in pixels")
                    })
                    put("y", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Y coordinate in pixels")
                    })
                    put("duration", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Hold duration in milliseconds (default: 1000)")
                        put("default", 1000)
                    })
                })
                put("required", org.json.JSONArray().apply {
                    put("x")
                    put("y")
                })
            }
            
            return McpTool(
                name = "android.long_press",
                description = """
                    在 Android 设备的屏幕上执行长按操作。
                    此工具允许通过提供屏幕的 X 和 Y 坐标以及长按时长来模拟用户的长按行为。
                    
                    **何时使用:**
                    - 需要长按屏幕上的元素以触发上下文菜单、拖动操作或其他长按特定功能时。
                    
                    **参数:**
                    - `x` (integer, 必需): 长按位置的 X 坐标 (像素)。
                    - `y` (integer, 必需): 长按位置的 Y 坐标 (像素)。
                    - `duration` (integer, 可选, 默认: `1000`): 长按持续时间 (毫秒)。
                    
                    **如何使用:**
                    调用此工具并提供要长按位置的 `x` 和 `y` 坐标。如果需要自定义长按时间，可以提供 `duration` 参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val x = arguments?.getInt("x") ?: throw IllegalArgumentException("Missing x parameter")
            val y = arguments?.getInt("y") ?: throw IllegalArgumentException("Missing y parameter")
            val duration = arguments?.optLong("duration", 1000L) ?: 1000L
            
            Log.i(TAG, "Long pressing at ($x, $y) for ${duration}ms")
            
            when (val response = apiHandler.performLongPress(x, y, duration)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认长按操作是否生效")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error long pressing", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 10. SwipeTool - 滑动
class SwipeTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "SwipeTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("startX", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Starting X coordinate in pixels")
                    })
                    put("startY", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Starting Y coordinate in pixels")
                    })
                    put("endX", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Ending X coordinate in pixels")
                    })
                    put("endY", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Ending Y coordinate in pixels")
                    })
                    put("duration", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Swipe duration in milliseconds (default: 300)")
                        put("default", 300)
                    })
                })
                put("required", org.json.JSONArray().apply {
                    put("startX")
                    put("startY")
                    put("endX")
                    put("endY")
                })
            }
            
            return McpTool(
                name = "android.swipe",
                description = """
                    在 Android 设备的屏幕上执行滑动操作。
                    此工具允许通过提供起始和结束坐标以及滑动时长来模拟用户的滑动行为。
                    
                    **何时使用:**
                    - 需要模拟用户滑动屏幕以滚动列表、切换页面或执行拖动操作时。
                    
                    **参数:**
                    - `startX` (integer, 必需): 滑动起始点的 X 坐标 (像素)。
                    - `startY` (integer, 必需): 滑动起始点的 Y 坐标 (像素)。
                    - `endX` (integer, 必需): 滑动结束点的 X 坐标 (像素)。
                    - `endY` (integer, 必需): 滑动结束点的 Y 坐标 (像素)。
                    - `duration` (integer, 可选, 默认: `300`): 滑动持续时间 (毫秒)。
                    
                    **如何使用:**
                    调用此工具并提供滑动起始点和结束点的 `startX`, `startY`, `endX`, `endY` 坐标。如果需要自定义滑动速度，可以提供 `duration` 参数。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val startX = arguments?.getInt("startX") ?: throw IllegalArgumentException("Missing startX parameter")
            val startY = arguments?.getInt("startY") ?: throw IllegalArgumentException("Missing startY parameter")
            val endX = arguments?.getInt("endX") ?: throw IllegalArgumentException("Missing endX parameter")
            val endY = arguments?.getInt("endY") ?: throw IllegalArgumentException("Missing endY parameter")
            val duration = arguments?.optInt("duration", 300) ?: 300
            
            Log.i(TAG, "Swiping from ($startX, $startY) to ($endX, $endY) in ${duration}ms")
            
            when (val response = apiHandler.performSwipe(startX, startY, endX, endY, duration)) {
                is ApiResponse.Success -> {
                    JSONObject().apply {
                        put("success", true)
                        put("message", response.data)
                        put("next_action", "请立即调用 android.screen.dump 工具确认滑动操作是否生效")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误,请重新执行此操作，连续五次报错后再反馈用户")
                        } else {
                            put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type")
                        put("next_action", "请立即调用 android.screen.get 工具截图确认当前界面状态")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error swiping", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}

// 11. WaitTool - 等待页面加载
class WaitTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "WaitTool"
        private const val MAX_TIMEOUT = 10000L  // 最大等待时间10秒
        private const val CHECK_INTERVAL = 200L  // 检查间隔200毫秒
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("timeout", JSONObject().apply {
                        put("type", "integer")
                        put("description", "最大等待时间(毫秒), 最大10000ms (10秒)")
                        put("default", 5000)
                        put("minimum", 100)
                        put("maximum", 10000)
                    })
                    put("condition", JSONObject().apply {
                        put("type", "object")
                        put("description", "等待条件")
                        put("properties", JSONObject().apply {
                            put("text", JSONObject().apply {
                                put("type", "string")
                                put("description", "等待包含特定文本的元素出现")
                            })
                            put("resource_id", JSONObject().apply {
                                put("type", "string")
                                put("description", "等待具有特定resource-id的元素出现")
                            })
                            put("class_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "等待具有特定类名的元素出现")
                            })
                            put("content_description", JSONObject().apply {
                                put("type", "string")
                                put("description", "等待具有特定content-description的元素出现")
                            })
                            put("not_present", JSONObject().apply {
                                put("type", "boolean")
                                put("description", "如果为true,则等待元素消失而非出现")
                                put("default", false)
                            })
                        })
                    })
                })
                put("required", org.json.JSONArray().apply {
                    put("condition")
                })
            }
            
            return McpTool(
                name = "android.wait",
                description = """
                    等待 Android 设备屏幕上的元素状态变化。
                    此工具会周期性检查屏幕状态,直到满足指定条件或超时。
                    
                    **何时使用:**
                    - 需要等待页面加载完成时。
                    - 需要等待特定元素出现或消失时。
                    - 需要在操作后等待界面更新时。
                    
                    **参数:**
                    - `timeout` (integer, 可选, 默认: 5000): 最大等待时间(毫秒), 范围100-10000ms。
                    - `condition` (object, 必需): 等待条件,至少包含以下一个属性:
                      - `text` (string, 可选): 等待包含此文本的元素出现。
                      - `resource_id` (string, 可选): 等待具有此resource-id的元素出现。
                      - `class_name` (string, 可选): 等待具有此类名的元素出现。
                      - `content_description` (string, 可选): 等待具有此content-description的元素出现。
                      - `not_present` (boolean, 可选, 默认: false): 如果为true,则等待元素消失。
                    
                    **如何使用:**
                    调用此工具并提供 `condition` 参数。可以组合多个条件,元素需要满足所有条件。
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val timeout = arguments?.optLong("timeout", 5000L)?.coerceIn(100L, MAX_TIMEOUT) ?: 5000L
            val condition = arguments?.optJSONObject("condition")
                ?: throw IllegalArgumentException("Missing condition parameter")
            
            // 提取条件
            val text = condition.optString("text", null)
            val resourceId = condition.optString("resource_id", null)
            val className = condition.optString("class_name", null)
            val contentDescription = condition.optString("content_description", null)
            val notPresent = condition.optBoolean("not_present", false)
            
            // 至少需要一个条件
            if (text.isNullOrEmpty() && resourceId.isNullOrEmpty() &&
                className.isNullOrEmpty() && contentDescription.isNullOrEmpty()) {
                throw IllegalArgumentException("At least one condition must be specified")
            }
            
            Log.i(TAG, "Waiting for condition (timeout=${timeout}ms, notPresent=$notPresent)")
            
            val startTime = System.currentTimeMillis()
            var lastError: String? = null
            
            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    // 获取当前屏幕状态
                    when (val response = apiHandler.getStateFull(true)) {
                        is ApiResponse.Success -> {
                            val stateData = response.data as String
                            val stateJson = JSONObject(stateData)
                            val a11yTree = stateJson.optJSONObject("a11y_tree")
                            
                            if (a11yTree != null) {
                                val found = checkCondition(a11yTree, text, resourceId, className, contentDescription)
                                
                                // 根据 notPresent 判断是否满足条件
                                if (notPresent && !found) {
                                    // 等待元素消失,且确实消失了
                                    return JSONObject().apply {
                                        put("success", true)
                                        put("message", "Element no longer present")
                                        put("elapsed_ms", System.currentTimeMillis() - startTime)
                                    }
                                } else if (!notPresent && found) {
                                    // 等待元素出现,且确实出现了
                                    return JSONObject().apply {
                                        put("success", true)
                                        put("message", "Element found")
                                        put("elapsed_ms", System.currentTimeMillis() - startTime)
                                    }
                                }
                            }
                        }
                        is ApiResponse.Error -> {
                            lastError = response.message
                        }
                        else -> {
                            lastError = "Unexpected response type"
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message
                }
                
                // 等待一段时间后重试
                Thread.sleep(CHECK_INTERVAL)
            }
            
            // 超时
            JSONObject().apply {
                put("success", false)
                put("error", "Timeout: condition not met within ${timeout}ms")
                if (lastError != null) {
                    put("last_error", lastError)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
    
    private fun checkCondition(
        node: JSONObject,
        text: String?,
        resourceId: String?,
        className: String?,
        contentDescription: String?
    ): Boolean {
        // 检查当前节点是否匹配所有条件
        var matches = true
        
        if (!text.isNullOrEmpty()) {
            val nodeText = node.optString("text", "")
            matches = matches && nodeText.contains(text, ignoreCase = true)
        }
        
        if (!resourceId.isNullOrEmpty()) {
            val nodeResourceId = node.optString("resourceId", "")
            matches = matches && nodeResourceId.contains(resourceId)
        }
        
        if (!className.isNullOrEmpty()) {
            val nodeClassName = node.optString("className", "")
            matches = matches && nodeClassName.contains(className)
        }
        
        if (!contentDescription.isNullOrEmpty()) {
            val nodeContentDesc = node.optString("contentDescription", "")
            matches = matches && nodeContentDesc.contains(contentDescription, ignoreCase = true)
        }
        
        if (matches) {
            return true
        }
        
        // 递归检查子节点
        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i)
                if (child != null && checkCondition(child, text, resourceId, className, contentDescription)) {
                    return true
                }
            }
        }
        
        return false
    }
}

// 12. ScreenVisionTool - 屏幕视觉分析工具
class ScreenVisionTool(private val apiHandler: ApiHandler) : McpToolHandler {
    
    companion object {
        private const val TAG = "ScreenVisionTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("question", JSONObject().apply {
                        put("type", "string")
                        put("description", "向AI提出的问题，描述你希望了解的屏幕内容")
                    })
                })
                put("required", org.json.JSONArray().put("question"))
            }
            
            return McpTool(
                name = "android.screen.vision",
                description = """
                    使用AI视觉识别分析当前屏幕内容。
                    此工具会获取屏幕截图并发送给AI识别服务进行分析，可以识别屏幕上的图像、广告、弹窗等视觉内容。
                    
                    **何时使用:**
                    - 需要了解屏幕上显示的图像、图标、广告等视觉内容时
                    - 需要识别无法通过XML结构获取的内容（如图片中的文字）时
                    - 需要判断是否出现了特定的弹窗或广告时
                    - 需要AI帮助定位屏幕上某个视觉元素的位置时
                    
                    **与android.screen.dump的区别:**
                    - `android.screen.dump`: 快速获取可交互元素的XML结构数据，速度快，但只能看到元素属性
                    - `android.screen.vision`: 通过AI识别屏幕截图内容，速度较慢，但可以识别图像、广告等视觉内容
                    
                    **使用建议:**
                    1. 对于普通的点击、输入等操作，优先使用`android.screen.dump`
                    2. 当需要识别图像内容或判断弹窗时，使用本工具
                    3. 本工具会自动附带设备信息和XML数据帮助AI分析
                    
                    **参数:**
                    - `question` (string, 必需): 向AI提出的问题
                      示例:
                      - "屏幕上显示了什么内容？"
                      - "是否出现了广告弹窗？"
                      - "登录按钮在哪个位置？"
                      - "屏幕中间的图片显示的是什么？"
                    
                    **注意事项:**
                    - 此工具需要网络连接到AI识别服务
                    - 处理时间较长（通常需要几秒钟）
                    - AI会根据截图和XML数据提供分析结果
                """.trimIndent(),
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val question = arguments?.getString("question")
                ?: throw IllegalArgumentException("Missing question parameter")
            
            Log.i(TAG, "Analyzing screen with vision AI, question: $question")
            
            when (val response = apiHandler.analyzeScreenWithVision(question)) {
                is ApiResponse.Raw -> {
                    // Vision API返回的JSON响应
                    JSONObject().apply {
                        put("success", true)
                        put("analysis", response.json)
                        put("message", "Screen analyzed successfully by AI")
                    }
                }
                is ApiResponse.Error -> {
                    val isNetworkError = response.message.contains("network", ignoreCase = true) ||
                                        response.message.contains("timeout", ignoreCase = true) ||
                                        response.message.contains("connection", ignoreCase = true)
                    
                    JSONObject().apply {
                        put("success", false)
                        put("error", response.message)
                        if (isNetworkError) {
                            put("next_action", "网络错误，请检查网络连接后重试")
                        } else {
                            put("next_action", "AI分析失败，可以尝试重新提问或使用android.screen.dump工具获取元素信息")
                        }
                    }
                }
                else -> {
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unexpected response type from vision API")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing screen with vision", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
                put("next_action", "执行失败，建议使用android.screen.dump工具获取元素信息")
            }
        }
    }
}