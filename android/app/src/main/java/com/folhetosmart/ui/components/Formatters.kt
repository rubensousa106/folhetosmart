package com.folhetosmart.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação PT-PT de preços e datas usada em toda a UI. */
object Formatters {

    private val localePt = Locale("pt", "PT")

    private val shortDayTime = DateTimeFormatter.ofPattern("EEE HH:mm", localePt)
    private val longDayTime = DateTimeFormatter.ofPattern("EEEE, HH:mm", localePt)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", localePt)

    /** 1.39 -> "1,39 €" */
    fun price(value: Double): String = String.format(localePt, "%.2f €", value)

    /** 12.4 -> "12,4%" */
    fun percent(value: Double): String = String.format(localePt, "%.1f%%", value)

    /** ISO instant -> "qui 08:14" (hora local). */
    fun shortDateTime(iso: String?): String = formatInstant(iso, shortDayTime)

    /** ISO instant -> "quinta-feira, 13:22" (hora local). */
    fun longDateTime(iso: String?): String = formatInstant(iso, longDayTime)

    /** ISO date (yyyy-MM-dd) -> "Válido até domingo". */
    fun validUntil(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            "Válido até " + LocalDate.parse(isoDate).format(weekday)
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatInstant(iso: String?, formatter: DateTimeFormatter): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            Instant.parse(iso)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        } catch (e: Exception) {
            ""
        }
    }
}
