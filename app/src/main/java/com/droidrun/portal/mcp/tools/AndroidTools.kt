package com.droidrun.portal.mcp.tools

import android.content.Context
import android.util.Base64
import android.util.Log
import com.droidrun.portal.api.ApiHandler
import com.droidrun.portal.api.ApiResponse
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
                name = "get_state",
                description = "Get current phone state including full accessibility tree XML and device context. Returns comprehensive screen information.",
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
                    put("type", JSONObject().apply {
                        put("type", "string")
                        put("enum", org.json.JSONArray().apply {
                            put("all")
                            put("user")
                            put("system")
                        })
                        put("description", "Filter packages by type: 'all' (default), 'user' (user-installed), or 'system' (system apps)")
                        put("default", "all")
                    })
                })
            }
            
            return McpTool(
                name = "get_packages",
                description = "Get list of installed applications with package names, labels, and version info. Can filter by user/system apps.",
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            val filterType = arguments?.optString("type", "all") ?: "all"
            
            Log.i(TAG, "Getting packages with filter=$filterType")
            
            when (val response = apiHandler.getPackages()) {
                is ApiResponse.Raw -> {
                    val packages = response.json.getJSONArray("packages")
                    
                    // Filter based on type
                    val filteredPackages = org.json.JSONArray()
                    for (i in 0 until packages.length()) {
                        val pkg = packages.getJSONObject(i)
                        val isSystem = pkg.getBoolean("isSystemApp")
                        
                        when (filterType) {
                            "user" -> if (!isSystem) filteredPackages.put(pkg as Any)
                            "system" -> if (isSystem) filteredPackages.put(pkg as Any)
                            else -> filteredPackages.put(pkg as Any)
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
                name = "launch_app",
                description = "Launch an Android application by package name. Optionally specify an activity to start directly.",
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
                name = "input_text",
                description = "Input text into the currently focused field. Text is automatically Base64 encoded for proper handling.",
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
                name = "clear_text",
                description = "Clear all text from the currently focused input field.",
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
                name = "press_key",
                description = "Simulate pressing an Android key. Common codes: ENTER(66), BACKSPACE(67), BACK(4), HOME(3).",
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
                name = "tap",
                description = "Perform a single tap at specified coordinates.",
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
                name = "double_tap",
                description = "Perform a double tap at specified coordinates.",
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
                name = "long_press",
                description = "Perform a long press at specified coordinates with customizable duration.",
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
                name = "swipe",
                description = "Perform a swipe gesture from start coordinates to end coordinates with customizable duration.",
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