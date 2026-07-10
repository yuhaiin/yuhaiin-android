package io.github.asutorufa.yuhaiin

import yuhaiin.Store

fun Store.remove(key: String) {
    try {
        putString(key, "")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
