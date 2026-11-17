package dev.rileyboughner.launcher.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.rileyboughner.launcher.homeassistant.HomeAssistantLight
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRoom
import dev.rileyboughner.launcher.homeassistant.HomeAssistantScene

private const val BRIGHTNESS_STEP_PERCENT = 10

/**
 * Plain-View (no Compose) D-pad-navigable popup panel: a dark card with a
 * Lights row and a Scenes row for the living room, built programmatically
 * since [HaPopupAccessibilityService] hosts this directly via
 * [android.view.WindowManager] rather than an Activity — there is no
 * ComponentActivity around to supply the Lifecycle/SavedStateRegistry/
 * ViewModelStore owners a Compose `ComposeView` would need in this window,
 * so this reimplements just enough of [dev.rileyboughner.launcher.ui.TvTile]
 * / [dev.rileyboughner.launcher.ui.LightTile] /
 * [dev.rileyboughner.launcher.ui.SceneTile]'s D-pad behavior (focus scale +
 * ring, channel up/down brightness step on a focused on/available light) using
 * the plain View focus system instead, which needs no such owners.
 *
 * BACK is intercepted in [dispatchKeyEvent] — not a Compose `BackHandler`,
 * for the same reason — so it always dismisses the popup and hands focus
 * back to whatever is underneath, no matter which child view currently has
 * it.
 */
internal class HaOverlayRootView(
    context: Context,
    private val onBackPressed: () -> Unit,
) : FrameLayout(context) {

    private val lightsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scenesRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val statusLabel = TextView(context).apply {
        setTextColor(Color.WHITE)
        alpha = 0.8f
        textSize = 16f
    }
    private var firstFocusTarget: View? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        background = GradientDrawable().apply {
            setColor(Color.argb(235, 18, 18, 18))
            cornerRadius = dp(16f)
        }
        val outerPadding = dp(28f).toInt()
        setPadding(outerPadding, outerPadding, outerPadding, outerPadding)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionHeader("Living Room"))
            addView(statusLabel, marginLp(topMargin = dp(4f).toInt(), bottomMargin = dp(20f).toInt()))
            addView(sectionHeader("Lights"))
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(lightsRow)
            })
            addView(spacer(dp(24f).toInt()))
            addView(sectionHeader("Scenes"))
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(scenesRow)
            })
        }
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Rebuilds the lights/scenes rows from a fresh snapshot. `room == null` covers "not configured"/error/not-found alike. */
    fun render(
        room: HomeAssistantRoom?,
        onToggleLight: (entityId: String) -> Unit,
        onBrightnessStep: (entityId: String, currentPercent: Int, delta: Int) -> Unit,
        onActivateScene: (entityId: String) -> Unit,
    ) {
        lightsRow.removeAllViews()
        scenesRow.removeAllViews()
        firstFocusTarget = null

        if (room == null) {
            statusLabel.text = "Home Assistant unavailable."
            return
        }
        statusLabel.text = ""

        room.lights.forEach { light ->
            val tile = lightTile(light, onToggleLight, onBrightnessStep)
            lightsRow.addView(tile, marginLp(rightMargin = dp(16f).toInt()))
            if (firstFocusTarget == null) firstFocusTarget = tile
        }
        room.scenes.forEach { scene ->
            val tile = sceneTile(scene, onActivateScene)
            scenesRow.addView(tile, marginLp(rightMargin = dp(16f).toInt()))
            if (firstFocusTarget == null) firstFocusTarget = tile
        }
        firstFocusTarget?.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun lightTile(
        light: HomeAssistantLight,
        onToggleLight: (String) -> Unit,
        onBrightnessStep: (entityId: String, currentPercent: Int, delta: Int) -> Unit,
    ): TextView {
        val canAdjustBrightness = light.isAvailable && light.isOn && light.brightnessPercent != null
        val statusText = when {
            !light.isAvailable -> "Unavailable"
            !light.isOn -> "Off"
            light.brightnessPercent != null -> "On · ${light.brightnessPercent}%"
            else -> "On"
        }
        return focusableTile(
            title = light.friendlyName,
            subtitle = statusText,
            onKeyDown = { keyCode ->
                if (!canAdjustBrightness) return@focusableTile false
                val delta = when (keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> -BRIGHTNESS_STEP_PERCENT
                    KeyEvent.KEYCODE_CHANNEL_UP -> BRIGHTNESS_STEP_PERCENT
                    else -> return@focusableTile false
                }
                onBrightnessStep(light.entityId, light.brightnessPercent ?: 0, delta)
                true
            },
        ).apply {
            if (light.isAvailable) {
                setOnClickListener { onToggleLight(light.entityId) }
            } else {
                isEnabled = false
            }
        }
    }

    private fun sceneTile(scene: HomeAssistantScene, onActivateScene: (String) -> Unit): TextView =
        focusableTile(title = scene.friendlyName, subtitle = "Scene", onKeyDown = { false })
            .apply { setOnClickListener { onActivateScene(scene.entityId) } }

    private fun focusableTile(
        title: String,
        subtitle: String,
        onKeyDown: (keyCode: Int) -> Boolean,
    ): TextView = TextView(context).apply {
        text = "$title\n$subtitle"
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        textSize = 14f
        val innerPadding = dp(14f).toInt()
        setPadding(innerPadding, innerPadding, innerPadding, innerPadding)
        minimumWidth = dp(140f).toInt()
        minimumHeight = dp(90f).toInt()
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        background = tileFocusDrawable(focused = false)
        setOnFocusChangeListener { focusedView, hasFocus ->
            focusedView.background = tileFocusDrawable(hasFocus)
            focusedView.scaleX = if (hasFocus) 1.08f else 1f
            focusedView.scaleY = if (hasFocus) 1.08f else 1f
        }
        setOnKeyListener { _, keyCode, event ->
            event.action == KeyEvent.ACTION_DOWN && onKeyDown(keyCode)
        }
    }

    private fun tileFocusDrawable(focused: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(28, 28, 28))
        cornerRadius = dp(12f)
        if (focused) setStroke(dp(3f).toInt(), Color.WHITE)
    }

    private fun sectionHeader(title: String): TextView = TextView(context).apply {
        text = title
        setTextColor(Color.WHITE)
        alpha = 0.85f
        textSize = 18f
        setPadding(0, 0, 0, dp(10f).toInt())
    }

    private fun spacer(heightPx: Int): View = View(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
    }

    private fun marginLp(topMargin: Int = 0, bottomMargin: Int = 0, rightMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
            this.rightMargin = rightMargin
        }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
