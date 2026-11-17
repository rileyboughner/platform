package dev.rileyboughner.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.theme.MonoLauncherTheme
import dev.rileyboughner.launcher.ui.AppTextRow
import dev.rileyboughner.launcher.ui.HairlineDivider

/**
 * Health Connect privacy-policy / rationale screen. Health Connect shows
 * the permission sheet only when the app declares an activity for
 * ACTION_SHOW_PERMISSIONS_RATIONALE (plus the VIEW_PERMISSION_USAGE alias
 * on Android 14+) — see AndroidManifest.xml. This is the screen that opens
 * when the user taps the privacy policy link in the Health Connect
 * permissions screen. It states, in plain text, what sleep data is read
 * and what happens to it: read-only, on-device only, shown on the main
 * page glance block, never uploaded anywhere.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonoLauncherTheme {
                PermissionsRationaleScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun PermissionsRationaleScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTextRow(label = "← Back", onClick = onBack)
        HairlineDivider()
        Text(
            text = "Sleep data",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        Text(
            text = "This launcher reads your sleep sessions from Health Connect " +
                "to show last night's sleep — duration and deep, REM, and light " +
                "stages — on the main page. It reads sleep only, and only " +
                "sessions from the last 36 hours. It never writes anything to " +
                "Health Connect, never sends your sleep data anywhere, and " +
                "never shares it with anyone. The data stays on this device " +
                "and is only shown on the launcher's main page. You can revoke " +
                "access at any time in Health Connect's app permissions.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
