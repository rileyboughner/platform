package dev.rileyboughner.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.HomelabLinks
import dev.rileyboughner.launcher.jellyfin.JellyfinRepository

/**
 * Leftmost page of the pager: the homelab dashboard. Jellyfin "continue
 * watching" (posters) up top, then plain text rows linking out to the rest
 * of the homelab services — text-first and minimal per Riley's restyle,
 * posters only inside the Jellyfin section.
 */
@Composable
fun DashboardScreen(jellyfinRepository: JellyfinRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        JellyfinContinueWatchingSection(jellyfinRepository = jellyfinRepository, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        HairlineDivider()

        HomelabLinks.LINKS.forEach { link ->
            AppTextRow(
                label = link.label,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
            )
        }
    }
}
