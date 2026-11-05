package org.siloserver.silo.common.ui

import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

/**
 * BK fork: checks this fork's GitHub releases on launch and, when a newer
 * build exists, offers a one-press update. The APK is streamed straight into
 * Android's PackageInstaller (no storage permission or FileProvider needed);
 * Android then shows its normal install confirmation.
 *
 * Builds are compared by BK's own build code, which is in the app's versionCode
 * and in each release's notes ("... code N ..."). It always rises, whatever
 * Silo's own version and build numbers do.
 */
object BkUpdater {
    private const val REPO = "Dj3mbe/silo-android"
    private const val PREFS = "bk_updater"
    private const val KEY_DECLINED_AT = "declined_at"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val QUIET_AFTER_DECLINE_MS = 12L * 60L * 60L * 1000L
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    @Volatile
    private var callbacksRegistered = false

    @Volatile
    private var promptShowing = false

    @Volatile
    private var checkInFlight = false

    @Volatile
    private var firstCheckDone = false

    @Volatile
    private var mainActivity: WeakReference<Activity>? = null

    @Volatile
    private var currentBuildNumber = 0

    @Volatile
    private var apkFileName = ""

    /** Set by [BkUpdateReceiver] when Android reports back on an install. */
    @Volatile
    var installResultSeen = false

    /**
     * Opens Android's install confirmation from the app's own screen when we can
     * (more reliable on Fire TV than starting it from the background receiver).
     */
    fun launchConfirm(context: Context, confirm: Intent) {
        val screen = mainActivity?.get()
        try {
            if (screen != null && !screen.isFinishing && !screen.isDestroyed) {
                screen.startActivity(confirm)
            } else {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open the installer.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Call from the main Activity's onCreate. Checks now, and again whenever the
     * app returns to the foreground (at most once every 12 hours), so devices that
     * never fully restart the app, like a Fire TV Stick, still get prompted.
     */
    fun checkOnLaunch(activity: Activity, versionCode: Int, apkName: String) {
        // The app's versionCode is our build code * 2 (+1 on TV); undo that.
        val current = versionCode / 2
        // Local, unstamped builds have a tiny code; never prompt those.
        if (current < 110_000_000) return
        currentBuildNumber = current
        apkFileName = apkName
        mainActivity = WeakReference(activity)
        if (!callbacksRegistered) {
            callbacksRegistered = true
            activity.application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(a: Activity) {
                        if (a === mainActivity?.get()) maybeCheck(a)
                    }

                    override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {}
                    override fun onActivityStarted(a: Activity) {}
                    override fun onActivityPaused(a: Activity) {}
                    override fun onActivityStopped(a: Activity) {}
                    override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
                    override fun onActivityDestroyed(a: Activity) {}
                },
            )
        }
        maybeCheck(activity)
    }

    private fun maybeCheck(activity: Activity) {
        if (promptShowing || checkInFlight) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_DECLINED_AT, 0L) < QUIET_AFTER_DECLINE_MS) return
        // The first check after the app starts always runs; later checks (the app
        // coming back to the front) wait for the interval.
        val forced = !firstCheckDone
        if (!forced && now - prefs.getLong(KEY_CHECKED_AT, 0L) < CHECK_INTERVAL_MS) return
        firstCheckDone = true
        checkInFlight = true
        val current = currentBuildNumber
        val apkName = apkFileName

        Thread {
            val latest = fetchLatest()
            checkInFlight = false
            // Only a successful answer counts as a check, so a failure retries
            // the next time the app is opened.
            if (latest != null) {
                prefs.edit().putLong(KEY_CHECKED_AT, System.currentTimeMillis()).apply()
            }
            if (latest != null && latest.code > current) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed && !promptShowing) {
                        showPrompt(activity, latest.label, apkName)
                    }
                }
            }
        }.start()
    }

    private class LatestRelease(val code: Int, val label: String)

    private fun fetchLatest(): LatestRelease? {
        return try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "BK-Updater")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val notes = json.optString("body", "")
            val code = Regex("code (\\d+)").find(notes)?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
            LatestRelease(code, json.optString("name", "").trim())
        } catch (e: Exception) {
            null
        }
    }

    private fun showPrompt(activity: Activity, label: String, apkName: String) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        promptShowing = true
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Update available")
            .setMessage(
                if (label.isBlank()) {
                    "A newer version of BK is ready. Update now?"
                } else {
                    "A newer version of BK is ready ($label). Update now?"
                },
            )
            .setPositiveButton("Update now") { _, _ -> startUpdate(activity, apkName) }
            .setNegativeButton("Later") { _, _ ->
                prefs.edit().putLong(KEY_DECLINED_AT, System.currentTimeMillis()).apply()
            }
            .setOnCancelListener {
                prefs.edit().putLong(KEY_DECLINED_AT, System.currentTimeMillis()).apply()
            }
            .setOnDismissListener { promptShowing = false }
            .show()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun askForInstallPermission(activity: Activity) {
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Allow BK to install updates")
            .setMessage(
                "To update, turn on \"Install unknown apps\" for BK. Press Open settings and switch BK on. " +
                    "If that screen does not open: Settings > My Fire TV > Developer options > " +
                    "Install unknown apps > BK. Then open the app and press Update now again.",
            )
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.packageName),
                            ),
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        activity,
                        "Open Settings > My Fire TV > Developer options > Install unknown apps > BK.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startUpdate(activity: Activity, apkName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            askForInstallPermission(activity)
            return
        }
        installResultSeen = false
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
                activity.runOnUiThread {
                    progress.dismiss()
                    // If Android never answers, say so instead of failing silently.
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!installResultSeen) {
                            Toast.makeText(
                                activity,
                                "The install did not start. Check Install unknown apps for BK, then try again.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }, 25_000L)
                }
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
