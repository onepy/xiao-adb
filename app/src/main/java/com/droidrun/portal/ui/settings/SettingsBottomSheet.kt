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
import com.droidrun.portal.mcp.tools.AdbDeviceInfoTool
import com.droidrun.portal.service.ReverseConnectionService
import com.droidrun.portal.utils.AdbHelper
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

        switchWsEnabled.isChecked = configManager.websocketEnabled
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

        switchReverseEnabled.isChecked = configManager.reverseConnectionEnabled
        inputReverseUrl.setText(configManager.reverseConnectionUrl)

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
                    val intent = Intent(requireContext(), ReverseConnectionService::class.java)
                    requireContext().stopService(intent)
                    requireContext().startService(intent)
                    Toast.makeText(requireContext(), "正在重启MCP连接服务...", Toast.LENGTH_SHORT).show()
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
        
        // Setup ADB Tools List (separate section)
        setupAdbToolsList(view)
    }
    
    private fun setupMcpToolsList(root: View) {
        val toolsContainer = root.findViewById<LinearLayout>(R.id.mcp_tools_container)
        toolsContainer.removeAllViews()
        
        // 检查ADB是否可用
        val adbAvailable = AdbHelper.isAdbAvailable()
        
        // Get all available accessibility tools
        val accessibilityTools = listOf(
            ToolInfo(
                name = "calculator",
                displayName = "Calculator",
                description = "Mathematical expression evaluation",
                toolDefinition = CalculatorTool.getToolDefinition()
            ),
            ToolInfo(
                name = "get_state",
                displayName = "Get State",
                description = "Get current phone state and accessibility tree",
                toolDefinition = com.droidrun.portal.mcp.tools.GetStateTool.getToolDefinition()
            ),
            ToolInfo(
                name = "get_packages",
                displayName = "Get Packages",
                description = "List installed applications",
                toolDefinition = com.droidrun.portal.mcp.tools.GetPackagesTool.getToolDefinition()
            ),
            ToolInfo(
                name = "launch_app",
                displayName = "Launch App",
                description = "Start an application by package name",
                toolDefinition = com.droidrun.portal.mcp.tools.LaunchAppTool.getToolDefinition()
            ),
            ToolInfo(
                name = "input_text",
                displayName = "Input Text",
                description = "Type text into focused field",
                toolDefinition = com.droidrun.portal.mcp.tools.InputTextTool.getToolDefinition()
            ),
            ToolInfo(
                name = "clear_text",
                displayName = "Clear Text",
                description = "Clear text from focused field",
                toolDefinition = com.droidrun.portal.mcp.tools.ClearTextTool.getToolDefinition()
            ),
            ToolInfo(
                name = "press_key",
                displayName = "Press Key",
                description = "Simulate key press (ENTER, BACK, etc)",
                toolDefinition = com.droidrun.portal.mcp.tools.PressKeyTool.getToolDefinition()
            ),
            ToolInfo(
                name = "tap",
                displayName = "Tap",
                description = "Single tap at coordinates",
                toolDefinition = com.droidrun.portal.mcp.tools.TapTool.getToolDefinition()
            ),
            ToolInfo(
                name = "double_tap",
                displayName = "Double Tap",
                description = "Double tap at coordinates",
                toolDefinition = com.droidrun.portal.mcp.tools.DoubleTapTool.getToolDefinition()
            ),
            ToolInfo(
                name = "long_press",
                displayName = "Long Press",
                description = "Long press at coordinates",
                toolDefinition = com.droidrun.portal.mcp.tools.LongPressTool.getToolDefinition()
            ),
            ToolInfo(
                name = "swipe",
                displayName = "Swipe",
                description = "Swipe gesture between two points",
                toolDefinition = com.droidrun.portal.mcp.tools.SwipeTool.getToolDefinition()
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
    
    private fun setupAdbToolsList(root: View) {
        val adbSection = root.findViewById<LinearLayout>(R.id.adb_tools_section)
        adbSection.removeAllViews()
        
        // 检查ADB是否可用
        val adbAvailable = AdbHelper.isAdbAvailable()
        
        // Add ADB tools section if available
        if (adbAvailable) {
            val adbTools = listOf(
                ToolInfo(
                    name = "adb_device_info",
                    displayName = "ADB Device Info",
                    description = "Query battery, volume via ADB",
                    toolDefinition = AdbDeviceInfoTool.getToolDefinition()
                )
            )
            
            val adbSectionHeader = TextView(requireContext()).apply {
                text = "ADB工具 (${adbTools.size}) ✓ ADB已安装"
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            }
            adbSection.addView(adbSectionHeader)
            
            adbTools.forEach { toolInfo ->
                val toolView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_mcp_tool, adbSection, false)
                
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
                
                adbSection.addView(toolView)
            }
        } else {
            val adbWarning = TextView(requireContext()).apply {
                text = "ADB工具 (0) ✗ 未检测到ADB\n提示: 在Termux中运行 'pkg install android-tools' 安装"
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            }
            adbSection.addView(adbWarning)
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
    
    companion object {
        const val TAG = "SettingsBottomSheet"
    }
}