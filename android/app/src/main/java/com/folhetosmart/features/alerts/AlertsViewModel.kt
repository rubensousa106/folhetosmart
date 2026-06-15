package com.folhetosmart.features.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.AlertDto
import com.folhetosmart.data.repository.AlertsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estados do ecrã Alertas. */
sealed interface AlertsUiState {
    data object Loading : AlertsUiState

    /** Sem sessão — mostra formulário de login/registo. */
    data class NeedsLogin(
        val submitting: Boolean = false,
        val error: String? = null
    ) : AlertsUiState

    /** Sessão iniciada — lista de alertas. */
    data class LoggedIn(
        val email: String,
        val alerts: List<AlertDto>,
        val refreshing: Boolean = false,
        val error: String? = null
    ) : AlertsUiState
}

class AlertsViewModel(private val repository: AlertsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertsUiState>(AlertsUiState.Loading)
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        bootstrap()
    }

    fun bootstrap() {
        if (repository.isLoggedIn) loadAlerts() else {
            _uiState.value = AlertsUiState.NeedsLogin()
        }
    }

    fun login(email: String, password: String) = authenticate(email, password, register = false)

    fun register(email: String, password: String) = authenticate(email, password, register = true)

    private fun authenticate(email: String, password: String, register: Boolean) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AlertsUiState.NeedsLogin(error = "Preenche o email e a palavra-passe.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AlertsUiState.NeedsLogin(submitting = true)
            try {
                if (register) repository.register(email, password)
                else repository.login(email, password)
                loadAlerts()
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401 -> "Email ou palavra-passe incorretos."
                    409 -> "Já existe uma conta com este email."
                    else -> "Não foi possível iniciar sessão (${e.code()})."
                }
                _uiState.value = AlertsUiState.NeedsLogin(error = message)
            } catch (e: Exception) {
                _uiState.value = AlertsUiState.NeedsLogin(
                    error = "Sem ligação ao servidor. Tenta novamente."
                )
            }
        }
    }

    fun loadAlerts() {
        viewModelScope.launch {
            val email = repository.email ?: return@launch bootstrap()
            _uiState.value = AlertsUiState.LoggedIn(email, emptyList(), refreshing = true)
            try {
                val alerts = repository.list()
                _uiState.value = AlertsUiState.LoggedIn(email, alerts)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    // Sessão expirada -> volta ao login.
                    repository.logout()
                    _uiState.value = AlertsUiState.NeedsLogin(
                        error = "A sessão expirou. Inicia sessão novamente."
                    )
                } else {
                    _uiState.value = AlertsUiState.LoggedIn(
                        email, emptyList(),
                        error = "Não foi possível carregar os alertas."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AlertsUiState.LoggedIn(
                    email, emptyList(),
                    error = "Sem ligação ao servidor."
                )
            }
        }
    }

    fun deleteAlert(alert: AlertDto) {
        val current = _uiState.value as? AlertsUiState.LoggedIn ?: return
        viewModelScope.launch {
            try {
                repository.delete(alert.id)
                _uiState.value = current.copy(alerts = current.alerts.filterNot { it.id == alert.id })
            } catch (e: Exception) {
                _uiState.value = current.copy(error = "Não foi possível remover o alerta.")
            }
        }
    }

    fun logout() {
        repository.logout()
        _uiState.value = AlertsUiState.NeedsLogin()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                AlertsViewModel(app.container.alertsRepository)
            }
        }
    }
}
