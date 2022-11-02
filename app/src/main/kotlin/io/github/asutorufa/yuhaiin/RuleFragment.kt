package io.github.asutorufa.yuhaiin
/*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment

class RuleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ContentCompose()
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Preview
    @Composable
    fun ContentCompose() {
        val openRouteDialog = remember { mutableStateOf(false) }
        val routeRadioOptions = listOf("ALL", "No-Local IPs", "No-China IPs")
        var routeText by remember { mutableStateOf(routeRadioOptions[0]) }

        val openRuleUrlDialog = remember { mutableStateOf(false) }
        var textRuleUrl by remember { mutableStateOf("http://") }

        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                PreferenceItem(
                    title = "Route",
                    description = routeText,
                    onClick = {
                        openRouteDialog.value = true
                    },
                    icon = painterResource(id = R.drawable.router)
                )

                PreferenceItem(
                    title = "Update Rule",
                    description = textRuleUrl,
                    icon = painterResource(id = R.drawable.account_circle_full),
                    onClick = {
                        openRuleUrlDialog.value = true
                    }
                )

                var expanded by remember { mutableStateOf(false) }
                var selectValue by remember { mutableStateOf("") }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }) {

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dp(10F)),
                        label = { Text("Route") },
                        value = selectValue,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Image(
                                painter = painterResource(id = R.drawable.router),
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )

                    ExposedDropdownMenu(
                        modifier = Modifier.fillMaxWidth(),
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        listOf("ALL", "No-Local IPs", "No-China IPs").forEach {
                            DropdownMenuItem(
                                modifier = Modifier.fillMaxWidth(),
                                text = { Text(it) },
                                onClick = {
                                    selectValue = it
                                    expanded = false
                                })
                        }
                    }
                }

                var text by rememberSaveable { mutableStateOf("text") }

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dp(10F)),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Label") },
                )
            }


            if (openRouteDialog.value) {
                RadioDialog(
                    onDismissRequest = { openRouteDialog.value = false },
                    radioOptions = routeRadioOptions, selected = routeText,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                routeText = it
                                openRouteDialog.value = false
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.router),
                            contentDescription = ""
                        )
                    },
                    title = {
                        Text(text = "Internal Bypass Rule")
                    },
                )
            }

            if (openRuleUrlDialog.value) {
                EditTextDialog(
                    onDismissRequest = { openRuleUrlDialog.value = false },
                    text = textRuleUrl,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                textRuleUrl = it
                                openRuleUrlDialog.value = false
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    title = {
                        Text(text = "Update Bypass Rule")
                    }
                )
            }
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditTextDialog(
        onDismissRequest: () -> Unit,
        text: String,
        confirmButton: @Composable (selectedOption: String) -> Unit = {},
        dismissButton: @Composable (() -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        title: @Composable (() -> Unit)? = null,
    ) {
        var t by remember { mutableStateOf(text) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = { confirmButton(t) },
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            text = {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = {
                        Text(text = "URL")
                    }
                )
            }
        )
    }

    @Composable
    fun RadioDialog(
        onDismissRequest: () -> Unit,
        radioOptions: List<String>,
        selected: String,
        confirmButton: @Composable (selectedOption: String) -> Unit = {},
        dismissButton: @Composable (() -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        title: @Composable (() -> Unit)? = null,
    ) {

        var i = radioOptions.indexOf(selected)
        if (i == -1) i = 0

        val (selectedOption, onOptionSelected) = remember { mutableStateOf(radioOptions[i]) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = { confirmButton(selectedOption) },
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            text = {
                Column(Modifier.selectableGroup()) {
                    radioOptions.forEach { text ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .selectable(
                                    selected = (text == selectedOption),
                                    onClick = { onOptionSelected(text) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically

                        ) {
                            RadioButton(
                                selected = (text == selectedOption),
                                onClick = { onOptionSelected(text) })
                            Text(text = text)
                        }
                    }
                }

            }
        )
    }

    @Composable
    fun PreferenceItem(
        title: String,
        description: String? = null,
        icon: Painter? = null,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
    ) {
        Surface(
            modifier = Modifier.clickable(onClick = onClick, enabled = enabled)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 16.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (icon == null) 12.dp else 0.dp)
                        .padding(end = 8.dp)
                ) {
                    with(MaterialTheme) {
                        Text(
                            text = title,
                            maxLines = 1,
                            style = typography.titleLarge.copy(fontSize = 20.sp),
                            color = colorScheme.onSurface
                        )
                        if (description != null)
                            Text(
                                text = description,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = typography.bodyMedium,
                            )
                    }
                }
            }
        }
    }

}
*/