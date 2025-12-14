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
                description = "Dump the UI hierarchy return as XML\n获取当前屏幕交互内容,适合及时性的交互操作,但是无法获取完整页面信息。",
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
                    // 临时禁用数据精简,返回完整JSON
                    // val cleanedData = A11yTreeCleaner.cleanA11yTree(response.data as String)
                    
                    JSONObject().apply {
                        put("success", true)
                        put("data", response.data)
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
                description = "获取 Android 手机已安装的应用列表,返回精简的应用信息(应用名、包名、是否系统应用)。参数说明:\n- filter: 可选,过滤类型。可选值:user(仅用户应用)、system(仅系统应用),默认为 user",
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
                    
                    // Filter based on type and create simplified packages
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
                            // Create simplified package info (only packageName, label, isSystemApp)
                            val simplifiedPkg = JSONObject().apply {
                                put("packageName", pkg.getString("packageName"))
                                put("label", pkg.getString("label"))
                                put("isSystemApp", isSystem)
                            }
                            filteredPackages.put(simplifiedPkg as Any)
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
                description = "启动指定的 Android 应用。参数说明:\n- package: 必需,应用的包名(如 com.taobao.taobao)",
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
                description = "在当前聚焦的输入框输入文本。参数说明:\n- text: 要输入的文本内容",
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
                description = "清除 Android 手机当前输入框中的文本。",
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
                description = "发送 Android KeyEvent 按键码。参数说明:\n- key_code: 必需,Android KeyEvent 键码(整数)\n\n常用按键码参考:\n- 3: HOME (主页键)\n- 4: BACK (返回键)\n- 24: VOLUME_UP (音量+)\n- 25: VOLUME_DOWN (音量-)\n- 26: POWER (电源键)\n- 66: ENTER (回车键)\n- 67: DEL (删除键)\n- 82: MENU (菜单键)\n- 187: APP_SWITCH (最近应用键)",
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
                description = "点击手机屏幕指定坐标位置。参数说明:\n- x: 点击的X坐标(整数)\n- y: 点击的Y坐标(整数)",
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
                description = "双击手机屏幕指定坐标位置。参数说明:\n- x: 双击的X坐标(整数)\n- y: 双击的Y坐标(整数)",
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
                description = "长按手机屏幕指定坐标位置。参数说明:\n- x: 长按的X坐标(整数)\n- y: 长按的Y坐标(整数)\n- duration: 可选,长按时长(毫秒),默认1000ms",
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
                description = "在手机屏幕上执行滑动操作。参数说明:\n- startX: 起始X坐标(整数)\n- startY: 起始Y坐标(整数)\n- endX: 结束X坐标(整数)\n- endY: 结束Y坐标(整数)\n- duration: 可选,滑动时长(毫秒),默认300ms",
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