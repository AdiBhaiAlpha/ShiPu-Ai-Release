package com.example.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shipu.ai.R
import com.example.data.model.Message
import com.example.ui.theme.DarkAssistantBubble
import com.example.ui.theme.DarkUserBubble
import com.example.ui.theme.LightAssistantBubble
import com.example.ui.theme.LightUserBubble

@Composable
fun ChatMessageItem(
    message: Message,
    isDarkTheme: Boolean,
    isHighlighted: Boolean = false,
    highlightKeyword: String? = null,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf<Boolean?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // ShiPu AI Assistant Avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "ShiPu AI Avatar",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isUser) 14.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 14.dp
                ),
                color = when {
                    isUser && isDarkTheme -> DarkUserBubble
                    isUser && !isDarkTheme -> LightUserBubble
                    !isUser && isDarkTheme -> DarkAssistantBubble
                    else -> LightAssistantBubble
                },
                border = if (isHighlighted) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(
                        1.dp,
                        if (isUser) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                },
                shadowElevation = if (isHighlighted) 4.dp else 0.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(
                        markdown = message.content,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // User Message Timestamp & Actions Bar
            if (isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ShiPu AI User Message", message.content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("copy_user_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Message Actions Bar (Copy, Regenerate, Feedback) for Assistant
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    // Copy Action
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ShiPu AI Response", message.content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("copy_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Regenerate Action
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("regenerate_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Regenerate response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Good Feedback (Thumbs Up)
                    IconButton(
                        onClick = {
                            isLiked = if (isLiked == true) null else true
                            Toast.makeText(context, if (isLiked == true) "Feedback: Helpful" else "Feedback reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("thumbs_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = "Helpful response",
                            tint = if (isLiked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Bad Feedback (Thumbs Down)
                    IconButton(
                        onClick = {
                            isLiked = if (isLiked == false) null else false
                            Toast.makeText(context, if (isLiked == false) "Feedback noted" else "Feedback reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("thumbs_down_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbDown,
                            contentDescription = "Unhelpful response",
                            tint = if (isLiked == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // More Options
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ShiPu AI Response", message.content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Full message copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("more_actions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "More message options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            // User Avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "User Avatar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

private fun formatTimestamp(timeMillis: Long): String {
    if (timeMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timeMillis))
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun StreamingMessageItem(
    streamingChunk: String,
    isDarkTheme: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "ShiPu AI Generating Response",
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = 2.dp,
                bottomEnd = 14.dp
            ),
            color = if (isDarkTheme) DarkAssistantBubble else LightAssistantBubble,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shadowElevation = 0.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (streamingChunk.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ShiPu AI is thinking...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MarkdownText(
                        markdown = "$streamingChunk ▍",
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Composable function using LazyColumn to display chat messages.
 */
@Composable
fun ChatMessageList(
    messages: List<Message>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    streamingChunk: String = "",
    highlightedMessageId: String? = null,
    searchHighlightKeyword: String? = null,
    listState: LazyListState = rememberLazyListState(),
    onRegenerate: () -> Unit = {}
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        items(messages, key = { it.messageId }) { msg ->
            ChatMessageItem(
                message = msg,
                isDarkTheme = isDarkTheme,
                isHighlighted = msg.messageId == highlightedMessageId,
                highlightKeyword = if (msg.messageId == highlightedMessageId) searchHighlightKeyword else null,
                onRegenerate = onRegenerate
            )
        }

        if (isStreaming) {
            item(key = "streaming_response_item") {
                StreamingMessageItem(
                    streamingChunk = streamingChunk,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyChatView(
    onPromptSelected: (String) -> Unit
) {
    val suggestions = listOf(
        "Explain Quantum Computing in plain terms",
        "Write a Python script for async Web Scraping",
        "Give me 5 strategies for deep focus at work",
        "Help me draft a polite project status email"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "ShiPu AI Logo",
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to ShiPu AI",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Your personal AI workspace with context memory and markdown support.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Card(
                    modifier = Modifier
                        .clickable { onPromptSelected(suggestion) }
                        .testTag("suggestion_card"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputPrompt: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        "Message ShiPu AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isStreaming) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("stop_generation_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = "Stop generation",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val hasText = inputPrompt.isNotBlank()
                IconButton(
                    onClick = onSend,
                    enabled = hasText,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasText) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (hasText) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
