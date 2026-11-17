package dev.rileyboughner.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.rileyboughner.launcher.homeassistant.HomeAssistantRepository
import dev.rileyboughner.launcher.homeassistant.HomeAssistantResult
import dev.rileyboughner.launcher.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val LIVING_ROOM_NAME = "Living Room"

/**
 * Global Menu-key popup for the living-room Home Assistant lights/scenes,
 * reachable from any screen on the TV (launcher, Jellyfin, YouTube, ...),
 * not just the launcher's own home page.
 *
 * ONE-TIME SETUP (user-side, manual — nothing here auto-enables itself or
 * ever will; see the task's explicit "do not auto-enable" requirement):
 *   1. TV Settings → Apps → See all apps → this launcher → turn on
 *      "Appear on top" (the SYSTEM_ALERT_WINDOW grant; exact wording
 *      varies by Android TV/Google TV build).
 *   2. TV Settings → Accessibility → this launcher's accessibility service
 *      → turn on, and accept the "key event filtering" warning dialog
 *      (required because [onKeyEvent] below requests
 *      `canRequestFilterKeyEvents`, i.e. permission to intercept hardware
 *      key events system-wide before the foreground app sees them).
 * Both grants are independent and both are required before Menu does
 * anything. [showOverlay] silently no-ops if the overlay permission isn't
 * granted; if the accessibility service itself isn't enabled, this class
 * never even runs. Either way, an un-set-up TV behaves exactly as if this
 * feature didn't exist — no crash, no misbehavior, Menu just does whatever
 * it already did before (usually nothing).
 */
class HaPopupAccessibilityService : AccessibilityService() {

    private var overlayView: HaOverlayRootView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var homeAssistantRepository: HomeAssistantRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        homeAssistantRepository = HomeAssistantRepository(SettingsRepository(applicationContext))
    }

    /**
     * Only [KeyEvent.KEYCODE_MENU] is ever consumed (returning `true`) here —
     * every other key passes through untouched (returning `false`), so this
     * service's only observable effect on the rest of the system is Menu
     * toggling the popup.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_MENU) return false
        if (event.action == KeyEvent.ACTION_DOWN) toggleOverlay()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceScope.cancel()
    }

    private fun toggleOverlay() {
        if (overlayView != null) removeOverlay() else showOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = HaOverlayRootView(this, onBackPressed = { removeOverlay() })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = android.view.Gravity.CENTER }

        runCatching { windowManager.addView(root, params) }.onFailure { return }
        overlayView = root
        root.requestFocus()
        refreshOverlay()
    }

    private fun removeOverlay() {
        val root = overlayView ?: return
        overlayView = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(root) }
    }

    private fun refreshOverlay() {
        val root = overlayView ?: return
        serviceScope.launch {
            val result = homeAssistantRepository.fetchSnapshot()
            val room = (result as? HomeAssistantResult.Success)?.value?.rooms
                ?.firstOrNull { it.name == LIVING_ROOM_NAME }
            if (overlayView === root) {
                root.render(
                    room = room,
                    onToggleLight = ::onToggleLight,
                    onBrightnessStep = ::onBrightnessStep,
                    onActivateScene = ::onActivateScene,
                )
            }
        }
    }

    private fun onToggleLight(entityId: String) {
        serviceScope.launch {
            homeAssistantRepository.toggleLight(entityId)
            refreshOverlay()
        }
    }

    private fun onBrightnessStep(entityId: String, currentPercent: Int, delta: Int) {
        serviceScope.launch {
            val next = (currentPercent + delta).coerceIn(1, 100)
            homeAssistantRepository.setLightBrightness(entityId, next)
            refreshOverlay()
        }
    }

    private fun onActivateScene(entityId: String) {
        serviceScope.launch { homeAssistantRepository.activateScene(entityId) }
    }
}
