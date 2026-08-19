package com.example.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatSearchResult
import com.example.data.model.Conversation
import com.example.data.model.SearchMatchType

@Composable
fun SidebarDrawerContent(
    conversations: List<Conversation>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchResults: List<ChatSearchResult> = emptyList(),
    onSelectSearchResult: (ChatSearchResult) -> Unit = {},
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    userName: String?,
    userEmail: String?,
    memoryCount: Int,
    onOpenMemories: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onOpenAdmin: (() -> Unit)? = null
) {
    var conversationToRename by remember { mutableStateOf<Conversation?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val filtered = conversations
    val pinnedList = filtered.filter { it.isPinned }
    val regularList = filtered.filter { !it.isPinned }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Header & New Chat Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.shipu.ai.R.mipmap.ic_launcher),
                            contentDescription = "ShiPu AI Logo",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ShiPu AI",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("new_chat_button"),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "New Chat",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search chats & memories...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_conversations_input"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Conversation Lists or Conversational Search Results
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "SEARCH RESULTS (${searchResults.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    items(searchResults, key = { "sr_${it.matchType}_${it.conversationId}_${it.matchedMessageId ?: it.snippetText.hashCode()}" }) { result ->
                        SearchResultDrawerItem(
                            result = result,
                            query = searchQuery,
                            onSelect = { onSelectSearchResult(result) }
                        )
                    }
                } else {
                    if (pinnedList.isNotEmpty()) {
                        item {
                            Text(
                                text = "PINNED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        items(pinnedList, key = { "pinned_${it.conversationId}" }) { conv ->
                            ConversationDrawerItem(
                                conversation = conv,
                                isActive = conv.conversationId == activeConversationId,
                                onSelect = { onSelectConversation(conv.conversationId) },
                                onRenameClick = {
                                    conversationToRename = conv
                                    renameInput = conv.title
                                },
                                onPinToggle = { onTogglePin(conv.conversationId, conv.isPinned) },
                                onDeleteClick = { onDeleteConversation(conv.conversationId) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    item {
                        Text(
                            text = "RECENT CHATS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (regularList.isEmpty() && pinnedList.isEmpty()) {
                        item {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching results found" else "No conversations found",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(regularList, key = { conv -> conv.conversationId }) { conv ->
                            ConversationDrawerItem(
                                conversation = conv,
                                isActive = conv.conversationId == activeConversationId,
                                onSelect = { onSelectConversation(conv.conversationId) },
                                onRenameClick = {
                                    conversationToRename = conv
                                    renameInput = conv.title
                                },
                                onPinToggle = { onTogglePin(conv.conversationId, conv.isPinned) },
                                onDeleteClick = { onDeleteConversation(conv.conversationId) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

            // MongoDB Cloud Sync Status Badge
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MongoDB Atlas: Cloud Synced",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // User Profile & Settings
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (userName?.take(1) ?: "U").uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName ?: "User",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userEmail ?: "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Memories Trigger
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMemories() }
                            .testTag("open_memories_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Memories",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Memories ($memoryCount)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Settings Trigger
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Logout Button
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (userEmail?.trim()?.lowercase() == "chitronbhattacharjee@gmail.com" && onOpenAdmin != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAdmin,
                        modifier = Modifier.fillMaxWidth().testTag("admin_console_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Super Admin Console", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (conversationToRename != null) {
        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            title = { Text("Rename Chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Title", fontSize = 13.sp) },
                    modifier = Modifier.testTag("rename_chat_input"),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = conversationToRename
                        if (target != null && renameInput.isNotBlank()) {
                            onRenameConversation(target.conversationId, renameInput)
                        }
                        conversationToRename = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save", fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) {
                    Text("Cancel", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: Conversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRenameClick: () -> Unit,
    onPinToggle: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect() }
            .testTag("conversation_item_${conversation.conversationId}"),
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = conversation.title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (conversation.isPinned) "Unpin" else "Pin", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onPinToggle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultDrawerItem(
    result: ChatSearchResult,
    query: String,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onSelect() }
            .testTag("search_result_item_${result.conversationId}"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val badgeText = when (result.matchType) {
                    SearchMatchType.TITLE -> "Chat"
                    SearchMatchType.MESSAGE_USER -> "You"
                    SearchMatchType.MESSAGE_ASSISTANT -> "ShiPu AI"
                    SearchMatchType.MEMORY -> "Memory"
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = result.conversationTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Highlighted snippet text
            val annotatedSnippet = androidx.compose.ui.text.buildAnnotatedString {
                val text = result.snippetText
                val idx = text.indexOf(query, ignoreCase = true)
                if (idx != -1 && query.isNotBlank()) {
                    append(text.substring(0, idx))
                    pushStyle(
                        androidx.compose.ui.text.SpanStyle(
                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    append(text.substring(idx, idx + query.length))
                    pop()
                    append(text.substring(idx + query.length))
                } else {
                    append(text)
                }
            }

            Text(
                text = annotatedSnippet,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}
