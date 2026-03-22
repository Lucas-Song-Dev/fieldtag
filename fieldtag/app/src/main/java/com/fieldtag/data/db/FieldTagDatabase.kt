package com.fieldtag.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pid_documents ADD COLUMN calibration_width REAL")
                db.execSQL("ALTER TABLE pid_documents ADD COLUMN calibration_height REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pid_documents ADD COLUMN calibration_shape TEXT NOT NULL DEFAULT 'RECTANGLE'")
                db.execSQL("ALTER TABLE instruments ADD COLUMN overlay_shape TEXT")
            }
        }
    }
}
