package com.example.btcwidget.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.btcwidget.MainActivity
import com.example.btcwidget.PriceData
import com.example.btcwidget.R

/** Evaluates alerts after each fetch and posts notifications for new crossings. */
object AlertNotifier {
    private const val CHANNEL_ID = "price_alerts"

    fun checkAndNotify(context: Context, data: PriceData) {
        val rules = AlertStore.load(context)
        if (rules.none { it.enabled }) return
        val result = PriceAlerts.check(rules, data, AlertStore.loadWasMet(context))
        AlertStore.saveWasMet(context, result.nowMet)
        result.toNotify.forEach { notify(context, it, data) }
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notify(context: Context, rule: AlertRule, data: PriceData) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(PriceAlerts.title(rule))
            .setContentText(PriceAlerts.body(rule, data))
            .setStyle(NotificationCompat.BigTextStyle().bigText(PriceAlerts.body(rule, data)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            // One notification per alert kind; a new crossing replaces the old one.
            NotificationManagerCompat.from(context).notify(rule.kind.ordinal + 100, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Price alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "BTC price and fee alerts you set in Block"
            }
        )
    }
}
