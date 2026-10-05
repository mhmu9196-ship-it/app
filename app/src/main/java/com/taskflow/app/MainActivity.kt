package com.taskflow.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private data class Task(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false
)

private data class TaskList(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tasks: List<Task>
)

private const val PREFS = "taskflow"
private const val KEY_LISTS = "saved_lists"

private fun loadLists(context: Context): List<TaskList> = try {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LISTS, null) ?: return emptyList()
    val array = JSONArray(raw)
    List(array.length()) { i ->
        val obj = array.getJSONObject(i)
        val taskArray = obj.getJSONArray("tasks")
        TaskList(
            id = obj.getString("id"),
            title = obj.getString("title"),
            tasks = List(taskArray.length()) { j ->
                val task = taskArray.getJSONObject(j)
                Task(task.getString("id"), task.getString("text"))
            }
        )
    }
} catch (_: Exception) {
    emptyList()
}

private fun saveLists(context: Context, lists: List<TaskList>) {
    val array = JSONArray()
    lists.forEach { list ->
        val obj = JSONObject().put("id", list.id).put("title", list.title)
        val taskArray = JSONArray()
        list.tasks.forEach { task ->
            taskArray.put(JSONObject().put("id", task.id).put("text", task.text))
        }
        obj.put("tasks", taskArray)
        array.put(obj)
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_LISTS, array.toString()).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaskFlowApp() }
    }
}

@Composable
private fun TaskFlowApp() {
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenWidthDp <= 320
    var savedLists by remember { mutableStateOf(loadLists(context)) }
    var current by remember {
        mutableStateOf(
            TaskList(
                title = "רשימת משימות",
                tasks = listOf(
                    Task(text = "לשתות מים"),
                    Task(text = "לסדר את החדר"),
                    Task(text = "לבדוק משימות להיום")
                )
            )
        )
    }
    var screen by remember { mutableStateOf(0) }
    var showAddTasks by remember { mutableStateOf(false) }
    var showNewList by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<TaskList?>(null) }

    LaunchedEffect(savedLists) { saveLists(context, savedLists) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF5B5FEF),
            background = Color(0xFFF7F8FC),
            surface = Color.White
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Color(0xFFF7F8FC),
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = screen == 0,
                            onClick = { screen = 0 },
                            icon = { Icon(Icons.Default.Checklist, null, modifier = Modifier.size(if (compact) 20.dp else 24.dp)) },
                            label = { Text("משימות", fontSize = if (compact) 11.sp else 12.sp) }
                        )
                        NavigationBarItem(
                            selected = screen == 1,
                            onClick = { screen = 1 },
                            icon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(if (compact) 20.dp else 24.dp)) },
                            label = { Text("רשימות", fontSize = if (compact) 11.sp else 12.sp) }
                        )
                    }
                }
            ) { padding ->
                AnimatedContent(targetState = screen, modifier = Modifier.padding(padding), label = "screen") { selected ->
                    if (selected == 0) {
                        Home(
                            list = current,
                            compact = compact,
                            onAddTasks = { showAddTasks = true },
                            onSaveList = { showNewList = true },
                            onToggle = { id ->
                                current = current.copy(tasks = current.tasks.map {
                                    if (it.id == id) it.copy(done = !it.done) else it
                                })
                            },
                            onDeleteTask = { id ->
                                current = current.copy(tasks = current.tasks.filterNot { it.id == id })
                            }
                        )
                    } else {
                        Library(
                            lists = savedLists,
                            compact = compact,
                            onNewList = { showNewList = true },
                            onOpen = { list ->
                                current = list.copy(
                                    id = UUID.randomUUID().toString(),
                                    tasks = list.tasks.map { it.copy(done = false) }
                                )
                                screen = 0
                            },
                            onDelete = { listToDelete = it }
                        )
                    }
                }
            }

            if (showAddTasks) {
                AddTasksDialog(
                    compact = compact,
                    onDismiss = { showAddTasks = false },
                    onConfirm = { texts ->
                        val additions = texts.filter { it.isNotBlank() }.map { Task(text = it.trim()) }
                        current = current.copy(tasks = current.tasks + additions)
                        showAddTasks = false
                    }
                )
            }

            if (showNewList) {
                NewListDialog(
                    compact = compact,
                    onDismiss = { showNewList = false },
                    onConfirm = { title, texts ->
                        val newList = TaskList(
                            title = title.trim(),
                            tasks = texts.filter { it.isNotBlank() }.map { Task(text = it.trim()) }
                        )
                        savedLists = savedLists + newList
                        current = newList.copy(id = UUID.randomUUID().toString())
                        showNewList = false
                        screen = 0
                    }
                )
            }

            listToDelete?.let { list ->
                AlertDialog(
                    onDismissRequest = { listToDelete = null },
                    title = { Text("מחיקת רשימה", fontWeight = FontWeight.Bold) },
                    text = { Text("למחוק את הרשימה "${list.title}"? המשימות שבה יימחקו מהרשימות השמורות.") },
                    confirmButton = {
                        TextButton(onClick = {
                            savedLists = savedLists.filterNot { it.id == list.id }
                            listToDelete = null
                        }) { Text("מחיקה", color = Color(0xFFC33C54)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { listToDelete = null }) { Text("ביטול") }
                    }
                )
            }
        }
    }
}

