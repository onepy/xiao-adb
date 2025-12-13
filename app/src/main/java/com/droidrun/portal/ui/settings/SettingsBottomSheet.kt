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
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.droidrun.portal.R
import com.droidrun.portal.config.ConfigManager
import com.droidrun.portal.events.model.EventType
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
    }
    
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