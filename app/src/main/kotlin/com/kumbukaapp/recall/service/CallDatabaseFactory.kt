package com.kumbukaapp.recall.service

import android.content.Context
import androidx.room.Room
import com.kumbukaapp.recall.data.db.CallDatabase

object CallDatabaseFactory {
    private var db: CallDatabase? = null

    fun getInstance(context: Context): CallDatabase {
        if (db == null) {
            db = Room.databaseBuilder(
                context.applicationContext,
                CallDatabase::class.java,
                "recall_database"
            ).build()
        }
        return db!!
    }

    fun clearInstance() {
        db = null
    }
}