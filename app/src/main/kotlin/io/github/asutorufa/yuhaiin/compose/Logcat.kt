package io.github.asutorufa.yuhaiin.compose

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import io.github.asutorufa.yuhaiin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.regex.Pattern


enum class LogLevel(val tag: String, val bgColor: Color, val priority: Int) {
    DEBUG("DEBUG", Color(0xFF4CAF50), 1),
    INFO("INFO", Color(0xFF2196F3), 2),
    WARN("WARN", Color(0xFFFFC107), 3),
    ERROR("ERROR", Color(0xFFF44336), 4);

    fun enabled(filter: LogLevel): Boolean {
        return priority >= filter.priority
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
@Preview
fun SharedTransitionScope.LogcatScreen(
    logs: SnapshotStateList<LogEntry> = remember {
        mutableStateListOf(
            LogEntry(
                LogLevel.WARN,
                "2025.0.1",
                "TestLogs"
            )
        )
    },
    navController: NavController? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(LogLevel.DEBUG) }
    val context = LocalActivity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .floatingToolbarVerticalNestedScroll(
                expanded = expanded,
                onExpand = { expanded = true },
                onCollapse = { expanded = false },
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            content = { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LogList(
                        listState = listState,
                        logs = logs.filter { it.level.enabled(filter) },
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                HorizontalFloatingToolbar(
                    modifier = Modifier.thenIfNotNull(animatedVisibilityScope) {
                        sharedBounds(
                            sharedContentState = rememberSharedContentState("OPEN_LOGCAT_FAB"),
                            animatedVisibilityScope = it
                        )
                    },
                    expanded = expanded,
                    trailingContent = {},
                    leadingContent = {
                        IconButton(
                            onClick = { navController?.popBackStack() }) {
                            Icon(
                                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = "Back"
                            )
                        }
                        IconButton(
                            onClick = { logs.clear() }) {
                            Icon(
                                painter = rememberVectorPainter(Icons.Filled.Clear),
                                contentDescription = "Clear all"
                            )
                        }
                        IconButton(
                            onClick = { if (context != null) exportLogFile(context, scope) }) {
                            Icon(
                                painter = rememberVectorPainter(Icons.Filled.Share),
                                contentDescription = "Export"
                            )
                        }
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
                    }
                ) {
                    FilledIconButton(
                        onClick = {
                            scope.launch {
                                if (logs.isNotEmpty())
                                    listState.animateScrollToItem(logs.lastIndex)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll Latest"
                        )
                    }
                }
            },
        )
    }
}


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun LogList(
    listState: LazyListState = rememberLazyListState(),
    logs: List<LogEntry> = remember {
        mutableStateListOf(
            LogEntry(
                LogLevel.WARN,
                "2025.0.1",
                "TestLogs"
            ),
            LogEntry(
                LogLevel.WARN,
                "2025.0.1",
                "TestLogs"
            ),
            LogEntry(
                LogLevel.WARN,
                "2025.0.1",
                "TestLogs"
            )
        )
    },
) {
    var infoLog by remember { mutableStateOf<LogEntry?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val statusBarHeight = WindowInsets.statusBars
        .getTop(LocalDensity.current)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = statusBarHeight.dp, bottom = 16.dp)
        ) {
            items(logs) { log ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    LogItem(
                        level = log.level,
                        timeText = log.time,
                        contentText = log.content,
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        infoLog = log
                        showBottomSheet = true
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = rememberModalBottomSheetState(true)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    if (infoLog != null) LogDetail(infoLog = infoLog!!)
                    else Text("Log is not exist")
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
fun LogDetail(
    modifier: Modifier = Modifier,
    infoLog: LogEntry = LogEntry(LogLevel.INFO, "2025.08.10", "Test Content")
) {
    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
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

/**
 * Match a single line of `logcat -v threadtime`, such as:
 * 05-26 11:02:36.886  5689  5689 D AndroidRuntime: CheckJNI is OFF
 */
val THREADTIME_LINE = Pattern.compile(
    "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1] */
            "(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+" +  /* pid/tid and log level [2-4] */
            "(.+?)\\s*: (.*)$" /* tag and message [5-6]*/
)

/**
 * Match a single line of `logcat -v time`, such as:
 * 06-04 02:32:14.002 D/dalvikvm(  236): GC_CONCURRENT freed 580K, 51% free [...]
 */
val TIME_LINE = Pattern.compile(
    "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1] */
            "(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$"
) /* level, tag, pid, msg [2-5] */

data class LogEntry(
    var level: LogLevel = LogLevel.INFO,
    var time: String = "",
    var content: String = "",
    var tag: String? = "",
    var pid: Int? = 0,
    var tid: Int? = 0,
)

fun parseLogv2(line: String): LogEntry {
    val log = LogEntry(
        content = line
    )

    val m = THREADTIME_LINE.matcher(line)
    if (m.matches()) {
        log.time = m.group(1) ?: ""
        log.pid = m.group(2)?.toInt()
        log.tid = m.group(3)?.toInt()
        log.level = when (m.group(4)) {
            "V", "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E", "F" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        log.tag = m.group(5)
        m.group(6)?.apply {
            log.content = this
        }
    } else {
        val tm = TIME_LINE.matcher(line)
        if (!tm.matches()) return log

        log.time = m.group(1) ?: ""
        log.level = when (m.group(4)) {
            "V", "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E", "F" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        log.tag = tm.group(3)
        log.pid = tm.group(4)?.toInt()
        tm.group(5)?.apply {
            log.content = this
        }
    }

    return log
}

fun parseLog(line: String): LogEntry {
    var i = 0
    val n = line.length

    fun skipSpaces() {
        while (i < n && line[i] == ' ') i++
    }

    fun readWord(): String {
        val start = i
        while (i < n && line[i] != ' ') i++
        return line.substring(start, i)
    }

    skipSpaces()
    val date = readWord()
    skipSpaces()
    val timeStr = readWord()
    skipSpaces()
    val pidStr = readWord()
    skipSpaces()
    val tidStr = readWord()
    skipSpaces()
    val levelStr = readWord()
    skipSpaces()
    val tagStart = i
    while (i < n && line[i] != ':') i++
    val tag = if (i < n) line.substring(tagStart, i) else ""
    i++ // skip ':'
    val content = if (i < n) line.substring(i).trimStart() else ""

    val level = when (levelStr) {
        "V", "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        "E", "F" -> LogLevel.ERROR
        else -> LogLevel.INFO
    }

    if (content.isEmpty())
        return LogEntry(time = Date().toString(), content = line)

    return LogEntry(
        level = level,
        time = "$date $timeStr",
        content = content,
        tag = tag,
        pid = pidStr.toIntOrNull(),
        tid = tidStr.toIntOrNull()
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
            .padding(6.dp)
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
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Text(
            text = contentText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
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

fun runLogcat(
    scope: CoroutineScope,
    excludeList: ACAutomaton = ACAutomaton(),
    pushLogs: (LogEntry) -> Unit
): Process {
    val process = ProcessBuilder(listOf("logcat", "-v", "threadtime")).start()

    scope.launch(Dispatchers.Default) {
        Log.i("logcat process", "start read logcat")
        process.inputStream.bufferedReader().use {
            while (true) {
                try {
                    it.readLine()?.let { line ->
                        if (excludeList.exist(line)) return@let
                        pushLogs(parseLogv2(line))
                    } ?: break
                } catch (e: Exception) {
                    Log.w("read log failed", "$e")
                    break
                }
            }
        }

        Log.i("logcat process", "stop read logcat")
        process.destroy()
    }

    return process
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LogcatCompose(
    excludeList: ArrayList<String>? = null,
    navController: NavController? = null,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val excludeList = remember {
        val mExcludeList = ACAutomaton()
        excludeList?.forEach { mExcludeList.insert(it) }
        mExcludeList.buildFail()
        mExcludeList
    }

    val logs = remember { mutableStateListOf<LogEntry>() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val process = runLogcat(scope, excludeList) { logs.add(it) }
        onDispose {
            logs.add(LogEntry(LogLevel.INFO, "", "stop read logcat"))
            process.destroy()
        }
    }

    LogcatScreen(
        logs = logs,
        navController = navController,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}
