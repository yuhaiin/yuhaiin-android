package io.github.asutorufa.yuhaiin.data

import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.getStringSet
import io.github.asutorufa.yuhaiin.putStringSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object RouteRepository {
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    val presets = setOf(Constants.ALL_ROUTE, Constants.NON_LOCAL_ROUTE, Constants.NON_CHINESE_ROUTE)

    suspend fun content(name: String): String =
        withContext(Dispatchers.IO) {
            MainApplication.settings.ready.await()
            MainApplication.store.getString(Constants.ROUTE_CONTENT_PREFIX + name)
        }

    suspend fun create(name: String) =
        MainApplication.settings.commit { store ->
            val routes = store.getStringSet(Constants.SAVED_ROUTES_LIST)
            require(name.isNotBlank() && name.length <= 80 && name !in routes)
            require(name.all { it.isLetterOrDigit() || it == '_' || it == '-' })
            store.putString(Constants.ROUTE_CONTENT_PREFIX + name, "")
            store.putStringSet(Constants.SAVED_ROUTES_LIST, routes + name)
            changes.value++
        }

    suspend fun save(name: String, content: String) =
        MainApplication.settings.commit { store ->
            require(name in store.getStringSet(Constants.SAVED_ROUTES_LIST))
            store.putString(Constants.ROUTE_CONTENT_PREFIX + name, content)
            changes.value++
        }

    suspend fun delete(name: String) =
        MainApplication.settings.commit { store ->
            require(
                name !in presets
            ) // Always keep a valid default, including for older selections.
            if (store.getString(Constants.ROUTE_KEY) == name)
                store.putString(Constants.ROUTE_KEY, Constants.ALL_ROUTE)
            store.putStringSet(
                Constants.SAVED_ROUTES_LIST,
                store.getStringSet(Constants.SAVED_ROUTES_LIST) - name,
            )
            store.putString(Constants.ROUTE_CONTENT_PREFIX + name, "")
            changes.value++
        }
}
