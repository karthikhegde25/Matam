package com.kARThik.vote.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDate(ms: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
fun fmtDateTime(ms: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(ms))
fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
fun pct(v: Double): String = String.format(Locale.getDefault(), "%.1f%%", v)

/** Initials used as a party symbol when no abbreviation/logo is given: "Green Alliance Party" -> "GAP", "Unity" -> "UNI". */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(4).map { it[0] }.joinToString("").uppercase()
    }
}
