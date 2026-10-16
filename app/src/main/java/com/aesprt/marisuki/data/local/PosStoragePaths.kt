package com.aesprt.marisuki.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Keeps the POS database and product photos together in app-private storage.
 * Existing databases are copied before Room opens them; the original is retained
 * as a rollback copy if opening or upgrading the new location later fails.
 */
object PosStoragePaths {
    private const val ROOT_DIRECTORY = "posdb"
    private const val LOG_TAG = "PosStoragePaths"

    fun storeDirectory(context: Context): File =
        File(File(context.filesDir, ROOT_DIRECTORY), context.packageName)

    fun databasePath(context: Context, databaseName: String): String {
        val legacy = context.getDatabasePath(databaseName)
        val directory = storeDirectory(context)
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            Log.e(LOG_TAG, "Could not create the POS storage folder; retaining the previous database location.")
            return legacy.absolutePath
        }

        val destination = File(directory, databaseName)
        if (destination.exists() || !legacy.exists()) return destination.absolutePath

        val temporary = File(directory, ".$databaseName.migrating")
        return try {
            checkpointAndClose(legacy)
            temporary.delete()
            FileInputStream(legacy).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            validateDatabase(temporary)
            if (!temporary.renameTo(destination)) throw IOException("Could not finalize the database copy.")
            destination.absolutePath
        } catch (error: Exception) {
            temporary.delete()
            Log.e(LOG_TAG, "Could not move the POS database; retaining the previous database location.", error)
            legacy.absolutePath
        }
    }

    private fun checkpointAndClose(file: File) {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { source ->
            source.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw IOException("The existing database is busy and could not be checkpointed.")
                }
            }
        }
    }

    private fun validateDatabase(file: File) {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    throw IOException("The copied database did not pass its integrity check.")
                }
            }
        }
    }
}
