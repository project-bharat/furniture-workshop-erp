package com.projectbharat.furnitureworkshoperp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(context: android.content.Context, session: SecureSession, onSignedIn: (SignedInUser) -> Unit) {
    val prefs = remember { context.getSharedPreferences("workshop_connection", android.content.Context.MODE_PRIVATE) }
    var base by remember { mutableStateOf(prefs.getString("api_base_url", "") ?: "") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F4F1)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("AAKRUTI WORKSHOP", style = MaterialTheme.typography.labelLarge, color = Color(0xFF525266))
            Spacer(Modifier.height(8.dp))
            Text("Sign in", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF525266))
            Text("Connect to your workshop account", color = Color(0xFF777784))
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(base, { base = it.trim(); error = "" }, Modifier.fillMaxWidth(),
                label = { Text("API server URL") }, placeholder = { Text("https://your-workshop-api") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(email, { email = it; error = "" }, Modifier.fillMaxWidth(),
                label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it; error = "" }, Modifier.fillMaxWidth(),
                label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (base.isBlank() || email.isBlank() || password.isBlank()) {
                        error = "Enter the API URL, email and password."
                    } else if (!base.startsWith("https://") && !base.startsWith("http://10.0.2.2")) {
                        error = "Use HTTPS for the server URL."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                prefs.edit().putString("api_base_url", base.trimEnd('/')).apply()
                                val user = WorkshopApi(base.trimEnd('/'), session).login(base.trimEnd('/'), email, password,
                                    android.os.Build.MODEL ?: "Android", session)
                                password = ""
                                OutboxSyncWorker.enqueue(context)
                                onSignedIn(user)
                            } catch (e: Exception) {
                                error = e.message ?: "Unable to sign in. Check the server URL and credentials."
                            } finally { busy = false }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text(if (busy) "Signing in…" else "Sign in") }
            Spacer(Modifier.height(14.dp))
            Text("Your password is used only to sign in. Session tokens are stored encrypted on this device.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF777784))
        }
    }
}
