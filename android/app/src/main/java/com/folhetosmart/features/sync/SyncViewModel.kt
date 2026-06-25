package com.folhetosmart.features.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.models.FlyerOfferingDto
import com.folhetosmart.data.repository.CompareRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Ecrã Sincronizar (modelo simples): existe UM só ficheiro — os produtos
 * normalizados (do R2). "Sincronizar agora" descarrega-o (e fica em cache para
 * uso offline). ✓ se sincronizado, ✗ se ainda não.
 */
class SyncViewModel(
    private val compareRepository: CompareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    init {
        // Carga automática (usa a cache se estiver fresca) — sem Snackbar.
        verify(manual = false)
        // E, se o servidor já tiver um feed mais recente, descarrega-o sozinho —
        // sincroniza "mal esteja disponível", sem o utilizador carregar em nada.
        autoSyncIfNewer()
    }

    /** Botão "Sincronizar agora" — descarrega o ficheiro mais recente. */
    fun verify() = verify(manual = true)

    private fun verify(manual: Boolean, force: Boolean = manual) {
        viewModelScope.launch {
            (_uiState.value as? SyncUiState.Content)?.let {
                _uiState.value = it.copy(checking = true, errorMessage = null)
            }
            try {
                // force = descarrega fresco; senão usa a cache se estiver fresca.
                val offerings = withTimeout(TIMEOUT_MS) { compareRepository.allOfferings(force = force) }
                val content = SyncUiState.Content(
                    synced = offerings.isNotEmpty(),
                    productCount = offerings.size,
                    validityLabel = validityFromOfferings(offerings),
                    lastCheckedLabel = LocalTime.now().format(HHMM),
                    checking = false
                )
                _uiState.value = content
                if (manual) _events.tryEmit(SyncEvent.Checked(content.synced))
            } catch (e: TimeoutCancellationException) {
                onCheckFailure("⚠️ Sem resposta. Verifica a ligação e tenta novamente.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onCheckFailure("⚠️ Não foi possível sincronizar. Verifica a ligação.")
            }
        }
    }

    private fun onCheckFailure(message: String) {
        _uiState.value = when (val s = _uiState.value) {
            is SyncUiState.Content -> s.copy(checking = false, errorMessage = message)
            else -> SyncUiState.Error(message)
        }
    }

    /** Se o servidor tiver um feed mais recente que o sincronizado, descarrega-o. */
    private fun autoSyncIfNewer() {
        viewModelScope.launch {
            try {
                if (compareRepository.hasNewerFeed()) verify(manual = false, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // silencioso — a sincronização manual continua disponível
            }
        }
    }

    /**
     * Datas REAIS dos ficheiros .json: início mais cedo → fim mais tarde entre as
     * ofertas válidas (cada `validade` é "dd/MM/yyyy a dd/MM/yyyy"). Assim a data do
     * ecrã coincide SEMPRE com os feeds. Sem ofertas datáveis, cai na semana atual.
     */
    private fun validityFromOfferings(offerings: List<FlyerOfferingDto>): String {
        val starts = mutableListOf<LocalDate>()
        val ends = mutableListOf<LocalDate>()
        offerings.forEach { o ->
            val parts = o.validade?.split(" a ") ?: return@forEach
            if (parts.size == 2) {
                parseDate(parts[0])?.let { starts += it }
                parseDate(parts[1])?.let { ends += it }
            }
        }
        if (starts.isEmpty() || ends.isEmpty()) return currentWeekValidity()
        return "${starts.min().format(DM)} a ${ends.max().format(DMY)}"
    }

    private fun parseDate(s: String): LocalDate? = try {
        LocalDate.parse(s.trim(), DMY)
    } catch (e: Exception) {
        null
    }

    private fun currentWeekValidity(): String {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        return "${monday.format(DM)} a ${sunday.format(DMY)}"
    }

    companion object {
        private const val TIMEOUT_MS = 45_000L   // sobrevive ao cold-start do Render
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        private val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SyncViewModel(app.container.compareRepository)
            }
        }
    }
}
