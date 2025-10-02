package com.github.logviewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

enum class LogLevel(val tag: String, val bgColor: Color, val priority: Int) {
    DEBUG("DEBUG", Color(0xFF4CAF50), 1),
    INFO("INFO", Color(0xFF2196F3), 2),
    WARN("WARN", Color(0xFFFFC107), 3),
    ERROR("ERROR", Color(0xFFF44336), 4);
}

fun isLevelEnabled(filter: LogLevel, logLevel: LogLevel): Boolean {
    return logLevel.priority >= filter.priority
}

@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
@Preview
fun LogcatScreen(
    modifier: Modifier = Modifier,
    logs: SnapshotStateList<LogEntry> = remember {
        mutableStateListOf(
            LogEntry(
                LogLevel.WARN,
                "2025.0.1",
                "TestLogs"
            )
        )
    },
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var mainMenuExpanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(LogLevel.DEBUG) }
    val context = LocalContext.current as? Activity
    val listState = rememberLazyListState()
    var infoLog by remember { mutableStateOf<LogEntry?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()


    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text("Logcat") },
                navigationIcon = {
                    IconButton(onClick = { context?.finish() }) {
                        Icon(
                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { filterMenuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.sort),
                                contentDescription = "Filter",
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false }
                        ) {
                            LogLevel.entries.forEach { level ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(
                                                    level.bgColor,
                                                    shape = MaterialTheme.shapes.small
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = level.tag.first().toString(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    },
                                    text = { Text(level.tag) },
                                    onClick = {
                                        filter = level
                                        filterMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { mainMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = mainMenuExpanded,
                            onDismissRequest = { mainMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = rememberVectorPainter(Icons.Filled.Clear),
                                        contentDescription = "Export"
                                    )
                                },
                                text = { Text("Clear all") },
                                onClick = {
                                    logs.clear()
                                    mainMenuExpanded = false
                                })
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = rememberVectorPainter(Icons.Filled.Share),
                                        contentDescription = "Export"
                                    )
                                },
                                text = {
                                    Text("Export...")
                                }, onClick = {
                                    if (context != null) exportLogFile(context, scope)
                                    mainMenuExpanded = false
                                })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(logs.lastIndex)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    painter = rememberVectorPainter(Icons.Filled.KeyboardArrowDown),
                    contentDescription = "Scroll to bottom"
                )
            }
        },
        content = { padding ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                verticalArrangement = Arrangement.Top,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs) { log ->
                    if (isLevelEnabled(filter, log.level))
                        LogItem(
                            level = log.level,
                            timeText = log.time,
                            contentText = log.content,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            infoLog = log
                            showDialog = true
                        }
                }
            }

            AnimatedVisibility(
                visible = showDialog,
                enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(targetScale = 0.8f) + fadeOut()
            ) {
                AlertDialog(
                    modifier = Modifier.animateEnterExit(
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ),
                    onDismissRequest = { showDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Close")
                        }
                    },
                    title = { Text("Log Detail") },
                    text = {
                        if (infoLog != null) LogDetail(infoLog!!)
                        else Text("Log is not exist")
                    }
                )
            }
        }
    )
}

@Composable
@Preview
fun LogDetail(infoLog: LogEntry = LogEntry(LogLevel.INFO, "2025.08.10", "Test Content")) {
    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            infoLog.level.bgColor,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = infoLog.level.tag.first().toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = infoLog.tag ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${infoLog.time}   PID:${infoLog.pid}  TID:${infoLog.tid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = infoLog.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

data class LogEntry(
    val level: LogLevel,
    val time: String = "",
    val content: String = "",
    val tag: String? = "",
    val pid: Int? = 0,
    val tid: Int? = 0,
)

private val sLogcatPattern = Pattern.compile(
    "([\\d^-]+-[\\d^ ]+ [\\d^:]+:[\\d^:]+\\.\\d+) +(\\d+) +(\\d+) ([VDIWEF]) ((?!: ).)+: (.*)"
)

fun parseLog(line: String): LogEntry {
    val matcher = sLogcatPattern.matcher(line)
    check(matcher.find()) { "logcat pattern not match: $line" }

    val time = matcher.group(1) ?: ""
    val pid = matcher.group(2)?.toInt()
    val tid = matcher.group(3)?.toInt()
    val level = when (matcher.group(4)) {
        "V" -> LogLevel.DEBUG
        "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        "E" -> LogLevel.ERROR
        "F" -> LogLevel.ERROR
        else -> LogLevel.INFO
    }
    val tag = matcher.group(5)
    val content = matcher.group(6) ?: ""


    return LogEntry(
        level,
        time,
        content,
        tag,
        pid,
        tid
    )
}

@Composable
@Preview
fun LogItem(
    modifier: Modifier = Modifier,
    level: LogLevel = LogLevel.INFO,
    timeText: String = "2025.01.01",
    contentText: String = "Test Logs",
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        level.bgColor,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.tag.first().toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.offset(y = (-1.5).dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = timeText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = contentText,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun exportLogFile(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        File(context.externalCacheDir, "yuhaiin.log").apply {
            writeText("Yuhaiin Logcat:\n")
            Runtime.getRuntime()
                .exec(arrayOf("logcat", "-d")).inputStream.use { input ->
                    FileOutputStream(this, true).use {
                        input.copyTo(it)
                    }
                }

            val authority = "${context.packageName}.logcat_fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, this)
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(Intent.EXTRA_STREAM, uri),
                    "Export Logcat"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}