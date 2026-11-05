package com.projectbharat.furnitureworkshoperp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import java.util.Date
import java.util.TimeZone
import java.text.SimpleDateFormat

private val Coral = Color(0xFFFF6666)
private val Slate = Color(0xFF525266)
private val Ivory = Color(0xFFF7F4F1)
private val Line = Color(0xFFEAE4E0)
private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 244, 241)
        window.navigationBarColor = android.graphics.Color.rgb(255, 255, 255)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Coral, onPrimary = Color.White, secondary = Slate,
                background = Ivory, surface = Color.White, onSurface = Slate
            )) { WorkshopApp(WorkshopDatabase.getInstance(this)) }
        }
    }
}

@Composable
private fun WorkshopApp(database: WorkshopDatabase) {
    var page by remember { mutableStateOf("Overview") }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WorkRecord?>(null) }
    var selectedWorker by remember { mutableStateOf<WorkRecord?>(null) }
    var editing by remember { mutableStateOf<WorkRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WorkRecord?>(null) }
    val dao = remember(database) { database.workRecordDao() }
    val records by dao.observeAll().collectAsState(initial = emptyList())
    val attendanceDao = remember(database) { database.attendanceDao() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(dao, attendanceDao) {
        if (dao.count() == 0) {
            val initial = initialWorkshopRecords()
            dao.insertAll(initial)
            initial.filter { it.kind == "Workforce" }.forEach { person ->
                attendanceDao.save(AttendanceEntry(personId = person.id, attendanceDate = todayDate(), status = person.attendance.ifBlank { "Absent" }, note = "Initial attendance entry"))
            }
        }
    }
    fun persist(record: WorkRecord) {
        scope.launch {
            if (record.id == 0) dao.insert(record) else dao.update(record)
        }
    }

    val currentRecords = records.filter { it.kind == pageKind(page) &&
        (query.isBlank() || listOf(it.title,it.detail,it.status,it.role,it.phone,it.task).any { s -> s.contains(query,true) }) }

    Scaffold(
        containerColor = Ivory,
        topBar = {
            Column(
                Modifier
                    .background(Color.White)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(54.dp).padding(horizontal=14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandLogo(Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen; query = "" }) {
                        Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", tint = Slate)
                    }
                    IconButton(onClick = { page = "Approvals" }) {
                        BadgedBox(badge = { if (records.count { it.kind == "Approval" && it.status == "Awaiting approval" } > 0) Badge { Text(records.count { it.kind == "Approval" && it.status == "Awaiting approval" }.toString()) } }) {
                            Icon(Icons.Default.NotificationsNone, "Approvals", tint = Slate)
                        }
                    }
                }
                if (searchOpen) OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                    placeholder = { Text("Search records, people, tasks") }, singleLine = true
                )
                HorizontalDivider(color = Line)
            }
        },
        floatingActionButton = {
            if (page != "Overview" && page != "More") FloatingActionButton(
                onClick = { editing = null; adding = true },
                containerColor = Coral, contentColor = Color.White
            ) { Icon(Icons.Default.Add, "Add record") }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Overview","Projects","Production","Inventory","More").forEach { name ->
                    val icon = when (name) {
                        "Overview" -> Icons.Default.Dashboard
                        "Projects" -> Icons.Default.Folder
                        "Production" -> Icons.Default.Handyman
                        "Inventory" -> Icons.Default.Warehouse
                        else -> Icons.Default.Menu
                    }
                    NavigationBarItem(
                        selected = page == name || (name == "More" && page in listOf("Enquiries","Workforce","Approvals")),
                        onClick = { page = name; selectedWorker = null; query = ""; searchOpen = false },
                        icon = { Icon(icon, null) }, label = { Text(name, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "Overview" -> Overview(records, onOpen = { page = it }, onAdd = { page = "Enquiries"; adding = true }, onSelect = { selected = it })
                "More" -> MoreModules { page = it; selectedWorker = null }
                else -> {
                    if (page == "Workforce" && selectedWorker != null) {
                        val person = selectedWorker!!
                        WorkforcePersonPage(
                            person = person,
                            attendance = attendanceDao.observeForPerson(person.id).collectAsState(initial = emptyList()).value,
                            onBack = { selectedWorker = null },
                            onEdit = { editing = person },
                            onDelete = { deleting = person },
                            onSaveAttendance = { date, status, note ->
                                scope.launch {
                                    attendanceDao.save(AttendanceEntry(personId = person.id, attendanceDate = date, status = status, note = note))
                                    if (date == todayDate()) persist(person.copy(attendance = status, status = status))
                                }
                            }
                        )
                    } else ModulePage(
                        page = page, data = currentRecords, allData = records,
                        onOpen = { if (page == "Workforce") selectedWorker = it else selected = it },
                        onStatus = { record, status -> persist(record.copy(status = status, attendance = if (record.kind == "Workforce") status else record.attendance)) },
                        onAttendance = { record ->
                            val next = if (record.attendance == "Present") "Absent" else "Present"
                            scope.launch {
                                attendanceDao.save(AttendanceEntry(personId = record.id, attendanceDate = todayDate(), status = next))
                                persist(record.copy(attendance = next, status = next))
                            }
                        }
                    )
                }
            }
        }
    }

    if (adding || editing != null) RecordEditor(
        initial = editing, defaultKind = pageKind(page),
        onDismiss = { adding = false; editing = null },
        onSave = { value ->
            persist(if (editing == null) value.copy(id = 0) else value)
            if (selectedWorker != null && editing != null) selectedWorker = value
            adding = false; editing = null
        }
    )
    selected?.let { record ->
        RecordDetails(record, onDismiss = { selected = null },
            onEdit = { editing = record; selected = null },
            onDelete = { deleting = record; selected = null },
            onStatus = { status -> persist(record.copy(status = status, attendance = if (record.kind == "Workforce") status else record.attendance)); selected = record.copy(status = status) },
            onAttendance = { val next = if (record.attendance == "Present") "Absent" else "Present"; persist(record.copy(attendance = next, status = next)); selected = record.copy(attendance = next, status = next) }
        )
    }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete record?") },
            text = { Text("“${record.title}” will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { scope.launch { dao.deleteById(record.id); if (record.kind == "Workforce") attendanceDao.deleteForPerson(record.id) }; deleting = null; selectedWorker = null }) { Text("Delete", color = Coral) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

private fun pageKind(page: String) = when(page) {
    "Enquiries" -> "Enquiry"; "Projects" -> "Project"; "Production" -> "Production"
    "Inventory" -> "Inventory"; "Workforce" -> "Workforce"; "Approvals" -> "Approval"
    else -> ""
}
@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    val chosen = remember { drawable("logo_horizontal") }
    if (chosen != 0) Image(
        painterResource(chosen), "Aakruti", modifier.height(32.dp).widthIn(max = 150.dp),
        contentScale = ContentScale.Fit
    ) else Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Slate, shape = RoundedCornerShape(7.dp)) {
            Text("A", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("AAKRUTI", fontWeight = FontWeight.ExtraBold, color = Slate, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}
private fun drawable(name: String): Int = try {
    Class.forName("com.projectbharat.furnitureworkshoperp.R\$drawable").getField(name).getInt(null)
} catch (_: Exception) { 0 }

@Composable
private fun Overview(data: List<WorkRecord>, onOpen: (String) -> Unit, onAdd: () -> Unit, onSelect: (WorkRecord) -> Unit) {
    val metrics = listOf(
        "Enquiries" to data.count { it.kind == "Enquiry" },
        "Projects" to data.count { it.kind == "Project" },
        "Production" to data.count { it.kind == "Production" && it.status != "Completed" },
        "Approvals" to data.count { it.kind == "Approval" && it.status == "Awaiting approval" }
    )
    val workforcePresent = data.count { it.kind == "Workforce" && it.attendance == "Present" }
    val projectValue = data.filter { it.kind == "Project" }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Overview", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Slate)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Slate), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text("PROJECT VALUE", fontSize = 9.sp, color = Color.White.copy(.68f), letterSpacing = 1.sp)
                    Text(money.format(projectValue), fontSize = 25.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        metrics.take(3).forEach { (label,count) -> Column { Text(count.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text(label, color = Color.White.copy(.7f), fontSize = 9.sp) } }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { (label,count) ->
                    Card(Modifier.weight(1f).clickable { onOpen(label) }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.padding(11.dp)) { Text(count.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Slate); Text(label, fontSize = 9.sp, color = Slate.copy(.65f)) }
                    }
                }
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Quick actions", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Slate); Text("All modules", Modifier.clickable { onOpen("More") }, color = Coral, fontSize = 10.sp) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Quick("New enquiry", Icons.Default.PersonAdd, onAdd, Modifier.weight(1f))
                Quick("Workforce", Icons.Default.Groups, { onOpen("Workforce") }, Modifier.weight(1f))
                Quick("Inventory", Icons.Default.Warehouse, { onOpen("Inventory") }, Modifier.weight(1f))
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Workforce today", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Slate); Text("$workforcePresent present", color = Coral, fontSize = 10.sp) } }
        items(data.filter { it.kind == "Workforce" }.take(3), key = { it.id }) { RecordTile(it) { onSelect(it) } }
        item { Text("Recent records", fontWeight = FontWeight.Bold, color = Slate) }
        items(data.filter { it.kind != "Workforce" }.take(4), key = { it.id }) { RecordTile(it) { onSelect(it) } }
    }
}

@Composable
private fun Quick(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = action, modifier = modifier.height(58.dp), contentPadding = PaddingValues(4.dp), shape = RoundedCornerShape(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Coral, modifier = Modifier.size(18.dp)); Text(label, fontSize = 9.sp, color = Slate) }
    }
}

@Composable
private fun MoreModules(open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { Text("Modules", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Slate) }
        items(listOf("Enquiries","Projects","Production","Inventory","Workforce","Approvals")) { name ->
            Card(Modifier.fillMaxWidth().clickable { open(name) }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold, color = Slate); Text(moduleDescription(name), fontSize = 10.sp, color = Slate.copy(.58f)) }
                    Icon(Icons.Default.ChevronRight, null, tint = Coral)
                }
            }
        }
    }
}
private fun moduleDescription(name: String) = when(name) {
    "Enquiries" -> "Customers and follow-ups"; "Projects" -> "Orders and measurements"
    "Production" -> "Jobs and workshop stages"; "Inventory" -> "Materials and stock"
    "Workforce" -> "People, attendance and tasks"; else -> "Quotation and purchase decisions"
}

