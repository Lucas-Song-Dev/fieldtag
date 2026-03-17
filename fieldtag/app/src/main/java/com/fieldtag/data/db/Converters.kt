package com.fieldtag.data.db

import androidx.room.TypeConverter
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.MediaRole
import com.fieldtag.data.db.entities.MediaSource
import com.fieldtag.data.db.entities.MediaType
import com.fieldtag.data.db.entities.ParseStatus
import com.fieldtag.data.db.entities.ProjectStatus

class Converters {

    @TypeConverter fun projectStatusToString(value: ProjectStatus): String = value.name
    @TypeConverter fun stringToProjectStatus(value: String): ProjectStatus = ProjectStatus.valueOf(value)

    @TypeConverter fun parseStatusToString(value: ParseStatus): String = value.name
    @TypeConverter fun stringToParseStatus(value: String): ParseStatus = ParseStatus.valueOf(value)

    @TypeConverter fun fieldStatusToString(value: FieldStatus): String = value.name
    @TypeConverter fun stringToFieldStatus(value: String): FieldStatus = FieldStatus.valueOf(value)

    @TypeConverter fun mediaTypeToString(value: MediaType): String = value.name
    @TypeConverter fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter fun mediaRoleToString(value: MediaRole): String = value.name
    @TypeConverter fun stringToMediaRole(value: String): MediaRole = MediaRole.valueOf(value)

    @TypeConverter fun mediaSourceToString(value: MediaSource): String = value.name
    @TypeConverter fun stringToMediaSource(value: String): MediaSource = MediaSource.valueOf(value)
}
