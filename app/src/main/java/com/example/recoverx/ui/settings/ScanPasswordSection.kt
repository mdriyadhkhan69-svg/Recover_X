package com.example.recoverx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.recoverx.security.ScanPasswordManager
import com.example.recoverx.security.ScanPasswordManager.VerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScanPasswordSection() {
    val context = LocalContext.current
    val manager = remember { ScanPasswordManager.get(context) }
    var hasPassword by remember { mutableStateOf(manager.hasPassword()) }
    var showDialog by remember { mutableStateOf(false) }

    Text(
        text = if (hasPassword) "Scan Password is set" else "No Scan Password set",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "Required before Normal Scan or Deep Scan starts. There is no reset option: if you forget it, it cannot be recovered inside the app.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { showDialog = true }) {
        Text(if (hasPassword) "Change Password" else "Set Password")
    }

    if (showDialog) {
        PasswordDialog(
            hasPassword = hasPassword,
            manager = manager,
            onDone = { hasPassword = manager.hasPassword(); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun PasswordDialog(
    hasPassword: Boolean,
    manager: ScanPasswordManager,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (hasPassword) "Change Scan Password" else "Set Scan Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPassword) PwField("Current Password", current) { current = it; error = null }
                PwField("New Password", newPw) { newPw = it; error = null }
                PwField(if (hasPassword) "Confirm New Password" else "Confirm Password", confirm) { confirm = it; error = null }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                when {
                    hasPassword && current.isEmpty() -> error = "Enter your current password"
                    newPw.length < 4 -> error = "New password must be at least 4 characters"
                    newPw != confirm -> error = "Passwords do not match"
                    else -> {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.Default) {
                                if (hasPassword) manager.changePassword(current, newPw)
                                else { manager.setInitialPassword(newPw); VerifyResult.Ok }
                            }
                            busy = false
                            when (r) {
                                VerifyResult.Ok -> onDone()
                                VerifyResult.Wrong -> error = "Current password is incorrect"
                                is VerifyResult.LockedOut -> error = "Too many attempts. Try again in ${r.seconds}s"
                            }
                        }
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PwField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}