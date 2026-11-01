package com.calendarietto.app.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.calendarietto.app.MainActivity
import com.calendarietto.app.R
import com.calendarietto.app.data.Mascot
import com.calendarietto.app.data.Prefs

object Notifications {
    const val CH_ALARM = "sveglie"
    const val CH_MORNING = "mattino"
    const val ID_ALARM = 1
    const val ID_MORNING = 2
    const val ID_MISSED = 3

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun ensureChannels(ctx: Context) {
        val alarm = NotificationChannel(CH_ALARM, "Sveglie degli appuntamenti", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Suona a schermo intero prima di ogni appuntamento"
            setSound(null, null) // il suono lo fa l'app, in loop
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val morning = NotificationChannel(CH_MORNING, "Riepilogo del mattino", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Gli impegni della giornata, ogni mattina"
        }
        nm(ctx).createNotificationChannels(listOf(alarm, morning))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canFullScreen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || nm(ctx).canUseFullScreenIntent()

    private fun accent(ctx: Context): Int =
        if (Prefs(ctx).mascot == Mascot.ROBOT) 0xFF4A5AA8.toInt() else 0xFFB79CED.toInt()

    fun alarmNotification(ctx: Context, payload: AlarmPayload): Notification {
        ensureChannels(ctx)
        val full = PendingIntent.getActivity(
            ctx, 10,
            payload.toIntent(Intent(ctx, AlarmActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getService(
            ctx, 11, payload.toIntent(Intent(ctx, AlarmService::class.java).setAction(AlarmService.ACTION_SNOOZE)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            ctx, 12, Intent(ctx, AlarmService::class.java).setAction(AlarmService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val emoji = if (Prefs(ctx).mascot == Mascot.ROBOT) "🤖" else "🦄"
        return NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent(ctx))
            .setContentTitle("$emoji ${payload.title}")
            .setContentText(payload.subtitle)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "💤 Posticipa", snooze)
            .addAction(0, "✨ Ho capito!", stop)
            .build()
    }

    fun showAlarmFallback(ctx: Context, payload: AlarmPayload) {
        if (canPost(ctx)) nm(ctx).notify(ID_ALARM, alarmNotification(ctx, payload))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 20, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun showMorning(ctx: Context, lines: List<String>) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val prefs = Prefs(ctx)
        val emoji = if (prefs.mascot == Mascot.ROBOT) "🤖" else "🦄"
        val hello = if (prefs.name.isBlank()) "Buongiorno!" else "Buongiorno ${prefs.name}!"
        val text = when (lines.size) {
            0 -> "Oggi niente impegni: giornata tutta per te ☁️"
            1 -> "Oggi hai 1 impegno 💕"
            else -> "Oggi hai ${lines.size} impegni 💕"
        }
        val style = NotificationCompat.InboxStyle().setSummaryText(text)
        lines.take(7).forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(ctx, CH_MORNING)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent(ctx))
            .setContentTitle("$emoji $hello")
            .setContentText(text)
            .setStyle(if (lines.isEmpty()) NotificationCompat.BigTextStyle().bigText(text) else style)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(ID_MORNING, n)
    }

    fun showMissed(ctx: Context, payload: AlarmPayload) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_MORNING)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent(ctx))
            .setContentTitle("Sveglia non spenta: ${payload.title}")
            .setContentText(payload.subtitle)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        nm(ctx).notify(ID_MISSED, n)
    }

    fun cancelAlarm(ctx: Context) = nm(ctx).cancel(ID_ALARM)
}
