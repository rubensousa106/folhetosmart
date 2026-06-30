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

/** Estado do ecrã "Recuperar palavra-passe". */
data class ForgotPasswordUiState(
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

class ForgotPasswordViewModel(private val repository: AlertsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    /** Pede a palavra-passe temporária. Resposta neutra do backend (não revela se o email existe). */
    fun submit(email: String) {
        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState(submitting = true)
            try {
                repository.forgotPassword(email.trim())
                _uiState.value = ForgotPasswordUiState(done = true)
            } catch (e: HttpException) {
                _uiState.value = ForgotPasswordUiState(error = e.friendlyMessage())
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState(
                    error = "Sem ligação ao servidor. Tenta novamente."
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                ForgotPasswordViewModel(app.container.alertsRepository)
            }
        }
    }
}
