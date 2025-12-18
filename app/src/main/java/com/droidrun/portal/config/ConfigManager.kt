package com.droidrun.portal.config

import android.content.Context
import android.content.SharedPreferences
import com.droidrun.portal.events.model.EventType
import androidx.core.content.edit

/**
 * Centralized configuration manager for Droidrun Portal
 * Handles SharedPreferences operations and provides a clean API for configuration management
 */
class ConfigManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "droidrun_config"
        private const val KEY_OVERLAY_VISIBLE = "overlay_visible"
        private const val KEY_OVERLAY_OFFSET = "overlay_offset"
        private const val KEY_AUTO_OFFSET_ENABLED = "auto_offset_enabled"
        private const val KEY_AUTO_OFFSET_CALCULATED = "auto_offset_calculated"
        private const val KEY_SOCKET_SERVER_ENABLED = "socket_server_enabled"
        private const val KEY_SOCKET_SERVER_PORT = "socket_server_port"
        
        // WebSocket & Events
        private const val KEY_WEBSOCKET_ENABLED = "websocket_enabled"
        private const val KEY_WEBSOCKET_PORT = "websocket_port"
        private const val KEY_REVERSE_CONNECTION_URL = "reverse_connection_url"
        private const val KEY_REVERSE_CONNECTION_ENABLED = "reverse_connection_enabled"
        private const val PREFIX_EVENT_ENABLED = "event_enabled_"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_AUTH_ENABLED = "auth_enabled"
        private const val KEY_NOTIFICATION_WHITELIST = "notification_whitelist"
        private const val KEY_MCP_TOOLS_ENABLED = "mcp_tools_enabled"
        
        // WebSocket Heartbeat Configuration
        private const val KEY_HEARTBEAT_INTERVAL = "heartbeat_interval"
        private const val KEY_HEARTBEAT_TIMEOUT = "heartbeat_timeout"
        private const val KEY_RECONNECT_INTERVAL = "reconnect_interval"
        
        private const val DEFAULT_OFFSET = 0
        private const val DEFAULT_SOCKET_PORT = 8080
        private const val DEFAULT_WEBSOCKET_PORT = 8081
        private const val DEFAULT_SCREENSHOT_QUALITY = 70
        
        // Screenshot settings
        private const val KEY_SCREENSHOT_QUALITY = "screenshot_quality"
        
        // Vision API settings (固定配置,不允许修改)
        const val VISION_API_URL = "https://api.xiaozhi.me/vision/explain"
        const val VISION_API_TOKEN = "test-token"
        
        // Vision custom prompt (可在配置界面优化)
        private const val KEY_VISION_CUSTOM_PROMPT = "vision_custom_prompt"
        private const val DEFAULT_VISION_CUSTOM_PROMPT = """【分析要求】
1. 请详细描述屏幕上显示的内容，包括文字、图标、按钮等
2. 如果询问某个元素的位置，请以坐标形式返回 (x, y)
3. 坐标原点在屏幕左上角，x向右增加，y向下增加
4. 返回格式要规范，便于程序解析
5. 参考提供的XML数据来定位可交互元素"""
        
        // Default heartbeat settings (in milliseconds)
        private const val DEFAULT_HEARTBEAT_INTERVAL = 30000L  // 30 seconds
        private const val DEFAULT_HEARTBEAT_TIMEOUT = 10000L   // 10 seconds
        private const val DEFAULT_RECONNECT_INTERVAL = 5000L   // 5 seconds
        
        @Volatile
        private var INSTANCE: ConfigManager? = null
        
        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Auth Token (Auto-generated if missing, or user-defined)
    val authToken: String
        get() {
            var token = sharedPrefs.getString(KEY_AUTH_TOKEN, null)
            if (token == null) {
                token = java.util.UUID.randomUUID().toString()
                sharedPrefs.edit { putString(KEY_AUTH_TOKEN, token) }
            }
            return token
        }
    
    // Set custom auth token
    fun setAuthToken(token: String) {
        sharedPrefs.edit { putString(KEY_AUTH_TOKEN, token) }
    }
    
    // Generate new random token
    fun generateNewAuthToken(): String {
        val token = java.util.UUID.randomUUID().toString()
        sharedPrefs.edit { putString(KEY_AUTH_TOKEN, token) }
        return token
    }

    // Auth Token Verification Enabled
    var authEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_AUTH_ENABLED, true)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_AUTH_ENABLED, value) }
        }

    // Overlay visibility
    var overlayVisible: Boolean
        get() = sharedPrefs.getBoolean(KEY_OVERLAY_VISIBLE, true)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_OVERLAY_VISIBLE, value) }
        }
    
    // Overlay offset
    var overlayOffset: Int
        get() = sharedPrefs.getInt(KEY_OVERLAY_OFFSET, DEFAULT_OFFSET)
        set(value) {
            sharedPrefs.edit { putInt(KEY_OVERLAY_OFFSET, value) }
        }

    // Auto offset enabled
    var autoOffsetEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_AUTO_OFFSET_ENABLED, true)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_AUTO_OFFSET_ENABLED, value) }
        }

    // Track if auto offset has been calculated before
    var autoOffsetCalculated: Boolean
        get() = sharedPrefs.getBoolean(KEY_AUTO_OFFSET_CALCULATED, false)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_AUTO_OFFSET_CALCULATED, value) }
        }

    // Socket server enabled (REST API)
    var socketServerEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_SOCKET_SERVER_ENABLED, true)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_SOCKET_SERVER_ENABLED, value) }
        }
    
    // Socket server port (REST API)
    var socketServerPort: Int
        get() = sharedPrefs.getInt(KEY_SOCKET_SERVER_PORT, DEFAULT_SOCKET_PORT)
        set(value) {
            sharedPrefs.edit { putInt(KEY_SOCKET_SERVER_PORT, value) }
        }

    // WebSocket Server Enabled
    var websocketEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_WEBSOCKET_ENABLED, true)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_WEBSOCKET_ENABLED, value) }
        }

    // WebSocket Server Port
    var websocketPort: Int
        get() = sharedPrefs.getInt(KEY_WEBSOCKET_PORT, DEFAULT_WEBSOCKET_PORT)
        set(value) {
            sharedPrefs.edit().putInt(KEY_WEBSOCKET_PORT, value).apply()
        }

    // Reverse Connection URL
    var reverseConnectionUrl: String
        get() = sharedPrefs.getString(KEY_REVERSE_CONNECTION_URL, "") ?: ""
        set(value) {
            sharedPrefs.edit { putString(KEY_REVERSE_CONNECTION_URL, value) }
        }

    // Reverse Connection Enabled
    var reverseConnectionEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_REVERSE_CONNECTION_ENABLED, false)
        set(value) {
            sharedPrefs.edit { putBoolean(KEY_REVERSE_CONNECTION_ENABLED, value) }
        }
    
    // WebSocket Heartbeat Configuration
    var heartbeatInterval: Long
        get() = sharedPrefs.getLong(KEY_HEARTBEAT_INTERVAL, DEFAULT_HEARTBEAT_INTERVAL)
        set(value) {
            sharedPrefs.edit { putLong(KEY_HEARTBEAT_INTERVAL, value) }
        }
    
    var heartbeatTimeout: Long
        get() = sharedPrefs.getLong(KEY_HEARTBEAT_TIMEOUT, DEFAULT_HEARTBEAT_TIMEOUT)
        set(value) {
            sharedPrefs.edit { putLong(KEY_HEARTBEAT_TIMEOUT, value) }
        }
    
    var reconnectInterval: Long
        get() = sharedPrefs.getLong(KEY_RECONNECT_INTERVAL, DEFAULT_RECONNECT_INTERVAL)
        set(value) {
            sharedPrefs.edit { putLong(KEY_RECONNECT_INTERVAL, value) }
        }
    
    // Screenshot quality (1-100)
    var screenshotQuality: Int
        get() = sharedPrefs.getInt(KEY_SCREENSHOT_QUALITY, DEFAULT_SCREENSHOT_QUALITY)
        set(value) {
            sharedPrefs.edit { putInt(KEY_SCREENSHOT_QUALITY, value.coerceIn(1, 100)) }
        }
    
    // Vision自定义提示词 (可在配置界面修改)
    var visionCustomPrompt: String
        get() = sharedPrefs.getString(KEY_VISION_CUSTOM_PROMPT, DEFAULT_VISION_CUSTOM_PROMPT)
            ?: DEFAULT_VISION_CUSTOM_PROMPT
        set(value) {
            sharedPrefs.edit { putString(KEY_VISION_CUSTOM_PROMPT, value) }
        }
    
    // 生成完整的Vision API问题 - 组合所有部分
    fun buildVisionQuestion(userQuestion: String, xmlData: String): String {
        val displayMetrics = context.resources.displayMetrics
        
        return buildString {
            // 1. 用户问题
            append(userQuestion)
            append("\n\n")
            
            // 2. 设备默认信息
            append("【设备信息】\n")
            append("- 屏幕尺寸: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}像素\n")
            append("- DPI: ${displayMetrics.densityDpi}\n\n")
            
            // 3. 可配置的提示词
            append(visionCustomPrompt)
            append("\n\n")
            
            // 4. XML数据
            append("【屏幕元素XML数据】\n")
            append(xmlData)
        }
    }

    // Dynamic Event Toggles
    fun isEventEnabled(type: EventType): Boolean {
        // Default all events to true unless explicitly disabled
        return sharedPrefs.getBoolean(PREFIX_EVENT_ENABLED + type.name, true)
    }

    fun setEventEnabled(type: EventType, enabled: Boolean) {
        sharedPrefs.edit { putBoolean(PREFIX_EVENT_ENABLED + type.name, enabled) }
        // We could notify listeners here if needed, but usually this is polled by EventHub
    }
    
    // Notification Package Whitelist Management
    fun getNotificationWhitelist(): Set<String> {
        val json = sharedPrefs.getString(KEY_NOTIFICATION_WHITELIST, null)
        if (json.isNullOrEmpty()) return emptySet()
        
        return try {
            org.json.JSONArray(json).let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setNotificationWhitelist(packages: Set<String>) {
        val jsonArray = org.json.JSONArray(packages.toList())
        sharedPrefs.edit { putString(KEY_NOTIFICATION_WHITELIST, jsonArray.toString()) }
    }
    
    fun addToNotificationWhitelist(packageName: String) {
        val current = getNotificationWhitelist().toMutableSet()
        current.add(packageName)
        setNotificationWhitelist(current)
    }
    
    fun removeFromNotificationWhitelist(packageName: String) {
        val current = getNotificationWhitelist().toMutableSet()
        current.remove(packageName)
        setNotificationWhitelist(current)
    }
    
    fun isPackageInNotificationWhitelist(packageName: String): Boolean {
        return getNotificationWhitelist().contains(packageName)
    }
    
    // MCP Tool Management
    fun getMcpToolsEnabled(): Set<String> {
        val json = sharedPrefs.getString(KEY_MCP_TOOLS_ENABLED, null)
        if (json.isNullOrEmpty()) {
            // Default: all tools enabled
            return setOf("calculator")
        }
        
        return try {
            org.json.JSONArray(json).let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
        } catch (e: Exception) {
            setOf("calculator")
        }
    }
    
    fun setMcpToolsEnabled(tools: Set<String>) {
        val jsonArray = org.json.JSONArray(tools.toList())
        sharedPrefs.edit { putString(KEY_MCP_TOOLS_ENABLED, jsonArray.toString()) }
    }
    
    fun isMcpToolEnabled(toolName: String): Boolean {
        return getMcpToolsEnabled().contains(toolName)
    }
    
    fun setMcpToolEnabled(toolName: String, enabled: Boolean) {
        val current = getMcpToolsEnabled().toMutableSet()
        if (enabled) {
            current.add(toolName)
        } else {
            current.remove(toolName)
        }
        setMcpToolsEnabled(current)
    }
    
    // Listener interface for configuration changes
    interface ConfigChangeListener {
        fun onOverlayVisibilityChanged(visible: Boolean)
        fun onOverlayOffsetChanged(offset: Int)
        fun onSocketServerEnabledChanged(enabled: Boolean)
        fun onSocketServerPortChanged(port: Int)
        // New WebSocket listeners
        fun onWebSocketEnabledChanged(enabled: Boolean) {}
        fun onWebSocketPortChanged(port: Int) {}
        fun onAuthEnabledChanged(enabled: Boolean) {}
    }
    
    private val listeners = mutableSetOf<ConfigChangeListener>()
    
    fun addListener(listener: ConfigChangeListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: ConfigChangeListener) {
        listeners.remove(listener)
    }
    
    fun setOverlayVisibleWithNotification(visible: Boolean) {
        overlayVisible = visible
        listeners.forEach { it.onOverlayVisibilityChanged(visible) }
    }
    
    fun setOverlayOffsetWithNotification(offset: Int) {
        overlayOffset = offset
        listeners.forEach { it.onOverlayOffsetChanged(offset) }
    }

    fun setSocketServerEnabledWithNotification(enabled: Boolean) {
        socketServerEnabled = enabled
        listeners.forEach { it.onSocketServerEnabledChanged(enabled) }
    }
    
    fun setSocketServerPortWithNotification(port: Int) {
        socketServerPort = port
        listeners.forEach { it.onSocketServerPortChanged(port) }
    }

    fun setWebSocketEnabledWithNotification(enabled: Boolean) {
        websocketEnabled = enabled
        listeners.forEach { it.onWebSocketEnabledChanged(enabled) }
    }

    fun setWebSocketPortWithNotification(port: Int) {
        websocketPort = port
        listeners.forEach { it.onWebSocketPortChanged(port) }
    }

    fun setAuthEnabledWithNotification(enabled: Boolean) {
        authEnabled = enabled
        listeners.forEach { it.onAuthEnabledChanged(enabled) }
    }
    
    // Bulk configuration update
    fun updateConfiguration(
        overlayVisible: Boolean? = null,
        overlayOffset: Int? = null,
        autoOffsetEnabled: Boolean? = null,
        socketServerEnabled: Boolean? = null,
        socketServerPort: Int? = null,
        websocketEnabled: Boolean? = null,
        websocketPort: Int? = null
    ) {
        val editor = sharedPrefs.edit()
        var hasChanges = false
        
        overlayVisible?.let {
            editor.putBoolean(KEY_OVERLAY_VISIBLE, it)
            hasChanges = true
        }
        
        overlayOffset?.let {
            editor.putInt(KEY_OVERLAY_OFFSET, it)
            hasChanges = true
        }

        autoOffsetEnabled?.let {
            editor.putBoolean(KEY_AUTO_OFFSET_ENABLED, it)
            hasChanges = true
        }

        socketServerEnabled?.let {
            editor.putBoolean(KEY_SOCKET_SERVER_ENABLED, it)
            hasChanges = true
        }
        
        socketServerPort?.let {
            editor.putInt(KEY_SOCKET_SERVER_PORT, it)
            hasChanges = true
        }

        websocketEnabled?.let {
            editor.putBoolean(KEY_WEBSOCKET_ENABLED, it)
            hasChanges = true
        }

        websocketPort?.let {
            editor.putInt(KEY_WEBSOCKET_PORT, it)
            hasChanges = true
        }
        
        if (hasChanges) {
            editor.apply()
            
            // Notify listeners
            overlayVisible?.let { listeners.forEach { listener -> listener.onOverlayVisibilityChanged(it) } }
            overlayOffset?.let { listeners.forEach { listener -> listener.onOverlayOffsetChanged(it) } }
            socketServerEnabled?.let { listeners.forEach { listener -> listener.onSocketServerEnabledChanged(it) } }
            socketServerPort?.let { listeners.forEach { listener -> listener.onSocketServerPortChanged(it) } }
            websocketEnabled?.let { listeners.forEach { listener -> listener.onWebSocketEnabledChanged(it) } }
            websocketPort?.let { listeners.forEach { listener -> listener.onWebSocketPortChanged(it) } }
        }
    }
    
    // Get all configuration as a data class
    data class Configuration(
        val overlayVisible: Boolean,
        val overlayOffset: Int,
        val autoOffsetEnabled: Boolean,
        val autoOffsetCalculated: Boolean,
        val socketServerEnabled: Boolean,
        val socketServerPort: Int,
        val websocketEnabled: Boolean,
        val websocketPort: Int,
        val authToken: String,
        val authEnabled: Boolean
    )

    fun getCurrentConfiguration(): Configuration {
        return Configuration(
            overlayVisible = overlayVisible,
            overlayOffset = overlayOffset,
            autoOffsetEnabled = autoOffsetEnabled,
            autoOffsetCalculated = autoOffsetCalculated,
            socketServerEnabled = socketServerEnabled,
            socketServerPort = socketServerPort,
            websocketEnabled = websocketEnabled,
            websocketPort = websocketPort,
            authToken = authToken,
            authEnabled = authEnabled
        )
    }
}