package com.kumbukaapp.recall.di

import android.content.Context
import com.kumbukaapp.recall.data.db.CallDatabase
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.service.CallDatabaseFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCallDatabase(@ApplicationContext context: Context): CallDatabase {
        // Single shared instance: raw callers use CallDatabaseFactory too.
        return CallDatabaseFactory.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideRecordingRepository(database: CallDatabase): RecordingRepository {
        return RecordingRepository(database)
    }
}
