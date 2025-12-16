package com.droidrun.portal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.droidrun.portal.R
import com.droidrun.portal.api.ApiHandler
import com.droidrun.portal.config.ConfigManager
import com.droidrun.portal.core.StateRepository
import com.droidrun.portal.input.DroidrunKeyboardIME
import com.droidrun.portal.mcp.*
import com.droidrun.portal.mcp.tools.*
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reverse Connection Service
 * Connects to remote MCP server via WebSocket and exposes local tools
 */
class ReverseConnectionService : Service() {
    
    companion object {
        private const val TAG = "ReverseConnectionService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "reverse_connection_channel"
        private const val INITIAL_BACKOFF = 1000L // 1 second
        private const val MAX_BACKOFF = 60000L // 1 minute
        
        // Broadcast actions
        const val ACTION_CONNECTION_STATUS = "com.droidrun.portal.MCP_CONNECTION_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"
        
        // Status values
        const val STATUS_CONNECTING = "connecting"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_DISCONNECTED = "disconnected"
        const val STATUS_ERROR = "error"
    }
    
    private lateinit var configManager: ConfigManager
    private lateinit var apiHandler: ApiHandler
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var currentBackoff = INITIAL_BACKOFF
    private var isFirstConnection = true // 标记是否首次连接
    
    // MCP State
    private var isInitialized = false
    private val tools = mutableMapOf<String, McpToolHandler>()
    private var messageIdCounter = 0
    
