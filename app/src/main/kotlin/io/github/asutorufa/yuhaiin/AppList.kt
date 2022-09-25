package io.github.asutorufa.yuhaiin

import android.graphics.drawable.Drawable

data class AppList(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable,
    val isSystemApp: Boolean = false,
    var isChecked: Boolean = false
)