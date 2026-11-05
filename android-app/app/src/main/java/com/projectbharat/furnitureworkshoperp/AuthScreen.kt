package com.projectbharat.furnitureworkshoperp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var setupMode by remember { mutableStateOf(false) }
    var bootstrapSecret by remember { mutableStateOf("") }
    var organizationName by remember { mutableStateOf("") }
    var legalName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("Main Workshop") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun validServer(): Boolean {
        val normalized = base.trim().trimEnd('/')
        if (normalized.isBlank()) { error = "Enter your API server URL."; return false }
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://10.0.2.2")) {
            error = "Use HTTPS for the server URL. HTTP is allowed only for the Android emulator."
            return false
        }
        return true
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F4F1)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("AAKRUTI WORKSHOP", style = MaterialTheme.typography.labelLarge, color = Color(0xFF525266))
            Spacer(Modifier.height(8.dp))
            Text(if (setupMode) "Set up your workshop" else "Sign in",
                style = MaterialTheme.typography.headlineMedium, color = Color(0xFF525266))
            Text(
                if (setupMode) "Create the first owner account and organization."
                else "Connect to your workshop account",
                color = Color(0xFF777784)
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(base, { base = it.trim(); error = "" }, Modifier.fillMaxWidth(),
                label = { Text("API server URL") }, placeholder = { Text("https://your-workshop-api") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            if (setupMode) {
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(bootstrapSecret, { bootstrapSecret = it; error = "" },
                    Modifier.fillMaxWidth(), label = { Text("One-time setup secret") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(organizationName, { organizationName = it; error = "" },
                    Modifier.fillMaxWidth(), label = { Text("Workshop display name") }, singleLine = true)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(legalName, { legalName = it; error = "" },
                    Modifier.fillMaxWidth(), label = { Text("Legal business name") }, singleLine = true)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(branchName, { branchName = it; error = "" },
                    Modifier.fillMaxWidth(), label = { Text("Main branch name") }, singleLine = true)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(ownerName, { ownerName = it; error = "" },
                    Modifier.fillMaxWidth(), label = { Text("Owner full name") }, singleLine = true)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(email, { email = it; error = "" }, Modifier.fillMaxWidth(),
                    label = { Text("Owner email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(phone, { phone = it; error = "" }, Modifier.fillMaxWidth(),
                    label = { Text("Phone (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(password, { password = it; error = "" }, Modifier.fillMaxWidth(),
                    label = { Text("Owner password (12–72 characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                Text("This is a one-time, server-authorized operation. The setup secret is not saved on this device. Only use this screen with a trusted HTTPS API.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF777784))
            } else {
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(email, { email = it; error = "" }, Modifier.fillMaxWidth(),
                    label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(password, { password = it; error = "" }, Modifier.fillMaxWidth(),
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!validServer()) return@Button
                    val normalizedBase = base.trim().trimEnd('/')
                    val valid = if (setupMode) {
                        when {
                            bootstrapSecret.isBlank() -> "Enter the one-time setup secret."
                            organizationName.isBlank() || legalName.isBlank() || ownerName.isBlank() -> "Complete the workshop, legal and owner names."
                            !email.contains("@") -> "Enter a valid owner email address."
                            password.length !in 12..72 -> "Owner password must contain 12 to 72 characters."
                            else -> ""
                        }
                    } else {
                        if (email.isBlank() || password.isBlank()) "Enter your email and password." else ""
                    }
                    if (valid.isNotBlank()) {
                        error = valid
                    } else {
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                prefs.edit().putString("api_base_url", normalizedBase).apply()
                                val api = WorkshopApi(normalizedBase, session)
                                val user = if (setupMode) {
                                    api.bootstrap(normalizedBase, bootstrapSecret, organizationName, legalName,
                                        branchName, ownerName, email, phone, password,
                                        android.os.Build.MODEL ?: "Android", session)
                                } else {
                                    api.login(normalizedBase, email, password,
                                        android.os.Build.MODEL ?: "Android", session)
                                }
                                password = ""
                                bootstrapSecret = ""
                                OutboxSyncWorker.enqueue(context)
                                onSignedIn(user)
                            } catch (e: Exception) {
                                error = e.message ?: if (setupMode) "Unable to create the first owner account." else "Unable to sign in. Check the server URL and credentials."
                            } finally { busy = false }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text(when { busy && setupMode -> "Creating owner account…"; busy -> "Signing in…"; setupMode -> "Create workshop & owner"; else -> "Sign in" }) }
            TextButton(
                enabled = !busy,
                onClick = {
                    setupMode = !setupMode
                    error = ""
                    password = ""
                    bootstrapSecret = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (setupMode) "Already have an account? Sign in" else "First time here? Set up a new workshop",
                    color = Color(0xFF525266))
            }
            Spacer(Modifier.height(4.dp))
            Text("Your password is used only for authentication. Session tokens are stored encrypted on this device.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF777784))
        }
    }
}
