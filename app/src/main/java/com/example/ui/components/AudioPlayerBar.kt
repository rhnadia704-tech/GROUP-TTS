package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.PlaybackState

@Composable
fun AudioPlayerBar(
    playbackState: PlaybackState,
    totalLines: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying = playbackState is PlaybackState.Playing
    val isPaused = playbackState is PlaybackState.Paused
    val currentLineIndex = when (playbackState) {
        is PlaybackState.Playing -> playbackState.currentLineIndex
        is PlaybackState.Paused -> playbackState.currentLineIndex
        PlaybackState.Idle -> 0
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_player_bar"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Progress line
            AnimatedVisibility(
                visible = isPlaying || isPaused,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    val progress = if (totalLines > 0) (currentLineIndex + 1).toFloat() / totalLines.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Réplique ${currentLineIndex + 1} sur $totalLines",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isPlaying) "Lecture en cours…" else "En pause",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Info label when idle
                if (!isPlaying && !isPaused) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lecteur Groupe TTS",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalLines répliques détectées",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Control buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Previous
                    IconButton(
                        onClick = onPrevious,
                        enabled = isPlaying || isPaused,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_player_previous")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Précédent"
                        )
                    }

                    // Play / Pause / Resume
                    FilledIconButton(
                        onClick = {
                            when {
                                isPlaying -> onPause()
                                isPaused -> onResume()
                                else -> onPlay()
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("btn_player_play_pause"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Stop
                    IconButton(
                        onClick = onStop,
                        enabled = isPlaying || isPaused,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_player_stop")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Arrêter"
                        )
                    }

                    // Next
                    IconButton(
                        onClick = onNext,
                        enabled = isPlaying || isPaused,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_player_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Suivant"
                        )
                    }
                }
            }
        }
    }
}
