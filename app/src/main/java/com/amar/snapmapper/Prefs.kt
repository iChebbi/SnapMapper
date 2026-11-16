package com.amar.snapmapper

import android.content.Context
import android.content.SharedPreferences

enum class Gesture(val label: String) {
    SINGLE("Single press"),
    DOUBLE("Double press"),
    LONG("Long press"),
}

/**
 * Where a set of actions applies. Each scope stores its own actions:
 *  - [NORMAL]: the everyday actions
 *  - [LOCKED]: used when the screen is off or locked (if enabled)
 *  - app(pkg): single-press override while that app is in front
 */
@JvmInline
value class Scope(val key: String) {
    companion object {
        val NORMAL = Scope("")
        val LOCKED = Scope("locked_")
        fun app(pkg: String) = Scope("app:${pkg}_")
    }
}

/** All settings live in one small SharedPreferences file. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("snapmapper", Context.MODE_PRIVATE)

    // ---- Behaviour ----

    /** Time SnapMapper waits for a second tap before running the single-press action. */
    var doublePressMs: Int
        get() = sp.getInt("double_ms", 350)
        set(v) = sp.edit().putInt("double_ms", v).apply()

    /**
     * How long the Circle to Search fallback holds the navigation handle. On the Find X9 (ColorOS 17)
     * 900 ms opens it and 800 ms doesn't; too short and the touch lands on the app underneath.
     */
    var circleHoldMs: Int
        get() = sp.getInt("circle_hold_ms", 1000)
        set(v) = sp.edit().putInt("circle_hold_ms", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    var lockedActionsEnabled: Boolean
        get() = sp.getBoolean("locked_enabled", false)
        set(v) = sp.edit().putBoolean("locked_enabled", v).apply()

    var pauseInCalls: Boolean
        get() = sp.getBoolean("pause_calls", true)
        set(v) = sp.edit().putBoolean("pause_calls", v).apply()

    var showPopup: Boolean
        get() = sp.getBoolean("popup", true)
        set(v) = sp.edit().putBoolean("popup", v).apply()

    /** Vertical nudge (dp) for the camera pill, to line it up with the camera hole. */
    var islandOffsetDp: Int
        get() = sp.getInt("island_offset_v2", 0)
        set(v) = sp.edit().putInt("island_offset_v2", v).apply()

    var advanced: Boolean
        get() = sp.getBoolean("advanced", false)
        set(v) = sp.edit().putBoolean("advanced", v).apply()

    // ---- Actions per scope ----

    fun action(g: Gesture, scope: Scope = Scope.NORMAL): KeyAction {
        val stored = sp.getString("action_${scope.key}${g.name}", null)
        KeyAction.entries.firstOrNull { it.name == stored }?.let { return it }
        // Locked actions start out the same as the normal ones.
        if (scope == Scope.LOCKED) return action(g)
        return if (scope == Scope.NORMAL && g == Gesture.SINGLE) KeyAction.FLASHLIGHT else KeyAction.NONE
    }

    fun setAction(g: Gesture, a: KeyAction, scope: Scope = Scope.NORMAL) =
        sp.edit().putString("action_${scope.key}${g.name}", a.name).apply()

    /** Package to open when the action is LAUNCH_APP. */
    fun appPackage(g: Gesture, scope: Scope = Scope.NORMAL): String? =
        sp.getString("app_${scope.key}${g.name}", null)

    fun setAppPackage(g: Gesture, pkg: String?, scope: Scope = Scope.NORMAL) =
        sp.edit().putString("app_${scope.key}${g.name}", pkg).apply()

    /** URL to open when the action is OPEN_URL. */
    fun url(g: Gesture, scope: Scope = Scope.NORMAL): String? = sp.getString("url_${scope.key}${g.name}", null)
    fun setUrl(g: Gesture, url: String?, scope: Scope = Scope.NORMAL) =
        sp.edit().putString("url_${scope.key}${g.name}", url).apply()

    // ---- Per-app single-press overrides ----

    var appRules: List<String>
        get() = sp.getString("app_rules", "")!!.split(',').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("app_rules", v.distinct().joinToString(",")).apply()

    // ---- Press history (Advanced) ----

    val history: List<String>
        get() = sp.getString("history", "")!!.split('\n').filter { it.isNotBlank() }

    fun addHistory(line: String) {
        val list = (listOf(line) + history).take(30)
        sp.edit().putString("history", list.joinToString("\n")).apply()
    }

    fun clearHistory() = sp.edit().remove("history").apply()
}
