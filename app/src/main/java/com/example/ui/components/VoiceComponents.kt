package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DialogueLine
import com.example.data.model.VoiceSlot

@Composable
fun VoiceChip(
    voice: VoiceSlot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceColor = Color(voice.colorHex)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("voice_chip_${voice.id}"),
        color = voiceColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, voiceColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = voice.avatarEmoji,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = voice.name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(voiceColor)
            )
        }
    }
}

@Composable
fun DialogueBubbleItem(
    line: DialogueLine,
    voice: VoiceSlot?,
    isPlaying: Boolean,
    onPlaySingle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceColor = voice?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary
    val bubbleBgColor by animateColorAsState(
        targetValue = if (isPlaying) voiceColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 250),
        label = "bubbleBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("dialogue_line_${line.index}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleBgColor),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(2.dp, voiceColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(voiceColor.copy(alpha = 0.2f))
                    .border(1.5.dp, voiceColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = voice?.avatarEmoji ?: "🎙️",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text and Speaker
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = line.speakerName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = voiceColor
                        )
                    )
                    Text(
                        text = "#${line.index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play single line button
            IconButton(
                onClick = onPlaySingle,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("play_single_${line.index}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = "Écouter cette réplique",
                    tint = if (isPlaying) voiceColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
