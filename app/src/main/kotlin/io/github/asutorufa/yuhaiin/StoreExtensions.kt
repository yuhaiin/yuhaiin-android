package io.github.asutorufa.yuhaiin

import io.github.asutorufa.yuhaiin.core.Store

fun Store.remove(key: String) {
    try {
        putString(key, "")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
