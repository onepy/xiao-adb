package com.droidrun.portal.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.droidrun.portal.R
import com.droidrun.portal.config.ConfigManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NotificationAppSelectorDialog : BottomSheetDialogFragment() {

    private lateinit var configManager: ConfigManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private val installedApps = mutableListOf<AppInfo>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_notification_app_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        configManager = ConfigManager.getInstance(requireContext())
        
        recyclerView = view.findViewById(R.id.apps_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        loadInstalledApps()
        
        adapter = AppAdapter(installedApps) { packageName, isChecked ->
            if (isChecked) {
                configManager.addToNotificationWhitelist(packageName)
            } else {
                configManager.removeFromNotificationWhitelist(packageName)
            }
        }
        
        recyclerView.adapter = adapter
    }

    private fun loadInstalledApps() {
        val pm = requireContext().packageManager
        val whitelist = configManager.getNotificationWhitelist()
        
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || whitelist.contains(it.packageName) }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        
        installedApps.clear()
        packages.forEach { appInfo ->
            installedApps.add(
                AppInfo(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isChecked = whitelist.contains(appInfo.packageName)
                )
            )
        }
    }

    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable,
        var isChecked: Boolean
    )

    private class AppAdapter(
        private val apps: List<AppInfo>,
        private val onCheckedChange: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val label: TextView = view.findViewById(R.id.app_label)
            val packageName: TextView = view.findViewById(R.id.app_package)
            val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            holder.packageName.text = app.packageName
            
            // 移除旧的监听器,避免在设置isChecked时触发
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isChecked
            
            // 整个item点击时切换状态
            holder.itemView.setOnClickListener {
                app.isChecked = !app.isChecked
                holder.checkbox.isChecked = app.isChecked
                onCheckedChange(app.packageName, app.isChecked)
            }
            
            // CheckBox点击时也切换状态
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (app.isChecked != isChecked) {
                    app.isChecked = isChecked
                    onCheckedChange(app.packageName, isChecked)
                }
            }
        }

        override fun getItemCount() = apps.size
    }

    companion object {
        const val TAG = "NotificationAppSelector"
    }
}