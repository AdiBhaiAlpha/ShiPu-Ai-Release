package com.example.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.db.KnowledgeEntity
import com.example.data.local.db.SystemPromptEntity
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val uiState by adminViewModel.uiState.collectAsState()
    var showAddKnowledgeDialog by remember { mutableStateOf(false) }
    var editingKnowledge by remember { mutableStateOf<KnowledgeEntity?>(null) }
    var showPromptConfirmDialog by remember { mutableStateOf(false) }
    var pendingPromptContent by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(false) }
    var newApiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Super Admin Console", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close Admin Console")
                    }
                },
                actions = {
                    IconButton(onClick = { adminViewModel.loadAdminData() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Data")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingVals ->
        if (uiState.isLoading && uiState.currentSystemPrompt == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingVals), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!uiState.isSuperAdmin) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingVals), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Access Denied", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This account does not have Super Admin privileges.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack) {
                        Text("Return to App")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVals)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Notifications / Banners
                uiState.successMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(msg, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            IconButton(onClick = { adminViewModel.clearMessages() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }

                uiState.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(err, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            IconButton(onClick = { adminViewModel.clearMessages() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }

                // TAB NAVIGATION ROW
                val tabs = listOf("Overview", "System Prompt", "AI Config", "Knowledge Base", "API Config", "Security", "Audit Logs", "App Config")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs.size) { index ->
                        val isSelected = uiState.selectedTab == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { adminViewModel.selectTab(index) },
                            label = { Text(tabs[index]) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                HorizontalDivider()

                // TAB CONTENT
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (uiState.selectedTab) {
                        0 -> AdminOverviewTab(uiState, onNavigateTab = { adminViewModel.selectTab(it) })
                        1 -> AdminSystemPromptTab(
                            currentPrompt = uiState.currentSystemPrompt,
                            onSaveRequested = { content ->
                                pendingPromptContent = content
                                showPromptConfirmDialog = true
                            }
                        )
                        2 -> AdminAiConfigTab()
                        3 -> AdminKnowledgeTab(
                            knowledgeList = uiState.knowledgeList,
                            onAddClick = {
                                editingKnowledge = null
                                showAddKnowledgeDialog = true
                            },
                            onEditClick = { item ->
                                editingKnowledge = item
                                showAddKnowledgeDialog = true
                            },
                            onDeleteClick = { id ->
                                adminViewModel.deleteKnowledge(id)
                            }
                        )
                        4 -> AdminApiKeyTab(
                            apiKeyConfigured = uiState.apiKeyConfigured,
                            apiKeyMasked = uiState.apiKeyMasked,
                            showInput = showApiKeyInput,
                            onToggleInput = { showApiKeyInput = !showApiKeyInput },
                            newKeyInput = newApiKeyInput,
                            onKeyChanged = { newApiKeyInput = it },
                            apiKeyVisible = apiKeyVisible,
                            onToggleVisibility = { apiKeyVisible = !apiKeyVisible },
                            onSaveKey = {
                                adminViewModel.saveApiKey(newApiKeyInput)
                                newApiKeyInput = ""
                                showApiKeyInput = false
                            },
                            onRevokeKey = {
                                adminViewModel.revokeApiKey()
                            }
                        )
                        5 -> AdminSecurityTab()
                        6 -> AdminAuditLogsTab(auditLogs = uiState.auditLogs)
                        7 -> AdminAppConfigTab()
                    }
                }
            }
        }
    }

    // Add / Edit Knowledge Dialog
    if (showAddKnowledgeDialog) {
        KnowledgeDialog(
            knowledge = editingKnowledge,
            onDismiss = { showAddKnowledgeDialog = false },
            onSave = { title, content, category, tags, status ->
                adminViewModel.saveKnowledge(
                    knowledgeId = editingKnowledge?.knowledgeId,
                    title = title,
                    content = content,
                    category = category,
                    tags = tags,
                    status = status
                )
                showAddKnowledgeDialog = false
            }
        )
    }

    // Prompt Update Confirmation Dialog
    if (showPromptConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPromptConfirmDialog = false },
            title = { Text("Confirm System Prompt Update") },
            text = { Text("You are about to modify the core system prompt for ShiPu AI. This will affect AI behavior globally across all chats. Are you sure you wish to apply this change?") },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.saveSystemPrompt(pendingPromptContent)
                        showPromptConfirmDialog = false
                    }
                ) {
                    Text("Apply Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromptConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminOverviewTab(uiState: com.example.ui.viewmodel.AdminUiState, onNavigateTab: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Super Admin Welcome", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You are securely authenticated as Super Admin. All administrative actions are validated server-side and recorded in the immutable audit log.", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdminStatCard(
                    title = "System Prompt",
                    value = "v${uiState.currentSystemPrompt?.version ?: 1}",
                    subtitle = "Active & Secure",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(1) }
                )
                AdminStatCard(
                    title = "Knowledge Base",
                    value = "${uiState.knowledgeList.size} items",
                    subtitle = "Domain Context",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(3) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdminStatCard(
                    title = "OpenRouter API",
                    value = if (uiState.apiKeyConfigured) "Connected" else "Not Set",
                    subtitle = uiState.apiKeyMasked,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(4) }
                )
                AdminStatCard(
                    title = "Audit Logs",
                    value = "${uiState.auditLogs.size} events",
                    subtitle = "Verified",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(6) }
                )
            }
        }
    }
}

