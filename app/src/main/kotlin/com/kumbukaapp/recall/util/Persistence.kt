package com.kumbukaapp.recall.util

import android.content.Context
import java.io.File

/** Simple file-based persistence for the last call-log sync timestamp. */
object Persistence {
    private const val FILE_NAME = "last_sync_timestamp.txt"

    /** Save the last call log sync timestamp. */
    fun saveLastSync(context: Context, timestamp: Long) {
        File(context.applicationContext.filesDir, FILE_NAME).writeText(timestamp.toString())
    }

    /** Load the last call log sync timestamp. */
    fun loadLastSync(context: Context): Long {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText().toLongOrNull() ?: 0L else 0L
    }
}
