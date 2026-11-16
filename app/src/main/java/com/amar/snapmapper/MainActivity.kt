package com.amar.snapmapper

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * One screen, styled like ColorOS Settings: large title, rounded cards, grouped rows.
 * Setup steps only show while something is missing; detailed controls hide behind Advanced.
 */
class MainActivity : Activity(), SnapKeyService.Listener {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val dp get() = resources.displayMetrics.density

    private fun color(id: Int) = getColor(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), 0, px(16), px(24))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        SnapKeyService.listener = this
        render()
    }

    override fun onPause() {
        super.onPause()
        if (SnapKeyService.listener === this) SnapKeyService.listener = null
    }

    override fun onServiceStateChanged() = runOnUiThread { render() }
    override fun onGesture(g: Gesture, a: KeyAction) = render()

    /** Settings changed: tell the service and redraw, keeping the scroll position. */
    private fun changed() {
        SnapKeyService.instance?.reconfigure()
        render()
    }

    // ---- Screen ----

    private fun render() {
        val y = scroll.scrollY
        content.removeAllViews()

        content.addView(text("SnapMapper", 30f, color(R.color.text), medium = true).apply {
            setPadding(px(8), px(28), px(8), px(2))
        })
        content.addView(text("Give your Snap Key a new job", 14f, color(R.color.text_secondary)).apply {
            setPadding(px(8), 0, px(8), px(20))
        })

        content.addView(statusCard())

        // Everyday actions.
        section("Snap Key")
        content.addView(card(Gesture.entries.map { gestureRow(it, Scope.NORMAL) }))

        // Locked screen.
        section("When locked")
        val lockedRows = mutableListOf<View>(
            switchRow(
                "Different actions when locked",
                "Used when the screen is off or locked",
                prefs.lockedActionsEnabled,
            ) { prefs.lockedActionsEnabled = it; changed() }
        )
        if (prefs.lockedActionsEnabled) lockedRows += Gesture.entries.map { gestureRow(it, Scope.LOCKED) }
        content.addView(card(lockedRows))

        // Per-app single press.
        section("In specific apps")
        val appRows = prefs.appRules.map { appRuleRow(it) } + row("Add an app", "Change the single press while that app is open", plus = true) {
            addAppRule()
        }
        content.addView(card(appRows))

        // Behaviour.
        section("Behaviour")
        content.addView(
            card(
                listOf(
                    switchRow("Vibrate on press", "A light tap when an action runs", prefs.vibrate) {
                        prefs.vibrate = it
                    },
                    switchRow("On-screen confirmation", "A small pill around the camera shows what ran", prefs.showPopup) {
                        prefs.showPopup = it
                    },
                    switchRow("Pause during calls", "Ignore the Snap Key while you're on a call", prefs.pauseInCalls) {
                        prefs.pauseInCalls = it
                    },
                    row("Double press speed", speedLabel(prefs.doublePressMs)) { pickSpeed() },
                )
            )
        )

        // Advanced.
        section("Advanced")
        val adv = mutableListOf<View>(
            switchRow("Advanced settings", "Exact timings and press history", prefs.advanced) {
                prefs.advanced = it; render()
            }
        )
        if (prefs.advanced) {
            adv += row("Double-press window", "${prefs.doublePressMs} ms · how long to wait for a second press") {
                askMs(
                    "Double-press window",
                    "How long SnapMapper waits for a second press, in milliseconds (100–1000). " +
                        "Shorter makes single presses faster; longer makes double presses easier.",
                    100..1000, prefs.doublePressMs,
                ) { prefs.doublePressMs = it }
            }
            adv += row("Circle to Search hold", "${prefs.circleHoldMs} ms · only used if the phone refuses the direct call") {
                askMs(
                    "Circle to Search hold",
                    "On phones that refuse the direct call, Circle to Search is opened by holding the navigation " +
                        "handle for this long, in milliseconds (300–3000). Raise it if the press taps the app " +
                        "underneath instead of opening Circle to Search.",
                    300..3000, prefs.circleHoldMs,
                ) { prefs.circleHoldMs = it }
            }
            if (prefs.showPopup) {
                val off = prefs.islandOffsetDp
                adv += row("Pill position", "Line the pill up with the camera · ${if (off > 0) "+" else ""}$off dp") {
                    adjustIsland()
                }
            }
            val last = prefs.history.firstOrNull()
            adv += row("Press history", last ?: "No presses yet") { showHistory() }
        }
        content.addView(card(adv))

        content.addView(
            text(
                "No background battery use. SnapMapper only wakes when you press the Snap Key.",
                12f, color(R.color.text_secondary)
            ).apply {
                gravity = Gravity.CENTER
                setPadding(px(24), px(20), px(24), px(8))
            }
        )
        scroll.post { scroll.scrollTo(0, y) }
    }

    // ---- Status / setup ----

    private fun listenerOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, SnapKeyService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me } &&
            SnapKeyService.instance != null
    }

    private fun snapKeyOnTranslate(): Boolean = try {
        Settings.System.getString(contentResolver, "oplus_action_button_switch_state") == "translate"
    } catch (e: Exception) {
        false
    }

    private fun statusCard(): View {
        val listener = listenerOn()
        val translate = snapKeyOnTranslate()
        val ready = listener && translate

        val rows = mutableListOf<View>()
        val last = SnapKeyService.lastSnapPressAt
        rows += heroRow(
            ready = ready,
            title = if (ready) "Snap Key is ready" else "Finish setup",
            summary = when {
                !ready -> "SnapMapper needs these steps to receive the Snap Key."
                last == 0L -> "Press the Snap Key to try it."
                else -> "Last press ${DateUtils.getRelativeTimeSpanString(last)}"
            },
        )
        if (!listener) {
            rows += stepRow(
                "1", "Turn on SnapMapper",
                "Tap here, then Downloaded apps → SnapMapper → On",
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        if (!translate) {
            rows += stepRow(
                if (listener) "1" else "2", "Set the Snap Key to \"Translate\"",
                "Open Settings → Additional settings → Snap Key, then choose Translate",
                onClick = null,
            )
        }
        return card(rows)
    }

    private fun heroRow(ready: Boolean, title: String, summary: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(20), px(18), px(20), px(18))
        }
        val badgeColor = color(if (ready) R.color.ok else R.color.warn)
        row.addView(TextView(this).apply {
            text = if (ready) "✓" else "!"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(badgeColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((badgeColor and 0x00FFFFFF) or 0x26000000)
            }
        }, LinearLayout.LayoutParams(px(44), px(44)))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), 0, 0, 0)
        }
        col.addView(text(title, 18f, color(R.color.text), medium = true))
        col.addView(text(summary, 13f, color(R.color.text_secondary)).apply { setPadding(0, px(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return row
    }

    private fun stepRow(number: String, title: String, summary: String, onClick: (() -> Unit)?): View {
        val row = baseRow(onClick)
        row.addView(TextView(this).apply {
            text = number
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.accent))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(R.color.accent_soft))
            }
        }, LinearLayout.LayoutParams(px(26), px(26)).apply { marginEnd = px(14) })
        row.addView(titleSummary(title, summary), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (onClick != null) row.addView(chevron())
        return row
    }

    // ---- Action rows and pickers ----

    private fun actionSummary(g: Gesture, scope: Scope): String {
        val a = prefs.action(g, scope)
        return when (a) {
            KeyAction.LAUNCH_APP -> "Open ${ActionRunner.ctxLabel(this, prefs.appPackage(g, scope)) ?: "an app"}"
            KeyAction.OPEN_URL -> prefs.url(g, scope)?.let { "Open $it" } ?: a.label
            else -> a.label
        }
    }

    private fun gestureRow(g: Gesture, scope: Scope): View {
        val a = prefs.action(g, scope)
        return row(g.label, actionSummary(g, scope), highlight = a != KeyAction.NONE) {
            pickAction(g, scope, g.label)
        }
    }

    private fun pickAction(g: Gesture, scope: Scope, title: String, onDone: () -> Unit = { changed() }) {
        val actions = KeyAction.entries
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(actions.map { it.label }.toTypedArray(), actions.indexOf(prefs.action(g, scope))) { d, i ->
                d.dismiss()
                when (val a = actions[i]) {
                    KeyAction.LAUNCH_APP -> pickApp("Open which app?") { pkg ->
                        prefs.setAppPackage(g, pkg, scope)
                        prefs.setAction(g, KeyAction.LAUNCH_APP, scope)
                        onDone()
                    }
                    KeyAction.OPEN_URL -> askUrl(g, scope, onDone)
                    else -> { prefs.setAction(g, a, scope); onDone() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickApp(title: String, exclude: Collection<String> = emptyList(), onPicked: (String) -> Unit) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName && it.first !in exclude }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(apps.map { it.second }.toTypedArray()) { _, i -> onPicked(apps[i].first) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askUrl(g: Gesture, scope: Scope, onDone: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://…"
            setText(prefs.url(g, scope) ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Open which link?")
            .setView(padded(input))
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    prefs.setUrl(g, url, scope)
                    prefs.setAction(g, KeyAction.OPEN_URL, scope)
                }
                onDone()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- Per-app rules ----

    private fun appRuleRow(pkg: String): View {
        val label = ActionRunner.ctxLabel(this, pkg) ?: pkg
        val scope = Scope.app(pkg)
        return row(label, "Single press: ${actionSummary(Gesture.SINGLE, scope)}", highlight = true) {
            AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(arrayOf("Change action", "Remove")) { _, i ->
                    if (i == 0) pickAction(Gesture.SINGLE, scope, "Single press in $label")
                    else { prefs.appRules = prefs.appRules - pkg; changed() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun addAppRule() {
        pickApp("Choose an app", exclude = prefs.appRules) { pkg ->
            val label = ActionRunner.ctxLabel(this, pkg) ?: pkg
            pickAction(Gesture.SINGLE, Scope.app(pkg), "Single press in $label") {
                prefs.appRules = prefs.appRules + pkg
                changed()
            }
        }
    }

    // ---- Timing ----

    private val speeds = listOf(250 to "Fast", 350 to "Normal", 500 to "Relaxed")

    private fun speedLabel(ms: Int): String =
        speeds.firstOrNull { it.first == ms }?.second ?: "Custom ($ms ms)"

    private fun pickSpeed() {
        val current = speeds.indexOfFirst { it.first == prefs.doublePressMs }
        AlertDialog.Builder(this)
            .setTitle("Double press speed")
            .setSingleChoiceItems(speeds.map { "${it.second} · ${it.first} ms" }.toTypedArray(), current) { d, i ->
                prefs.doublePressMs = speeds[i].first
                d.dismiss()
                changed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askMs(title: String, message: String, range: IntRange, current: Int, save: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(padded(input))
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().toIntOrNull()?.let { save(it.coerceIn(range)) }
                changed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Nudge the pill up/down with a live preview around the camera. */
    private fun adjustIsland() {
        val svc = SnapKeyService.instance
        val value = text("", 16f, color(R.color.text)).apply { gravity = Gravity.CENTER }
        fun show() {
            val off = prefs.islandOffsetDp
            value.text = "${if (off > 0) "+" else ""}$off dp"
            svc?.previewIsland()
        }
        fun stepButton(label: String, delta: Int) = TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.accent))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(color(R.color.accent_soft))
            }
            setOnClickListener {
                prefs.islandOffsetDp = (prefs.islandOffsetDp + delta).coerceIn(-30, 30)
                show()
            }
        }
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(24), px(16), px(24), px(8))
            addView(stepButton("Up", -1), LinearLayout.LayoutParams(0, px(44), 1f))
            addView(value, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(stepButton("Down", +1), LinearLayout.LayoutParams(0, px(44), 1f))
        }
        AlertDialog.Builder(this)
            .setTitle("Pill position")
            .setMessage(if (svc == null) "Turn on SnapMapper first to see the preview." else "Tap Up or Down until the pill sits evenly around the camera.")
            .setView(rowView)
            .setPositiveButton("Done") { _, _ -> render() }
            .setNeutralButton("Reset") { _, _ -> prefs.islandOffsetDp = 0; svc?.previewIsland(); render() }
            .show()
        show()
    }

    private fun showHistory() {
        val items = prefs.history
        AlertDialog.Builder(this)
            .setTitle("Press history")
            .setItems(if (items.isEmpty()) arrayOf("No presses yet") else items.toTypedArray(), null)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ -> prefs.clearHistory(); render() }
            .show()
    }

    // ---- ColorOS-style building blocks ----

    private fun section(title: String) {
        content.addView(text(title, 13f, color(R.color.section)).apply {
            setPadding(px(12), px(24), px(12), px(8))
        })
    }

    /** Rounded card holding rows separated by inset dividers. */
    private fun card(rows: List<View>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = px(22).toFloat()
                setColor(color(R.color.card))
            }
            clipToOutline = true
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) card.addView(View(this).apply {
                setBackgroundColor(color(R.color.divider))
            }, LinearLayout.LayoutParams(MATCH_PARENT, maxOf(1, (0.5f * dp).toInt())).apply {
                marginStart = px(20); marginEnd = px(20)
            })
            card.addView(r)
        }
        return card
    }

    private fun baseRow(onClick: (() -> Unit)?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(64)
        setPadding(px(20), px(14), px(16), px(14))
        if (onClick != null) {
            background = RippleDrawable(
                ColorStateList.valueOf(color(R.color.divider)), null, ColorDrawable(0xFFFFFFFF.toInt())
            )
            setOnClickListener { onClick() }
        }
    }

    private fun row(
        title: String,
        summary: String,
        highlight: Boolean = false,
        plus: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val row = baseRow(onClick)
        if (plus) {
            row.addView(TextView(this).apply {
                text = "+"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(color(R.color.accent))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color(R.color.accent_soft))
                }
            }, LinearLayout.LayoutParams(px(30), px(30)).apply { marginEnd = px(14) })
        }
        row.addView(titleSummary(title, summary, highlight), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (!plus) row.addView(chevron())
        return row
    }

    private fun switchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = Switch(this).apply {
            isChecked = checked
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()))
            trackTintList = ColorStateList(states, intArrayOf(color(R.color.accent), color(R.color.track_off)))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        val row = baseRow { sw.toggle() }
        row.addView(titleSummary(title, summary), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(sw)
        return row
    }

    private fun titleSummary(title: String, summary: String, highlight: Boolean = false): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16f, color(R.color.text)))
            addView(text(summary, 13f, color(if (highlight) R.color.accent else R.color.text_secondary)).apply {
                setPadding(0, px(3), 0, 0)
            })
        }

    private fun chevron() = text("›", 22f, color(R.color.text_secondary)).apply {
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        setPadding(px(12), 0, 0, px(2))
    }

    private fun padded(v: View) = LinearLayout(this).apply {
        setPadding(px(24), px(8), px(24), 0)
        addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun text(s: String, size: Float, c: Int, medium: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(c)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun px(v: Int) = (v * dp).toInt()
}
