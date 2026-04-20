package io.github.asutorufa.yuhaiin.compose.route

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.Constants
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.getStringSet
import io.github.asutorufa.yuhaiin.putStringSet
import io.github.asutorufa.yuhaiin.remove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yuhaiin.Store
import yuhaiin.Yuhaiin

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.RouteConfigScreen(
    navController: NavController,
    animatedContentScope: AnimatedContentScope?
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var routeList by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            routeList = MainApplication.store.getStringSet(Constants.SAVED_ROUTES_LIST).toList().sorted()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteRouteName by remember { mutableStateOf<String?>(null) }
    val allRouteName = stringResource(R.string.adv_route_all)

    with(animatedContentScope ?: return) {
        Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_config_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .animateContentSize()
            ) {
                items(items = routeList, key = { it }) { routeName ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                deleteRouteName = routeName
                                true
                            } else false
                        }
                    )

                LaunchedEffect(deleteRouteName) {
                    if (deleteRouteName == null) dismissState.reset()
                }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = MaterialTheme.colorScheme.errorContainer
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    routeName,
                                    modifier = Modifier.sharedElement(
                                        rememberSharedContentState(key = "ROUTE_NAME_$routeName"),
                                        animatedVisibilityScope = this@with
                                    )
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.router),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("RouteEdit/$routeName")
                            }
                        )
                    }
                }
            }
        }
    }

    if (deleteRouteName != null) {
        AlertDialog(
            onDismissRequest = { deleteRouteName = null },
            title = { Text(stringResource(R.string.route_config_delete_confirm_title)) },
            text = { Text(stringResource(R.string.route_config_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteRouteName?.let { route ->
                            val currentRoutes =
                                MainApplication.store.getStringSet("saved_routes_list")
                                    .toMutableSet()
                            currentRoutes.remove(route)
                            MainApplication.store.putStringSet(
                                "saved_routes_list",
                                currentRoutes
                            )
                            MainApplication.store.remove("route_content_$route")

                            if (MainApplication.store.getString("route") == route) {
                                MainApplication.store.putString("route", allRouteName)
                            }
                            routeList = currentRoutes.toList().sorted()
                        }
                        deleteRouteName = null
                    }
                ) {
                    Text(stringResource(R.string.route_config_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRouteName = null }) {
                    Text(stringResource(R.string.route_config_delete_confirm_no))
                }
            }
        )
    }

    if (showAddDialog) {
        var newRouteName by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.route_config_add_route)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newRouteName,
                        onValueChange = {
                            newRouteName = it
                            isError = it.isBlank() || routeList.contains(it) || !it.all { char ->
                                char.isLetterOrDigit() || char == '_' || char == '-'
                            }
                        },
                        label = { Text(stringResource(R.string.route_config_name_hint)) },
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    when {
                                        newRouteName.isBlank() -> stringResource(R.string.route_config_error_name_empty)
                                        routeList.contains(newRouteName) -> "Route already exists"
                                        else -> stringResource(R.string.route_config_error_name_invalid)
                                    }
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newList = routeList.toMutableSet()
                        newList.add(newRouteName)
                        MainApplication.store.putStringSet(Constants.SAVED_ROUTES_LIST, newList)
                        // Initialize with empty or default content if needed
                        MainApplication.store.putString(
                            Constants.ROUTE_CONTENT_PREFIX + newRouteName,
                            ""
                        )
                        routeList = newList.toList().sorted()
                        showAddDialog = false
                    },
                    enabled = !isError && newRouteName.isNotBlank()
                ) {
                    Text(stringResource(R.string.route_config_save_route))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.RouteEditScreen(
    navController: NavController,
    routeName: String,
    animatedContentScope: AnimatedContentScope?
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var routeContent by remember {
        mutableStateOf(MainApplication.store.getString(Constants.ROUTE_CONTENT_PREFIX + routeName))
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isValid by remember { mutableStateOf(true) }

    with(animatedContentScope ?: return) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.route_config_edit_route_title,
                                    routeName
                                ).substringBefore(routeName)
                            )
                            Text(
                                routeName,
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "ROUTE_NAME_$routeName"),
                                    animatedVisibilityScope = this@with
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (isValid) {
                    MainApplication.store.putString("route_content_$routeName", routeContent)
                    navController.popBackStack()
                }
            }) {
                Icon(painterResource(R.drawable.save), contentDescription = "Save")
            }
        }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                OutlinedTextField(
                    value = routeContent,
                onValueChange = {
                    routeContent = it
                    isValid = it.lineSequence()
                        .filter { line -> line.isNotBlank() }
                        .all { line ->
                            try {
                                Yuhaiin.parseCIDR(line)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                },
                isError = !isValid,
                supportingText = {
                    if (!isValid) {
                        Text(stringResource(R.string.route_config_error_content_invalid))
                    }
                },
                label = { Text(stringResource(R.string.route_config_content_hint)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                    maxLines = Int.MAX_VALUE
                )
            }
        }
    }

    if (showDeleteDialog) {
        val allRouteName = stringResource(R.string.adv_route_all)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.route_config_delete_confirm_title)) },
            text = { Text(stringResource(R.string.route_config_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentRoutes =
                            MainApplication.store.getStringSet(Constants.SAVED_ROUTES_LIST)
                                .toMutableSet()
                        currentRoutes.remove(routeName)
                        MainApplication.store.putStringSet(
                            Constants.SAVED_ROUTES_LIST,
                            currentRoutes
                        )
                        MainApplication.store.remove(Constants.ROUTE_CONTENT_PREFIX + routeName)

                        // If current selected route is deleted, reset to All
                        if (MainApplication.store.getString(Constants.ROUTE_KEY) == routeName) {
                            MainApplication.store.putString(Constants.ROUTE_KEY, allRouteName)
                        }

                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(stringResource(R.string.route_config_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.route_config_delete_confirm_no))
                }
            }
        )
    }
}
