package dev.rileyboughner.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rileyboughner.launcher.LauncherAppInfo
import dev.rileyboughner.launcher.ui.AppTextRow
import dev.rileyboughner.launcher.ui.HairlineDivider

/**
 * The main-page app picker, reached from the "Main-page apps" row in
 * [SettingsScreen]'s Main page section. [initialFavorites] is always the
 * *current effective* favorites — see
 * [SettingsRepository.getEffectiveMainPageFavorites] — so a user who opens
 * this screen, makes no edits, and saves sees zero change on the main page.
 *
 * Two groups: picked favorites in order at top (custom-label field + reorder
 * + remove), then every other installed app below alphabetically (tap to
 * add it to the bottom of the picked list). [onSave] is only invoked from
 * the Save row; [onBack] discards any in-progress edits.
 */
@Composable
fun MainPageFavoritesPickerScreen(
    allApps: List<LauncherAppInfo>,
    initialFavorites: List<MainPageFavorite>,
    onSave: (List<MainPageFavorite>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = remember { mutableStateListOf<MainPageFavorite>().apply { addAll(initialFavorites) } }
    val pickedPackages = picked.map { it.packageName }.toSet()
    val others = allApps.filter { it.packageName !in pickedPackages }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTextRow(label = "← Back", onClick = onBack)
        HairlineDivider()

        Text(
            text = "Main-page apps",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        Text(
            text = "Pick which apps show on the main page, in what order, and under what name. Per-device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        Text(
            text = "On main page",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (picked.isEmpty()) {
            Text(
                text = "No apps picked — add one from the list below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        picked.forEachIndexed { index, favorite ->
            PickedFavoriteRow(
                favorite = favorite,
                canMoveUp = index > 0,
                canMoveDown = index < picked.lastIndex,
                onLabelChange = { newLabel -> picked[index] = favorite.copy(label = newLabel) },
                onMoveUp = {
                    val above = picked[index - 1]
                    picked[index - 1] = favorite
                    picked[index] = above
                },
                onMoveDown = {
                    val below = picked[index + 1]
                    picked[index + 1] = favorite
                    picked[index] = below
                },
                onRemove = { picked.removeAt(index) },
            )
            HairlineDivider()
        }

        Text(
            text = "Add an app",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        others.forEach { app ->
            AppTextRow(
                label = app.label,
                onClick = { picked.add(MainPageFavorite(packageName = app.packageName, label = app.label)) },
            )
            HairlineDivider()
        }

        AppTextRow(
            label = "Save",
            onClick = { onSave(picked.map { it.copy(label = it.label.trim()) }) },
        )
    }
}

@Composable
private fun PickedFavoriteRow(
    favorite: MainPageFavorite,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLabelChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = favorite.label,
            onValueChange = onLabelChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        ReorderGlyph(text = "↑", enabled = canMoveUp, onClick = onMoveUp)
        ReorderGlyph(text = "↓", enabled = canMoveDown, onClick = onMoveDown)
        ReorderGlyph(text = "✕", enabled = true, onClick = onRemove)
    }
}

@Composable
private fun ReorderGlyph(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
    )
}
