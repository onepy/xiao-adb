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

// ============================================================================
// 基于控件的操作工具 - Element-Based Operations
// ============================================================================

/**
 * 控件查找辅助类
 * 提供通用的控件查找逻辑
 */
object ElementFinder {
    private const val TAG = "ElementFinder"
    
    /**
     * 根据resourceId查找控件
     */
    fun findByResourceId(resourceId: String): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
            ?: return null
        
        val rootNode = service.rootInActiveWindow ?: return null
        
        try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
            return if (nodes.isNotEmpty()) nodes[0] else null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element by resourceId: $resourceId", e)
            return null
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 根据文本查找控件
     */
    fun findByText(text: String, exact: Boolean = false): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
            ?: return null
        
        val rootNode = service.rootInActiveWindow ?: return null
        
        try {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (nodes.isEmpty()) return null
            
            return if (exact) {
                nodes.find { it.text?.toString() == text }
            } else {
                nodes[0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element by text: $text", e)
            return null
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 根据contentDescription查找控件
     */
    fun findByContentDescription(desc: String): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
            ?: return null
        
        val rootNode = service.rootInActiveWindow ?: return null
        
        try {
            return findNodeByContentDesc(rootNode, desc)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element by contentDescription: $desc", e)
            return null
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 递归查找具有指定contentDescription的节点
     */
    private fun findNodeByContentDesc(
        node: android.view.accessibility.AccessibilityNodeInfo,
        desc: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == desc) {
            return android.view.accessibility.AccessibilityNodeInfo(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = findNodeByContentDesc(child, desc)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        
        return null
    }
    
    /**
     * 根据className查找控件
     */
    fun findByClassName(className: String): android.view.accessibility.AccessibilityNodeInfo? {
        val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
            ?: return null
        
        val rootNode = service.rootInActiveWindow ?: return null
        
        try {
            return findNodeByClassName(rootNode, className)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element by className: $className", e)
            return null
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 递归查找具有指定className的节点
     */
    private fun findNodeByClassName(
        node: android.view.accessibility.AccessibilityNodeInfo,
        className: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.className?.toString() == className) {
            return android.view.accessibility.AccessibilityNodeInfo(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = findNodeByClassName(child, className)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        
        return null
    }
    
    /**
     * 通用查找方法 - 支持多种查找策略
     */
    fun find(
        resourceId: String? = null,
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 优先使用resourceId
        if (!resourceId.isNullOrEmpty()) {
            val node = findByResourceId(resourceId)
            if (node != null) return node
        }
        
        // 其次使用text
        if (!text.isNullOrEmpty()) {
            val node = findByText(text)
            if (node != null) return node
        }
        
        // 然后使用contentDescription
        if (!contentDescription.isNullOrEmpty()) {
            val node = findByContentDescription(contentDescription)
            if (node != null) return node
        }
        
        // 最后使用className
        if (!className.isNullOrEmpty()) {
            val node = findByClassName(className)
            if (node != null) return node
        }
        
        return null
    }
}

// 12. FindElementTool - 查找控件
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
            
            // 13. ClickElementTool - 点击控件
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
                                此工具会查找匹配的控件并执行点击操作，比坐标点击更稳定可靠。
                                
                                **何时使用:**
                                - 需要点击具有特定ID或文本的按钮时
                                - 界面布局可能变化，但控件ID稳定时
                                - 需要更精确的控件操作而不是坐标点击时
                                
                                **优势:**
                                - 不依赖屏幕坐标，对UI变化更精准
                                - 直接操作控件对象，更精确
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
                                
                                JSONObject().apply {
                                    put("success", success)
                                    if (success) {
                                        put("message", "Element clicked successfully")
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
            
            // 14. ScrollElementTool - 滚动控件内部
            class ScrollElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
                
                companion object {
                    private const val TAG = "ScrollElementTool"
                    
                    fun getToolDefinition(): McpTool {
                        val inputSchema = JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("resource_id", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "可滚动控件的resource-id")
                                })
                                put("text", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "可滚动控件包含的文本")
                                })
                                put("content_description", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "可滚动控件的content-description")
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
                            put("required", org.json.JSONArray())
                        }
                        
                        return McpTool(
                            name = "android.element.scroll",
                            description = """
                                在指定的可滚动控件内部执行滚动操作。
                                此工具只会滚动目标控件，不会影响整个屏幕或父容器。
                                
                                **何时使用:**
                                - 需要滚动特定的列表或滚动视图时
                                - 需要精确控制滚动范围，避免误触其他区域时
                                - 处理嵌套滚动视图时
                                
                                **优势:**
                                - 只滚动目标控件，不影响外部
                                - 自动识别可滚动的控件
                                - 支持RecyclerView、ScrollView、ListView等各种可滚动组件
                                
                                **参数:**
                                - `resource_id` (string, 可选): 可滚动控件的resource-id
                                - `text` (string, 可选): 可滚动控件包含的文本
                                - `content_description` (string, 可选): 控件的无障碍描述
                                - `direction` (string, 可选, 默认: 'forward'): 滚动方向
                                
                                如果不提供任何查找参数，将尝试滚动当前焦点所在的可滚动控件。
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
                        val direction = arguments?.optString("direction", "forward") ?: "forward"
                        
                        Log.i(TAG, "Scrolling element: resourceId=$resourceId, direction=$direction")
                        
                        val node = if (!resourceId.isNullOrEmpty() || !text.isNullOrEmpty() || !contentDescription.isNullOrEmpty()) {
                            ElementFinder.find(resourceId, text, contentDescription, null)
                        } else {
                            // 如果没有指定查找条件，尝试找到第一个可滚动的控件
                            findFirstScrollableElement()
                        }
                        
                        if (node != null) {
                            try {
                                if (!node.isScrollable) {
                                    return JSONObject().apply {
                                        put("success", false)
                                        put("error", "Element is not scrollable")
                                    }
                                }
                                
                                val action = if (direction == "forward") {
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                } else {
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                                }
                                
                                val success = node.performAction(action)
                                
                                JSONObject().apply {
                                    put("success", success)
                                    if (success) {
                                        put("message", "Element scrolled $direction successfully")
                                    } else {
                                        put("error", "Failed to scroll element (might be at end)")
                                    }
                                }
                            } finally {
                                node.recycle()
                            }
                        } else {
                            JSONObject().apply {
                                put("success", false)
                                put("error", "Scrollable element not found")
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
                
                private fun findFirstScrollableElement(): android.view.accessibility.AccessibilityNodeInfo? {
                    val service = com.droidrun.portal.service.DroidrunAccessibilityService.getInstance()
                        ?: return null
                    
                    val rootNode = service.rootInActiveWindow ?: return null
                    
                    try {
                        return findScrollableNode(rootNode)
                    } finally {
                        rootNode.recycle()
                    }
                }
                
                private fun findScrollableNode(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                    if (node.isScrollable) {
                        return android.view.accessibility.AccessibilityNodeInfo(node)
                    }
                    
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        try {
                            val found = findScrollableNode(child)
                            if (found != null) return found
                        } finally {
                            child.recycle()
                        }
                    }
                    
                    return null
                }
            }
            
            // 15. LongPressElementTool - 长按控件
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
                                通过控件属性定位并长按控件。
                                此工具会查找匹配的控件并执行长按操作，触发上下文菜单或长按特定功能。
                                
                                **何时使用:**
                                - 需要触发控件的上下文菜单时
                                - 需要长按才能触发的特殊功能时
                                - 界面需要长按操作（如选择、编辑等）时
                                
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
                        
                        Log.i(TAG, "Long pressing element with: resourceId=$resourceId, text=$text")
                        
                        val node = ElementFinder.find(resourceId, text, contentDescription, className)
                        
                        if (node != null) {
                            try {
                                if (!node.isLongClickable && !node.isEnabled) {
                                    return JSONObject().apply {
                                        put("success", false)
                                        put("error", "Element is not long-clickable or enabled")
                                    }
                                }
                                
                                val success = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)
                                
                                JSONObject().apply {
                                    put("success", success)
                                    if (success) {
                                        put("message", "Element long pressed successfully")
                                    } else {
                                        put("error", "Failed to perform long press action")
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
            
            // 16. SetTextTool - 设置控件文本
            class SetTextTool(private val apiHandler: ApiHandler) : McpToolHandler {
                
                companion object {
                    private const val TAG = "SetTextTool"
                    
                    fun getToolDefinition(): McpTool {
                        val inputSchema = JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("resource_id", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "输入框控件的resource-id")
                                })
                                put("text", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "要设置的文本内容")
                                })
                                put("content_description", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "输入框的content-description")
                                })
                            })
                            put("required", org.json.JSONArray().put("text"))
                        }
                        
                        return McpTool(
                            name = "android.element.set_text",
                            description = """
                                通过控件属性定位输入框并设置文本内容。
                                此工具直接操作控件对象设置文本，比模拟键盘输入更快速可靠。
                                
                                **何时使用:**
                                - 需要在特定输入框中输入文本时
                                - 需要批量填充表单时
                                - 需要替换现有文本内容时
                                
                                **优势:**
                                - 直接设置文本，不需要聚焦输入框
                                - 速度快，不受键盘动画影响
                                - 可以设置任意文本，包括特殊字符
                                
                                **参数:**
                                - `resource_id` (string, 可选): 输入框的resource-id
                                - `text` (string, 必需): 要设置的文本内容
                                - `content_description` (string, 可选): 输入框的无障碍描述
                                
                                至少提供resource_id或content_description之一。
                            """.trimIndent(),
                            inputSchema = inputSchema
                        )
                    }
                }
                
                override fun execute(arguments: JSONObject?): JSONObject {
                    return try {
                        val resourceId = arguments?.optString("resource_id", null)
                        val text = arguments?.getString("text") ?: return JSONObject().apply {
                            put("success", false)
                            put("error", "Missing text parameter")
                        }
                        val contentDescription = arguments.optString("content_description", null)
                        
                        if (resourceId.isNullOrEmpty() && contentDescription.isNullOrEmpty()) {
                            return JSONObject().apply {
                                put("success", false)
                                put("error", "Either resource_id or content_description is required")
                            }
                        }
                        
                        Log.i(TAG, "Setting text on element: resourceId=$resourceId, text length=${text.length}")
                        
                        val node = ElementFinder.find(resourceId, null, contentDescription, null)
                        
                        if (node != null) {
                            try {
                                if (!node.isEditable) {
                                    return JSONObject().apply {
                                        put("success", false)
                                        put("error", "Element is not editable")
                                    }
                                }
                                
                                val bundle = android.os.Bundle()
                                bundle.putCharSequence(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    text
                                )
                                
                                val success = node.performAction(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                                    bundle
                                )
                                
                                JSONObject().apply {
                                    put("success", success)
                                    if (success) {
                                        put("message", "Text set successfully")
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
            
            // 17. ToggleCheckboxTool - 切换复选框状态
            class ToggleCheckboxTool(private val apiHandler: ApiHandler) : McpToolHandler {
                
                companion object {
                    private const val TAG = "ToggleCheckboxTool"
                    
                    fun getToolDefinition(): McpTool {
                        val inputSchema = JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("resource_id", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "复选框控件的resource-id")
                                })
                                put("text", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "复选框关联的文本标签")
                                })
                                put("content_description", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "复选框的content-description")
                                })
                                put("checked", JSONObject().apply {
                                    put("type", "boolean")
                                    put("description", "目标状态: true(选中) 或 false(未选中)。如果不指定，则切换当前状态")
                                })
                            })
                        }
                        
                        // 18. DoubleTapElementTool - 双击控件
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
                                            通过控件属性定位并双击控件。
                                            此工具会查找匹配的控件并执行双击操作,触发双击特定功能。
                                            
                                            **何时使用:**
                                            - 需要双击才能触发的特殊功能时
                                            - 某些控件需要双击才能选中或编辑时
                                            - 触发图片放大或其他双击交互时
                                            
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
                                    
                                    Log.i(TAG, "Double tapping element with: resourceId=$resourceId, text=$text")
                                    
                                    val node = ElementFinder.find(resourceId, text, contentDescription, className)
                                    
                                    if (node != null) {
                                        try {
                                            // 获取控件中心坐标
                                            val bounds = android.graphics.Rect()
                                            node.getBoundsInScreen(bounds)
                                            val centerX = bounds.centerX()
                                            val centerY = bounds.centerY()
                                            
                                            // 使用ApiHandler执行双击
                                            when (val response = apiHandler.performDoubleTap(centerX, centerY)) {
                                                is com.droidrun.portal.api.ApiResponse.Success -> {
                                                    JSONObject().apply {
                                                        put("success", true)
                                                        put("message", "Element double tapped successfully at ($centerX, $centerY)")
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
                                    Log.e(TAG, "Error double tapping element", e)
                                    JSONObject().apply {
                                        put("success", false)
                                        put("error", e.message ?: "Unknown error")
                                    }
                                }
                            }
                        }
                        
                        // 19. DragElementTool - 拖动控件
                        class DragElementTool(private val apiHandler: ApiHandler) : McpToolHandler {
                            
                            companion object {
                                private const val TAG = "DragElementTool"
                                
                                fun getToolDefinition(): McpTool {
                                    val inputSchema = JSONObject().apply {
                                        put("type", "object")
                                        put("properties", JSONObject().apply {
                                            put("source_resource_id", JSONObject().apply {
                                                put("type", "string")
                                                put("description", "源控件的resource-id")
                                            })
                                            put("source_text", JSONObject().apply {
                                                put("type", "string")
                                                put("description", "源控件的文本")
                                            })
                                            put("target_resource_id", JSONObject().apply {
                                                put("type", "string")
                                                put("description", "目标控件的resource-id")
                                            })
                                            put("target_text", JSONObject().apply {
                                                put("type", "string")
                                                put("description", "目标控件的文本")
                                            })
                                            put("target_x", JSONObject().apply {
                                                put("type", "integer")
                                                put("description", "目标位置X坐标(像素)")
                                            })
                                            put("target_y", JSONObject().apply {
                                                put("type", "integer")
                                                put("description", "目标位置Y坐标(像素)")
                                            })
                                            put("duration", JSONObject().apply {
                                                put("type", "integer")
                                                put("description", "拖动持续时间(毫秒,默认500)")
                                                put("default", 500)
                                            })
                                        })
                                    }
                                    
                                    return McpTool(
                                        name = "android.element.drag",
                                        description = """
                                            拖动控件到指定位置或另一个控件上。
                                            此工具可以将一个控件拖动到目标位置,支持拖拽排序、拖放等操作。
                                            
                                            **何时使用:**
                                            - 需要拖动列表项进行排序时
                                            - 需要拖放文件或图片时
                                            - 需要拖动滑块到特定位置时
                                            - 需要拖动控件到另一个区域时
                                            
                                            **参数:**
                                            源控件定位(至少提供一个):
                                            - `source_resource_id` (string, 可选): 源控件的resource-id
                                            - `source_text` (string, 可选): 源控件的文本
                                            
                                            目标位置(两种方式选一种):
                                            方式1 - 拖到另一个控件:
                                            - `target_resource_id` (string, 可选): 目标控件的resource-id
                                            - `target_text` (string, 可选): 目标控件的文本
                                            
                                            方式2 - 拖到指定坐标:
                                            - `target_x` (integer, 可选): 目标X坐标
                                            - `target_y` (integer, 可选): 目标Y坐标
                                            
                                            其他:
                                            - `duration` (integer, 可选, 默认500): 拖动持续时间
                                            
                                            **注意:** 必须提供源控件定位参数和目标位置参数。
                                        """.trimIndent(),
                                        inputSchema = inputSchema
                                    )
                                }
                            }
                            
                            override fun execute(arguments: JSONObject?): JSONObject {
                                return try {
                                    // 解析源控件参数
                                    val sourceResourceId = arguments?.optString("source_resource_id", null)
                                    val sourceText = arguments?.optString("source_text", null)
                                    
                                    if (sourceResourceId.isNullOrEmpty() && sourceText.isNullOrEmpty()) {
                                        return JSONObject().apply {
                                            put("success", false)
                                            put("error", "Source element parameters required (source_resource_id or source_text)")
                                        }
                                    }
                                    
                                    // 解析目标参数
                                    val targetResourceId = arguments?.optString("target_resource_id", null)
                                    val targetText = arguments?.optString("target_text", null)
                                    val targetX = if (arguments?.has("target_x") == true) arguments.getInt("target_x") else null
                                    val targetY = if (arguments?.has("target_y") == true) arguments.getInt("target_y") else null
                                    val duration = arguments?.optInt("duration", 500) ?: 500
                                    
                                    // 验证目标参数
                                    val hasTargetElement = !targetResourceId.isNullOrEmpty() || !targetText.isNullOrEmpty()
                                    val hasTargetCoords = targetX != null && targetY != null
                                    
                                    if (!hasTargetElement && !hasTargetCoords) {
                                        return JSONObject().apply {
                                            put("success", false)
                                            put("error", "Target parameters required (either target element or target coordinates)")
                                        }
                                    }
                                    
                                    Log.i(TAG, "Dragging element: source=$sourceResourceId/$sourceText, target=$targetResourceId/$targetText or ($targetX,$targetY)")
                                    
                                    // 查找源控件
                                    val sourceNode = ElementFinder.find(sourceResourceId, sourceText, null, null)
                                    if (sourceNode == null) {
                                        return JSONObject().apply {
                                            put("success", false)
                                            put("error", "Source element not found")
                                        }
                                    }
                                    
                                    try {
                                        // 获取源控件中心坐标
                                        val sourceBounds = android.graphics.Rect()
                                        sourceNode.getBoundsInScreen(sourceBounds)
                                        val startX = sourceBounds.centerX()
                                        val startY = sourceBounds.centerY()
                                        
                                        // 确定目标坐标
                                        val (endX, endY) = if (hasTargetCoords) {
                                            // 使用指定坐标
                                            Pair(targetX!!, targetY!!)
                                        } else {
                                            // 查找目标控件
                                            val targetNode = ElementFinder.find(targetResourceId, targetText, null, null)
                                            if (targetNode == null) {
                                                return JSONObject().apply {
                                                    put("success", false)
                                                    put("error", "Target element not found")
                                                }
                                            }
                                            
                                            try {
                                                val targetBounds = android.graphics.Rect()
                                                targetNode.getBoundsInScreen(targetBounds)
                                                Pair(targetBounds.centerX(), targetBounds.centerY())
                                            } finally {
                                                targetNode.recycle()
                                            }
                                        }
                                        
                                        // 执行拖动
                                        when (val response = apiHandler.performSwipe(startX, startY, endX, endY, duration)) {
                                            is com.droidrun.portal.api.ApiResponse.Success -> {
                                                JSONObject().apply {
                                                    put("success", true)
                                                    put("message", "Element dragged from ($startX,$startY) to ($endX,$endY)")
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
                                        sourceNode.recycle()
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
                        
                        return McpTool(
                            name = "android.element.toggle_checkbox",
                            description = """
                                通过控件属性定位并切换复选框状态。
                                此工具可以设置复选框为选中或未选中状态，或者直接切换当前状态。
                                
                                **何时使用:**
                                - 需要勾选或取消勾选复选框时
                                - 需要切换开关控件状态时
                                - 需要批量设置多个复选框时
                                
                                **支持的控件类型:**
                                - CheckBox (复选框)
                                - Switch (开关)
                                - RadioButton (单选按钮)
                                - ToggleButton (切换按钮)
                                
                                **参数:**
                                - `resource_id` (string, 可选): 复选框的resource-id
                                - `text` (string, 可选): 复选框关联的文本
                                - `content_description` (string, 可选): 控件的无障碍描述
                                - `checked` (boolean, 可选): 目标状态。不指定则切换当前状态
                                
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
                        val targetChecked = if (arguments?.has("checked") == true) {
                            arguments.getBoolean("checked")
                        } else {
                            null // 如果未指定，则切换当前状态
                        }
                        
                        if (resourceId.isNullOrEmpty() && text.isNullOrEmpty() && contentDescription.isNullOrEmpty()) {
                            return JSONObject().apply {
                                put("success", false)
                                put("error", "At least one search parameter is required")
                            }
                        }
                        
                        Log.i(TAG, "Toggling checkbox: resourceId=$resourceId, targetChecked=$targetChecked")
                        
                        val node = ElementFinder.find(resourceId, text, contentDescription, null)
                        
                        if (node != null) {
                            try {
                                if (!node.isCheckable) {
                                    return JSONObject().apply {
                                        put("success", false)
                                        put("error", "Element is not checkable")
                                    }
                                }
                                
                                val currentChecked = node.isChecked
                                
                                // 如果指定了目标状态且已经是目标状态，则无需操作
                                if (targetChecked != null && currentChecked == targetChecked) {
                                    return JSONObject().apply {
                                        put("success", true)
                                        put("message", "Element already in target state")
                                        put("checked", currentChecked)
                                    }
                                }
                                
                                // 执行点击以切换状态
                                val success = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                
                                JSONObject().apply {
                                    put("success", success)
                                    if (success) {
                                        put("message", "Checkbox toggled successfully")
                                        put("previous_state", currentChecked)
                                        put("new_state", !currentChecked)
                                    } else {
                                        put("error", "Failed to toggle checkbox")
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
                        Log.e(TAG, "Error toggling checkbox", e)
                        JSONObject().apply {
                            put("success", false)
                            put("error", e.message ?: "Unknown error")
                        }
                    }
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