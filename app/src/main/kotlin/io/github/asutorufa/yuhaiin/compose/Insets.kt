package io.github.asutorufa.yuhaiin.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import io.github.asutorufa.yuhaiin.data.SettingKey

/** Scaffold content insets; the defaults leave a landscape notch over the content. */
val appContentInsets: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.displayCutout)

@Suppress("UNCHECKED_CAST")
fun <T : Any> Map<String, Any>.setting(key: SettingKey<T>): T = this[key.name] as? T ?: key.default
