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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val Coral = Color(0xFFFF6666)
private val Slate = Color(0xFF525266)
private val Ivory = Color(0xFFF7F4F1)
private val Line = Color(0xFFEAE4E0)
private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

private data class WorkRecord(
    val id: Int, val title: String, val detail: String, val status: String,
    val amount: Double = 0.0, val kind: String, val phone: String = "",
    val role: String = "", val attendance: String = "", val task: String = ""
)

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
            )) { WorkshopApp() }
        }
    }
}

@Composable
private fun WorkshopApp() {
    var page by remember { mutableStateOf("Overview") }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WorkRecord?>(null) }
    var editing by remember { mutableStateOf<WorkRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WorkRecord?>(null) }
    var nextId by remember { mutableIntStateOf(100) }
    val records = remember { mutableStateListOf(
        WorkRecord(1,"Sharma Residence","Modular kitchen and wardrobes · Amit Sharma","Site visit",485000.0,"Enquiry",phone="9876543210"),
        WorkRecord(2,"Mehta Living Room","TV unit and wall panels · Neha Mehta","Quotation sent",185000.0,"Enquiry",phone="9876501234"),
        WorkRecord(3,"Kulkarni Villa","Full home interiors · Rohan Kulkarni","In production",1250000.0,"Project"),
        WorkRecord(4,"Patil Bedroom Set","Bed, side tables and wardrobe · Sonal Patil","Design approved",275000.0,"Project"),
        WorkRecord(5,"Kitchen carcass · Cutting","Kulkarni Villa · Assigned to Ramesh","In progress",0.0,"Production",role="Carpentry"),
        WorkRecord(6,"Wardrobe panels · Polishing","Patil Bedroom Set · Assigned to Suresh","Pending",0.0,"Production",role="Finishing"),
        WorkRecord(7,"BWP Plywood 18mm","Greenply · Godown A · 12 sheets","In stock",0.0,"Inventory",role="Sheets"),
        WorkRecord(8,"Hettich soft-close hinges","Hardware · Godown A · 8 units · reorder at 20","Low stock",0.0,"Inventory",role="Hardware"),
        WorkRecord(9,"Ramesh Pawar","Carpenter · 9876543211","Present",0.0,"Workforce",phone="9876543211",role="Carpenter",attendance="Present",task="Kitchen carcass cutting"),
        WorkRecord(10,"Suresh Jadhav","Polisher · 9876543212","Present",0.0,"Workforce",phone="9876543212",role="Finisher",attendance="Present",task="Wardrobe panel polishing"),
        WorkRecord(11,"Imran Shaikh","Installer · 9876543213","Absent",0.0,"Workforce",phone="9876543213",role="Installer",attendance="Absent",task="Site measurement"),
        WorkRecord(12,"Purchase · Edge band rolls","City Hardware · 18,500","Awaiting approval",18500.0,"Approval"),
        WorkRecord(13,"Quotation · Kulkarni Villa","Final estimate","Awaiting approval",1250000.0,"Approval")
    ) }

    val currentRecords = records.filter { it.kind == pageKind(page) &&
        (query.isBlank() || listOf(it.title,it.detail,it.status,it.role,it.phone,it.task).any { s -> s.contains(query,true) }) }

    Scaffold(
        containerColor = Ivory,
        topBar = {
            Column(Modifier.background(Color.White)) {
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
                        onClick = { page = name; query = ""; searchOpen = false },
                        icon = { Icon(icon, null) }, label = { Text(name, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "Overview" -> Overview(records, onOpen = { page = it }, onAdd = { page = "Enquiries"; adding = true }, onSelect = { selected = it })
                "More" -> MoreModules { page = it }
                else -> ModulePage(
                    page = page, data = currentRecords, allData = records,
                    onOpen = { selected = it },
                    onStatus = { record, status -> replaceRecord(records, record.copy(status = status, attendance = if (record.kind == "Workforce") status else record.attendance)) },
                    onAttendance = { record -> val next = if (record.attendance == "Present") "Absent" else "Present"; replaceRecord(records, record.copy(attendance = next, status = next)) }
                )
            }
        }
    }

    if (adding || editing != null) RecordEditor(
        initial = editing, defaultKind = pageKind(page),
        onDismiss = { adding = false; editing = null },
        onSave = { value ->
            if (editing == null) records.add(0, value.copy(id = nextId++))
            else replaceRecord(records, value)
            adding = false; editing = null
        }
    )
    selected?.let { record ->
        RecordDetails(record, onDismiss = { selected = null },
            onEdit = { editing = record; selected = null },
            onDelete = { deleting = record; selected = null },
            onStatus = { status -> replaceRecord(records, record.copy(status = status, attendance = if (record.kind == "Workforce") status else record.attendance)); selected = record.copy(status = status) },
            onAttendance = { val next = if (record.attendance == "Present") "Absent" else "Present"; replaceRecord(records, record.copy(attendance = next, status = next)); selected = record.copy(attendance = next, status = next) }
        )
    }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete record?") },
            text = { Text("“${record.title}” will be removed from this demo workspace.") },
            confirmButton = { TextButton(onClick = { records.removeAll { it.id == record.id }; deleting = null }) { Text("Delete", color = Coral) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

private fun pageKind(page: String) = when(page) {
    "Enquiries" -> "Enquiry"; "Projects" -> "Project"; "Production" -> "Production"
    "Inventory" -> "Inventory"; "Workforce" -> "Workforce"; "Approvals" -> "Approval"
    else -> ""
}
private fun replaceRecord(records: MutableList<WorkRecord>, record: WorkRecord) {
    val index = records.indexOfFirst { it.id == record.id }
    if (index >= 0) records[index] = record
}

@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    val logo = remember { drawable("logo_horizontal") }
    val darkLogo = remember { drawable("logo_horizontal_dark") }
    val chosen = if (darkLogo != 0) darkLogo else logo
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
                }
                if (kind in listOf("Project","Enquiry","Approval")) OutlinedTextField(amount, { amount = it }, label = { Text("Amount ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val attendance = if (kind == "Workforce") (initial?.attendance ?: "Absent") else ""
                onSave(WorkRecord(initial?.id ?: 0,title.trim(),detail.trim(),status.trim().ifBlank{"New"},amount.toDoubleOrNull() ?: 0.0,kind,phone.trim(),role.trim(),attendance,task.trim()))
            }) { Text("Save", color = Coral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Slate) } }
    )
}
