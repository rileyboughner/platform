package dev.rileyboughner.launcher

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The *only* activity declaring the HOME category in the manifest. Routes
 * to [TvMainActivity] or [MainActivity] based on [UiModeManager]'s detected
 * form factor, then finishes itself — so whichever of the two gets exported
 * as "home" is never the OS's choice, it's always this runtime check.
 *
 * Added after a real incident: on a Hisense Google TV, HOME resolved to
 * [MainActivity] (the phone's touch/gesture pager, not D-pad usable) instead
 * of [TvMainActivity], leaving the TV stuck on launch. That happened
 * because both activities used to declare their own HOME intent-filter —
 * Android is then free to pick either as the installed "home app" per
 * device, and on that TV it picked wrong. With only this router declaring
 * HOME, there is exactly one candidate to pick, and it always re-derives
 * the correct target at runtime instead of baking in a static manifest
 * guess. [MainActivity]/[TvMainActivity] keep their own LAUNCHER/
 * LEANBACK_LAUNCHER filters so they're still directly reachable (e.g. from
 * an app drawer during development) without going through this router.
 */
class HomeRouterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTelevision = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val targetActivity = if (isTelevision) TvMainActivity::class.java else MainActivity::class.java
        startActivity(
            Intent(this, targetActivity).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }
}
