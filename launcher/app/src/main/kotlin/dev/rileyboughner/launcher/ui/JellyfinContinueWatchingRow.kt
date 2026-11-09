package dev.rileyboughner.launcher.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.R

/**
 * STUB. "Continue watching" placeholder for the home screen.
 *
 * Scope, per the platform proposal (section 3, "Media/Jellyfin"): this is
 * a deep link into the native Jellyfin Android app, nothing more. There is
 * intentionally:
 *  - no Jellyfin API client here (no network calls, no auth),
 *  - no playback code,
 *  - no reimplemented player.
 *
 * A real implementation would replace the placeholder row below with
 * actual "continue watching" items fetched from Jellyfin's API, each
 * still deep-linking out to the Jellyfin app to actually play.
 */
private const val JELLYFIN_PACKAGE_NAME = "org.jellyfin.mobile"

@Composable
fun JellyfinContinueWatchingRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .clickable { openJellyfin(context) },
    ) {
        Text(
            text = stringResource(R.string.jellyfin_row_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.jellyfin_row_stub_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.jellyfin_open_app),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Deep-links into the Jellyfin app if installed; no-ops (with a toast) otherwise. */
private fun openJellyfin(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(JELLYFIN_PACKAGE_NAME)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "Jellyfin app not installed", Toast.LENGTH_SHORT).show()
    }
}