@Composable
private fun Home(
    list: TaskList,
    compact: Boolean,
    onAddTasks: () -> Unit,
    onSaveList: () -> Unit,
    onToggle: (String) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    val horizontal = if (compact) 10.dp else 18.dp

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F8FC)).padding(horizontal = horizontal)
    ) {
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(list.title, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${list.tasks.count { !it.done }} משימות פתוחות",
                    fontSize = if (compact) 12.sp else 14.sp,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onSaveList) {
                Icon(Icons.Default.BookmarkAdd, "שמור כרשימה", tint = Color(0xFF5B5FEF))
            }
        }

        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

        val active = list.tasks.filter { !it.done }
        val done = list.tasks.filter { it.done }

        if (list.tasks.isEmpty()) {
            EmptyState(compact)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(active, key = { it.id }) { task ->
                    TaskRow(task, compact, onToggle, onDeleteTask)
                }
                if (done.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(if (compact) 4.dp else 7.dp))
                        Text("בוצע", color = Color.Gray, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(done, key = { it.id }) { task ->
                        TaskRow(task, compact, onToggle, onDeleteTask)
                    }
                }
            }
        }

        Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
        Button(
            onClick = onAddTasks,
            modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp),
            shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(if (compact) 19.dp else 22.dp))
            Spacer(Modifier.width(6.dp))
            Text("הוספת משימות", fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(if (compact) 7.dp else 11.dp))
    }
}

@Composable
private fun TaskRow(
    task: Task,
    compact: Boolean,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .background(Color.White)
            .padding(horizontal = if (compact) 8.dp else 13.dp, vertical = if (compact) 8.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onToggle(task.id) }, modifier = Modifier.size(if (compact) 32.dp else 38.dp)) {
            Icon(
                if (task.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (task.done) Color(0xFF55B889) else Color(0xFFB9BCC8),
                modifier = Modifier.size(if (compact) 23.dp else 27.dp)
            )
        }
        Spacer(Modifier.width(if (compact) 4.dp else 9.dp))
        Text(
            task.text,
            modifier = Modifier.weight(1f),
            fontSize = if (compact) 14.sp else 16.sp,
            lineHeight = if (compact) 18.sp else 21.sp,
            color = if (task.done) Color(0xFF9A9DA8) else Color(0xFF252631),
            fontWeight = if (task.done) FontWeight.Normal else FontWeight.Medium
        )
        IconButton(onClick = { onDelete(task.id) }, modifier = Modifier.size(if (compact) 32.dp else 36.dp)) {
            Icon(Icons.Default.DeleteOutline, "מחק משימה", tint = Color(0xFFC5C7D0), modifier = Modifier.size(if (compact) 19.dp else 22.dp))
        }
    }
}

@Composable
private fun Library(
    lists: List<TaskList>,
    compact: Boolean,
    onNewList: () -> Unit,
    onOpen: (TaskList) -> Unit,
    onDelete: (TaskList) -> Unit
) {
    val horizontal = if (compact) 10.dp else 18.dp
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F8FC)).padding(horizontal = horizontal)
    ) {
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        Text("הרשימות שלי", fontSize = if (compact) 23.sp else 29.sp, fontWeight = FontWeight.Bold)
        Text("פותחים רשימה ומתחילים מחדש", color = Color.Gray, fontSize = if (compact) 12.sp else 14.sp)
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))

        if (lists.isEmpty()) {
            EmptyState(compact)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(lists, key = { it.id }) { list ->
                    Card(shape = RoundedCornerShape(if (compact) 14.dp else 19.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.clickable { onOpen(list) }
                                .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 9.dp else 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ListAlt, null, tint = Color(0xFF5B5FEF), modifier = Modifier.size(if (compact) 25.dp else 30.dp))
                            Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(list.title, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 17.sp)
                                Text("${list.tasks.size} משימות", color = Color.Gray, fontSize = if (compact) 11.sp else 13.sp)
                            }
                            IconButton(onClick = { onDelete(list) }, modifier = Modifier.size(if (compact) 32.dp else 38.dp)) {
                                Icon(Icons.Default.DeleteOutline, "מחק רשימה", tint = Color(0xFFC33C54), modifier = Modifier.size(if (compact) 19.dp else 22.dp))
                            }
                            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF5B5FEF), modifier = Modifier.size(if (compact) 20.dp else 24.dp))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onNewList,
            modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp),
            shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(if (compact) 19.dp else 22.dp))
            Spacer(Modifier.width(6.dp))
            Text("הוספת רשימת משימות", fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(if (compact) 7.dp else 11.dp))
    }
}