@Composable
private fun ModulePage(
    page: String, data: List<WorkRecord>, allData: List<WorkRecord>,
    onOpen: (WorkRecord) -> Unit, onStatus: (WorkRecord,String) -> Unit, onAttendance: (WorkRecord) -> Unit
) {
    val workforce = page == "Workforce"
    val present = allData.count { it.kind == "Workforce" && it.attendance == "Present" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text(page.uppercase(), fontSize = 9.sp, color = Coral, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(page, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Slate)
            if (workforce) {
                Text("${data.size} people · $present present · ${data.size-present} absent", fontSize = 10.sp, color = Slate.copy(.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallMetric("Present", present); SmallMetric("Absent", data.size-present); SmallMetric("Tasks", data.count { it.task.isNotBlank() })
                }
            } else Text("${data.size} records", fontSize = 10.sp, color = Slate.copy(.6f))
        }
        if (workforce) item { Text("Team", fontWeight = FontWeight.Bold, color = Slate) }
        if (data.isEmpty()) item { Text("No records. Use + to add.", color = Slate.copy(.6f), modifier = Modifier.padding(12.dp)) }
        items(data, key = { it.id }) { record ->
            Column {
                RecordTile(record) { onOpen(record) }
                if (workforce) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onAttendance(record) }) { Text(if (record.attendance == "Present") "Mark absent" else "Mark present", color = Coral, fontSize = 10.sp) }
                    TextButton(onClick = { onOpen(record) }) { Text("Details", color = Slate, fontSize = 10.sp) }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onStatus(record, nextStatus(page, record.status)) }) { Text("Update status", color = Coral, fontSize = 10.sp) }
                    TextButton(onClick = { onOpen(record) }) { Text("Details", color = Slate, fontSize = 10.sp) }
                }
            }
        }
    }
}

