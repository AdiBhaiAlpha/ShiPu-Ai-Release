package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.AdminAuditLogEntity
import com.example.data.local.db.KnowledgeEntity
import com.example.data.local.db.SystemPromptEntity
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val isSuperAdmin: Boolean = false,
    val selectedTab: Int = 0,
    val currentSystemPrompt: SystemPromptEntity? = null,
    val knowledgeList: List<KnowledgeEntity> = emptyList(),
    val auditLogs: List<AdminAuditLogEntity> = emptyList(),
    val apiKeyConfigured: Boolean = false,
    val apiKeyMasked: String = "Not Configured",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AdminViewModel(
    private val userId: String,
    private val userEmail: String,
    context: Context? = null
) : ViewModel() {
    private val adminRepository = AdminRepository(context)

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAdminData()
    }

    fun loadAdminData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isSuper = adminRepository.verifySuperAdmin(userId)
            if (!isSuper) {
                _uiState.update { it.copy(isSuperAdmin = false, isLoading = false, errorMessage = "Unauthorized: Super Admin access required.") }
                return@launch
            }

            val prompt = adminRepository.getSystemPrompt(userId)
            val knowledge = adminRepository.getAllKnowledge(userId)
            val logs = adminRepository.getAuditLogs(userId)
            val (hasKey, maskedKey) = adminRepository.getApiKeyStatus(userId)

            _uiState.update {
                it.copy(
                    isSuperAdmin = true,
                    currentSystemPrompt = prompt,
                    knowledgeList = knowledge,
                    auditLogs = logs,
                    apiKeyConfigured = hasKey,
                    apiKeyMasked = maskedKey,
                    isLoading = false
                )
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun saveSystemPrompt(newContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = adminRepository.saveSystemPrompt(userId, userEmail, newContent)
            if (success) {
                val updatedPrompt = adminRepository.getSystemPrompt(userId)
                val logs = adminRepository.getAuditLogs(userId)
                _uiState.update {
                    it.copy(
                        currentSystemPrompt = updatedPrompt,
                        auditLogs = logs,
                        isLoading = false,
                        successMessage = "System prompt updated successfully"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to update system prompt") }
            }
        }
    }

    fun saveKnowledge(knowledgeId: String?, title: String, content: String, category: String, tags: String, status: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = adminRepository.saveKnowledge(userId, userEmail, knowledgeId, title, content, category, tags, status)
            if (success) {
                val knowledge = adminRepository.getAllKnowledge(userId)
                val logs = adminRepository.getAuditLogs(userId)
                _uiState.update {
                    it.copy(
                        knowledgeList = knowledge,
                        auditLogs = logs,
                        isLoading = false,
                        successMessage = "Knowledge saved successfully"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to save knowledge") }
            }
        }
    }

    fun deleteKnowledge(knowledgeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = adminRepository.deleteKnowledge(userId, userEmail, knowledgeId)
            if (success) {
                val knowledge = adminRepository.getAllKnowledge(userId)
                val logs = adminRepository.getAuditLogs(userId)
                _uiState.update {
                    it.copy(
                        knowledgeList = knowledge,
                        auditLogs = logs,
                        isLoading = false,
                        successMessage = "Knowledge deleted successfully"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to delete knowledge") }
            }
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = adminRepository.saveApiKey(userId, userEmail, apiKey)
            if (success) {
                val (hasKey, maskedKey) = adminRepository.getApiKeyStatus(userId)
                val logs = adminRepository.getAuditLogs(userId)
                _uiState.update {
                    it.copy(
                        apiKeyConfigured = hasKey,
                        apiKeyMasked = maskedKey,
                        auditLogs = logs,
                        isLoading = false,
                        successMessage = "OpenRouter API key rotated securely"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to rotate API key") }
            }
        }
    }

    fun revokeApiKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = adminRepository.revokeApiKey(userId, userEmail)
            if (success) {
                val (hasKey, maskedKey) = adminRepository.getApiKeyStatus(userId)
                val logs = adminRepository.getAuditLogs(userId)
                _uiState.update {
                    it.copy(
                        apiKeyConfigured = hasKey,
                        apiKeyMasked = maskedKey,
                        auditLogs = logs,
                        isLoading = false,
                        successMessage = "OpenRouter API key revoked"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to revoke API key") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
