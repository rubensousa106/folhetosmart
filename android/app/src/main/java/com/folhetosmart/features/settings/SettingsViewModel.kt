package com.folhetosmart.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.serverMessage
import com.folhetosmart.data.repository.PrivacyRepository
import com.folhetosmart.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estado do ecrã Definições (privacidade RGPD + perfil da conta). */
data class SettingsUiState(
    val loggedIn: Boolean = false,
    val exporting: Boolean = false,
    val deleting: Boolean = false,
    /** JSON exportado pronto a partilhar (consumido pelo ecrã). */
    val exportedJson: String? = null,
    val message: String? = null,
    // --- Perfil ("A minha conta") ---
    val profileLoading: Boolean = false,
    val name: String = "",
    val email: String = "",
    val district: String? = null,
    val city: String = "",
    val savingProfile: Boolean = false,
    val savingEmail: Boolean = false,
    val savingPassword: Boolean = false
)

class SettingsViewModel(
    private val privacy: PrivacyRepository,
    private val users: UserRepository
) : ViewModel() {

    // Semeia já com o email do token (local) para o avatar ser estável desde o
    // início — não espera, nem depende, da chamada de rede do loadProfile().
    private val _uiState = MutableStateFlow(
        SettingsUiState(loggedIn = privacy.isLoggedIn, email = users.email.orEmpty())
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        if (privacy.isLoggedIn) loadProfile()
    }

    /** Reavalia a sessão (ex.: ao voltar a este separador). */
    fun refreshSession() {
        val logged = privacy.isLoggedIn
        _uiState.update { it.copy(loggedIn = logged) }
        if (logged && _uiState.value.email.isBlank()) loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(profileLoading = true) }
            try {
                val me = users.me()
                _uiState.update {
                    it.copy(
                        profileLoading = false,
                        name = me.name.orEmpty(),
                        email = me.email,
                        district = me.district,
                        city = me.city.orEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(profileLoading = false) }
            }
        }
    }

    fun saveProfile(name: String, district: String?, city: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingProfile = true, message = null) }
            try {
                val me = users.updateProfile(name.ifBlank { null }, district, city.ifBlank { null })
                _uiState.update {
                    it.copy(
                        savingProfile = false,
                        name = me.name.orEmpty(),
                        district = me.district,
                        city = me.city.orEmpty(),
                        message = "Perfil atualizado."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(savingProfile = false, message = humanize(e)) }
            }
        }
    }

    fun changeEmail(currentPassword: String, newEmail: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingEmail = true, message = null) }
            try {
                users.changeEmail(currentPassword, newEmail.trim())
                _uiState.update {
                    it.copy(savingEmail = false, email = newEmail.trim(), message = "Email atualizado.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(savingEmail = false, message = humanize(e)) }
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingPassword = true, message = null) }
            try {
                users.changePassword(currentPassword, newPassword)
                _uiState.update { it.copy(savingPassword = false, message = "Palavra-passe atualizada.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(savingPassword = false, message = humanize(e)) }
            }
        }
    }

    /** Exporta todos os dados do utilizador (RGPD — portabilidade). */
    fun exportData() {
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, message = null) }
            try {
                val json = privacy.exportMyData()
                _uiState.update { it.copy(exporting = false, exportedJson = json) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exporting = false, message = "Não foi possível exportar os dados. Verifica a ligação.")
                }
            }
        }
    }

    /** Elimina a conta e todos os dados — irreversível. */
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, message = null) }
            try {
                privacy.deleteMyAccount()
                _uiState.value = SettingsUiState(
                    loggedIn = false,
                    message = "A tua conta e todos os dados foram eliminados."
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(deleting = false, message = "Não foi possível eliminar a conta. Tenta novamente.")
                }
            }
        }
    }

    fun consumeExport() = _uiState.update { it.copy(exportedJson = null) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun humanize(e: Exception): String = when (e) {
        is HttpException -> e.serverMessage() ?: when (e.code()) {
            400 -> "A palavra-passe atual está incorreta (ou os dados são inválidos)."
            409 -> "Já existe uma conta com este email."
            else -> "Não foi possível guardar (erro ${e.code()})."
        }
        else -> "Sem ligação ao servidor. Tenta novamente."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SettingsViewModel(app.container.privacyRepository, app.container.userRepository)
            }
        }
    }
}