private fun todayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
private fun pickerDate(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
}

@Composable
private fun WorkforcePersonPage(
    person: WorkRecord,
    attendance: List<AttendanceEntry>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaveAttendance: (String, String, String) -> Unit
) {
    var chosenDate by remember(person.id) { mutableStateOf(todayDate()) }
    var chosenStatus by remember(person.id) { mutableStateOf("Present") }
    var note by remember(person.id) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val monthEntries = attendance.filter { it.attendanceDate.startsWith(currentMonth()) }
    val presentDays = monthEntries.count { it.status == "Present" }
    val halfDays = monthEntries.count { it.status == "Half day" }
    val absentDays = monthEntries.count { it.status == "Absent" }
    val paidDays = presentDays + halfDays * 0.5
    val earned = if (person.monthlySalary > 0) person.monthlySalary / 26.0 * paidDays else 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Slate) }
                Column(Modifier.weight(1f)) {
                    Text(person.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate)
                    Text(person.role.ifBlank { "Team member" }, fontSize = 11.sp, color = Slate.copy(.6f))
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit person", tint = Coral) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete person", tint = Color(0xFFB23A3A)) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Slate), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MONTHLY SALARY SUMMARY", color = Color.White.copy(.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (person.monthlySalary > 0) money.format(person.monthlySalary) else "Salary not set", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column { Text(presentDays.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text("Present", color = Color.White.copy(.7f), fontSize = 10.sp) }
                        Column { Text(halfDays.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text("Half days", color = Color.White.copy(.7f), fontSize = 10.sp) }
                        Column { Text(absentDays.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text("Absent", color = Color.White.copy(.7f), fontSize = 10.sp) }
                    }
                    HorizontalDivider(color = Color.White.copy(.2f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Estimated earned this month", color = Color.White.copy(.7f), fontSize = 10.sp)
                            Text(money.format(earned), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onEdit) { Text("Edit salary", color = Color.White) }
                    }
                    Text("Estimate uses 26 working days per month; adjust salary and attendance before payroll.", color = Color.White.copy(.65f), fontSize = 9.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Record attendance", fontWeight = FontWeight.Bold, color = Slate)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text(chosenDate)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Present", "Absent", "Half day", "Leave").forEach { value ->
                            FilterChip(selected = chosenStatus == value, onClick = { chosenStatus = value }, label = { Text(value, fontSize = 10.sp) })
                        }
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note (optional)") }, singleLine = true
                    )
                    Button(
                        onClick = { onSaveAttendance(chosenDate, chosenStatus, note.trim()); note = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save attendance") }
                }
            }
        }
        item { Text("Attendance history", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate) }
        if (attendance.isEmpty()) item { Text("No attendance entries recorded yet.", color = Slate.copy(.6f), fontSize = 12.sp) }
        items(attendance, key = { it.id }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.attendanceDate, fontWeight = FontWeight.SemiBold, color = Slate, fontSize = 12.sp)
                        if (entry.note.isNotBlank()) Text(entry.note, color = Slate.copy(.6f), fontSize = 10.sp)
                    }
                    Text(entry.status, color = if (entry.status == "Present") Color(0xFF27835A) else Coral, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Contact & assignment", fontWeight = FontWeight.Bold, color = Slate)
                    DetailLine("Phone", person.phone.ifBlank { "Not added" })
                    DetailLine("Current task", person.task.ifBlank { "Not assigned" })
                    DetailLine("Details", person.detail.ifBlank { "—" })
                }
            }
        }
    }
    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { chosenDate = pickerDate(it) }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    }
}

