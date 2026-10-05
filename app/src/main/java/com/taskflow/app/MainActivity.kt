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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private data class Task(val id: String = UUID.randomUUID().toString(), val text: String, val done: Boolean = false)
private data class TaskList(val id: String = UUID.randomUUID().toString(), val title: String, val tasks: List<Task>)

private const val PREFS = "taskflow"
private const val KEY_LISTS = "saved_lists"

private fun loadLists(context: Context): List<TaskList> {
    return try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LISTS, null) ?: return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val tasks = obj.getJSONArray("tasks")
            TaskList(obj.getString("id"), obj.getString("title"), List(tasks.length()) { j ->
                val t = tasks.getJSONObject(j)
                Task(t.getString("id"), t.getString("text"), false)
            })
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveLists(context: Context, lists: List<TaskList>) {
    val array = JSONArray()
    lists.forEach { list ->
        val obj = JSONObject().put("id", list.id).put("title", list.title)
        val tasks = JSONArray()
        list.tasks.forEach { task -> tasks.put(JSONObject().put("id", task.id).put("text", task.text)) }
        obj.put("tasks", tasks)
        array.put(obj)
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LISTS, array.toString()).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaskFlowApp() }
    }
}

@Composable
private fun TaskFlowApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf(loadLists(context)) }
    var current by remember {
        mutableStateOf(
            TaskList(title = "הרשימה שלי", tasks = listOf(
                Task(text = "לשתות מים"),
                Task(text = "לסדר את החדר"),
                Task(text = "לבדוק משימות להיום")
            ))
        )
    }
    var screen by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { saveLists(context, saved) }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF5B5FEF),
        background = Color(0xFFF7F8FC),
        surface = Color.White
    )) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Color(0xFFF7F8FC),
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = screen == 0,
                            onClick = { screen = 0 },
                            icon = { Icon(Icons.Default.Checklist, null) },
                            label = { Text("היום") }
                        )
                        NavigationBarItem(
                            selected = screen == 1,
                            onClick = { screen = 1 },
                            icon = { Icon(Icons.Default.Folder, null) },
                            label = { Text("הרשימות שלי") }
                        )
                    }
                }
            ) { padding ->
                AnimatedContent(targetState = screen, modifier = Modifier.padding(padding), label = "screen") { selected ->
                    if (selected == 0) {
                        Home(
                            list = current,
                            onAdd = { showAdd = true },
                            onSave = { showSave = true },
                            onToggle = { id ->
                                current = current.copy(tasks = current.tasks.map {
                                    if (it.id == id) it.copy(done = !it.done) else it
                                })
                            }
                        )
                    } else {
                        Library(
                            lists = saved,
                            onOpen = { list ->
                                current = list.copy(
                                    id = UUID.randomUUID().toString(),
                                    tasks = list.tasks.map { it.copy(done = false) }
                                )
                                screen = 0
                            },
                            onSave = { showSave = true }
                        )
                    }
                }
            }

            if (showAdd) {
                DialogInput("הוספת משימה", "מה צריך לעשות?", "הוספה", "") { text ->
                    if (text.isNotBlank()) current = current.copy(tasks = current.tasks + Task(text = text.trim()))
                    showAdd = false
                }
            }

            if (showSave) {
                DialogInput("שמירת רשימה", "שם הרשימה, למשל: שגרת ערב", "שמירה", current.title) { text ->
                    if (text.isNotBlank()) {
                        val newList = current.copy(
                            id = UUID.randomUUID().toString(),
                            title = text.trim(),
                            tasks = current.tasks.map { it.copy(done = false) }
                        )
                        saved = saved + newList
                        current = newList
                    }
                    showSave = false
                }
            }
        }
    }
}

@Composable
private fun Home(
    list: TaskList,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onToggle: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F8FC)).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("היום", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(list.title, fontSize = 15.sp, color = Color.Gray)
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.BookmarkAdd, "שמור רשימה", tint = Color(0xFF5B5FEF))
            }
        }
        Spacer(Modifier.height(18.dp))

        val active = list.tasks.filter { !it.done }
        val done = list.tasks.filter { it.done }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("משימות", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(active.size.toString() + " נשארו", color = Color(0xFF5B5FEF), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (list.tasks.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(active, key = { it.id }) { TaskRow(it, onToggle) }
                if (done.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("בוצע", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(done, key = { it.id }) { TaskRow(it, onToggle) }
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("הוספת משימה", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onToggle(task.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (task.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (task.done) Color(0xFF55B889) else Color(0xFFB9BCC8),
            modifier = Modifier.size(27.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            task.text,
            fontSize = 16.sp,
            color = if (task.done) Color(0xFF9A9DA8) else Color(0xFF252631),
            fontWeight = if (task.done) FontWeight.Normal else FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.TaskAlt, null, tint = Color(0xFF5B5FEF), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("אין עדיין משימות", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("הוסף משימה ראשונה והתחל להתקדם", color = Color.Gray)
    }
}

@Composable
private fun Library(
    lists: List<TaskList>,
    onOpen: (TaskList) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8FC)).padding(20.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("הרשימות שלי", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("רשימות קבועות שאפשר להתחיל מחדש בכל פעם", color = Color.Gray)
        Spacer(Modifier.height(22.dp))

        if (lists.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(lists, key = { it.id }) { list ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(list) }
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ListAlt, null, tint = Color(0xFF5B5FEF), modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(list.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(list.tasks.size.toString() + " משימות", color = Color.Gray, fontSize = 13.sp)
                            }
                            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF5B5FEF))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("שמירת הרשימה הנוכחית")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DialogInput(
    title: String,
    placeholder: String,
    confirmText: String,
    initial: String,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onConfirm("") },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm("") }) { Text("ביטול") }
        }
    )
}
