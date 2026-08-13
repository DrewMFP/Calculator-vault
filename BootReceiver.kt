package com.calculator.vault.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calculator.vault.services.VaultBackgroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart the vault's background service after reboot so
            // scheduled notifications keep working.
            VaultBackgroundService.start(context)
        }
    }
}
