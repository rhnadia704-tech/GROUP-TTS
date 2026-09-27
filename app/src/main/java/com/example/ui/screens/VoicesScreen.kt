package com.example.ui.screens

import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VoiceSlot
import com.example.ui.MainViewModel

@Composable
fun VoicesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val voiceSlots by viewModel.voiceSlots.collectAsState()
    val availableVoices by viewModel.availableSystemVoices.collectAsState()

    var editingVoice by remember { mutableStateOf<VoiceSlot?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Configuration des 5 Voix",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personnalisez jusqu'à 5 voix différentes avec leurs propres tonalités, vitesses et voix système Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(voiceSlots, key = { it.id }) { voice ->
            VoiceCard(
                voice = voice,
                availableVoices = availableVoices,
                onEditName = { editingVoice = voice },
                onPitchChange = { viewModel.updateVoicePitch(voice.id, it) },
                onSpeedChange = { viewModel.updateVoiceSpeed(voice.id, it) },
                onSelectSystemVoice = { systemName, localeTag ->
                    viewModel.setSystemVoice(voice.id, systemName, localeTag)
                },
                onTestVoice = { viewModel.testVoice(voice) }
            )
        }

        item {
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_reset_voices")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Réinitialiser les 5 voix par défaut")
            }
        }
    }

    // Rename Dialog
    editingVoice?.let { voice ->
        var tempName by remember { mutableStateOf(voice.name) }
        AlertDialog(
            onDismissRequest = { editingVoice = null },
            title = { Text("Renommer la voix ${voice.id}") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Nom du personnage / locuteur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            viewModel.renameVoice(voice.id, tempName)
                        }
                        editingVoice = null
                    }
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingVoice = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Reset Confirmation
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Réinitialisation") },
            text = { Text("Voulez-vous réinitialiser les 5 voix avec leurs valeurs d'origine ?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetVoicesToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceCard(
    voice: VoiceSlot,
    availableVoices: List<Voice>,
    onEditName: () -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSelectSystemVoice: (String, String) -> Unit,
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceColor = Color(voice.colorHex)
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_card_${voice.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Avatar, Name, Edit, Test button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(voiceColor.copy(alpha = 0.2f))
                        .border(1.5.dp, voiceColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = voice.avatarEmoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = voice.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onEditName,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("btn_rename_voice_${voice.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Renommer",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Slot ${voice.id} • ${voice.languageTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = voiceColor
                    )
                }

                FilledTonalButton(
                    onClick = onTestVoice,
                    modifier = Modifier.testTag("btn_test_voice_${voice.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tester")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // System Voice Dropdown
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (voice.systemVoiceName.isNotBlank()) voice.systemVoiceName else "Voix par défaut du système (${voice.languageTag})",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Moteur vocal TTS Android") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true)
                        .testTag("dropdown_voice_${voice.id}"),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Par défaut du système") },
                        onClick = {
                            onSelectSystemVoice("", voice.languageTag)
                            isDropdownExpanded = false
                        }
                    )
                    availableVoices.take(30).forEach { sysVoice ->
                        val isSelected = sysVoice.name == voice.systemVoiceName
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${sysVoice.locale.toLanguageTag()} - ${sysVoice.name}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectSystemVoice(sysVoice.name, sysVoice.locale.toLanguageTag())
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pitch Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tonalité (Pitch)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${String.format("%.2f", voice.pitch)}x",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = voiceColor
                )
            }
            Slider(
                value = voice.pitch,
                onValueChange = onPitchChange,
                valueRange = 0.5f..2.0f,
                steps = 15,
                modifier = Modifier.testTag("slider_pitch_${voice.id}")
            )

            // Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vitesse d'élocution",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${String.format("%.2f", voice.speechRate)}x",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = voiceColor
                )
            }
            Slider(
                value = voice.speechRate,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..2.0f,
                steps = 15,
                modifier = Modifier.testTag("slider_speed_${voice.id}")
            )
        }
    }
}
