package io.github.asutorufa.yuhaiin.view

import android.view.LayoutInflater
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import io.github.asutorufa.yuhaiin.R


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun Main() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("title") },
                navigationIcon = {
                    IconButton(onClick = {

                    }) {
                        Icon(painterResource(R.drawable.sort), contentDescription = "")
                    }
                },

                actions = {
                    IconButton(onClick = {}) {
                        Icon(painterResource(R.drawable.menu_open), contentDescription = null)
                    }
                }
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(onClick = { /*TODO*/ }) {
                Icon(painterResource(R.drawable.play_arrow), contentDescription = null)
            }
        },
        content = {
            AndroidView(
                modifier = Modifier.padding(it),
                factory = { context ->
                    val view =
                        LayoutInflater.from(context).inflate(R.layout.main_activity, null, false)
                    view
                },
                update = {
                },
            )
        }
    )
}

@Preview
@Composable
fun PreviewMessageCard() {
    CenterAlignedTopAppBar(
        title = { Text("title") },
        navigationIcon = {
            IconButton(onClick = {

            }) {
                Icon(painterResource(R.drawable.sort), contentDescription = "")
            }
        },

        actions = {
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.menu_open), contentDescription = null)
            }
        }
    )
}