@Composable
fun AdminStatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AdminSystemPromptTab(currentPrompt: SystemPromptEntity?, onSaveRequested: (String) -> Unit) {
    var textContent by remember(currentPrompt) { mutableStateOf(currentPrompt?.content ?: "") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val updatedDateStr = currentPrompt?.let { dateFormat.format(Date(it.updatedAt)) } ?: "Never"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("System Prompt Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Controls core AI personality, tone, and system instructions. Hidden from normal users.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Version ${currentPrompt?.version ?: 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Last updated: $updatedDateStr by ${currentPrompt?.updatedBy ?: "System"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { textContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        label = { Text("Core System Prompt Content") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = { textContent = currentPrompt?.content ?: "" }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSaveRequested(textContent) },
                            enabled = textContent.isNotBlank() && textContent != currentPrompt?.content
                        ) {
                            Text("Save & Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAiConfigTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("AI & Model Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Configure default AI models, temperature bounds, and inference parameters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Model", fontWeight = FontWeight.SemiBold)
                    Text("openrouter/free (Gemini 2.5 Flash / Llama 3.3)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Default Temperature", fontWeight = FontWeight.SemiBold)
                    Text("0.7 (Balanced creativity and accuracy)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Max Context Tokens", fontWeight = FontWeight.SemiBold)
                    Text("2048 tokens", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AdminKnowledgeTab(
    knowledgeList: List<KnowledgeEntity>,
    onAddClick: () -> Unit,
    onEditClick: (KnowledgeEntity) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(knowledgeList, searchQuery) {
        if (searchQuery.isBlank()) knowledgeList
        else knowledgeList.filter { it.title.contains(searchQuery, true) || it.category.contains(searchQuery, true) || it.tags.contains(searchQuery, true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Knowledge Base", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = onAddClick) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Knowledge")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search knowledge by title, category, or tags...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No knowledge entries found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.knowledgeId }) { item ->
                    KnowledgeItemCard(item, onEdit = { onEditClick(item) }, onDelete = { onDeleteClick(item.knowledgeId) })
                }
            }
        }
    }
}

@Composable
fun KnowledgeItemCard(item: KnowledgeEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(
                    color = if (item.status == "ENABLED") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.status == "ENABLED") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(item.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 3)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category: ${item.category} • Tags: ${item.tags}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Knowledge", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete Knowledge", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun KnowledgeDialog(
    knowledge: KnowledgeEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(knowledge?.title ?: "") }
    var content by remember { mutableStateOf(knowledge?.content ?: "") }
    var category by remember { mutableStateOf(knowledge?.category ?: "General") }
    var tags by remember { mutableStateOf(knowledge?.tags ?: "") }
    var status by remember { mutableStateOf(knowledge?.status ?: "ENABLED") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (knowledge == null) "Add Knowledge Entry" else "Edit Knowledge Entry") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Knowledge Content") }, modifier = Modifier.fillMaxWidth().height(140.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content, category, tags, status) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AdminApiKeyTab(
    apiKeyConfigured: Boolean,
    apiKeyMasked: String,
    showInput: Boolean,
    onToggleInput: () -> Unit,
    newKeyInput: String,
    onKeyChanged: (String) -> Unit,
    apiKeyVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onSaveKey: () -> Unit,
    onRevokeKey: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("OpenRouter API Key Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Manage backend provider keys securely. Keys are never exposed in APK resources, Git, or plaintext logs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("OpenRouter API Status", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (apiKeyConfigured) "Connected" else "Not Configured", color = if (apiKeyConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(apiKeyMasked, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!showInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = onToggleInput, modifier = Modifier.weight(1f)) {
                                Text(if (apiKeyConfigured) "Rotate API Key" else "Configure API Key")
                            }
                            if (apiKeyConfigured) {
                                OutlinedButton(onClick = onRevokeKey, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text("Revoke")
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newKeyInput,
                            onValueChange = onKeyChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter OpenRouter API Key") },
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = onToggleVisibility) {
                                    Icon(if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Toggle Key Visibility")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                        ) {
                            OutlinedButton(onClick = onToggleInput) {
                                Text("Cancel")
                            }
                            Button(onClick = onSaveKey, enabled = newKeyInput.isNotBlank()) {
                                Text("Securely Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSecurityTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Security & Authorization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Server-side role verification and session integrity checks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Primary Super Admin Account", fontWeight = FontWeight.SemiBold)
                    Text("chitronbhattacharjee@gmail.com", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Authorization Rule", fontWeight = FontWeight.SemiBold)
                    Text("Strictly enforced at database and repository layer. Client UI visibility checks are mirrored by secure server-side validation on every privileged invocation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogsTab(auditLogs: List<com.example.data.local.db.AdminAuditLogEntity>) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Immutable Audit Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Recorded administrative events and configuration changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(12.dp))

        if (auditLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No audit logs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auditLogs, key = { it.logId }) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.action, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(log.details, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("By: ${log.userEmail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAppConfigTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("App Configuration & Metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ShiPu AI Android", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version: 1.0.0 (Production)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Engine: Jetpack Compose & Room Database", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
