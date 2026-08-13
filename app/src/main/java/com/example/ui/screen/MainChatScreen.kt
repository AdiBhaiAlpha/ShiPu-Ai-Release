package com.example.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.component.ChatInputBar
import com.example.ui.component.ChatMessageList
import com.example.ui.component.EmptyChatView
import com.example.ui.component.MemoryManagerDialog
import com.example.ui.component.SidebarDrawerContent
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Main Chat Screen for ShiPu AI.
 * Displays clean conversation UI with real streaming responses, drawer navigation,
 * and seamless full-screen settings page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    chatViewModel: ChatViewModel,
    chatUiState: ChatUiState,
    authUiState: AuthUiState,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputPrompt by remember { mutableStateOf("") }
    var showMemoryModal by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    val activeConv = chatUiState.conversations.firstOrNull { it.conversationId == chatUiState.activeConversationId }

    // Auto scroll list to bottom when new messages arrive or stream updates
    LaunchedEffect(chatUiState.messages.size, chatUiState.streamingChunk) {
        val totalCount = chatUiState.messages.size + if (chatUiState.isStreaming) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    if (showSettingsScreen) {
        SettingsScreen(
            chatViewModel = chatViewModel,
            chatUiState = chatUiState,
            authUiState = authUiState,
            onBack = { showSettingsScreen = false },
            onLogout = onLogout,
            onDeleteAccount = onDeleteAccount
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    SidebarDrawerContent(
                        conversations = chatUiState.conversations,
                        activeConversationId = chatUiState.activeConversationId,
                        searchQuery = chatUiState.searchQuery,
                        onSearchQueryChanged = { chatViewModel.setSearchQuery(it) },
                        onSelectConversation = { convId ->
                            chatViewModel.selectConversation(convId)
                            scope.launch { drawerState.close() }
                        },
                        onNewChat = {
                            chatViewModel.createNewChat()
                            scope.launch { drawerState.close() }
                        },
                        onRenameConversation = { id, title ->
                            chatViewModel.renameConversation(id, title)
                        },
                        onTogglePin = { id, current ->
                            chatViewModel.togglePinConversation(id, current)
                        },
                        onDeleteConversation = { id ->
                            chatViewModel.deleteConversation(id)
                        },
                        userName = authUiState.currentUser?.name,
                        userEmail = authUiState.currentUser?.email,
                        memoryCount = chatUiState.userMemories.size,
                        onOpenMemories = {
                            showMemoryModal = true
                            scope.launch { drawerState.close() }
                        },
                        onOpenSettings = {
                            showSettingsScreen = true
                            scope.launch { drawerState.close() }
                        },
                        onLogout = {
                            onLogout()
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeConv?.title ?: "ShiPu AI",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Clean ShiPu AI Branding Badge
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Image(
                                                painter = painterResource(id = com.example.R.drawable.ic_shipu_logo),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "ShiPu AI",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Menu,
                                        contentDescription = "Open sidebar",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            actions = {
                                // New Chat Action
                                IconButton(
                                    onClick = { chatViewModel.createNewChat() },
                                    modifier = Modifier.testTag("top_new_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "New Chat",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Theme Toggle Action
                                IconButton(
                                    onClick = { chatViewModel.toggleTheme() },
                                    modifier = Modifier.testTag("top_theme_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (chatUiState.isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                        contentDescription = "Toggle Theme",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                },
                bottomBar = {
                    ChatInputBar(
                        inputPrompt = inputPrompt,
                        onInputChanged = { inputPrompt = it },
                        onSend = {
                            val text = inputPrompt
                            inputPrompt = ""
                            chatViewModel.sendMessage(text)
                        },
                        isStreaming = chatUiState.isStreaming,
                        onStop = { chatViewModel.stopGeneration() }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Error Banner
                        AnimatedVisibility(visible = chatUiState.errorMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chatUiState.errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { chatViewModel.clearError() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Dismiss error",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Loading State
                        if (chatUiState.isLoadingMessages) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (chatUiState.messages.isEmpty() && !chatUiState.isStreaming) {
                            // Empty State
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyChatView(
                                    onPromptSelected = { selectedPrompt ->
                                        inputPrompt = selectedPrompt
                                    }
                                )
                            }
                        } else {
                            // Messages List using ChatMessageList
                            ChatMessageList(
                                messages = chatUiState.messages,
                                isDarkTheme = chatUiState.isDarkTheme,
                                isStreaming = chatUiState.isStreaming,
                                streamingChunk = chatUiState.streamingChunk,
                                listState = listState,
                                onRegenerate = { chatViewModel.regenerateLastResponse() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Memories Dialog
        if (showMemoryModal) {
            MemoryManagerDialog(
                memories = chatUiState.userMemories,
                onDismiss = { showMemoryModal = false },
                onAddMemory = { fact, category -> chatViewModel.addMemory(fact, category) },
                onUpdateMemory = { id, fact, category -> chatViewModel.updateMemory(id, fact, category) },
                onDeleteMemory = { id -> chatViewModel.deleteMemory(id) },
                onClearAll = { chatViewModel.clearAllMemories() }
            )
        }
    }
}
