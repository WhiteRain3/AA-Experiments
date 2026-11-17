package io.github.highwindbr.aaxp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import io.github.highwindbr.aaxp.core.AvailableUpdate
import io.github.highwindbr.aaxp.core.UpdateCheck
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Downloads and installs builds of this fork from its GitHub releases. The system installer
 * asks before installing, and Android itself refuses an APK signed with a different key.
 */
internal object Updater {
    private fun open(url: String): HttpsURLConnection = (URL(url).openConnection() as HttpsURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "AA-Experiments-updater")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    /** The latest published build; null when there is none that can be used. Throws on network errors. */
    fun latest(): AvailableUpdate? {
        // First the release page's redirect, which has no hourly limit; the API only as a fallback.
        runCatching {
            val page = open(UpdateCheck.LATEST_PAGE).apply { instanceFollowRedirects = false }
            try {
                if (page.responseCode in 301..308) UpdateCheck.fromLatestRedirect(page.getHeaderField("Location")) else null
            } finally { page.disconnect() }
        }.getOrNull()?.let { return it }
        val connection = open(UpdateCheck.LATEST_URL)
        try {
            if (connection.responseCode == 404) return null
            check(connection.responseCode != 403 && connection.responseCode != 429) {
                "GitHub's hourly limit for this network is used up; try again later or on Wi-Fi"
            }
            check(connection.responseCode == 200) { "GitHub answered ${connection.responseCode}" }
            return UpdateCheck.parse(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally { connection.disconnect() }
    }

    /** Downloads [update] into the cache and returns the file. [progress] gets 0..100. */
    fun download(context: Context, update: AvailableUpdate, progress: (Int) -> Unit): File {
        val target = File(context.cacheDir, "update.apk")
        val connection = open(update.apkUrl)
        try {
            check(connection.responseCode == 200) { "Download answered ${connection.responseCode}" }
            // GitHub redirects release downloads to its own storage; only https is ever followed.
            check(connection.url.protocol == "https") { "Download left https" }
            // The size comes from the release list when known, otherwise from the download itself.
            val expected = if (update.sizeBytes > 0) update.sizeBytes else connection.contentLengthLong
            var total = 0L
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer); if (read < 0) break
                    total += read
                    check(total <= UpdateCheck.MAX_APK_BYTES) { "Download is too large" }
                    output.write(buffer, 0, read)
                    if (expected > 0) progress((total * 100 / expected).toInt().coerceIn(0, 100))
                }
            } }
            check(total > 0 && (expected <= 0 || total == expected)) { "Download is incomplete" }
            return target
        } finally { connection.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo?): Set<String> {
        val signing = info?.signingInfo
        val list = if (signing != null) {
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else info?.signatures
        return list.orEmpty().map { it.toCharsString() }.toSet()
    }

    /**
     * Why [apk] cannot update this app, or null when it can: it must be this app, and signed
     * with the same key as the installed copy.
     */
    @Suppress("DEPRECATION")
    fun problemWith(context: Context, apk: File): String? {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = manager.getPackageArchiveInfo(apk.path, flags) ?: return "The download is not an installable app."
        if (archive.packageName != context.packageName) return "The download is a different app."
        val installed = signatures(manager.getPackageInfo(context.packageName, flags))
        val incoming = signatures(archive)
        if (incoming.isNotEmpty() && installed.isNotEmpty() && incoming != installed) {
            return "This build is signed with a different key than the installed app, so Android will not update over it. " +
                "Save your look to a file, uninstall AA Experiments once, then install the downloaded build."
        }
        return null
    }

    /** Hands [apk] to the system installer, which asks the user before installing. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateStatusReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's progress: opens its confirmation screen, or reports a failure. */
class UpdateStatusReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Toast.makeText(context, "Update not installed: " +
                (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"), Toast.LENGTH_LONG).show()
        }
    }
}
