package com.fieldtag.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.dao.MediaDao
import com.fieldtag.data.db.dao.PidDocumentDao
import com.fieldtag.data.db.dao.ProjectDao
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.data.db.entities.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        PidDocumentEntity::class,
        InstrumentEntity::class,
        MediaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FieldTagDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pidDocumentDao(): PidDocumentDao
    abstract fun instrumentDao(): InstrumentDao
    abstract fun mediaDao(): MediaDao

    companion object {
        const val DATABASE_NAME = "fieldtag.db"
    }
}
