package com.example.recoverx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recoverx.model.AppSettings
import com.example.recoverx.model.ThemeMode

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Appearance")
        SettingsCard {
            var selected by AppSettings.themeMode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { AppSettings.themeMode.value = mode },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.SYSTEM -> "System"
                                }
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Scan Settings")
        SettingsCard {
            SettingsSwitchRow("Images", AppSettings.scanImages)
            SettingsSwitchRow("Videos", AppSettings.scanVideos)
            SettingsSwitchRow("Documents", AppSettings.scanDocuments)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Recovery")
        SettingsCard {
            SettingsSwitchRow("Confirm before recovery", AppSettings.confirmBeforeRecovery)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Recovered files are saved to: /RecoverX/Recovered",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("About")
        SettingsCard {
            Text(
                text = "RecoverX v${AppSettings.appVersion}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "RecoverX Android-এর normal app permission-এর মধ্যে থেকে recoverable ফাইল খোঁজে। " +
                        "এটা কখনো ১০০% deleted data recovery guarantee করে না — Android-এর scoped storage " +
                        "এবং filesystem limitation-এর কারণে root ছাড়া সত্যিকারের low-level file recovery সম্ভব না। " +
                        "এই app শুধু accessible storage এবং media records থেকে যতটুকু সম্ভব খুঁজে বের করে।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    state: androidx.compose.runtime.MutableState<Boolean>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(checked = state.value, onCheckedChange = { state.value = it })
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope