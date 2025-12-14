package com.droidrun.portal.mcp.tools

import android.util.Log
import com.droidrun.portal.mcp.McpTool
import com.droidrun.portal.utils.AdbHelper
import org.json.JSONObject

/**
 * ADB Device Info Tool - 查询设备信息
 */
class AdbDeviceInfoTool : McpToolHandler {
    
    companion object {
        private const val TAG = "AdbDeviceInfoTool"
        
        fun getToolDefinition(): McpTool {
            val inputSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("info_type", JSONObject().apply {
                        put("type", "string")
                        put("enum", org.json.JSONArray().apply {
                            put("battery")
                            put("volume")
                            put("all")
                        })
                        put("description", "Type of device info to query: 'battery' (battery status), 'volume' (audio volume), or 'all' (default)")
                        put("default", "all")
                    })
                })
            }
            
            return McpTool(
                name = "adb_device_info",
                description = "Query device information using ADB. Get battery level, temperature, volume, etc. Requires ADB tools installed (e.g., via Termux).",
                inputSchema = inputSchema
            )
        }
    }
    
    override fun execute(arguments: JSONObject?): JSONObject {
        return try {
            // 检查ADB是否可用
            if (!AdbHelper.isAdbAvailable()) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", "ADB not available. Please install ADB tools (e.g., 'pkg install android-tools' in Termux)")
                }
            }
            
            val infoType = arguments?.optString("info_type", "all") ?: "all"
            
            Log.i(TAG, "Querying device info: $infoType")
            
            val result = JSONObject().apply {
                put("success", true)
            }
            
            when (infoType) {
                "battery" -> {
                    val batteryInfo = AdbHelper.getBatteryInfo()
                    if (batteryInfo.isSuccess) {
                        val info = batteryInfo.getOrThrow()
                        result.put("battery", JSONObject().apply {
                            put("level", info.level)
                            put("scale", info.scale)
                            put("percentage", info.percentage)
                            put("status", when (info.status) {
                                2 -> "充电中"
                                3 -> "放电中"
                                4 -> "未充电"
                                5 -> "已充满"
                                else -> "未知"
                            })
                            put("temperature", info.temperature)
                        })
                    } else {
                        result.put("battery_error", batteryInfo.exceptionOrNull()?.message)
                    }
                }
                "volume" -> {
                    val volumeInfo = AdbHelper.getVolumeInfo()
                    if (volumeInfo.isSuccess) {
                        val info = volumeInfo.getOrThrow()
                        result.put("volume", JSONObject().apply {
                            put("media", info.mediaVolume)
                            put("ringer", info.ringerVolume)
                        })
                    } else {
                        result.put("volume_error", volumeInfo.exceptionOrNull()?.message)
                    }
                }
                else -> { // "all"
                    val batteryInfo = AdbHelper.getBatteryInfo()
                    if (batteryInfo.isSuccess) {
                        val info = batteryInfo.getOrThrow()
                        result.put("battery", JSONObject().apply {
                            put("level", info.level)
                            put("scale", info.scale)
                            put("percentage", info.percentage)
                            put("status", when (info.status) {
                                2 -> "充电中"
                                3 -> "放电中"
                                4 -> "未充电"
                                5 -> "已充满"
                                else -> "未知"
                            })
                            put("temperature", info.temperature)
                        })
                    }
                    
                    val volumeInfo = AdbHelper.getVolumeInfo()
                    if (volumeInfo.isSuccess) {
                        val info = volumeInfo.getOrThrow()
                        result.put("volume", JSONObject().apply {
                            put("media", info.mediaVolume)
                            put("ringer", info.ringerVolume)
                        })
                    }
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error querying device info", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
        }
    }
}