package com.folhetosmart.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.friendlyMessage
import com.folhetosmart.data.repository.AlertsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estado do ecrã de Login (entrada da app). */
data class LoginUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    /** Entrou com palavra-passe temporária — a UI deve forçar a definição de uma nova. */
    val requirePasswordChange: Boolean = false
)

class LoginViewModel(private val repository: AlertsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(submitting = true)
            try {
                val auth = repository.login(email.trim(), password)
                _uiState.value = if (auth.mustChangePassword) {
                    LoginUiState(requirePasswordChange = true)
                } else {
                    LoginUiState(loggedIn = true)
                }
            } catch (e: HttpException) {
                _uiState.value = LoginUiState(error = e.friendlyMessage())
            } catch (e: Exception) {
                _uiState.value = LoginUiState(error = "Sem ligação ao servidor. Tenta novamente.")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                LoginViewModel(app.container.alertsRepository)
            }
        }
    }
}
