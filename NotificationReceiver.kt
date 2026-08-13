package com.calculator.vault.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calculator.vault.managers.NotificationManager

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Vault Reminder"
        val message = intent.getStringExtra("message") ?: ""

        val notificationManager = NotificationManager(context)
        notificationManager.showNotification(title, message)
    }
}
