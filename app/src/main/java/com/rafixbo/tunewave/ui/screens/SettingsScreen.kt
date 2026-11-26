package com.rafixbo.tunewave.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rafixbo.tunewave.BuildConfig
import com.rafixbo.tunewave.TuneWaveState
import com.rafixbo.tunewave.model.AudioQuality
import com.rafixbo.tunewave.ui.components.MoodChip

@Composable
fun SettingsScreen(state: TuneWaveState, onOpenDeveloper: () -> Unit) {
    val streamQuality by state.streamQuality.collectAsState(initial = AudioQuality.HIGH)
    val downloadQuality by state.downloadQuality.collectAsState(initial = AudioQuality.HIGH)
    val themeMode by state.themeMode.collectAsState(initial = "DARK")
    var showClearCache by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionTitle("Audio quality (streaming)")
        QualityRow(
            selected = streamQuality,
            onSelect = { state.setStreamQuality(it) },
        )
        Text(
            "YouTube's best audio is Opus ~256 kbps. TuneWave never claims FLAC or lossless.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle("Download quality")
        QualityRow(
            selected = downloadQuality,
            onSelect = { state.setDownloadQuality(it) },
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle("Theme")
        Row {
            MoodChip("Dark", themeMode == "DARK") { state.setThemeMode("DARK") }
            Spacer(Modifier.width(8.dp))
            MoodChip("System", themeMode == "SYSTEM") { state.setThemeMode("SYSTEM") }
        }
        Spacer(Modifier.height(20.dp))

        SectionTitle("Sleep timer")
        Text(
            "Set a sleep timer from the player screen (10–60 min). Playback stops automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle("Storage")
        Row {
            Button(onClick = { state.clearHistory() }, shape = RoundedCornerShape(20.dp)) {
                Text("Clear history")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { showClearCache = true }, shape = RoundedCornerShape(20.dp)) {
                Text("Clear search history")
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionTitle("About")
        Text("TuneWave ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
        Text("No ads. No login. No tracking. Music from YouTube, played your way.")
        Text(
            "Downloads save the best available YouTube audio (Opus ~256 kbps) for offline listening.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // Developer row — opens the Developer Details screen.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenDeveloper).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Developer", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("Meet the person behind TuneWave", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text("Clear search history?") },
            text = { Text("Your recent searches will be removed from this device.") },
            confirmButton = {
                Button(onClick = { state.clearSearchHistory(); showClearCache = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun QualityRow(selected: AudioQuality, onSelect: (AudioQuality) -> Unit) {
    Row {
        MoodChip("High", selected == AudioQuality.HIGH) { onSelect(AudioQuality.HIGH) }
        Spacer(Modifier.width(8.dp))
        MoodChip("Medium", selected == AudioQuality.MEDIUM) { onSelect(AudioQuality.MEDIUM) }
        Spacer(Modifier.width(8.dp))
        MoodChip("Low", selected == AudioQuality.LOW) { onSelect(AudioQuality.LOW) }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        when (selected) {
            AudioQuality.HIGH -> "High · Opus ~256 kbps (itag 251)"
            AudioQuality.MEDIUM -> "Medium · AAC 128 kbps (itag 140)"
            AudioQuality.LOW -> "Low · Opus ~70 kbps (itag 249)"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
