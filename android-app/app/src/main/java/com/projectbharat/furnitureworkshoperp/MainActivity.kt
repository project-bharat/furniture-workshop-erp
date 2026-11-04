package com.projectbharat.furnitureworkshoperp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Coral = Color(0xFFFF6666)
private val Slate = Color(0xFF525266)
private val Ivory = Color(0xFFF7F4F1)

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

private data class ModuleItem(val title: String, val description: String)

@Composable
private fun WorkshopHome() {
    val modules = listOf(
        ModuleItem("Enquiries", "Customer requirements and follow-ups"),
        ModuleItem("Projects", "Rooms, furniture and milestones"),
        ModuleItem("Production", "Workshop stages and assigned jobs"),
        ModuleItem("Inventory", "Materials, stock and purchasing"),
        ModuleItem("Workforce", "Attendance, tasks and work logs"),
        ModuleItem("Approvals", "Quotations, purchases and expenses")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(horizontal = 20.dp, vertical = 26.dp)
    ) {
        Text(
            text = "WORKSHOP ERP",
            color = Coral,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Overview",
            color = Slate,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your furniture workshop control centre",
            color = Slate.copy(alpha = 0.68f),
            fontSize = 13.sp
        )
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
                    "Connect your account to load live workshop information.",
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
                    modifier = Modifier.height(118.dp)
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
}
