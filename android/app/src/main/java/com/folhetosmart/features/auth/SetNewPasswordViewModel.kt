package com.folhetosmart.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.friendlyMessage
import com.folhetosmart.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estado do ecrã "Definir nova palavra-passe" (após entrar com a temporária). */
data class SetNewPasswordUiState(
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

class SetNewPasswordViewModel(private val users: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SetNewPasswordUiState())
    val uiState: StateFlow<SetNewPasswordUiState> = _uiState.asStateFlow()

    /** Troca a palavra-passe temporária pela nova (a sessão já está ativa após o login). */
    fun submit(tempPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = SetNewPasswordUiState(submitting = true)
            try {
                users.changePassword(tempPassword, newPassword)
                _uiState.value = SetNewPasswordUiState(done = true)
            } catch (e: HttpException) {
                _uiState.value = SetNewPasswordUiState(
                    error = e.friendlyMessage(
                        mapOf(400 to "A palavra-passe temporária já não é válida. Pede uma nova.")
                    )
                )
            } catch (e: Exception) {
                _uiState.value = SetNewPasswordUiState(
                    error = "Sem ligação ao servidor. Tenta novamente."
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SetNewPasswordViewModel(app.container.userRepository)
            }
        }
    }
}
