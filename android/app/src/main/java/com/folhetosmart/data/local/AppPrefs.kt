package com.folhetosmart.data.local

import android.content.Context
import java.time.LocalDate
import java.time.temporal.WeekFields

/** Preferências simples da app (semana da última sincronização). */
class AppPrefs(context: Context) {

    private val prefs =
        context.getSharedPreferences("folheto_prefs", Context.MODE_PRIVATE)

    /** Semana (ISO) da última sincronização de dados bem-sucedida, ou null. */
    var lastSyncWeek: String?
        get() = prefs.getString(KEY_LAST_SYNC_WEEK, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_WEEK, value).apply()

    /** True se ainda não sincronizou esta semana (precisa de ler da BD). */
    fun needsWeeklySync(): Boolean = lastSyncWeek != currentWeekKey()

    companion object {
        private const val KEY_LAST_SYNC_WEEK = "last_sync_week"

        /** Chave da semana ISO atual, ex.: "2026-W24". */
        fun currentWeekKey(today: LocalDate = LocalDate.now()): String {
            val fields = WeekFields.ISO
            val week = today.get(fields.weekOfWeekBasedYear())
            val year = today.get(fields.weekBasedYear())
            return "%d-W%02d".format(year, week)
        }
    }
}
