package com.projectbharat.furnitureworkshoperp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Coral = Color(0xFFFF6666)
private val Slate = Color(0xFF525266)
private val Ivory = Color(0xFFF7F4F1)

private data class ModuleItem(val title: String, val description: String, val detail: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Coral,
                    onPrimary = Color.White,
                    secondary = Slate,
                    background = Ivory,
                    surface = Color.White,
                    onSurface = Slate
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Ivory) {
                    WorkshopHome()
                }
            }
        }
    }
}

@Composable
private fun WorkshopHome() {
    val modules = listOf(
        ModuleItem("Enquiries", "Customer requirements and follow-ups", "Capture customer details, requirements, site visits and follow-up dates."),
        ModuleItem("Projects", "Rooms, furniture and milestones", "Organise custom furniture and interior projects, project items, measurements and milestones."),
        ModuleItem("Production", "Workshop stages and assigned jobs", "Track design, cutting, carpentry, assembly, polishing, quality checks and installation."),
        ModuleItem("Inventory", "Materials, stock and purchasing", "Maintain material catalogues, stock movements, reorder levels and purchase requests."),
        ModuleItem("Workforce", "Attendance, tasks and work logs", "Assign work, record attendance and track task progress and labour time."),
        ModuleItem("Approvals", "Quotations, purchases and expenses", "Review quotations, purchases, discounts and major expenses before approval.")
    )
    var selectedModule by remember { mutableStateOf<ModuleItem?>(null) }

    if (selectedModule == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ivory)
                .padding(horizontal = 20.dp, vertical = 26.dp)
        ) {
            Text("WORKSHOP ERP", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Overview", color = Slate, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your furniture workshop control centre", color = Slate.copy(alpha = 0.68f), fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("FOUNDATION WORKSPACE", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Your operations start here.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "This is the first installable app preview. Module navigation is available; live data and account setup are the next integration steps.",
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("OPERATIONS", color = Slate, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(modules) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(118.dp)
                            .clickable { selectedModule = item }
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text(item.title, color = Slate, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(item.description, color = Slate.copy(alpha = 0.68f), fontSize = 10.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    } else {
        ModuleWorkspace(module = selectedModule!!, onBack = { selectedModule = null })
    }
}

@Composable
private fun ModuleWorkspace(module: ModuleItem, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(20.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Slate),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Back to overview")
        }
        Spacer(Modifier.height(28.dp))
        Text("WORKSHOP ERP", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(module.title, color = Slate, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("MODULE PREVIEW", color = Coral, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(10.dp))
                Text(module.detail, color = Slate, fontSize = 15.sp, lineHeight = 23.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    "This screen is a navigation preview. Records, forms, offline storage and synchronisation will be enabled as each module is implemented.",
                    color = Slate.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
