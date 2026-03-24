package com.fieldtag

import android.content.Context
import java.io.File

object FieldTagTestUtils {

    /** Clears FieldTag preferences DataStore so first-launch welcome is shown again. */
    fun clearPreferencesDataStore(context: Context) {
        val dir = File(context.filesDir, "datastore")
        if (dir.exists()) dir.deleteRecursively()
    }
}