@Composable
private fun SmallMetric(label: String, value: Int) {
    Card(Modifier.width(96.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp)) { Text(value.toString(), fontWeight = FontWeight.Bold, color = Slate, fontSize = 18.sp); Text(label, color = Slate.copy(.6f), fontSize = 9.sp) }
    }
}

private fun nextStatus(page: String, status: String) = when(page) {
    "Approvals" -> if (status == "Approved") "Awaiting approval" else "Approved"
    "Production" -> when(status) { "Pending" -> "In progress"; "In progress" -> "Completed"; else -> "In progress" }
    "Inventory" -> if (status == "Low stock") "Reorder requested" else "In stock"
    else -> when(status) { "New" -> "Contacted"; "Contacted" -> "Site visit"; "Site visit" -> "Quotation sent"; "Quotation sent" -> "Converted"; else -> "In progress" }
}

@Composable
private fun RecordTile(record: WorkRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFFFEFED), shape = RoundedCornerShape(9.dp)) {
                Icon(when(record.kind) {
                    "Project" -> Icons.Default.HomeWork; "Production" -> Icons.Default.Handyman
                    "Inventory" -> Icons.Default.Warehouse; "Workforce" -> Icons.Default.Person
                    "Approval" -> Icons.Default.FactCheck; else -> Icons.Default.Person
                }, null, tint = Coral, modifier = Modifier.padding(9.dp).size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(record.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate)
                Text(record.detail, fontSize = 9.sp, color = Slate.copy(.58f))
                Text(if (record.kind == "Workforce") "${record.role} · ${record.attendance}" else record.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Coral)
            }
            if (record.amount > 0) Text(money.format(record.amount), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate)
            Icon(Icons.Default.ChevronRight, null, tint = Slate.copy(.35f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RecordDetails(
    record: WorkRecord, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onStatus: (String) -> Unit, onAttendance: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(record.title, color = Slate, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                DetailLine("Type", record.kind); DetailLine("Details", record.detail); DetailLine("Status", record.status)
                if (record.amount > 0) DetailLine("Amount", money.format(record.amount))
                if (record.phone.isNotBlank()) DetailLine("Phone", record.phone)
                if (record.role.isNotBlank()) DetailLine("Role", record.role)
                if (record.attendance.isNotBlank()) DetailLine("Attendance", record.attendance)
                if (record.task.isNotBlank()) DetailLine("Assigned task", record.task)
                if (record.kind == "Workforce") TextButton(onClick = onAttendance) { Text(if (record.attendance == "Present") "Mark absent" else "Mark present", color = Coral) }
                else TextButton(onClick = { onStatus(nextStatus(record.kind.let { if(it=="Approval") "Approvals" else it },record.status)) }) { Text("Update status", color = Coral) }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit", color = Coral) } },
        dismissButton = { Row { TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFB23A3A)) }; TextButton(onClick = onDismiss) { Text("Close", color = Slate) } } }
    )
}
@Composable
private fun DetailLine(label: String, value: String) { Column { Text(label, fontSize = 9.sp, color = Slate.copy(.55f)); Text(value, fontSize = 12.sp, color = Slate) } }

