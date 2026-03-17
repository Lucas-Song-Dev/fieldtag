package com.fieldtag.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fieldtag.data.db.FieldTagDatabase
import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.dao.MediaDao
import com.fieldtag.data.db.dao.PidDocumentDao
import com.fieldtag.data.db.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FieldTagDatabase {
        return Room.databaseBuilder(
            context,
            FieldTagDatabase::class.java,
            FieldTagDatabase.DATABASE_NAME,
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    @Provides fun provideProjectDao(db: FieldTagDatabase): ProjectDao = db.projectDao()
    @Provides fun providePidDocumentDao(db: FieldTagDatabase): PidDocumentDao = db.pidDocumentDao()
    @Provides fun provideInstrumentDao(db: FieldTagDatabase): InstrumentDao = db.instrumentDao()
    @Provides fun provideMediaDao(db: FieldTagDatabase): MediaDao = db.mediaDao()
}
