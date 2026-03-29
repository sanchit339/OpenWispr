// ui/MainScreen.kt
package com.poc.voicepipeline.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.voicepipeline.ui.theme.AmberAccent
import com.poc.voicepipeline.ui.theme.GreenAccent
import com.poc.voicepipeline.ui.theme.RedAccent
import com.poc.voicepipeline.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: UiState,
    onToggleListening: () -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onDismissError: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Voice Pipeline POC")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.DeleteSweep, "Clear all")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissError) {
                                Icon(Icons.Default.Close, "Dismiss")
                            }
                        }
                    }
                }
            }

            // Status indicator
            StatusCard(uiState)

            // Mic button + Send button
            ControlsRow(
                isListening = uiState.isListening,
                isRefining = uiState.isRefining,
                hasText = uiState.rawPipelineText.isNotBlank(),
                onToggleListening = onToggleListening,
                onSend = onSend
            )

            // Partial / live text
            AnimatedVisibility(visible = uiState.partialText.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingDot()
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Listening...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            uiState.partialText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Raw pipeline text (with ambiguity markers)
            if (uiState.rawPipelineText.isNotBlank()) {
                TextCard(
                    title = "Raw Pipeline Text",
                    subtitle = "With ambiguity markers [alternatives]",
                    icon = Icons.Outlined.DataObject,
                    content = uiState.rawPipelineText,
                    highlightBrackets = true,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Refined text result
            AnimatedVisibility(
                visible = uiState.refinedText.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TextCard(
                    title = "✨ Refined Text",
                    subtitle = "LLM-corrected output",
                    icon = Icons.Outlined.AutoAwesome,
                    content = uiState.refinedText,
                    highlightBrackets = false,
                    containerColor = GreenAccent.copy(alpha = 0.1f)
                )
            }

            // Performance metrics
            uiState.refinementResult?.let { result ->
                if (result.success) {
                    MetricsCard(result)
                }
            }

            // Spacer at bottom
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatusCard(uiState: UiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                uiState.isRefining -> AmberAccent.copy(alpha = 0.15f)
                uiState.isListening -> GreenAccent.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when {
                    uiState.isRefining -> AmberAccent
                    uiState.isListening -> GreenAccent
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        uiState.isRefining -> "Refining with LLM..."
                        uiState.isListening -> "Listening"
                        uiState.refinedText.isNotBlank() -> "Complete"
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                "Segments: ${uiState.segmentCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ControlsRow(
    isListening: Boolean,
    isRefining: Boolean,
    hasText: Boolean,
    onToggleListening: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mic button
        val micScale by animateFloatAsState(
            targetValue = if (isListening) 1.1f else 1.0f,
            animationSpec = if (isListening) {
                infiniteRepeatable(
                    animation = tween(600, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                tween(300)
            },
            label = "micScale"
        )

        FilledTonalButton(
            onClick = onToggleListening,
            enabled = !isRefining,
            modifier = Modifier
                .size(72.dp)
                .scale(micScale),
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isListening)
                    RedAccent.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop" else "Start",
                modifier = Modifier.size(32.dp),
                tint = if (isListening) RedAccent else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(24.dp))

        // Send button
        Button(
            onClick = onSend,
            enabled = hasText && !isRefining,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isRefining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Refining...")
            } else {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refine", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TextCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: String,
    highlightBrackets: Boolean,
    containerColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            if (highlightBrackets) {
                HighlightedText(content)
            } else {
                Text(
                    content,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

/**
 * Renders text with [alternatives] highlighted in a different color
 */
@Composable
fun HighlightedText(text: String) {
    val annotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text[i] == '[') {
                val end = text.indexOf(']', i)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            color = AmberAccent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            background = AmberAccent.copy(alpha = 0.1f)
                        )
                    ) {
                        append(text.substring(i, end + 1))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            } else {
                append(text[i])
                i++
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 26.sp
    )
}

@Composable
fun MetricsCard(result: com.poc.voicepipeline.pipeline.RefinementResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Performance Metrics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("E2E Latency", "${result.latencyMs}ms")
                MetricItem("Groq Time", "${result.groqProcessingTimeMs}ms")
                MetricItem("Tokens", "${result.tokensUsed}")
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(RedAccent.copy(alpha = alpha))
    )
}

private val EaseInOutSine: Easing = CubicBezierEasing(0.37f, 0.0f, 0.63f, 1.0f)