@Composable
private fun RecordEditor(initial: WorkRecord?, defaultKind: String, onDismiss: () -> Unit, onSave: (WorkRecord) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var detail by remember(initial) { mutableStateOf(initial?.detail ?: "") }
    var amount by remember(initial) { mutableStateOf(if ((initial?.amount ?: 0.0) > 0) initial!!.amount.toString() else "") }
    var kind by remember(initial) { mutableStateOf(initial?.kind ?: defaultKind.ifBlank { "Enquiry" }) }
    var status by remember(initial) { mutableStateOf(initial?.status ?: "New") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var role by remember(initial) { mutableStateOf(initial?.role ?: "") }
    var task by remember(initial) { mutableStateOf(initial?.task ?: "") }
    var monthlySalary by remember(initial) { mutableStateOf(initial?.monthlySalary?.takeIf { it > 0 }?.toString() ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val kinds = listOf("Enquiry","Project","Production","Inventory","Workforce","Approval")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if(initial == null) "Add record" else "Edit record", color = Slate, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(kind); Icon(Icons.Default.ArrowDropDown, null) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        kinds.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { kind = it; expanded = false }) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text(if(kind=="Workforce") "Person name" else "Name / title") }, singleLine = true)
                OutlinedTextField(detail, { detail = it }, label = { Text("Details") }, minLines = 2)
                if (kind == "Workforce") {
                    OutlinedTextField(role, { role = it }, label = { Text("Role / designation") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(task, { task = it }, label = { Text("Assigned task") }, singleLine = true)
                    OutlinedTextField(monthlySalary, { monthlySalary = it }, label = { Text("Monthly salary ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (kind in listOf("Project","Enquiry","Approval")) OutlinedTextField(amount, { amount = it }, label = { Text("Amount ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val attendance = if (kind == "Workforce") (initial?.attendance ?: "Absent") else ""
                onSave(WorkRecord(initial?.id ?: 0,title.trim(),detail.trim(),status.trim().ifBlank{"New"},amount.toDoubleOrNull() ?: 0.0,kind,phone.trim(),role.trim(),attendance,task.trim(),monthlySalary.toDoubleOrNull() ?: 0.0))
            }) { Text("Save", color = Coral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Slate) } }
    )
}
