package com.example.remotebuttontester

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var instructions: TextView
    private lateinit var eventLog: TextView
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logLines = ArrayDeque<String>()
    private val maxLogLines = 150

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Bluetooth Remote Button Tester"
            textSize = 22f
            setTextColor(0xFF172554.toInt())
            setPadding(0, 0, 0, 10)
        }
        root.addView(title)

        instructions = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFF1F2937.toInt())
            text = "Pair your remote in Android Settings → Bluetooth, return here, then press its buttons."
            setPadding(0, 0, 0, 12)
        }
        root.addView(instructions)

        val clearButton = Button(this).apply {
            text = "Clear log"
            setOnClickListener {
                logLines.clear()
                refreshLog()
                instructions.text = "Log cleared. Press a button on your remote to see its Android event."
            }
        }
        root.addView(clearButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val logScroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isFocusableInTouchMode = false
        }

        eventLog = TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF111827.toInt())
            setTextIsSelectable(true)
            setPadding(8, 12, 8, 12)
            gravity = Gravity.TOP
            text = "No button events yet.\n"
        }

        logScroll.addView(eventLog)
        root.addView(logScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        logKeyEvent(event)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = event.source and android.view.InputDevice.SOURCE_JOYSTICK ==
            android.view.InputDevice.SOURCE_JOYSTICK
        val isGamepad = event.source and android.view.InputDevice.SOURCE_GAMEPAD ==
            android.view.InputDevice.SOURCE_GAMEPAD
        if (isJoystick || isGamepad) {
            logMotionEvent(event)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun logKeyEvent(event: KeyEvent) {
        val device = event.device
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> "ACTION_${event.action}"
        }
        val line = buildString {
            append(timeFormat.format(Date(event.eventTime)))
            append("  $action  ")
            append(KeyEvent.keyCodeToString(event.keyCode))
            append(" (keyCode=${event.keyCode}, scanCode=${event.scanCode})")
            if (event.repeatCount > 0) append(" repeat=${event.repeatCount}")
            append("\n    device=${device?.name ?: "unknown"}")
            append(" | source=${sourceName(event.source)}")
            append(" | meta=${metaStateToString(event.metaState).ifEmpty { "none" }}")
        }
        addLogLine(line)
        runOnUiThread {
            instructions.text = "Event received. keyCode is Android's button identifier; scanCode is the device-specific code."
        }
    }

    private fun logMotionEvent(event: MotionEvent) {
        val device = event.device
        val line = buildString {
            append(timeFormat.format(Date(event.eventTime)))
            append("  MOTION  action=${event.actionMasked}")
            append("\n    device=${device?.name ?: "unknown"}")
            append(" | source=${sourceName(event.source)}")
            for (i in 0 until event.pointerCount) {
                append("\n    pointer $i: x=${event.getX(i)}, y=${event.getY(i)}")
            }
            for (axis in listOf(
                MotionEvent.AXIS_X,
                MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X,
                MotionEvent.AXIS_HAT_Y
            )) {
                val value = event.getAxisValue(axis)
                if (value != 0f) append("\n    ${MotionEvent.axisToString(axis)}=$value")
            }
        }
        addLogLine(line)
    }

    private fun sourceName(source: Int): String {
        val names = mutableListOf<String>()
        if (source and android.view.InputDevice.SOURCE_KEYBOARD == android.view.InputDevice.SOURCE_KEYBOARD) {
            names += "keyboard"
        }
        if (source and android.view.InputDevice.SOURCE_DPAD == android.view.InputDevice.SOURCE_DPAD) {
            names += "dpad"
        }
        if (source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD) {
            names += "gamepad"
        }
        if (source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK) {
            names += "joystick"
        }
        return if (names.isEmpty()) "0x${source.toString(16)}" else names.distinct().joinToString("+")
    }

    // Local replacement for KeyEvent.metaStateToString, which is a hidden
    // framework API and not callable from apps. Returns "" when no meta
    // keys are held so the call site can fall back to "none".
    private fun metaStateToString(metaState: Int): String {
        if (metaState == 0) return ""
        val parts = mutableListOf<String>()
        if ((metaState and KeyEvent.META_SHIFT_ON) != 0) parts += "SHIFT"
        if ((metaState and KeyEvent.META_ALT_ON) != 0) parts += "ALT"
        if ((metaState and KeyEvent.META_SYM_ON) != 0) parts += "SYM"
        if ((metaState and KeyEvent.META_FUNCTION_ON) != 0) parts += "FUNCTION"
        if ((metaState and KeyEvent.META_CTRL_ON) != 0) parts += "CTRL"
        if ((metaState and KeyEvent.META_META_ON) != 0) parts += "META"
        if ((metaState and KeyEvent.META_CAPS_LOCK_ON) != 0) parts += "CAPS_LOCK"
        if ((metaState and KeyEvent.META_NUM_LOCK_ON) != 0) parts += "NUM_LOCK"
        if ((metaState and KeyEvent.META_SCROLL_LOCK_ON) != 0) parts += "SCROLL_LOCK"
        return parts.joinToString("|")
    }

    private fun addLogLine(line: String) {
        runOnUiThread {
            logLines.addFirst(line)
            while (logLines.size > maxLogLines) logLines.removeLast()
            refreshLog()
        }
    }

    private fun refreshLog() {
        eventLog.text = if (logLines.isEmpty()) {
            "No button events yet.\n"
        } else {
            logLines.joinToString("\n\n")
        }
    }
}
