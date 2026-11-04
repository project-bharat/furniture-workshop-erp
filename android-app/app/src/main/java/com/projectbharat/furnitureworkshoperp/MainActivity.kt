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
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val Coral = Color(0xFFFF6666)
private val Slate = Color(0xFF525266)
private val Ivory = Color(0xFFF7F4F1)
private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

private data class WorkRecord(val id: Int, val title: String, val detail: String, val status: String, val amount: Double, val kind: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247,244,241)
        window.navigationBarColor = android.graphics.Color.rgb(247,244,241)
        window.decorView.systemUiVisibility = 8192 or 16
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary=Coral, onPrimary=Color.White, secondary=Slate, background=Ivory, surface=Color.White, onSurface=Slate)) {
                WorkshopApp()
            }
        }
    }
}

@Composable
private fun WorkshopApp() {
    var splash by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf("Overview") }
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var nextId by remember { mutableIntStateOf(30) }
    val records = remember { mutableStateListOf(
        WorkRecord(1,"Sharma Residence","Amit Sharma · Modular kitchen + wardrobes","Site visit",485000.0,"Enquiry"),
        WorkRecord(2,"Mehta Living Room","Neha Mehta · TV unit and wall panels","Quotation sent",185000.0,"Enquiry"),
        WorkRecord(3,"Kulkarni Villa","Rohan Kulkarni · Full home interiors","In production",1250000.0,"Project"),
        WorkRecord(4,"Patil Bedroom Set","Sonal Patil · Bed, side tables, wardrobe","Design approved",275000.0,"Project"),
        WorkRecord(5,"Cutting · Kitchen carcass","Kulkarni Villa · Ramesh","In progress",0.0,"Production"),
        WorkRecord(6,"Polishing · Wardrobe panels","Patil Bedroom Set · Suresh","Pending",0.0,"Production"),
        WorkRecord(7,"BWP Plywood 18mm","Greenply · 12 sheets in stock","In stock",0.0,"Inventory"),
        WorkRecord(8,"Hettich soft-close hinges","Hardware · 8 units · reorder at 20","Low stock",0.0,"Inventory"),
        WorkRecord(9,"Ramesh Pawar","Carpenter · Present today · 8h 15m","Present",0.0,"Workforce"),
        WorkRecord(10,"Purchase: Edge band rolls","City Hardware · ₹18,500","Awaiting approval",18500.0,"Approval"),
        WorkRecord(11,"Quotation: Kulkarni Villa","Final estimate · ₹12,50,000","Awaiting approval",1250000.0,"Approval")
    ) }
    LaunchedEffect(Unit) { delay(1100); splash = false }
    if (splash) {
        Box(Modifier.fillMaxSize().background(Ivory), contentAlignment=Alignment.Center) {
            val id = drawable("splash_screen")
            if (id != 0) Image(painterResource(id), null, Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
            else Column(horizontalAlignment=Alignment.CenterHorizontally) { BrandLogo(); Text("FURNITURE WORKSHOP ERP", color=Slate, fontSize=10.sp) }
        }
        return
    }
    Scaffold(
        containerColor=Ivory,
        topBar={ Column(Modifier.background(Color.White)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
                BrandLogo(Modifier.weight(1f))
                IconButton(onClick={ search = if(search.isEmpty()) " " else "" }) { Icon(Icons.Default.Search,"Search") }
                IconButton(onClick={ page="Approvals" }) { BadgedBox(badge={ Badge { Text("2") } }) { Icon(Icons.Default.NotificationsNone,"Approvals") } }
            }
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,bottom=10.dp), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Aakruti Enterprises",fontSize=12.sp,fontWeight=FontWeight.SemiBold); Text("Owner workspace · Sample data",fontSize=9.sp,color=Slate.copy(.55f)) }
                Text("● LOCAL",color=Color(0xFF397A50),fontSize=9.sp,fontWeight=FontWeight.Bold)
            }
            HorizontalDivider(color=Ivory)
        } },
        floatingActionButton={ if(page!="Overview" && page!="More") FloatingActionButton(onClick={showAdd=true},containerColor=Coral,contentColor=Color.White){Icon(Icons.Default.Add,"Add")} },
        bottomBar={ NavigationBar(containerColor=Color.White) {
            listOf("Overview","Projects","Production","Inventory","More").forEach { name ->
                val icon = when(name) { "Overview"->Icons.Default.Dashboard; "Projects"->Icons.Default.Folder; "Production"->Icons.Default.Handyman; "Inventory"->Icons.Default.Warehouse; else->Icons.Default.Menu }
                NavigationBarItem(selected=page==name || (name=="More" && page in listOf("Enquiries","Workforce","Approvals")),onClick={page=name},icon={Icon(icon,null)},label={Text(name,fontSize=9.sp)})
            }
        } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if(search.isNotEmpty()) OutlinedTextField(search.trimStart(),{search=it},Modifier.fillMaxWidth().padding(12.dp),placeholder={Text("Search records")},singleLine=true)
            when(page) {
                "Overview" -> Overview(records,{page=it},{page="Enquiries";showAdd=true})
                "More" -> MoreModules { page=it }
                else -> ModulePage(page,records,search.trim()) { id,status ->
                    val i=records.indexOfFirst{it.id==id}; if(i>=0) records[i]=records[i].copy(status=status)
                }
            }
        }
    }
    if(showAdd) AddDialog(page,{showAdd=false}) { title,detail,amount,kind ->
        records.add(0,WorkRecord(nextId++,title,detail,"New",amount,kind)); showAdd=false
    }
}

