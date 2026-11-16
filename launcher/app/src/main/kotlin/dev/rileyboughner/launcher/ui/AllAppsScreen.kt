package dev.rileyboughner.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.LauncherAppInfo

/** Full alphabetical app list (already sorted by [dev.rileyboughner.launcher.AppListRepository]), with a plain text filter. */
@Composable
fun AllAppsScreen(
    apps: List<LauncherAppInfo>,
    onAppClick: (LauncherAppInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }

    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        AppTextRow(label = "← Apps", onClick = onBack)
        HairlineDivider()

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Filter") },
            modifier = Modifier
                .fillMaxWidth()
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
