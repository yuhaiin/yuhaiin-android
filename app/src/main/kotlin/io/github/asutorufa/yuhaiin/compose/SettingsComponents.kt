package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.asutorufa.yuhaiin.R
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

@Composable
fun ListPreferenceSetting(
    title: String,
    icon: Painter? = null,
    entries: Map<String, String>,
    selected: String,
    onSelectedChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsItem(title, entries[selected] ?: selected, icon) { expanded = true }
    if (expanded)
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    entries.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected == key,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelectedChange(key)
                                        expanded = false
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected == key, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.close)) }
            },
        )
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

/** A bounded reading column on tablets; insets belong to the enclosing Scaffold. */
@Composable
fun ReadingPane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) { content() }
    }
}
