package dev.rileyboughner.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
 * Apps page's swipe-up gesture — it focuses the filter field and opens
 * the keyboard immediately so Riley can start typing without an extra tap.
 * When opened by tapping "All apps" instead, neither of those kick in
 * until he actually types.
 *
 * The list takes the remaining space above the keyboard (weight(1f)) so
 * the results always render fully in view instead of extending behind it.
 * Typing only filters — launching is always an explicit tap on a row. An
 * earlier single-match auto-launch was removed: it fired the moment the
 * filter narrowed to one app, so the results never rendered stably and
 * Riley was yanked into an app mid-typing.
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

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
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

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.packageName }) { app ->
                AppTextRow(label = app.label, badgeCount = app.badgeCount, onClick = { onAppClick(app) })
                HairlineDivider()
            }
        }
    }
}
