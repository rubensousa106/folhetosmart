package com.folhetosmart.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.repository.AlertsRepository
import com.folhetosmart.data.repository.PrivacyRepository
import com.folhetosmart.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estado do registo (Passo 1: conta + consentimentos; Passo 2: localização Aldi). */
data class OnboardingUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    /** Passo 1 concluído — a UI avança para a localização. */
    val accountCreated: Boolean = false,
    /** Passo 2 concluído (guardado ou saltado) — a UI termina o registo. */
    val locationDone: Boolean = false,
    val notificationsAccepted: Boolean = false,
    /** Nome dado no passo 1 (guardado já aí; reenviado no passo 2 p/ não ser apagado). */
    val name: String = ""
)

class OnboardingViewModel(
    private val alertsRepository: AlertsRepository,
    private val privacyRepository: PrivacyRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Passo 1 — cria a conta. Só é chamado com a checkbox de termos marcada.
     * Regista o consentimento (versão + notificações) logo a seguir.
     */
    fun createAccount(name: String, email: String, password: String, notificationsAccepted: Boolean) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = OnboardingUiState(error = "Preenche o email e a palavra-passe.")
            return
        }
        viewModelScope.launch {
            _uiState.value = OnboardingUiState(submitting = true)
            try {
                alertsRepository.register(email.trim(), password)
                // Guarda já o nome (a conta existe e tem sessão) — best effort.
                val cleanName = name.trim()
                if (cleanName.isNotBlank()) {
                    try {
                        userRepository.updateProfile(cleanName, null, null)
                    } catch (e: Exception) {
                        // Não bloqueia o registo; o nome pode ser definido depois.
                    }
                }
                // Consentimento explícito (RGPD) — best effort: a conta já existe.
                try {
                    privacyRepository.registerConsent(notificationsAccepted)
                } catch (e: Exception) {
                    // Não bloqueia o registo.
                }
                _uiState.value = OnboardingUiState(
                    accountCreated = true,
                    notificationsAccepted = notificationsAccepted,
                    name = cleanName
                )
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    409 -> "Já existe uma conta com este email. Volta atrás e inicia sessão."
                    429 -> "Demasiadas tentativas. Tenta novamente daqui a 15 minutos."
                    else -> "Não foi possível criar a conta (${e.code()})."
                }
                _uiState.value = OnboardingUiState(error = message)
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState(
                    error = "Sem ligação ao servidor. Tenta novamente."
                )
            }
        }
    }

    /** Passo 2 — guarda distrito/cidade (folheto regional do Aldi). */
    fun saveLocation(district: String?, city: String?) {
        val current = _uiState.value
        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true, error = null)
            try {
                // Reenvia o nome: o PUT /me substitui os 3 campos, por isso enviar
                // só a localização apagaria o nome dado no passo 1.
                userRepository.updateProfile(current.name.ifBlank { null }, district, city)
                _uiState.value = current.copy(submitting = false, locationDone = true)
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    submitting = false,
                    error = "Não foi possível guardar a localização. Podes definir mais tarde."
                )
            }
        }
    }

    /** Passo 2 — saltar (a localização é opcional; só afeta o folheto Aldi). */
    fun skipLocation() {
        _uiState.value = _uiState.value.copy(locationDone = true, error = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                OnboardingViewModel(
                    app.container.alertsRepository,
                    app.container.privacyRepository,
                    app.container.userRepository
                )
            }
        }
    }
}
