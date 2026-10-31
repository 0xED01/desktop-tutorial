package com.yuandian.quote.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val COMPACT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val CN: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    fun format(d: LocalDate): String = d.format(ISO)

    fun parse(s: String): LocalDate = try { LocalDate.parse(s, ISO) } catch (e: Exception) { today() }

    fun compact(d: LocalDate): String = d.format(COMPACT)

    fun chinese(s: String): String = parse(s).format(CN)

    fun daysBetween(from: String, to: String): Long = ChronoUnit.DAYS.between(parse(from), parse(to))

    /** Material3 DatePicker 使用 UTC 零点毫秒 */
    fun toPickerMillis(s: String): Long = parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(ms: Long): String = format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())

    fun isExpired(validUntil: String): Boolean = parse(validUntil).isBefore(today())
}
