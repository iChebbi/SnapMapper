package com.amar.snapmapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * Runs Snap Key actions. Presses arrive from ColorOS through KeyMessengerService (Snap Key set
 * to "Translate"). This accessibility service supplies what normal apps can't do: global actions
 * (screenshot, lock screen…), starting apps from the background, the on-screen confirmation
 * overlay, and knowing which app is in front (only while per-app actions exist).
 */
class SnapKeyService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var runner: ActionRunner
    private val handler = Handler(Looper.getMainLooper())

    /** Package of the app currently in front; tracked only while per-app actions exist. */
    @Volatile private var foregroundPkg: String? = null
    private var ignoredPkgs: Set<String> = emptySet()

    override fun onServiceConnected() {
        prefs = Prefs(this)
        runner = ActionRunner(this)
        instance = this
        reconfigure()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        removePopup()
        if (::runner.isInitialized) runner.release()
        instance = null
        listener?.onServiceStateChanged()
        super.onDestroy()
    }

    /** Applies settings: window tracking is switched on only while per-app actions exist. */
    fun reconfigure() {
        val trackApps = prefs.appRules.isNotEmpty()
        serviceInfo = serviceInfo?.apply {
            eventTypes = if (trackApps) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        }
        if (!trackApps) foregroundPkg = null
        ignoredPkgs = buildSet {
            add(packageName); add("android"); add("com.android.systemui"); add("com.oplus.gesture")
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { add(it.packageName) }
        }
        listener?.onServiceStateChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in ignoredPkgs) foregroundPkg = pkg
    }

    override fun onInterrupt() = Unit

    // ---- Presses: ColorOS sends a completed tap, or "long press reached" ----

    private var tapPending = false
    private val tapRunnable = Runnable {
        tapPending = false
        fire(Gesture.SINGLE)
    }

    internal fun onSnapTap() {
        when {
            tapPending -> {
                handler.removeCallbacks(tapRunnable)
                tapPending = false
                fire(Gesture.DOUBLE)
            }
            !doubleInUse() -> fire(Gesture.SINGLE)
            else -> {
                tapPending = true
                handler.postDelayed(tapRunnable, prefs.doublePressMs.toLong())
            }
        }
    }

    internal fun holdNavHandle() = runner.holdNavHandle(prefs.circleHoldMs)

    internal fun onSnapLong() {
        handler.removeCallbacks(tapRunnable)
        tapPending = false
        // ColorOS always wakes the screen on a long press. If that's what just turned it on,
        // remember it so a "quiet" action can turn it back off.
        // The screen-on broadcast can arrive after this call, so "off more recently than on"
        // also counts as woken by this press.
        val now = System.currentTimeMillis()
        val wokeByPress = getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            (screenOffAt > screenOnAt || now - screenOnAt < 2500)
        fire(Gesture.LONG, wokeByPress)
    }

    // ---- Screen-on tracking (for undoing ColorOS's long-press wake) ----

    @Volatile private var screenOnAt = 0L
    @Volatile private var screenOffAt = 0L
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: android.content.Intent) {
            if (i.action == android.content.Intent.ACTION_SCREEN_ON) screenOnAt = System.currentTimeMillis()
            else screenOffAt = System.currentTimeMillis()
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
        })
    }

    /** Actions that don't need the screen, so a press-caused wake-up can be undone. */
    private fun isQuiet(a: KeyAction) = a in setOf(
        KeyAction.FLASHLIGHT, KeyAction.RING_VIBRATE, KeyAction.RING_MODE,
        KeyAction.PLAY_PAUSE, KeyAction.NEXT_TRACK, KeyAction.PREV_TRACK, KeyAction.NONE,
    )

    /** Waiting for a second tap only makes sense if some double-press action is set. */
    private fun doubleInUse(): Boolean =
        prefs.action(Gesture.DOUBLE) != KeyAction.NONE ||
            (prefs.lockedActionsEnabled && prefs.action(Gesture.DOUBLE, Scope.LOCKED) != KeyAction.NONE)

    private fun fire(g: Gesture, wokeByPress: Boolean = false) {
        val audio = getSystemService(AudioManager::class.java)
        if (prefs.pauseInCalls &&
            (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION)
        ) {
            history(g, "Ignored during call")
            return
        }

        val power = getSystemService(PowerManager::class.java)
        // A long press that woke the screen counts as "screen off" for choosing actions.
        val screenOn = power.isInteractive && !wokeByPress
        val locked = !screenOn || getSystemService(KeyguardManager::class.java).isKeyguardLocked

        // Pick which set of actions applies.
        var scope = Scope.NORMAL
        var where = ""
        if (locked && prefs.lockedActionsEnabled) {
            scope = Scope.LOCKED
            where = "locked"
        } else if (!locked && g == Gesture.SINGLE) {
            foregroundPkg?.takeIf { it in prefs.appRules }?.let {
                scope = Scope.app(it)
                where = ActionRunner.ctxLabel(this, it) ?: it
            }
        }

        val action = prefs.action(g, scope)
        // Undo ColorOS's long-press wake-up when the action doesn't need the screen.
        if (wokeByPress && isQuiet(action)) {
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 400)
        }
        if (action == KeyAction.NONE) {
            history(g, "Nothing set", where)
            return
        }
        val ownHaptics = action == KeyAction.RING_VIBRATE || action == KeyAction.RING_MODE
        if (prefs.vibrate && !ownHaptics) tick()

        val result = runner.run(action, g, prefs, scope)
        // The flashlight is its own confirmation, and so is Circle to Search (which would also capture the pill).
        if (prefs.showPopup && screenOn && action != KeyAction.FLASHLIGHT && action != KeyAction.CIRCLE_TO_SEARCH) popup(result)
        history(g, result, where)
        listener?.onGesture(g, action)
    }

    private fun tick() {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun history(g: Gesture, what: String, where: String = "") {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val suffix = if (where.isNotEmpty()) "  ·  $where" else ""
        prefs.addHistory("$time  ${g.label} → $what$suffix")
    }

    // ---- On-screen confirmation: a small pill around the front camera ----
    //
    // One small overlay window is created once and kept (unfocusable, untouchable). Between
    // presses its surface is hidden (View.GONE), so it draws nothing and costs no battery.
    // Each press only animates the pill inside it.

    private var islandRoot: android.widget.FrameLayout? = null
    private var islandPill: View? = null
    private var islandIcon: android.widget.ImageView? = null
    private var islandLp: WindowManager.LayoutParams? = null
    private val hideIsland = Runnable {
        val pill = islandPill ?: return@Runnable
        pill.animate().alpha(0f).scaleX(0.4f).scaleY(0.9f).setDuration(220)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                pill.visibility = View.INVISIBLE
                islandRoot?.visibility = View.GONE
            }.start()
    }

    private fun px(v: Float) = (v * resources.displayMetrics.density).toInt()

    /** ~80 × 26 dp, centred on the camera and the middle of the status bar. */
    private fun islandLayout(wm: WindowManager): WindowManager.LayoutParams {
        val metrics = wm.currentWindowMetrics
        val cutout = metrics.windowInsets.displayCutout?.boundingRects
            ?.filter { it.top < px(80f) }?.minByOrNull { it.top }
        val camCenterX = cutout?.centerX() ?: (metrics.bounds.width() / 2)
        val centerY = (cutout?.top ?: 0) + (cutout?.height() ?: px(40f)) / 2 + px(prefs.islandOffsetDp.toFloat())
        val w = px(80f)
        val h = px(26f)
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = camCenterX - w / 2
            y = maxOf(0, centerY - h / 2)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "SnapMapper island"
        }
    }

    private fun ensureIsland(wm: WindowManager): Boolean {
        val lp = islandLayout(wm)
        val root = islandRoot
        if (root != null) {
            val old = islandLp!!
            if (old.x != lp.x || old.y != lp.y || old.width != lp.width) {
                islandLp = lp
                try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
            }
            return true
        }
        val cutoutW = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
            ?.filter { it.top < px(80f) }?.minByOrNull { it.top }?.width() ?: px(21f)
        val sideW = (lp.width - cutoutW) / 2
        val iconSize = px(16f)
        fun iconView(res: Int?, color: Int) = android.widget.FrameLayout(this).apply {
            val iv = android.widget.ImageView(this@SnapKeyService).apply {
                res?.let { setImageResource(it) }
                imageTintList = android.content.res.ColorStateList.valueOf(color)
            }
            addView(iv, android.widget.FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            if (res == null) islandIcon = iv
        }
        val pill = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = lp.height / 2f
                setColor(0xFF000000.toInt())
            }
            addView(iconView(R.drawable.ic_island_snap, 0xFFFFFFFF.toInt()), android.widget.LinearLayout.LayoutParams(sideW, lp.height))
            addView(View(this@SnapKeyService), android.widget.LinearLayout.LayoutParams(cutoutW, lp.height))
            addView(iconView(null, 0xFFFFFFFF.toInt()), android.widget.LinearLayout.LayoutParams(sideW, lp.height))
            visibility = View.INVISIBLE
        }
        val frame = android.widget.FrameLayout(this).apply {
            addView(pill, android.widget.FrameLayout.LayoutParams(lp.width, lp.height))
            visibility = View.GONE
        }
        try {
            wm.addView(frame, lp)
        } catch (e: Exception) {
            return false
        }
        islandRoot = frame
        islandPill = pill
        islandLp = lp
        return true
    }

    private fun popup(message: String) {
        val wm = getSystemService(WindowManager::class.java)
        if (!ensureIsland(wm)) return
        val pill = islandPill ?: return
        val (iconRes, tint) = when (message) {
            "Vibrate" -> R.drawable.ic_island_vibrate to 0xFF8BA4FF.toInt()
            "Ring" -> R.drawable.ic_island_ring to 0xFF5BD68A.toInt()
            "Silent" -> R.drawable.ic_island_silent to 0xFFFF6B6B.toInt()
            "Flashlight on" -> R.drawable.ic_island_flash_on to 0xFFFFC24D.toInt()
            "Flashlight off" -> R.drawable.ic_island_flash_off to 0xFFB0B0B0.toInt()
            else -> R.drawable.ic_island_check to 0xFF5BD68A.toInt()
        }
        islandIcon?.apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(tint)
        }
        pill.contentDescription = message
        handler.removeCallbacks(hideIsland)
        pill.animate().cancel()
        pill.pivotX = (islandLp?.width ?: 0) / 2f
        pill.pivotY = (islandLp?.height ?: 0) / 2f
        islandRoot?.visibility = View.VISIBLE
        if (pill.visibility != View.VISIBLE) {
            pill.alpha = 0f; pill.scaleX = 0.4f; pill.scaleY = 0.9f
            pill.visibility = View.VISIBLE
        }
        // Grow out of the camera with a little overshoot, hold, then shrink back into it.
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
        handler.postDelayed(hideIsland, 1300)
    }

    /** Shows the pill once, for lining it up from the settings screen. */
    fun previewIsland() = popup("Vibrate")

    private fun removePopup() {
        val root = islandRoot ?: return
        try { getSystemService(WindowManager::class.java).removeView(root) } catch (_: Exception) {}
        islandRoot = null; islandPill = null; islandIcon = null; islandLp = null
    }

    interface Listener {
        fun onServiceStateChanged() {}
        fun onGesture(g: Gesture, a: KeyAction) {}
    }

    companion object {
        @Volatile var instance: SnapKeyService? = null
        var listener: Listener? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        /** Last time ColorOS delivered a Snap Key press (for the status card). */
        @Volatile var lastSnapPressAt = 0L

        fun snapTap(ctx: Context) = deliver(ctx) { it.onSnapTap() }
        fun snapLongPress(ctx: Context) = deliver(ctx) { it.onSnapLong() }

        private fun deliver(ctx: Context, f: (SnapKeyService) -> Unit) {
            lastSnapPressAt = System.currentTimeMillis()
            mainHandler.post {
                val svc = instance
                if (svc != null) f(svc)
                else android.widget.Toast.makeText(
                    ctx.applicationContext,
                    "SnapMapper: turn it on in Accessibility settings",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

    }
}
