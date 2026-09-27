package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.ExportedAudio
import com.example.data.repository.VoiceRepository
import com.example.tts.PlaybackState
import com.example.ui.MainViewModel
import com.example.ui.components.AudioPlayerBar
import com.example.ui.components.DialogueBubbleItem
import com.example.ui.components.VoiceChip
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scriptText by viewModel.scriptText.collectAsState()
    val voiceSlots by viewModel.voiceSlots.collectAsState()
    val dialogueLines by viewModel.dialogueLines.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isReadingPdf by viewModel.isReadingPdf.collectAsState()
    val isAiOptimizing by viewModel.isAiOptimizing.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    var selectedViewMode by remember { mutableIntStateOf(0) } // 0 = Script, 1 = Dialogue Visualizer
    var showSampleMenu by remember { mutableStateOf(false) }
    var lastExportedAudio by remember { mutableStateOf<ExportedAudio?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importPdf(uri)
        }
    }

    val listState = rememberLazyListState()
    val state = playbackState
    val currentLineIndex = when (state) {
        is PlaybackState.Playing -> state.currentLineIndex
        is PlaybackState.Paused -> state.currentLineIndex
        else -> -1
    }

    // Auto-scroll to active dialogue line
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex in dialogueLines.indices) {
            listState.animateScrollToItem(currentLineIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // space for player bar
        ) {
            // Quick Voice insert chips
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Insérer un locuteur :",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${voiceSlots.size} voix actives",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voiceSlots.forEach { voice ->
                            VoiceChip(
                                voice = voice,
                                onClick = { viewModel.insertSpeakerTag(voice) }
                            )
                        }
                    }
                }
            }

            // Toolbar with actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Import PDF / Text
                OutlinedButton(
                    onClick = {
                        pdfPickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                    },
                    modifier = Modifier.testTag("btn_import_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF / Texte")
                }

                // Sample script dropdown
                Box {
                    FilledTonalButton(
                        onClick = { showSampleMenu = true },
                        modifier = Modifier.testTag("btn_samples")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exemples")
                    }
                    DropdownMenu(
                        expanded = showSampleMenu,
                        onDismissRequest = { showSampleMenu = false }
                    ) {
                        VoiceRepository.SAMPLE_SCRIPTS.forEachIndexed { idx, sample ->
                            DropdownMenuItem(
                                text = { Text(sample.title) },
                                onClick = {
                                    viewModel.loadSample(idx)
                                    showSampleMenu = false
                                }
                            )
                        }
                    }
                }

                // AI Dialogue Optimizer
                Button(
                    onClick = { viewModel.optimizeScriptWithAi() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier.testTag("btn_ai_optimize")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Optimiser IA")
                }

                // Export to WAV Audio
                FilledTonalButton(
                    onClick = {
                        viewModel.exportToAudio { exported ->
                            lastExportedAudio = exported
                        }
                    },
                    modifier = Modifier.testTag("btn_export_audio")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exporter Audio")
                }
            }

            // Mode Toggle: Script Editor vs Interactive Dialogue
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SegmentedButton(
                    selected = selectedViewMode == 0,
                    onClick = { selectedViewMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Éditeur Script")
                }
                SegmentedButton(
                    selected = selectedViewMode == 1,
                    onClick = { selectedViewMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dialogue (${dialogueLines.size})")
                }
            }

            // Content Body
            if (selectedViewMode == 0) {
                // Script Text Field
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { viewModel.updateScript(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("script_text_field"),
                    placeholder = {
                        Text(
                            "Tapez votre texte ou dialogue multi-voix ici…\n\nExemple :\nAlice: Bonjour Bob !\nBob: Salut Alice, comment vas-tu ?\nNarrateur: Et la journée commença bien."
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            } else {
                // Interactive Dialogue Timeline
                if (dialogueLines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune réplique détectée.\nTapez un script ou importez un PDF pour commencer.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val voiceMap = voiceSlots.associateBy { it.id }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(dialogueLines, key = { it.index }) { line ->
                            DialogueBubbleItem(
                                line = line,
                                voice = voiceMap[line.voiceSlotId],
                                isPlaying = currentLineIndex == line.index,
                                onPlaySingle = {
                                    viewModel.playSingleLine(line.index)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Docked Audio Player at Bottom
        AudioPlayerBar(
            playbackState = playbackState,
            totalLines = dialogueLines.size,
            onPlay = { viewModel.playDialogue() },
            onPause = { viewModel.pausePlayback() },
            onResume = { viewModel.resumePlayback() },
            onStop = { viewModel.stopPlayback() },
            onNext = { viewModel.nextLine() },
            onPrevious = { viewModel.previousLine() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Loading Dialogs
        if (isReadingPdf) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = { CircularProgressIndicator(modifier = Modifier.size(32.dp)) },
                title = { Text("Importation en cours") },
                text = { Text("Extraction du contenu textuel du document PDF…") }
            )
        }

        if (isAiOptimizing) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = { Text("Optimisation par l'IA") },
                text = {
                    Column {
                        Text("Gemini structure et optimise votre dialogue pour une synthèse vocale multi-voix naturelle…")
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            )
        }

        exportProgress?.let { (current, total) ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = { CircularProgressIndicator(modifier = Modifier.size(32.dp)) },
                title = { Text("Génération Audio WAV") },
                text = {
                    Column {
                        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        Text("Synthèse de la réplique $current sur $total…")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }

        // Export Success Dialog
        lastExportedAudio?.let { audio ->
            AlertDialog(
                onDismissRequest = { lastExportedAudio = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Audio Exporté avec Succès !") },
                text = {
                    Column {
                        Text(
                            text = "Fichier : ${File(audio.filePath).name}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Taille : ${audio.fileSizeBytes / 1024} Ko • ${audio.lineCount} répliques",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Format : WAV 16-bit sans compression haute qualité",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val file = File(audio.filePath)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Partager l'audio"))
                            lastExportedAudio = null
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partager")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { lastExportedAudio = null }) {
                        Text("Fermer")
                    }
                }
            )
        }
    }
}
