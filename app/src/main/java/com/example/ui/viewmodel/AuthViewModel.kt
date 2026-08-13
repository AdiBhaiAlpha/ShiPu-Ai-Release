package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionManager
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val currentUser: User? = null,
    val userId: String? = null,
    val errorMessage: String? = null,
    val isSignUpMode: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager(application)
    private val authRepository = AuthRepository(sessionManager)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkExistingSession()
    }

    fun checkExistingSession() {
        val userId = sessionManager.getActiveUserId()
        val name = sessionManager.getActiveUserName()
        val email = sessionManager.getActiveUserEmail()

        if (userId != null && email != null) {
            val user = User(
                userId = userId,
                email = email,
                passwordHash = "",
                name = name ?: "User"
            )
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    userId = userId,
                    currentUser = user,
                    isLoading = false
                )
            }
            // Background sync user details from database
            viewModelScope.launch {
                val dbUser = authRepository.getCurrentUser(userId)
                if (dbUser != null) {
                    _uiState.update { it.copy(currentUser = dbUser) }
                } else {
                    logout()
                }
            }
        } else {
            _uiState.update { it.copy(isAuthenticated = false, isLoading = false) }
        }
    }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null
            )
        }
    }

    fun signUp(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.signUp(email, password, name)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            currentUser = result.data,
                            userId = result.data.userId,
                            errorMessage = null
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            currentUser = result.data,
                            userId = result.data.userId,
                            errorMessage = null
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.logout()
            _uiState.update {
                AuthUiState(
                    isAuthenticated = false,
                    isLoading = false
                )
            }
        }
    }

    fun deleteAccount() {
        val currentUserId = _uiState.value.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.deleteAccount(currentUserId)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        AuthUiState(
                            isAuthenticated = false,
                            isLoading = false
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
