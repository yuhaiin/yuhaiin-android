package io.github.asutorufa.yuhaiin.compose.route

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.MainApplication
import io.github.asutorufa.yuhaiin.R
import io.github.asutorufa.yuhaiin.getStringSet
import io.github.asutorufa.yuhaiin.putStringSet
import yuhaiin.Store
import yuhaiin.Yuhaiin

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.RouteConfigScreen(
    navController: NavController,
    animatedContentScope: AnimatedContentScope?
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var routeList by remember {
        mutableStateOf(
            MainApplication.store.getStringSet("saved_routes_list").toList().sorted()
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }

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
        ) {
            items(routeList) { routeName ->
                ListItem(
                    headlineContent = { Text(routeName) },
                    modifier = Modifier.clickable {
                        navController.navigate("RouteEdit/$routeName")
                    }
                )
            }
        }
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
                            isError =
                                it.isBlank() || routeList.contains(it) || it.contains('/') // Prevent '/' as it breaks navigation
                        },
                        label = { Text(stringResource(R.string.route_config_name_hint)) },
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    when {
                                        newRouteName.isBlank() -> stringResource(R.string.route_config_error_name_empty)
                                        newRouteName.contains('/') -> "Name cannot contain '/'"
                                        else -> "Route already exists"
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
                        if (newRouteName.isNotBlank() && !routeList.contains(newRouteName) && !newRouteName.contains(
                                '/'
                            )
                        ) {
                            val newList = routeList.toMutableSet()
                            newList.add(newRouteName)
                            MainApplication.store.putStringSet("saved_routes_list", newList)
                            // Initialize with empty or default content if needed
                            MainApplication.store.putString("route_content_$newRouteName", "")
                            routeList = newList.toList().sorted()
                            showAddDialog = false
                        } else {
                            isError = true
                        }
                    }
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
        mutableStateOf(MainApplication.store.getString("route_content_$routeName"))
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isValid by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_config_edit_route) + " - " + routeName) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = routeContent,
                onValueChange = {
                    routeContent = it
                    isValid = try {
                        it.split('\n').filter { line -> line.isNotBlank() }.forEach { line ->
                            Yuhaiin.parseCIDR(line)
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                },
                isError = !isValid,
                supportingText = {
                    if (!isValid) {
                        Text(stringResource(R.string.route_config_error_content_invalid))
                    }
                },
                label = { Text(stringResource(R.string.route_config_content_hint)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                maxLines = Int.MAX_VALUE
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.route_config_delete_confirm_title)) },
            text = { Text(stringResource(R.string.route_config_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentRoutes =
                            MainApplication.store.getStringSet("saved_routes_list").toMutableSet()
                        currentRoutes.remove(routeName)
                        MainApplication.store.putStringSet("saved_routes_list", currentRoutes)
                        MainApplication.store.delete("route_content_$routeName")

                        // If current selected route is deleted, reset to All
                        val all = stringResource(R.string.adv_route_all)
                        if (MainApplication.store.getString("route") == routeName) {
                            MainApplication.store.putString("route", all)
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
