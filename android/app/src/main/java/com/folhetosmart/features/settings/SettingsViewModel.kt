package com.folhetosmart.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.repository.PrivacyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado do ecrã Definições. */
data class SettingsUiState(
    val loggedIn: Boolean = false,
    val exporting: Boolean = false,
    val deleting: Boolean = false,
    /** JSON exportado pronto a partilhar (consumido pelo ecrã). */
    val exportedJson: String? = null,
    val message: String? = null
)

class SettingsViewModel(private val repository: PrivacyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(loggedIn = repository.isLoggedIn))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Reavalia a sessão (ex.: ao voltar a este separador). */
    fun refreshSession() {
        _uiState.value = _uiState.value.copy(loggedIn = repository.isLoggedIn)
    }

    /** Exporta todos os dados do utilizador (RGPD — portabilidade). */
    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exporting = true, message = null)
            try {
                val json = repository.exportMyData()
                _uiState.value = _uiState.value.copy(exporting = false, exportedJson = json)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    exporting = false,
                    message = "Não foi possível exportar os dados. Verifica a ligação."
                )
            }
        }
    }

    /** Elimina a conta e todos os dados — irreversível. */
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleting = true, message = null)
            try {
                repository.deleteMyAccount()
                _uiState.value = SettingsUiState(
                    loggedIn = false,
                    message = "A tua conta e todos os dados foram eliminados."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    deleting = false,
                    message = "Não foi possível eliminar a conta. Tenta novamente."
                )
            }
        }
    }

    fun consumeExport() {
        _uiState.value = _uiState.value.copy(exportedJson = null)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SettingsViewModel(app.container.privacyRepository)
            }
        }
    }
}