private fun drawable(name:String):Int = try { Class.forName("com.projectbharat.furnitureworkshoperp.R\$drawable").getField(name).getInt(null) } catch(_:Exception){0}

@Composable
private fun BrandLogo(modifier:Modifier=Modifier) {
    val id=remember{drawable("logo_horizontal")}
    if(id!=0) Image(painterResource(id),"Aakruti",modifier.height(38.dp).widthIn(max=190.dp),contentScale=ContentScale.Fit)
    else Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        Surface(color=Slate,shape=RoundedCornerShape(8.dp)){Text("A",Modifier.padding(9.dp),color=Color.White,fontWeight=FontWeight.Bold,fontSize=19.sp)}
        Spacer(Modifier.width(8.dp)); Column { Text("AAKRUTI",fontWeight=FontWeight.ExtraBold,color=Slate,fontSize=13.sp,letterSpacing=1.sp);Text("WORKSHOP ERP",color=Slate.copy(.55f),fontSize=8.sp) }
    }
}

@Composable
private fun Overview(data:List<WorkRecord>,open:(String)->Unit,add:()->Unit) {
    val projectValue=data.filter{it.kind=="Project"}.sumOf{it.amount}
    val counts=listOf("Enquiries" to data.count{it.kind=="Enquiry"},"Projects" to data.count{it.kind=="Project"},"Jobs" to data.count{it.kind=="Production" && it.status!="Completed"},"Approvals" to data.count{it.kind=="Approval" && it.status.contains("Awaiting")})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        item { Text("Good afternoon",fontSize=11.sp,color=Slate.copy(.6f));Text("Workshop overview",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Slate);Text("4 November · Demo workspace",fontSize=10.sp,color=Slate.copy(.55f)) }
        item { Card(colors=CardDefaults.cardColors(containerColor=Slate),shape=RoundedCornerShape(16.dp)){Column(Modifier.fillMaxWidth().padding(18.dp)){Text("ACTIVE PROJECT VALUE",fontSize=9.sp,color=Color.White.copy(.65f),letterSpacing=1.sp);Text(money.format(projectValue),fontSize=26.sp,color=Color.White,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(25.dp)){counts.take(3).forEach{Column{Text(it.second.toString(),color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(it.first,color=Color.White.copy(.65f),fontSize=9.sp)}}}}} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){counts.forEach{Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(12.dp)){Text(it.second.toString(),fontSize=22.sp,fontWeight=FontWeight.Bold,color=Slate);Text(it.first,fontSize=9.sp,color=Slate.copy(.6f))}}} } }
        item { Row(verticalAlignment=Alignment.CenterVertically){Text("Quick actions",Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Slate);Text("All modules",Modifier.clickable{open("More")},color=Coral,fontSize=10.sp)} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Quick("New enquiry",Icons.Default.PersonAdd,add,Modifier.weight(1f));Quick("Projects",Icons.Default.Folder,{open("Projects")},Modifier.weight(1f));Quick("Inventory",Icons.Default.Warehouse,{open("Inventory")},Modifier.weight(1f))} }
        item { Text("Recent activity",fontSize=16.sp,fontWeight=FontWeight.Bold,color=Slate) }
        items(data.take(5)){RecordTile(it)}
        item { Text("Demo records only. Changes are held in app memory for this test session.",fontSize=9.sp,color=Slate.copy(.5f)) }
    }
}

@Composable
private fun Quick(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit,mod:Modifier) {
    OutlinedButton(onClick=click,modifier=mod.height(64.dp),contentPadding=PaddingValues(4.dp),shape=RoundedCornerShape(10.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=Coral);Text(label,fontSize=9.sp,color=Slate)}}
}

@Composable
private fun MoreModules(open:(String)->Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("All modules",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Slate);Text("Choose an area to manage",fontSize=11.sp,color=Slate.copy(.6f));Spacer(Modifier.height(14.dp))
        listOf("Enquiries","Projects","Production","Inventory","Workforce","Approvals").forEach { name ->
            Card(Modifier.fillMaxWidth().padding(bottom=8.dp).clickable{open(name)},colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.SemiBold,color=Slate);Text(moduleDescription(name),fontSize=9.sp,color=Slate.copy(.55f))};Icon(Icons.Default.ChevronRight,null,tint=Coral)}}
        }
    }
}

