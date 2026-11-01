package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.data.SettingKey

@Suppress("UNCHECKED_CAST")
fun <T : Any> Map<String, Any>.setting(key: SettingKey<T>): T = this[key.name] as? T ?: key.default

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { IconSlot(icon) },
    )
}

@Composable
private fun IconSlot(icon: Painter?) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, contentDescription = null)
    }
}

@Composable
fun SwitchSetting(
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier =
            Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { IconSlot(icon) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPreferenceSetting(
    title: String,
    icon: Painter? = null,
    entries: Map<String, String>,
    selected: String,
    summary: String? = null,
    onSelectedChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ListItem(
            modifier =
                Modifier.clickable(role = Role.DropdownList, onClick = { expanded = true }),
            headlineContent = { Text(title) },
            supportingContent = {
                Column {
                    Text(entries[selected] ?: selected, color = MaterialTheme.colorScheme.primary)
                    if (summary != null) Text(summary)
                }
            },
            leadingContent = { IconSlot(icon) },
            trailingContent = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Line the menu up with the row's text instead of its leading icon.
            offset = DpOffset(56.dp, 0.dp),
        ) {
            entries.forEach { (key, label) ->
                val isSelected = selected == key
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelectedChange(key)
                    },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.semantics { this.selected = isSelected },
                )
            }
        }
    }
}

@Composable
fun SectionHeading(title: String) {
    Text(
        title,
        Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Applies Scaffold padding except the bottom, which scrolling content adds as contentPadding so
 * its rows can pass behind the transparent navigation bar.
 */
@Composable
fun Modifier.scaffoldBody(padding: PaddingValues): Modifier {
    val direction = LocalLayoutDirection.current
    return padding(
        start = padding.calculateStartPadding(direction),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(direction),
    )
}

/** A bounded reading column on tablets; insets belong to the enclosing Scaffold. */
@Composable
fun ReadingPane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) { content() }
    }
}
