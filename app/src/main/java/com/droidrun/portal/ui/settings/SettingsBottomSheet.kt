package com.droidrun.portal.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.droidrun.portal.R
import com.droidrun.portal.config.ConfigManager
import com.droidrun.portal.events.model.EventType
import com.droidrun.portal.mcp.tools.CalculatorTool
import com.droidrun.portal.service.ReverseConnectionService
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var configManager: ConfigManager
    
    private val connectionStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ReverseConnectionService.ACTION_CONNECTION_STATUS) {
                val status = intent.getStringExtra(ReverseConnectionService.EXTRA_STATUS)
                val message = intent.getStringExtra(ReverseConnectionService.EXTRA_MESSAGE)
                
                when (status) {
                    ReverseConnectionService.STATUS_CONNECTED -> {
                        Toast.makeText(context, "✓ $message", Toast.LENGTH_SHORT).show()
                    }
                    ReverseConnectionService.STATUS_ERROR -> {
                        Toast.makeText(context, "✗ $message", Toast.LENGTH_LONG).show()
                    }
                    ReverseConnectionService.STATUS_CONNECTING -> {
                        Toast.makeText(context, "⟳ $message", Toast.LENGTH_SHORT).show()
                    }
                    ReverseConnectionService.STATUS_DISCONNECTED -> {
                        Toast.makeText(context, "○ $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configManager = ConfigManager.getInstance(requireContext())
        
        // Register broadcast receiver for connection status
        val filter = IntentFilter(ReverseConnectionService.ACTION_CONNECTION_STATUS)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(connectionStatusReceiver, filter)

        // Server Settings
        val switchWsEnabled = view.findViewById<SwitchMaterial>(R.id.switch_ws_enabled)
        val inputWsPort = view.findViewById<TextInputEditText>(R.id.input_ws_port)
        val inputScreenshotQuality = view.findViewById<TextInputEditText>(R.id.input_screenshot_quality)
        val inputVisionPrompt = view.findViewById<TextInputEditText>(R.id.input_vision_prompt)

        switchWsEnabled.isChecked = configManager.websocketEnabled
        
        // Setup screenshot quality input
        inputScreenshotQuality.setText(configManager.screenshotQuality.toString())
        inputScreenshotQuality.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val quality = s.toString().toIntOrNull()
                if (quality != null && quality in 1..100) {
                    configManager.screenshotQuality = quality
                }
            }
        })
        inputScreenshotQuality.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val quality = v.text.toString().toIntOrNull()
                if (quality != null && quality in 1..100) {
                    configManager.screenshotQuality = quality
                    inputScreenshotQuality.clearFocus()
                    Toast.makeText(requireContext(), "截图质量已设置为 $quality%", Toast.LENGTH_SHORT).show()
                } else {
                    inputScreenshotQuality.error = "Invalid Quality (1-100)"
                }
                true
            } else {
                false
            }
        }
        
        // Setup vision prompt input
        inputVisionPrompt.setText(configManager.visionCustomPrompt)
        inputVisionPrompt.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val prompt = v.text.toString().trim()
                if (prompt.isBlank()) {
                    // 如果输入为空，恢复为默认值
                    val defaultPrompt = ConfigManager.getInstance(requireContext()).visionCustomPrompt
                    inputVisionPrompt.setText(defaultPrompt)
                    configManager.visionCustomPrompt = defaultPrompt
                    Toast.makeText(requireContext(), "输入不能为空，已恢复默认值", Toast.LENGTH_SHORT).show()
                } else {
                    configManager.visionCustomPrompt = prompt
                    Toast.makeText(requireContext(), "Vision提示词已更新", Toast.LENGTH_SHORT).show()
                }
                inputVisionPrompt.clearFocus()
                true
            } else {
                false
            }
        }
        
        // 添加失去焦点时的验证
        inputVisionPrompt.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val prompt = inputVisionPrompt.text.toString().trim()
                if (prompt.isBlank()) {
                    // 如果失去焦点时输入为空，恢复为当前配置值
                    inputVisionPrompt.setText(configManager.visionCustomPrompt)
                } else if (prompt != configManager.visionCustomPrompt) {
                    // 自动保存修改
                    configManager.visionCustomPrompt = prompt
                }
            }
        }
        
        switchWsEnabled.setOnCheckedChangeListener { _, isChecked ->
            configManager.setWebSocketEnabledWithNotification(isChecked)
        }

        inputWsPort.setText(configManager.websocketPort.toString())
        inputWsPort.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val port = v.text.toString().toIntOrNull()
                if (port != null && port in 1024..65535) {
                    configManager.setWebSocketPortWithNotification(port)
                    inputWsPort.clearFocus()
                } else {
                    inputWsPort.error = "Invalid Port"
                }
                true
            } else {
                false
            }
        }

        // Reverse Connection Settings
        val switchReverseEnabled = view.findViewById<SwitchMaterial>(R.id.switch_reverse_enabled)
        val inputReverseUrl = view.findViewById<TextInputEditText>(R.id.input_reverse_url)
        val inputHeartbeatInterval = view.findViewById<TextInputEditText>(R.id.input_heartbeat_interval)
        val inputHeartbeatTimeout = view.findViewById<TextInputEditText>(R.id.input_heartbeat_timeout)
        val inputReconnectInterval = view.findViewById<TextInputEditText>(R.id.input_reconnect_interval)

        switchReverseEnabled.isChecked = configManager.reverseConnectionEnabled
        inputReverseUrl.setText(configManager.reverseConnectionUrl)
        
        // Setup heartbeat configuration inputs (in seconds for better UX)
        inputHeartbeatInterval.setText((configManager.heartbeatInterval / 1000).toString())
        inputHeartbeatTimeout.setText((configManager.heartbeatTimeout / 1000).toString())
        inputReconnectInterval.setText((configManager.reconnectInterval / 1000).toString())
        
        // Heartbeat Interval
        inputHeartbeatInterval.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveHeartbeatInterval(inputHeartbeatInterval)
                inputHeartbeatInterval.clearFocus()
                true
            } else {
                false
            }
        }
        
        inputHeartbeatInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveHeartbeatInterval(inputHeartbeatInterval)
            }
        }
        
        // Heartbeat Timeout
        inputHeartbeatTimeout.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveHeartbeatTimeout(inputHeartbeatTimeout)
                inputHeartbeatTimeout.clearFocus()
                true
            } else {
                false
            }
        }
        
        inputHeartbeatTimeout.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveHeartbeatTimeout(inputHeartbeatTimeout)
            }
        }
        
        // Reconnect Interval
        inputReconnectInterval.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveReconnectInterval(inputReconnectInterval)
                inputReconnectInterval.clearFocus()
                true
            } else {
                false
            }
        }
        
        inputReconnectInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveReconnectInterval(inputReconnectInterval)
            }
        }

        // Toggle Service on Switch Change
        switchReverseEnabled.setOnCheckedChangeListener { _, isChecked ->
            configManager.reverseConnectionEnabled = isChecked
            
            val intent = Intent(requireContext(), ReverseConnectionService::class.java)
            if (isChecked) {
                // Ensure URL is saved before starting
                val url = inputReverseUrl.text.toString()
                if (url.isNotBlank()) {
                    configManager.reverseConnectionUrl = url
                    requireContext().startService(intent)
                    Toast.makeText(requireContext(), "正在启动MCP连接服务...", Toast.LENGTH_SHORT).show()
                } else {
                    inputReverseUrl.error = "URL required"
                    switchReverseEnabled.isChecked = false
                    Toast.makeText(requireContext(), "请先输入服务器URL", Toast.LENGTH_SHORT).show()
                }
            } else {
                requireContext().stopService(intent)
                Toast.makeText(requireContext(), "正在停止MCP连接服务...", Toast.LENGTH_SHORT).show()
            }
        }

        inputReverseUrl.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                configManager.reverseConnectionUrl = v.text.toString()
                inputReverseUrl.clearFocus()
                
                // If enabled, restart service to pick up new URL
                if (configManager.reverseConnectionEnabled) {
                    restartReverseConnectionService()
                }
                true
            } else {
                false
            }
        }

        // Event Filters
        setupEventToggle(view, R.id.switch_event_notification, EventType.NOTIFICATION)
        
        // Notification Apps Selector
        val notificationAppsSelector = view.findViewById<View>(R.id.notification_apps_selector)
        val notificationAppsCount = view.findViewById<TextView>(R.id.notification_apps_count)
        
        updateNotificationAppsCount(notificationAppsCount)
        
        notificationAppsSelector.setOnClickListener {
            val dialog = NotificationAppSelectorDialog()
            dialog.show(parentFragmentManager, NotificationAppSelectorDialog.TAG)
        }
        
        // Setup MCP Tools List
        setupMcpToolsList(view)
    }
    
    private fun setupMcpToolsList(root: View) {
        val toolsContainer = root.findViewById<LinearLayout>(R.id.mcp_tools_container)
        toolsContainer.removeAllViews()
        
        // Get all available accessibility tools
        val accessibilityTools = listOf(
            ToolInfo(
                name = "calculator",
                displayName = "Calculator",
                description = "Mathematical expression evaluation",
                toolDefinition = CalculatorTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.screen.dump",
                displayName = "Screen Dump",
                description = "获取当前屏幕交互内容",
                toolDefinition = com.droidrun.portal.mcp.tools.GetStateTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.screen.vision",
                displayName = "Screen Vision",
                description = "AI视觉分析屏幕内容",
                toolDefinition = com.droidrun.portal.mcp.tools.ScreenVisionTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.packages.list",
                displayName = "Packages List",
                description = "获取已安装应用列表",
                toolDefinition = com.droidrun.portal.mcp.tools.GetPackagesTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.launch_app",
                displayName = "Launch App",
                description = "启动指定应用",
                toolDefinition = com.droidrun.portal.mcp.tools.LaunchAppTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.text.input",
                displayName = "Text Input",
                description = "输入文本",
                toolDefinition = com.droidrun.portal.mcp.tools.InputTextTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.input.clear",
                displayName = "Clear Text",
                description = "清除文本",
                toolDefinition = com.droidrun.portal.mcp.tools.ClearTextTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.key.send",
                displayName = "Send Key",
                description = "发送按键",
                toolDefinition = com.droidrun.portal.mcp.tools.PressKeyTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.tap",
                displayName = "Tap",
                description = "点击屏幕",
                toolDefinition = com.droidrun.portal.mcp.tools.TapTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.double_tap",
                displayName = "Double Tap",
                description = "双击屏幕",
                toolDefinition = com.droidrun.portal.mcp.tools.DoubleTapTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.long_press",
                displayName = "Long Press",
                description = "长按屏幕",
                toolDefinition = com.droidrun.portal.mcp.tools.LongPressTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.swipe",
                displayName = "Swipe",
                description = "滑动屏幕",
                toolDefinition = com.droidrun.portal.mcp.tools.SwipeTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.wait",
                displayName = "Wait",
                description = "等待页面加载",
                toolDefinition = com.droidrun.portal.mcp.tools.WaitTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.find",
                displayName = "Find Element",
                description = "查找控件元素",
                toolDefinition = com.droidrun.portal.mcp.tools.FindElementTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.click",
                displayName = "Click Element",
                description = "点击控件",
                toolDefinition = com.droidrun.portal.mcp.tools.ClickElementTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.scroll",
                displayName = "Scroll Element",
                description = "滚动控件内部",
                toolDefinition = com.droidrun.portal.mcp.tools.ScrollElementTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.long_press",
                displayName = "Long Press Element",
                description = "长按控件",
                toolDefinition = com.droidrun.portal.mcp.tools.LongPressElementTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.set_text",
                displayName = "Set Text",
                description = "设置控件文本",
                toolDefinition = com.droidrun.portal.mcp.tools.SetTextTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.toggle_checkbox",
                displayName = "Toggle Checkbox",
                description = "切换复选框状态",
                toolDefinition = com.droidrun.portal.mcp.tools.ToggleCheckboxTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.double_tap",
                displayName = "Double Tap Element",
                description = "双击控件",
                toolDefinition = com.droidrun.portal.mcp.tools.DoubleTapElementTool.getToolDefinition()
            ),
            ToolInfo(
                name = "android.element.drag",
                displayName = "Drag Element",
                description = "拖动控件",
                toolDefinition = com.droidrun.portal.mcp.tools.DragElementTool.getToolDefinition()
            )
        )
        
        // Add each accessibility tool to the container (only accessibility tools)
        accessibilityTools.forEach { toolInfo ->
            val toolView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_mcp_tool, toolsContainer, false)
            
            val toolName = toolView.findViewById<TextView>(R.id.tool_name)
            val toolDescription = toolView.findViewById<TextView>(R.id.tool_description)
            val toolSwitch = toolView.findViewById<SwitchMaterial>(R.id.tool_switch)
            
            toolName.text = toolInfo.displayName
            toolDescription.text = toolInfo.description
            toolSwitch.isChecked = configManager.isMcpToolEnabled(toolInfo.name)
            
            toolSwitch.setOnCheckedChangeListener { _, isChecked ->
                configManager.setMcpToolEnabled(toolInfo.name, isChecked)
                
                // If reverse connection is active, restart service to update tools
                if (configManager.reverseConnectionEnabled) {
                    val intent = Intent(requireContext(), ReverseConnectionService::class.java)
                    requireContext().stopService(intent)
                    requireContext().startService(intent)
                    Toast.makeText(
                        requireContext(),
                        if (isChecked) "已启用 ${toolInfo.displayName}" else "已禁用 ${toolInfo.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            
            toolsContainer.addView(toolView)
        }
    }
    
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private data class ToolInfo(
        val name: String,
        val displayName: String,
        val description: String,
        val toolDefinition: com.droidrun.portal.mcp.McpTool
    )
    
    private fun updateNotificationAppsCount(textView: TextView) {
        val count = configManager.getNotificationWhitelist().size
        textView.text = if (count > 0) {
            "已选择 $count 个应用"
        } else {
            "所有应用 (未限制)"
        }
    }

    private fun setupEventToggle(root: View, switchId: Int, type: EventType) {
        val switch = root.findViewById<SwitchMaterial>(switchId)
        switch.isChecked = configManager.isEventEnabled(type)
        
        switch.setOnCheckedChangeListener { _, isChecked ->
            configManager.setEventEnabled(type, isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(connectionStatusReceiver)
    }
    
    private fun saveHeartbeatInterval(input: TextInputEditText) {
        val seconds = input.text.toString().toIntOrNull()
        if (seconds != null && seconds in 5..300) {
            val oldValue = configManager.heartbeatInterval
            val newValue = seconds * 1000L
            if (oldValue != newValue) {
                configManager.heartbeatInterval = newValue
                
                // Restart service if enabled to apply new settings
                if (configManager.reverseConnectionEnabled) {
                    restartReverseConnectionService()
                }
            }
        } else {
            // 恢复为当前配置值
            input.setText((configManager.heartbeatInterval / 1000).toString())
            if (seconds != null) {
                input.error = "范围: 5-300秒"
            }
        }
    }
    
    private fun saveHeartbeatTimeout(input: TextInputEditText) {
        val seconds = input.text.toString().toIntOrNull()
        if (seconds != null && seconds in 1..60) {
            val oldValue = configManager.heartbeatTimeout
            val newValue = seconds * 1000L
            if (oldValue != newValue) {
                configManager.heartbeatTimeout = newValue
                
                // Restart service if enabled to apply new settings
                if (configManager.reverseConnectionEnabled) {
                    restartReverseConnectionService()
                }
            }
        } else {
            // 恢复为当前配置值
            input.setText((configManager.heartbeatTimeout / 1000).toString())
            if (seconds != null) {
                input.error = "范围: 1-60秒"
            }
        }
    }
    
    private fun saveReconnectInterval(input: TextInputEditText) {
        val seconds = input.text.toString().toIntOrNull()
        if (seconds != null && seconds in 1..120) {
            val oldValue = configManager.reconnectInterval
            val newValue = seconds * 1000L
            if (oldValue != newValue) {
                configManager.reconnectInterval = newValue
                
                // Restart service if enabled to apply new settings
                if (configManager.reverseConnectionEnabled) {
                    restartReverseConnectionService()
                }
            }
        } else {
            // 恢复为当前配置值
            input.setText((configManager.reconnectInterval / 1000).toString())
            if (seconds != null) {
                input.error = "范围: 1-120秒"
            }
        }
    }
    
    private fun restartReverseConnectionService() {
        val intent = Intent(requireContext(), ReverseConnectionService::class.java)
        requireContext().stopService(intent)
        requireContext().startService(intent)
        Toast.makeText(requireContext(), "正在重启MCP连接服务...", Toast.LENGTH_SHORT).show()
    }
    
    companion object {
        const val TAG = "SettingsBottomSheet"
    }
}