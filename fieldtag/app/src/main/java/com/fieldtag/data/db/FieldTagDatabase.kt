package com.fieldtag.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fieldtag.data.db.dao.InstrumentDao
import com.fieldtag.data.db.dao.MediaDao
import com.fieldtag.data.db.dao.PidDocumentDao
import com.fieldtag.data.db.dao.PidPageCalibrationDao
import com.fieldtag.data.db.dao.ProjectDao
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.data.db.entities.PidPageCalibrationEntity
import com.fieldtag.data.db.entities.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        PidDocumentEntity::class,
        InstrumentEntity::class,
        MediaEntity::class,
        PidPageCalibrationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FieldTagDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pidDocumentDao(): PidDocumentDao
    abstract fun instrumentDao(): InstrumentDao
    abstract fun mediaDao(): MediaDao
    abstract fun pidPageCalibrationDao(): PidPageCalibrationDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pid_page_calibrations (
                        pid_document_id TEXT NOT NULL,
                        page_number     INTEGER NOT NULL,
                        calibration_width  REAL NOT NULL,
                        calibration_height REAL NOT NULL,
                        calibration_shape  TEXT NOT NULL DEFAULT 'RECTANGLE',
                        PRIMARY KEY (pid_document_id, page_number),
                        FOREIGN KEY (pid_document_id) REFERENCES pid_documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pid_page_calibrations_pid_document_id ON pid_page_calibrations(pid_document_id)",
                )
            }
        }
    }
}