@Composable
private fun EmptyState(compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(if (compact) 24.dp else 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.TaskAlt, null, tint = Color(0xFF5B5FEF), modifier = Modifier.size(if (compact) 40.dp else 48.dp))
        Spacer(Modifier.height(10.dp))
        Text("אין כאן עדיין משימות", fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Bold)
        Text("הוסף כמה משימות בבת אחת והתחל להתקדם", color = Color.Gray, fontSize = if (compact) 11.sp else 13.sp)
    }
}

@Composable
private fun AddTasksDialog(
    compact: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var rows by remember { mutableStateOf(listOf("")) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (compact) 0.97f else 0.92f).heightIn(max = if (compact) 540.dp else 680.dp),
            shape = RoundedCornerShape(if (compact) 18.dp else 24.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(if (compact) 12.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("הוספת משימות", fontSize = if (compact) 19.sp else 23.sp, fontWeight = FontWeight.Bold)
                        Text("הוסף כמה שורות ואשר את כולן יחד", color = Color.Gray, fontSize = if (compact) 11.sp else 13.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "סגירה") }
                }
                Spacer(Modifier.height(7.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(rows, key = { index, _ -> index }) { index, value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue -> rows = rows.toMutableList().also { it[index] = newValue } },
                                singleLine = true,
                                placeholder = { Text("משימה ${index + 1}", fontSize = if (compact) 12.sp else 14.sp) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = if (compact) 14.sp else 16.sp),
                                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                            )
                            if (rows.size > 1) {
                                IconButton(onClick = { rows = rows.filterIndexed { i, _ -> i != index } }, modifier = Modifier.size(if (compact) 32.dp else 38.dp)) {
                                    Icon(Icons.Default.RemoveCircleOutline, "הסר שורה", tint = Color(0xFFC33C54))
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { rows = rows + "" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("הוספת שורה", fontSize = if (compact) 13.sp else 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)) {
                        Text("ביטול", fontSize = if (compact) 13.sp else 14.sp)
                    }
                    Button(onClick = { onConfirm(rows) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)) {
                        Text("הוספת הכול", fontSize = if (compact) 13.sp else 14.sp)
                    }
                }
            }
        }
    }
}

private fun newValueSafe(list: MutableList<String>, index: Int, value: String): String = value

@Composable
private fun NewListDialog(
    compact: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf("")) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (compact) 0.97f else 0.92f).heightIn(max = if (compact) 580.dp else 720.dp),
            shape = RoundedCornerShape(if (compact) 18.dp else 24.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(if (compact) 12.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("רשימת משימות חדשה", fontSize = if (compact) 19.sp else 23.sp, fontWeight = FontWeight.Bold)
                        Text("כותרת וכל המשימות, ואז שמירה אחת", color = Color.Gray, fontSize = if (compact) 11.sp else 13.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "סגירה") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("כותרת הרשימה") },
                    placeholder = { Text("למשל: שגרת ערב") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = if (compact) 14.sp else 16.sp),
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                )
                Spacer(Modifier.height(7.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(rows, key = { index, _ -> index }) { index, value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue -> rows = rows.toMutableList().also { it[index] = newValue } },
                                singleLine = true,
                                placeholder = { Text("משימה ${index + 1}", fontSize = if (compact) 12.sp else 14.sp) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = if (compact) 14.sp else 16.sp),
                                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                            )
                            if (rows.size > 1) {
                                IconButton(onClick = { rows = rows.filterIndexed { i, _ -> i != index } }, modifier = Modifier.size(if (compact) 32.dp else 38.dp)) {
                                    Icon(Icons.Default.RemoveCircleOutline, "הסר שורה", tint = Color(0xFFC33C54))
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { rows = rows + "" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("הוספת שורה", fontSize = if (compact) 13.sp else 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (title.isNotBlank() && rows.any { it.isNotBlank() }) onConfirm(title, rows)
                    },
                    enabled = title.isNotBlank() && rows.any { it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth().height(if (compact) 47.dp else 52.dp),
                    shape = RoundedCornerShape(if (compact) 13.dp else 16.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(if (compact) 18.dp else 21.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("שמירת הרשימה", fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
