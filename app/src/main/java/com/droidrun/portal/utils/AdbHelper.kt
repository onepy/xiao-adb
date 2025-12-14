package com.droidrun.portal.utils

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB Helper
 * 用于检测和执行ADB命令
 */
object AdbHelper {
    
    private const val TAG = "AdbHelper"
    
    /**
     * 检测ADB是否可用
     * 直接尝试执行adb命令来检测
     */
    fun isAdbAvailable(): Boolean {
        return try {
            // 直接执行 adb version 来检测
            val process = Runtime.getRuntime().exec("adb version")
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Log.i(TAG, "ADB detected successfully")
                true
            } else {
                Log.w(TAG, "ADB command failed with exit code: $exitCode")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "ADB not available", e)
            false
        }
    }
    
    /**
     * 执行ADB shell命令
     * @param command shell命令(不包含"adb shell"前缀)
     * @return 命令输出结果
     */
    fun executeShellCommand(command: String): Result<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "shell", command))
            
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.readText()
            }
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Result.success(output.trim())
            } else {
                Result.failure(Exception("ADB command failed: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ADB command", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取设备电量信息
     */
    fun getBatteryInfo(): Result<BatteryInfo> {
        val result = executeShellCommand("dumpsys battery")
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull()!!)
        }
        
        return try {
            val output = result.getOrThrow()
            val level = output.lines().find { it.contains("level:") }
                ?.substringAfter("level:")?.trim()?.toIntOrNull() ?: 0
            val scale = output.lines().find { it.contains("scale:") }
                ?.substringAfter("scale:")?.trim()?.toIntOrNull() ?: 100
            val status = output.lines().find { it.contains("status:") }
                ?.substringAfter("status:")?.trim()?.toIntOrNull() ?: 0
            val temperature = output.lines().find { it.contains("temperature:") }
                ?.substringAfter("temperature:")?.trim()?.toIntOrNull() ?: 0
            
            Result.success(BatteryInfo(
                level = level,
                scale = scale,
                percentage = (level * 100 / scale),
                status = status,
                temperature = temperature / 10.0 // 转换为摄氏度
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取音量信息
     */
    fun getVolumeInfo(): Result<VolumeInfo> {
        // 获取媒体音量
        val mediaResult = executeShellCommand("cmd media_session volume --show --stream 3")
        // 获取铃声音量
        val ringerResult = executeShellCommand("cmd media_session volume --show --stream 2")
        
        return try {
            val mediaVolume = parseVolume(mediaResult.getOrNull() ?: "")
            val ringerVolume = parseVolume(ringerResult.getOrNull() ?: "")
            
            Result.success(VolumeInfo(
                mediaVolume = mediaVolume,
                ringerVolume = ringerVolume
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseVolume(output: String): Int {
        return try {
            output.lines().find { it.contains("volume") }
                ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    data class BatteryInfo(
        val level: Int,
        val scale: Int,
        val percentage: Int,
        val status: Int,
        val temperature: Double
    )
    
    data class VolumeInfo(
        val mediaVolume: Int,
        val ringerVolume: Int
    )
}