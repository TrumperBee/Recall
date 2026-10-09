package com.kumbukaapp.recall.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object FormatUtils {
    fun formatDateLong(date: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(calendar.timeInMillis)
    }

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "—"
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val mins = minutes % 60
        return if (mins > 0) "$hours h $mins m" else "$hours h"
    }
}
