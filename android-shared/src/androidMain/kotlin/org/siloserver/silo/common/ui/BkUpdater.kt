package org.siloserver.silo.common.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * BK fork: checks this fork's GitHub releases on launch and, when a newer
 * build exists, offers a one-press update. The APK is streamed straight into
 * Android's PackageInstaller (no storage permission or FileProvider needed);
 * Android then shows its normal install confirmation.
 *
 * Builds are compared by the build number that CI stamps into the app and
 * into each release's notes ("... build N ...").
 */
object BkUpdater {
    private const val REPO = "Dj3mbe/silo-android"
    private const val PREFS = "bk_updater"
    private const val KEY_DECLINED_AT = "declined_at"
    private const val QUIET_AFTER_DECLINE_MS = 12L * 60L * 60L * 1000L

    @Volatile
    private var checkedThisProcess = false

    /** Call once from the main Activity's onCreate. Safe to call repeatedly. */
    fun checkOnLaunch(activity: Activity, currentBuild: String, apkName: String) {
        if (checkedThisProcess) return
        checkedThisProcess = true
        val current = currentBuild.toIntOrNull() ?: return
        // Unstamped local builds report 0; never prompt those.
        if (current <= 0) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val declinedAt = prefs.getLong(KEY_DECLINED_AT, 0L)
        if (System.currentTimeMillis() - declinedAt < QUIET_AFTER_DECLINE_MS) return

        Thread {
            val latest = fetchLatestBuild()
            if (latest != null && latest > current) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showPrompt(activity, latest, apkName)
                    }
                }
            }
        }.start()
    }

    private fun fetchLatestBuild(): Int? {
        return try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "BK-Updater")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val notes = JSONObject(body).optString("body", "")
            Regex("build (\\d+)").find(notes)?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun showPrompt(activity: Activity, latest: Int, apkName: String) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Update available")
            .setMessage("A newer version of BK is ready (build $latest). Update now?")
            .setPositiveButton("Update now") { _, _ -> startUpdate(activity, apkName) }
            .setNegativeButton("Later") { _, _ ->
                prefs.edit().putLong(KEY_DECLINED_AT, System.currentTimeMillis()).apply()
            }
            .setOnCancelListener {
                prefs.edit().putLong(KEY_DECLINED_AT, System.currentTimeMillis()).apply()
            }
            .show()
    }

    private fun startUpdate(activity: Activity, apkName: String) {
        val progress = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Updating BK")
            .setMessage("Downloading... 0%")
            .setCancelable(false)
            .create()
        progress.show()

        Thread {
            val installer = activity.packageManager.packageInstaller
            var sessionId = -1
            try {
                val conn = URL("https://github.com/$REPO/releases/latest/download/$apkName")
                    .openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "BK-Updater")
                conn.connect()
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong

                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (total > 0) params.setSize(total)
                sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)
                try {
                    session.openWrite("bk-update.apk", 0, if (total > 0) total else -1).use { out ->
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct >= lastPct + 5) {
                                        lastPct = pct
                                        activity.runOnUiThread { progress.setMessage("Downloading... $pct%") }
                                    }
                                }
                            }
                        }
                        session.fsync(out)
                    }
                    val intent = Intent(activity, BkUpdateReceiver::class.java)
                    var flags = PendingIntent.FLAG_UPDATE_CURRENT
                    if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
                    val pending = PendingIntent.getBroadcast(activity, sessionId, intent, flags)
                    session.commit(pending.intentSender)
                } finally {
                    session.close()
                }
                activity.runOnUiThread { progress.dismiss() }
            } catch (e: Exception) {
                if (sessionId >= 0) {
                    try {
                        installer.abandonSession(sessionId)
                    } catch (ignored: Exception) {
                    }
                }
                activity.runOnUiThread {
                    progress.dismiss()
                    Toast.makeText(
                        activity,
                        "Update failed. Please try again later.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
    }
}
