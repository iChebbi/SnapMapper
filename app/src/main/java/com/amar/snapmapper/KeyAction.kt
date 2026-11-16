package com.amar.snapmapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast

enum class KeyAction(val label: String) {
    NONE("Do nothing"),
    FLASHLIGHT("Toggle flashlight"),
    SCREENSHOT("Take screenshot"),
    CAMERA("Open camera"),
    VIDEO("Record video"),
    LAUNCH_APP("Open an app…"),
    OPEN_URL("Open a link…"),
    PLAY_PAUSE("Play / pause media"),
    NEXT_TRACK("Next track"),
    PREV_TRACK("Previous track"),
    RING_VIBRATE("Switch ring / vibrate"),
    RING_MODE("Cycle ring / vibrate / silent"),
    ASSISTANT("Voice assistant"),
    NOTIFICATIONS("Show notifications"),
    QUICK_SETTINGS("Show quick settings"),
    RECENTS("Recent apps"),
    HOME("Go home"),
    BACK("Back"),
    LOCK_SCREEN("Lock screen"),
    POWER_MENU("Power menu"),
    SPLIT_SCREEN("Split screen"),
    TIMER("Start a timer"),
    WEB_SEARCH("Web search"),
    CIRCLE_TO_SEARCH("Circle to Search"),
}

/** Runs actions from inside the accessibility service. */
class ActionRunner(private val service: AccessibilityService) {

    private val main = Handler(Looper.getMainLooper())
    private val cameraManager = service.getSystemService(CameraManager::class.java)
    private val audio = service.getSystemService(AudioManager::class.java)
    private var torchOn = false
    private val torchCameraId: String? = cameraManager.cameraIdList.firstOrNull {
        cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }
    }

    init {
        cameraManager.registerTorchCallback(torchCallback, main)
    }

    fun release() = cameraManager.unregisterTorchCallback(torchCallback)

    /** Runs [action] and returns a short confirmation for the on-screen popup. */
    fun run(action: KeyAction, gesture: Gesture, prefs: Prefs, scope: Scope = Scope.NORMAL): String {
        var result = action.label
        try {
            when (action) {
                KeyAction.NONE -> Unit
                KeyAction.FLASHLIGHT -> result = toggleTorch()
                KeyAction.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                KeyAction.CAMERA -> start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                KeyAction.VIDEO -> start(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
                KeyAction.LAUNCH_APP -> {
                    val pkg = prefs.appPackage(gesture, scope)
                    launchApp(pkg)
                    result = "Opening ${ctxLabel(service, pkg) ?: "app"}"
                }
                KeyAction.OPEN_URL -> openUrl(prefs.url(gesture, scope))
                KeyAction.PLAY_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                KeyAction.NEXT_TRACK -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
                KeyAction.PREV_TRACK -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                KeyAction.RING_VIBRATE -> result = toggleRingVibrate()
                KeyAction.RING_MODE -> result = cycleRingMode()
                KeyAction.ASSISTANT -> start(Intent(Intent.ACTION_VOICE_COMMAND))
                KeyAction.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                KeyAction.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                KeyAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
                KeyAction.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
                KeyAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
                KeyAction.LOCK_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                KeyAction.POWER_MENU -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                KeyAction.SPLIT_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                KeyAction.TIMER -> start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_SKIP_UI, false))
                KeyAction.WEB_SEARCH -> start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, ""))
                KeyAction.CIRCLE_TO_SEARCH -> start(Intent(service, CircleToSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        } catch (e: Exception) {
            toast("${action.label} failed: ${e.message}")
        }
        return result
    }

    private fun toggleTorch(): String {
        val id = torchCameraId ?: run { toast("No flashlight found"); return "No flashlight" }
        val on = !torchOn
        cameraManager.setTorchMode(id, on)
        return if (on) "Flashlight on" else "Flashlight off"
    }

    private fun global(a: Int) {
        if (!service.performGlobalAction(a)) toast("This phone refused that action")
    }

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app can handle that")
        }
    }

    /**
     * Opens Circle to Search the way a finger does: a long press on the navigation handle.
     * Slower fallback for [CircleToSearchActivity]. Needs the handle (or the home button) to be shown.
     */
    fun holdNavHandle(holdMs: Int) {
        val metrics = service.getSystemService(WindowManager::class.java).currentWindowMetrics
        val bar = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        if (bar == 0) return toast("Circle to Search needs the navigation bar to be shown")
        val middleOfBar = Path().apply { moveTo(metrics.bounds.width() / 2f, metrics.bounds.height() - bar / 2f) }
        val press = GestureDescription.StrokeDescription(middleOfBar, 0, holdMs.toLong())
        if (!service.dispatchGesture(GestureDescription.Builder().addStroke(press).build(), null, null)) {
            toast("This phone refused that action")
        }
    }

    private fun launchApp(pkg: String?) {
        if (pkg == null) return toast("Pick an app in SnapMapper first")
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
            ?: return toast("That app is no longer installed")
        start(intent)
    }

    private fun openUrl(url: String?) {
        if (url.isNullOrBlank()) return toast("Set a link in SnapMapper first")
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun media(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun cycleRingMode() = setRinger(
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
    )

    private fun toggleRingVibrate() = setRinger(
        if (audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) AudioManager.RINGER_MODE_NORMAL
        else AudioManager.RINGER_MODE_VIBRATE
    )

    /**
     * Changes the ringer mode and confirms it by feel, so you know the result without looking:
     * vibrate = one long strong buzz, ring = two short strong buzzes, silent = one light tap.
     */
    private fun setRinger(mode: Int): String {
        try {
            audio.ringerMode = mode
        } catch (e: SecurityException) {
            // Silent can need Do Not Disturb access on some builds; go to ring instead.
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        return when (audio.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> {
                buzz(longArrayOf(0, 380), intArrayOf(0, 255)); "Vibrate"
            }
            AudioManager.RINGER_MODE_NORMAL -> {
                buzz(longArrayOf(0, 70, 110, 70), intArrayOf(0, 255, 0, 255)); "Ring"
            }
            else -> {
                buzz(longArrayOf(0, 40), intArrayOf(0, 160)); "Silent"
            }
        }
    }

    private fun buzz(timings: LongArray, amplitudes: IntArray) {
        val vibrator = service.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        // Short lead-in so it doesn't blend with ColorOS's own long-press buzz.
        val t = longArrayOf(150) + timings.drop(1)
        val effect = VibrationEffect.createWaveform(t, amplitudes, -1)
        // Alarm usage: same full strength whether the phone is locked, unlocked or silent
        // (touch/hardware feedback is scaled down by ColorOS while the screen is off).
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(service, msg, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        fun ctxLabel(context: Context, pkg: String?): String? = pkg?.let {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
            } catch (e: Exception) {
                null
            }
        }
    }
}
