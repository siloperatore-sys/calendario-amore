package com.calendarietto.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.calendarietto.app.data.Prefs

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_EVENT -> {
                val due = AlarmScheduler.dueEvents(context)
                if (due.isNotEmpty()) {
                    Prefs(context).markFired(due.map { it.key }, System.currentTimeMillis())
                    ring(context, AlarmScheduler.payloadFor(due))
                }
                AlarmScheduler.scheduleNextEvent(context)
            }
            AlarmScheduler.ACTION_RING_NOW -> ring(context, AlarmPayload.from(intent))
            AlarmScheduler.ACTION_MORNING -> {
                Notifications.showMorning(context, AlarmScheduler.morningLines(context))
                AlarmScheduler.scheduleMorning(context)
            }
        }
    }

    private fun ring(context: Context, payload: AlarmPayload) {
        val i = payload.toIntent(Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_RING))
        try {
            ContextCompat.startForegroundService(context, i)
        } catch (_: Exception) {
            // Se Android blocca il servizio, almeno la notifica a schermo intero arriva.
            Notifications.showAlarmFallback(context, payload)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
        AlarmScheduler.startPeriodicRefresh(context)
        ChangeWatcher.schedule(context)
    }
}
