package org.siloserver.silo.common.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * BK fork: receives the PackageInstaller result for [BkUpdater]. Android asks
 * for the user's confirmation through the intent it attaches; this forwards it.
 */
class BkUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BkUpdater.installResultSeen = true
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) BkUpdater.launchConfirm(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Toast.makeText(context, "The update could not be installed.", Toast.LENGTH_LONG).show()
        }
    }
}