    // Request Queue for handling requests during reconnection
    private data class PendingRequest(
        val id: Any,
        val method: String,
        val params: JSONObject?,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val pendingRequests = mutableListOf<PendingRequest>()
    private val maxPendingRequests = 10
    private val requestTimeout = 30000L // 30 seconds
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager.getInstance(this)
        
        // Initialize ApiHandler
        val service = DroidrunAccessibilityService.getInstance()
        if (service != null) {
            val stateRepo = StateRepository(service)
            apiHandler = ApiHandler(
                stateRepo = stateRepo,
                getKeyboardIME = { DroidrunKeyboardIME.getInstance() },
                getPackageManager = { packageManager },
                appVersionProvider = { "1.0.0" }
            )
            
            // Register all tools
            registerAllTools()
            Log.i(TAG, "Service created with ${tools.size} tools")
        } else {
            Log.e(TAG, "Failed to initialize: AccessibilityService not available")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        
        // Create notification for foreground service
        startForeground(NOTIFICATION_ID, createNotification("正在启动..."))
        
        // Start WebSocket connection
        connectToServer()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        
        webSocket?.close(1000, "Service destroyed")
        mainHandler.removeCallbacksAndMessages(null)
        broadcastStatus(STATUS_DISCONNECTED, "服务已停止")
    }
    
    private fun registerAllTools() {
        // Math tool
        tools["calculator"] = CalculatorTool()
        
        // Android operation tools (无障碍服务)
        tools["android.screen.dump"] = GetStateTool(apiHandler)
        tools["android.packages.list"] = GetPackagesTool(apiHandler)
        tools["android.launch_app"] = LaunchAppTool(apiHandler)
        tools["android.text.input"] = InputTextTool(apiHandler)
        tools["android.input.clear"] = ClearTextTool(apiHandler)
        tools["android.key.send"] = PressKeyTool(apiHandler)
        tools["android.tap"] = TapTool(apiHandler)
        tools["android.double_tap"] = DoubleTapTool(apiHandler)
        tools["android.long_press"] = LongPressTool(apiHandler)
        tools["android.swipe"] = SwipeTool(apiHandler)
        
        Log.i(TAG, "Registered ${tools.size} tools")
    }
    
    private fun connectToServer() {
        val url = configManager.reverseConnectionUrl
        if (url.isBlank()) {
            Log.e(TAG, "WebSocket URL is empty")
            updateNotification("连接失败: URL为空")
            broadcastStatus(STATUS_ERROR, "URL为空")
            stopSelf()
            return
        }
        
        Log.i(TAG, "Connecting to $url (attempt ${reconnectAttempt + 1})")
        updateNotification("正在连接... (尝试 ${reconnectAttempt + 1})")
        broadcastStatus(STATUS_CONNECTING, "正在连接到服务器...")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully")
                reconnectAttempt = 0
                currentBackoff = INITIAL_BACKOFF
                updateNotification("已连接,等待初始化")
                
                // 只在首次连接时发送广播提示
                if (isFirstConnection) {
                    broadcastStatus(STATUS_CONNECTED, "已连接,等待客户端初始化...")
                    isFirstConnection = false
                }
                
                // In reverse connection, Android is the SERVER
                // We wait for initialize request from remote client
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: ${text.take(200)}")
                handleMessage(text)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Received bytes: ${bytes.size}")
                handleMessage(bytes.utf8())
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                isInitialized = false
                broadcastStatus(STATUS_DISCONNECTED, "连接已关闭")
                scheduleReconnect()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                isInitialized = false
                val errorMsg = t.message ?: "未知错误"
                updateNotification("连接失败: $errorMsg")
                broadcastStatus(STATUS_ERROR, "连接失败: $errorMsg")
                scheduleReconnect()
            }
        })
    }
    
    private fun scheduleReconnect() {
        reconnectAttempt++
        val backoff = minOf(currentBackoff, MAX_BACKOFF)
        
        Log.i(TAG, "Scheduling reconnect in ${backoff}ms (attempt $reconnectAttempt)")
        updateNotification("等待重连... (${backoff/1000}秒)")
        
        mainHandler.postDelayed({
            if (configManager.reverseConnectionEnabled) {
                connectToServer()
            }
        }, backoff)
        
        currentBackoff = minOf(currentBackoff * 2, MAX_BACKOFF)
    }
    
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            
            // Check if it's a request or response
            if (json.has("method")) {
                // It's a request from server
                handleRequest(json)
            } else if (json.has("result") || json.has("error")) {
                // It's a response to our request
                handleResponse(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }
    
    private fun handleRequest(json: JSONObject) {
        val id = json.get("id")
        val method = json.getString("method")
        val params = json.optJSONObject("params")
        
        Log.i(TAG, "Handling request: $method")
        
        // Check if we should queue the request
        if (!isInitialized && method == "tools/call") {
            queueRequest(id, method, params)
            return
        }
        
        when (method) {
            "initialize" -> {
                val response = createInitializeResponse(id)
                sendMessage(response.toJson().toString())
                isInitialized = true
                Log.i(TAG, "MCP initialized with ${tools.size} tools")
                updateNotification("已初始化 (${tools.size}个工具)")
                
                // 只在首次初始化时发送广播提示
                if (!isFirstConnection) {
                    // 如果不是首次连接(即重连),不显示Toast
                    Log.i(TAG, "Reconnected and initialized silently")
                } else {
                    broadcastStatus(STATUS_CONNECTED, "已成功初始化 (${tools.size}个工具可用)")
                }
                
                // Process pending requests after initialization
                processPendingRequests()
            }
            "tools/list" -> {
                val response = createToolListResponse(id)
                sendMessage(response.toJson().toString())
            }
            "tools/call" -> {
                val response = handleToolCall(id, params)
                sendMessage(response.toJson().toString())
            }
            else -> {
                val error = McpError(
                    code = -32601,
                    message = "Method not found: $method"
                )
                val response = McpResponse(id = id, error = error)
                sendMessage(response.toJson().toString())
            }
        }
    }
    
    private fun handleResponse(json: JSONObject) {
        val response = McpResponse.fromJson(json)
        
        if (response.error != null) {
            Log.e(TAG, "Received error response: ${response.error.message}")
            return
        }
        
        Log.d(TAG, "Received response for id: ${response.id}")
    }
    
    private fun createInitializeResponse(id: Any): McpResponse {
        val result = JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("serverInfo", JSONObject().apply {
                put("name", "Droidrun Portal")
                put("version", "1.0.0")
            })
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject())
            })
        }
        
        Log.i(TAG, "Returning initialize response")
        return McpResponse(id = id, result = result)
    }
    
    private fun createToolListResponse(id: Any): McpResponse {
        val toolsList = mutableListOf<McpTool>()
        
        // Only add enabled tools
        val enabledTools = configManager.getMcpToolsEnabled()
        
        // Map of tool names to their definitions
        val toolDefinitions = mutableMapOf(
            "calculator" to CalculatorTool.getToolDefinition(),
            "android.screen.dump" to GetStateTool.getToolDefinition(),
            "android.packages.list" to GetPackagesTool.getToolDefinition(),
            "android.launch_app" to LaunchAppTool.getToolDefinition(),
            "android.text.input" to InputTextTool.getToolDefinition(),
            "android.input.clear" to ClearTextTool.getToolDefinition(),
            "android.key.send" to PressKeyTool.getToolDefinition(),
            "android.tap" to TapTool.getToolDefinition(),
            "android.double_tap" to DoubleTapTool.getToolDefinition(),
            "android.long_press" to LongPressTool.getToolDefinition(),
            "android.swipe" to SwipeTool.getToolDefinition()
        )
        
        // Add only enabled tools to the list
        enabledTools.forEach { toolName ->
            toolDefinitions[toolName]?.let { toolsList.add(it) }
        }
        
        val result = JSONObject().apply {
            put("tools", JSONArray().apply {
                toolsList.forEach { put(it.toJson()) }
            })
        }
        
        Log.i(TAG, "Returning ${toolsList.size} enabled tools")
        return McpResponse(id = id, result = result)
    }
    
    private fun handleToolCall(id: Any, params: JSONObject?): McpResponse {
        return try {
            if (params == null) {
                throw IllegalArgumentException("Missing params")
            }
            
            val toolName = params.getString("name")
            val arguments = params.optJSONObject("arguments")
            
            Log.i(TAG, "Calling tool: $toolName with args: $arguments")
            
            val handler = tools[toolName]
                ?: throw IllegalArgumentException("Tool not found: $toolName")
            
            val result = handler.execute(arguments)
            
            // Wrap in content array as per MCP spec
            val mcpResult = JSONObject().apply {
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", result.toString())
                    })
                })
            }
            
            Log.i(TAG, "Tool call successful: $result")
            McpResponse(id = id, result = mcpResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool", e)
            val error = McpError(
                code = -32603,
                message = "Internal error: ${e.message}"
            )
            McpResponse(id = id, error = error)
        }
    }
    
    private fun queueRequest(id: Any, method: String, params: JSONObject?) {
        synchronized(pendingRequests) {
            // Remove expired requests
            val now = System.currentTimeMillis()
            pendingRequests.removeAll { now - it.timestamp > requestTimeout }
            
            // Check queue size
            if (pendingRequests.size >= maxPendingRequests) {
                Log.w(TAG, "Request queue full, rejecting request")
                val error = McpError(
                    code = -32000,
                    message = "Server is reconnecting, request queue full. Please retry later."
                )
                val response = McpResponse(id = id, error = error)
                sendMessage(response.toJson().toString())
                return
            }
            
            // Add to queue
            pendingRequests.add(PendingRequest(id, method, params))
            Log.i(TAG, "Queued request $method, queue size: ${pendingRequests.size}")
            
            // Send pending response
            val error = McpError(
                code = -32001,
                message = "Server is initializing, request queued. It will be processed automatically when ready."
            )
            val response = McpResponse(id = id, error = error)
            sendMessage(response.toJson().toString())
        }
    }
    
    private fun processPendingRequests() {
        synchronized(pendingRequests) {
            if (pendingRequests.isEmpty()) {
                return
            }
            
            Log.i(TAG, "Processing ${pendingRequests.size} pending requests")
            val requestsToProcess = pendingRequests.toList()
            pendingRequests.clear()
            
            // Process each pending request
            requestsToProcess.forEach { request ->
                Log.i(TAG, "Processing queued request: ${request.method}")
                when (request.method) {
                    "tools/call" -> {
                        val response = handleToolCall(request.id, request.params)
                        sendMessage(response.toJson().toString())
                    }
                    else -> {
                        Log.w(TAG, "Unknown queued method: ${request.method}")
                    }
                }
            }
        }
    }
    
    private fun sendMessage(message: String) {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "Cannot send message: WebSocket not connected")
            return
        }
        
        // Note: We don't check isInitialized here because:
        // 1. initialize and tools/list responses need to be sent before isInitialized=true
        // 2. tools/call requests are already handled by queueing in handleRequest
        // 3. sendMessage should only check WebSocket connection status
        
        Log.d(TAG, "Sending: ${message.take(200)}")
        ws.send(message)
    }
    
    private fun nextMessageId(): Int {
        return ++messageIdCounter
    }
    
    private fun broadcastStatus(status: String, message: String) {
        val intent = Intent(ACTION_CONNECTION_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun createNotification(text: String): Notification {
        createNotificationChannel()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP反向连接")
            .setContentText(text)
            .setSmallIcon(R.drawable.unplug)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification(text))
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MCP反向连接服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示MCP反向连接状态"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}