private fun moduleDescription(n:String)=when(n){"Enquiries"->"Customers, requirements and follow-ups";"Projects"->"Orders, measurements and milestones";"Production"->"Workshop stages and assigned jobs";"Inventory"->"Materials, stock and purchase requests";"Workforce"->"Attendance, tasks and work logs";else->"Quotation, purchase and expense approvals"}

@Composable
private fun ModulePage(page:String,data:List<WorkRecord>,query:String,update:(Int,String)->Unit) {
    val kind=when(page){"Enquiries"->"Enquiry";"Projects"->"Project";"Production"->"Production";"Inventory"->"Inventory";"Workforce"->"Workforce";else->"Approval"}
    val list=data.filter{it.kind==kind && (query.isBlank() || (it.title+it.detail).contains(query,true))}
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=18.dp,vertical=13.dp)){Text("WORKSHOP / "+page.uppercase(),fontSize=9.sp,color=Coral,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text(page,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Slate);Text(moduleDescription(page),fontSize=10.sp,color=Slate.copy(.55f))}
        Text(list.size.toString()+" records · sample data",Modifier.padding(horizontal=18.dp,vertical=5.dp),fontSize=10.sp,color=Slate.copy(.6f))
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(list.isEmpty()) item{Text("No matching records. Tap + to add a record.",color=Slate.copy(.6f))}
            items(list,key={it.id}) { record ->
                Column {
                    RecordTile(record)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick={update(record.id,nextStatus(page,record.status))}){Text(if(page=="Approvals")"Approve / review" else "Update status",color=Coral,fontSize=10.sp)}
                    }
                }
            }
        }
    }
}

private fun nextStatus(page:String,current:String)=when(page) {
    "Approvals" -> if(current=="Approved")"Awaiting approval" else "Approved"
    "Production" -> when(current){"Pending"->"In progress";"In progress"->"Completed";else->"In progress"}
    "Inventory" -> if(current=="Low stock")"Reorder requested" else "In stock"
    "Workforce" -> if(current=="Present")"Absent" else "Present"
    else -> when(current){"New"->"Contacted";"Contacted"->"Site visit";"Site visit"->"Quotation sent";"Quotation sent"->"Converted";else->"In progress"}
}

@Composable
private fun RecordTile(r:WorkRecord) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(color=Color(0xFFFFEFED),shape=RoundedCornerShape(9.dp)){Icon(when(r.kind){"Project"->Icons.Default.HomeWork;"Production"->Icons.Default.Handyman;"Inventory"->Icons.Default.Warehouse;"Workforce"->Icons.Default.Badge;"Approval"->Icons.Default.FactCheck;else->Icons.Default.Person},null,tint=Coral,modifier=Modifier.padding(9.dp).size(19.dp))}
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){Text(r.title,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Slate);Text(r.detail,fontSize=9.sp,color=Slate.copy(.55f));Text(r.status,fontSize=9.sp,fontWeight=FontWeight.Bold,color=Coral)}
            if(r.amount>0) Text(money.format(r.amount),fontSize=9.sp,fontWeight=FontWeight.Bold,color=Slate)
        }
    }
}

@Composable
private fun AddDialog(page:String,dismiss:()->Unit,save:(String,String,Double,String)->Unit) {
    var title by remember{mutableStateOf("")};var detail by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")}
    var kind by remember{mutableStateOf(when(page){"Enquiries"->"Enquiry";"Projects"->"Project";"Production"->"Production";"Inventory"->"Inventory";"Workforce"->"Workforce";"Approvals"->"Approval";else->"Enquiry"})}
    var expanded by remember{mutableStateOf(false)}
    val options=listOf("Enquiry","Project","Production","Inventory","Workforce","Approval")
    AlertDialog(onDismissRequest=dismiss,title={Text("Add record",fontWeight=FontWeight.Bold,color=Slate)},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box { OutlinedButton(onClick={expanded=true}){Text(kind);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(expanded,{expanded=false}){options.forEach{DropdownMenuItem(text={Text(it)},onClick={kind=it;expanded=false})}} }
            OutlinedTextField(title,{title=it},label={Text("Name / title")},singleLine=true)
            OutlinedTextField(detail,{detail=it},label={Text("Customer / details")},minLines=2)
            OutlinedTextField(amount,{amount=it},label={Text("Amount in ₹ (optional)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        }
    },confirmButton={TextButton(enabled=title.isNotBlank(),onClick={save(title.trim(),detail.trim(),amount.toDoubleOrNull()?:0.0,kind)}){Text("Save",color=Coral)}},dismissButton={TextButton(onClick=dismiss){Text("Cancel",color=Slate)}})
}
