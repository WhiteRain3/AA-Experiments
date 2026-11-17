package io.github.highwindbr.aaxp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Receives reports from the module running inside Android Auto: a notice that crash safe mode
 * switched the look off, and descriptions of the on-screen layout. It only accepts callers that
 * run as the Android Auto app, stores everything on this phone, and sends nothing anywhere.
 */
class ModuleReportProvider : ContentProvider() {
    private fun requireAndroidAuto(context: Context) {
        val callers = context.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (AA_PACKAGE !in callers) throw SecurityException("Only Android Auto may report here")
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        requireAndroidAuto(context)
        if (method == "safe_mode") {
            preferences(context).edit()
                .putString(KEY_SAFE_PROCESS, arg.orEmpty().take(120))
                .putLong(KEY_SAFE_TIME, System.currentTimeMillis()).apply()
        }
        if (method == "restart_ack") preferences(context).edit().putString(KEY_RESTART_ACK, arg.orEmpty().take(120)).apply()
        return null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        requireAndroidAuto(context)
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "dump") throw SecurityException("Unknown report")
        val name = segments[1].replace(Regex("[^A-Za-z0-9_.-]"), "_").take(120)
        val directory = dumpDirectory(context).apply { mkdirs() }
        return ParcelFileDescriptor.open(File(directory, "layout_$name.txt"),
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val AA_PACKAGE = "com.google.android.projection.gearhead"
        const val KEY_SAFE_PROCESS = "safe_mode_process"
        const val KEY_SAFE_TIME = "safe_mode_time"
        const val KEY_RESTART_ACK = "restart_ack"
        /** Dumps larger than this are not a layout description. */
        const val MAX_DUMP_BYTES = 4L * 1024 * 1024

        fun preferences(context: Context) = context.getSharedPreferences("module_reports", Context.MODE_PRIVATE)
        fun dumpDirectory(context: Context) = File(context.filesDir, "reports")
    }
}
