package com.droidrun.portal.service

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log

/**
 * Controller for executing gestures and global actions via AccessibilityService.
 * eliminating the need for ADB 'input' commands.
 */
object GestureController {
    private const val TAG = "GestureController"
    // default
    private const val TAP_DURATION = 50L
    private const val LONG_PRESS_DURATION = 1000L
    private const val DOUBLE_TAP_INTERVAL = 100L

    /**
     * Perform a tap at specific coordinates.
     */
    fun tap(x: Int, y: Int): Boolean {
        val service = DroidrunAccessibilityService.getInstance() ?: return false

        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            
            val result = service.dispatchGesture(gesture, null, null)
            Log.d(TAG, "Tap at ($x, $y) dispatched: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Tap error", e)
            false
        }
    }

    /**
     * Perform a swipe from (startX, startY) to (endX, endY).
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 300): Boolean {
        val service = DroidrunAccessibilityService.getInstance() ?: return false

        return try {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            // Clamp duration to reasonable limits for a swipe
            val dur = durationMs.coerceIn(10, 5000)
            
            val stroke = GestureDescription.StrokeDescription(path, 0, dur.toLong())
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            
            val result = service.dispatchGesture(gesture, null, null)
            Log.d(TAG, "Swipe ($startX,$startY)->($endX,$endY) dispatched: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Swipe error", e)
            false
        }
    }

    /**
     * Perform a global action (Home, Back, Recents, etc.)
     * 
     * @param action The global action constant (e.g. AccessibilityService.GLOBAL_ACTION_HOME)
     */
    fun performGlobalAction(action: Int): Boolean {
        val service = DroidrunAccessibilityService.getInstance() ?: return false
        
        return try {
            val result = service.performGlobalAction(action)
            Log.d(TAG, "Global Action $action performed: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Global action error", e)
            false
        }
    }

    /**
     * Perform a long press at specific coordinates.
     * @param durationMs Duration to hold the press (default 1000ms)
     */
    fun longPress(x: Int, y: Int, durationMs: Long = LONG_PRESS_DURATION): Boolean {
        val service = DroidrunAccessibilityService.getInstance() ?: return false

        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            
            val result = service.dispatchGesture(gesture, null, null)
            Log.d(TAG, "Long press at ($x, $y) for ${durationMs}ms dispatched: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Long press error", e)
            false
        }
    }

    /**
     * Perform a double tap at specific coordinates.
     */
    fun doubleTap(x: Int, y: Int): Boolean {
        val service = DroidrunAccessibilityService.getInstance() ?: return false

        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            
            // First tap
            val stroke1 = GestureDescription.StrokeDescription(path, 0, TAP_DURATION)
            // Second tap with interval
            val stroke2 = GestureDescription.StrokeDescription(path, TAP_DURATION + DOUBLE_TAP_INTERVAL, TAP_DURATION)
            
            val gesture = GestureDescription.Builder()
                .addStroke(stroke1)
                .addStroke(stroke2)
                .build()
            
            val result = service.dispatchGesture(gesture, null, null)
            Log.d(TAG, "Double tap at ($x, $y) dispatched: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Double tap error", e)
            false
        }
    }
}
