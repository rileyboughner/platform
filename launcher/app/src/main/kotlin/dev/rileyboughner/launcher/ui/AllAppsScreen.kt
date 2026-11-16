package dev.rileyboughner.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.LauncherAppInfo

/**
 * Full alphabetical app list (already sorted by
 * [dev.rileyboughner.launcher.AppListRepository]), with a plain text
 * filter. [autoFocusSearch] is true when this screen was opened via the
 * Apps page's swipe-down gesture — it focuses the filter field and opens
 * the keyboard immediately so Riley can start typing without an extra tap,
 * and arms the single-match auto-launch below. When opened by tapping
 * "All apps" instead, neither of those kick in until he actually types.
 */
@Composable
fun AllAppsScreen(
    apps: List<LauncherAppInfo>,
    onAppClick: (LauncherAppInfo) -> Unit,
    onBack: () -> Unit,
    autoFocusSearch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }

    // Auto-launch the one remaining match — but only once per "search
    // session" (continuous non-empty typing), and never for an empty
    // query. Clearing the field back to empty re-arms it for the next
    // session instead of firing again on the match that's left over from
    // the previous one.
    var autoLaunchArmed by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(query, filtered.size) {
        if (query.isEmpty()) {
            autoLaunchArmed = true
        } else if (autoLaunchArmed && filtered.size == 1) {
            autoLaunchArmed = false
            onAppClick(filtered.single())
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        AppTextRow(label = "← Apps", onClick = onBack)
        HairlineDivider()

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Filter") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )

        if (filtered.isEmpty()) {
            Text(
                text = "No matching apps.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn {
            items(filtered, key = { it.packageName }) { app ->
                AppTextRow(label = app.label, badgeCount = app.badgeCount, onClick = { onAppClick(app) })
                HairlineDivider()
            }
        }
    }
}
