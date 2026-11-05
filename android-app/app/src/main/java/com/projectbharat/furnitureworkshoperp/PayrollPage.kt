package com.projectbharat.furnitureworkshoperp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

private val payMoney = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val paySlate = Color(0xFF525266)
private val payCoral = Color(0xFFFF6666)

@Composable
fun PayrollPage(workers: List<WorkRecord>, attendance: List<AttendanceEntry>, payrollDao: PayrollDao) {
    val adjustments by payrollDao.observeAdjustments().collectAsState(initial = emptyList())
    val records by payrollDao.observePayroll().collectAsState(initial = emptyList())
    val payments by payrollDao.observePayments().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(SimpleDateFormat("yyyy-MM", Locale.US).format(Date())) }
    var adjustmentTarget by remember { mutableStateOf<WorkRecord?>(null) }
    var paymentTarget by remember { mutableStateOf<PayrollRecord?>(null) }
    var componentType by remember { mutableStateOf("ADVANCE") }
    var componentAmount by remember { mutableStateOf("") }
    var componentNote by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var paymentReference by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    val monthRuns = records.filter { it.payrollMonth == month }
    val totalNet = monthRuns.sumOf { it.netPay }
    val totalPaid = monthRuns.sumOf { r -> payments.filter { it.payrollRecordId == r.id }.sumOf { it.amount } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("PAYROLL", color = payCoral, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Payroll & payments", color = paySlate, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Monthly salary, attendance, advances and payment ledger", color = paySlate.copy(.6f), fontSize = 10.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = paySlate), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(month, { if (it.length <= 7) month = it }, Modifier.fillMaxWidth(), label = { Text("Payroll month · YYYY-MM") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(.4f), focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(.7f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PaySummary("NET PAYROLL", totalNet, Modifier.weight(1f))
                        PaySummary("PAID", totalPaid, Modifier.weight(1f))
                        PaySummary("BALANCE", (totalNet-totalPaid).coerceAtLeast(0.0), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Button(onClick = {
                scope.launch {
                    if (!month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) {
                        notice = "Enter a valid payroll month in YYYY-MM format."
                        return@launch
                    }
                    if (month > currentMonth()) {
                        notice = "Future payroll months cannot be calculated."
                        return@launch
                    }
                    val cutoff = payCutoffDate(month)
                    val eligible = workers.filter { worker ->
                        val old = payrollDao.findPayroll(worker.id, month)
                        old == null || old.status == "DRAFT"
                    }
                    val incomplete = eligible.mapNotNull { worker ->
                        val entries = attendance.filter { it.personId == worker.id && it.attendanceDate.startsWith(month + "-") && it.attendanceDate <= cutoff }
                        val missing = payMissingWorkingDays(month, entries)
                        if (missing > 0) worker.title + ": " + missing + " unmarked workday(s)" else null
                    }
                    if (incomplete.isNotEmpty()) {
                        notice = "Attendance incomplete. " + incomplete.take(3).joinToString("; ")
                        return@launch
                    }
                    eligible.forEach { worker ->
                        val old = payrollDao.findPayroll(worker.id, month)
                        val entries = attendance.filter { it.personId == worker.id && it.attendanceDate.startsWith(month + "-") && it.attendanceDate <= cutoff }
                        val days = payWorkingDays(month, entries).toDouble().coerceAtLeast(1.0)
                        val elapsedDays = payElapsedWorkingDays(month, entries).toDouble()
                        val present = entries.count { it.status == "Present" }.toDouble()
                        val half = entries.count { it.status == "Half day" }.toDouble()
                        val leave = entries.count { it.status == "Paid leave" || it.status == "Leave" }.toDouble()
                        val unpaid = (entries.count { it.status == "Absent" || it.status == "Unpaid leave" }).toDouble() + half * .5
                        val paidDays = (elapsedDays - unpaid).coerceAtLeast(0.0)
                        val base = (worker.monthlySalary / days * paidDays).coerceAtLeast(0.0)
                        val current = adjustments.filter { it.personId == worker.id && it.adjustmentDate.startsWith(month + "-") && it.adjustmentDate <= cutoff }
                        val incentive = current.filter { it.type == "INCENTIVE" }.sumOf { it.amount }
                        val bonus = current.filter { it.type == "BONUS" }.sumOf { it.amount }
                        val deduction = current.filter { it.type == "DEDUCTION" }.sumOf { it.amount }
                        val advances = adjustments.filter { it.personId == worker.id && it.type == "ADVANCE" && it.adjustmentDate <= cutoff && it.amount > it.recoveredAmount }.sortedBy { it.adjustmentDate }
                        val recovery = advances.sumOf { (it.amount-it.recoveredAmount).coerceAtLeast(0.0) }.coerceAtMost((base+incentive+bonus-deduction).coerceAtLeast(0.0))
                        val gross = base + incentive + bonus
                        payrollDao.savePayroll(PayrollRecord(id=old?.id ?: 0, personId=worker.id, payrollMonth=month, monthlySalary=worker.monthlySalary, workingDays=days, presentDays=present, halfDays=half, paidLeaveDays=leave, unpaidDays=unpaid, paidDays=paidDays, incentives=incentive, bonuses=bonus, otherDeductions=deduction, advanceRecovery=recovery, grossPay=gross, netPay=(gross-deduction-recovery).coerceAtLeast(0.0), status="DRAFT", createdAt=payToday()))
                    }
                    notice = "Draft payroll calculated for " + month
                }
            }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = payCoral)) {
                Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(6.dp)); Text("Calculate / refresh draft payroll")
            }
            Text("Approved payroll is locked. Only draft payroll can be recalculated.", color = paySlate.copy(.6f), fontSize = 9.sp)
            if (notice.isNotBlank()) Text(notice, color = Color(0xFF27835A), fontSize = 10.sp)
        }
        item { Text("Employee payroll", color = paySlate, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        items(workers, key = { it.id }) { worker ->
            val run = monthRuns.firstOrNull { it.personId == worker.id }
            val historyPayments = payments.filter { it.personId == worker.id }.sortedByDescending { it.paymentDate }
            val paid = run?.let { r -> payments.filter { it.payrollRecordId == r.id }.sumOf { it.amount } } ?: 0.0
            val due = ((run?.netPay ?: 0.0)-paid).coerceAtLeast(0.0)
            var expanded by remember(worker.id) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(worker.title, color = paySlate, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(worker.role + " · " + payMoney.format(worker.monthlySalary) + " / month", color = paySlate.copy(.6f), fontSize = 9.sp)
                        }
                        Text(run?.status ?: "NOT RUN", color = if (run?.status == "PAID") Color(0xFF27835A) else payCoral, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                    if (run == null) Text("No payroll sheet for " + month + ". Calculate payroll after attendance is updated.", color = paySlate.copy(.65f), fontSize = 10.sp)
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PayMetric("GROSS", run.grossPay, Modifier.weight(1f))
                            PayMetric("DEDUCTIONS", run.otherDeductions+run.advanceRecovery, Modifier.weight(1f))
                            PayMetric("NET PAY", run.netPay, Modifier.weight(1f))
                        }
                        Text(run.presentDays.toInt().toString() + " present · " + run.halfDays.toInt() + " half · " + run.unpaidDays + " unpaid · " + run.paidDays + " payable / " + run.workingDays.toInt() + " working days", color = paySlate.copy(.65f), fontSize = 9.sp)
                        Text("Paid " + payMoney.format(paid) + " · Balance " + payMoney.format(due), color = paySlate, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (run.status == "DRAFT") OutlinedButton(onClick = {
                                scope.launch {
                                    var left = run.advanceRecovery
                                    adjustments.filter { it.personId == worker.id && it.type == "ADVANCE" && it.adjustmentDate <= month + "-31" && it.amount > it.recoveredAmount }.sortedBy { it.adjustmentDate }.forEach { a ->
                                        if (left > 0) {
                                            val take = minOf((a.amount-a.recoveredAmount).coerceAtLeast(0.0), left)
                                            payrollDao.setRecoveredAmount(a.id, a.recoveredAmount+take)
                                            left -= take
                                        }
                                    }
                                    payrollDao.updatePayrollStatus(run.id, "APPROVED", payToday())
                                    notice = worker.title + " payroll approved"
                                }
                            }, Modifier.weight(1f)) { Text("Approve payroll", fontSize = 10.sp) }
                            if (run.status == "APPROVED" || run.status == "PARTIALLY_PAID") Button(onClick = {
                                paymentTarget = run; paymentAmount = due.toString(); paymentMethod = "UPI"; paymentReference = ""; paymentNote = ""
                            }, enabled = due > 0, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = payCoral)) { Text("Record payment", fontSize = 10.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { adjustmentTarget = worker; componentType = "ADVANCE"; componentAmount = ""; componentNote = "" }) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Text("Advance / adjustment", fontSize = 9.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Hide history" else "Employee history", fontSize = 9.sp)
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(15.dp))
                        }
                    }
                    if (expanded) {
                        HorizontalDivider(color = Color(0xFFEAE4E0))
                        Text("Adjustments", color = paySlate, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        val workerAdjustments = adjustments.filter { it.personId == worker.id }.sortedByDescending { it.adjustmentDate }
                        if (workerAdjustments.isEmpty()) Text("No adjustments recorded.", color = paySlate.copy(.6f), fontSize = 9.sp)
                        workerAdjustments.forEach { a ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.type + " · " + a.adjustmentDate, color = paySlate, fontSize = 9.sp)
                                    if (a.note.isNotBlank()) Text(a.note, color = paySlate.copy(.55f), fontSize = 8.sp)
                                    if (a.type == "ADVANCE") Text("Recovered " + payMoney.format(a.recoveredAmount) + " · Outstanding " + payMoney.format((a.amount-a.recoveredAmount).coerceAtLeast(0.0)), color = paySlate.copy(.55f), fontSize = 8.sp)
                                }
                                Text(payMoney.format(a.amount), color = paySlate, fontSize = 9.sp)
                            }
                        }
                        Text("Payment history", color = paySlate, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        if (historyPayments.isEmpty()) Text("No payments recorded.", color = paySlate.copy(.6f), fontSize = 9.sp)
                        historyPayments.forEach { p ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.paymentDate + " · " + p.method, color = paySlate, fontSize = 9.sp)
                                    if (p.reference.isNotBlank()) Text("Ref: " + p.reference, color = paySlate.copy(.55f), fontSize = 8.sp)
                                }
                                Text(payMoney.format(p.amount), color = Color(0xFF27835A), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                        Text("Previous payroll", color = paySlate, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        records.filter { it.personId == worker.id && it.payrollMonth != month }.sortedByDescending { it.payrollMonth }.forEach {
                            Text(it.payrollMonth + " · " + payMoney.format(it.netPay) + " · " + it.status, color = paySlate.copy(.7f), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }

    adjustmentTarget?.let { worker ->
        AlertDialog(onDismissRequest = { adjustmentTarget = null }, title = { Text("Payroll component · " + worker.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ADVANCE", "DEDUCTION", "INCENTIVE", "BONUS").forEach { type ->
                            FilterChip(componentType == type, { componentType = type }, label = { Text(type, fontSize = 8.sp) })
                        }
                    }
                    OutlinedTextField(componentAmount, { componentAmount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(componentNote, { componentNote = it }, label = { Text("Reason / note") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                val amount = componentAmount.toDoubleOrNull()
                if (amount != null && amount > 0) scope.launch {
                    payrollDao.saveAdjustment(PayrollAdjustment(personId=worker.id, adjustmentDate=payToday(), type=componentType, amount=amount, note=componentNote.trim()))
                    adjustmentTarget = null
                }
            }) { Text("Save", color = payCoral) } },
            dismissButton = { TextButton(onClick = { adjustmentTarget = null }) { Text("Cancel") } }
        )
    }

    paymentTarget?.let { run ->
        AlertDialog(onDismissRequest = { paymentTarget = null }, title = { Text("Record salary payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val due = (run.netPay-payments.filter { it.payrollRecordId == run.id }.sumOf { it.amount }).coerceAtLeast(0.0)
                    Text("Outstanding: " + payMoney.format(due), color = paySlate, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(paymentAmount, { paymentAmount = it }, label = { Text("Payment amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("UPI", "Bank", "Cash", "Cheque").forEach { method ->
                            FilterChip(paymentMethod == method, { paymentMethod = method }, label = { Text(method, fontSize = 9.sp) })
                        }
                    }
                    OutlinedTextField(paymentReference, { paymentReference = it }, label = { Text("UTR / transaction reference") }, singleLine = true)
                    OutlinedTextField(paymentNote, { paymentNote = it }, label = { Text("Note (optional)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                val amount = paymentAmount.toDoubleOrNull()
                val due = (run.netPay-payments.filter { it.payrollRecordId == run.id }.sumOf { it.amount }).coerceAtLeast(0.0)
                if (amount != null && amount > 0 && amount <= due) scope.launch {
                    payrollDao.savePayment(PayrollPayment(payrollRecordId=run.id, personId=run.personId, paymentDate=payToday(), amount=amount, method=paymentMethod, reference=paymentReference.trim(), note=paymentNote.trim()))
                    val newPaid = payments.filter { it.payrollRecordId == run.id }.sumOf { it.amount } + amount
                    payrollDao.updatePayrollStatus(run.id, if (newPaid >= run.netPay) "PAID" else "PARTIALLY_PAID", run.approvedAt)
                    paymentTarget = null
                }
            }) { Text("Save payment", color = payCoral) } },
            dismissButton = { TextButton(onClick = { paymentTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PaySummary(label: String, value: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(.65f), fontSize = 8.sp)
        Text(payMoney.format(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
@Composable
private fun PayMetric(label: String, value: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = paySlate.copy(.55f), fontSize = 8.sp)
        Text(payMoney.format(value), color = paySlate, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}
private fun payCutoffDate(month: String): String {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return payToday()
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: return payToday()
    val calendar = Calendar.getInstance()
    calendar.set(year, (monthNumber - 1).coerceIn(0, 11), 1)
    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
    val monthEnd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    return minOf(monthEnd, payToday())
}
private fun payWorkingDays(month: String, entries: List<AttendanceEntry>): Int {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return 26
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: return 26
    val calendar = Calendar.getInstance()
    calendar.set(year, (monthNumber - 1).coerceIn(0, 11), 1)
    val off = entries.filter { it.status == "Holiday" || it.status == "Weekly off" }.map { it.attendanceDate }.toSet()
    var count = 0
    for (day in 1..calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val date = String.format(Locale.US, "%04d-%02d-%02d", year, monthNumber, day)
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && date !in off) count++
    }
    return count.coerceAtLeast(1)
}
private fun payElapsedWorkingDays(month: String, entries: List<AttendanceEntry>): Int {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return 0
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: return 0
    val calendar = Calendar.getInstance()
    calendar.set(year, (monthNumber - 1).coerceIn(0, 11), 1)
    val cutoff = payCutoffDate(month)
    val off = entries.filter { it.status == "Holiday" || it.status == "Weekly off" }.map { it.attendanceDate }.toSet()
    var count = 0
    val last = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (day in 1..last) {
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val date = String.format(Locale.US, "%04d-%02d-%02d", year, monthNumber, day)
        if (date > cutoff) break
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && date !in off) count++
    }
    return count
}
private fun payMissingWorkingDays(month: String, entries: List<AttendanceEntry>): Int {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return 0
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: return 0
    val calendar = Calendar.getInstance()
    calendar.set(year, (monthNumber - 1).coerceIn(0, 11), 1)
    val cutoff = payCutoffDate(month)
    val marked = entries.map { it.attendanceDate }.toSet()
    val off = entries.filter { it.status == "Holiday" || it.status == "Weekly off" }.map { it.attendanceDate }.toSet()
    var missing = 0
    for (day in 1..calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val date = String.format(Locale.US, "%04d-%02d-%02d", year, monthNumber, day)
        if (date > cutoff) break
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && date !in off && date !in marked) missing++
    }
    return missing
}
private fun payToday() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
