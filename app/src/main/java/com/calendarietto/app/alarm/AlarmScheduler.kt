package com.calendarietto.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.calendarietto.app.MainActivity
import com.calendarietto.app.data.CalendarRepository
import com.calendarietto.app.data.EventItem
import com.calendarietto.app.data.Prefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Cosa mostrare quando suona la sveglia. */
data class AlarmPayload(val title: String, val subtitle: String) {
    fun toIntent(intent: Intent): Intent = intent.putExtra(EXTRA_TITLE, title).putExtra(EXTRA_SUBTITLE, subtitle)

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        fun from(intent: Intent?): AlarmPayload = AlarmPayload(
            intent?.getStringExtra(EXTRA_TITLE) ?: "Promemoria",
            intent?.getStringExtra(EXTRA_SUBTITLE) ?: "",
        )
    }
}

object AlarmScheduler {
    const val ACTION_EVENT = "com.calendarietto.app.action.EVENT"
    const val ACTION_MORNING = "com.calendarietto.app.action.MORNING"
    const val ACTION_RING_NOW = "com.calendarietto.app.action.RING_NOW"

    private const val RC_EVENT = 100
    private const val RC_MORNING = 200
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    fun rescheduleAll(ctx: Context) {
        scheduleNextEvent(ctx)
        scheduleMorning(ctx)
    }

    fun startPeriodicRefresh(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private fun broadcast(ctx: Context, action: String, rc: Int, extras: (Intent) -> Intent = { it }): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, rc, extras(Intent(ctx, AlarmReceiver::class.java).setAction(action)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun setAlarm(ctx: Context, time: Long, pi: PendingIntent, showAsAlarmClock: Boolean) {
        val am = am(ctx)
        try {
            if (canExact(ctx)) {
                if (showAsAlarmClock) {
                    val show = PendingIntent.getActivity(
                        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    private fun candidates(ctx: Context, now: Long): List<EventItem> {
        val prefs = Prefs(ctx)
        val fired = prefs.firedKeys()
        val silent = prefs.silentCalendars
        return CalendarRepository(ctx)
            .events(now, now + 8L * 24 * 60 * 60 * 1000, prefs.hiddenCalendars)
            .filter { !it.allDay && it.calendarId !in silent && it.key !in fired }
    }

    /** Eventi per cui è ora di suonare. */
    fun dueEvents(ctx: Context, now: Long = System.currentTimeMillis()): List<EventItem> {
        val offset = Prefs(ctx).alarmMinutesBefore * 60_000L
        return candidates(ctx, now - 60_000L).filter { it.begin > now - 60_000L && it.begin - offset <= now + 60_000L }
    }

    fun scheduleNextEvent(ctx: Context) {
        val prefs = Prefs(ctx)
        val pi = broadcast(ctx, ACTION_EVENT, RC_EVENT)
        val now = System.currentTimeMillis()
        if (!prefs.alarmsEnabled) {
            am(ctx).cancel(pi)
            return
        }
        val offset = prefs.alarmMinutesBefore * 60_000L
        val next = candidates(ctx, now)
            .filter { it.begin > now }
            .minOfOrNull { maxOf(it.begin - offset, now + 2_000L) }
        if (next == null) {
            am(ctx).cancel(pi)
        } else {
            setAlarm(ctx, next, pi, showAsAlarmClock = true)
        }
    }

    fun scheduleMorning(ctx: Context) {
        val prefs = Prefs(ctx)
        val pi = broadcast(ctx, ACTION_MORNING, RC_MORNING)
        if (!prefs.morningEnabled) {
            am(ctx).cancel(pi)
            return
        }
        val zone = ZoneId.systemDefault()
        val nowLdt = LocalDateTime.now(zone)
        var at = nowLdt.toLocalDate().atTime(prefs.morningHour, prefs.morningMinute)
        if (!at.isAfter(nowLdt)) at = at.plusDays(1)
        setAlarm(ctx, at.atZone(zone).toInstant().toEpochMilli(), pi, showAsAlarmClock = false)
    }

    /** Ripete la sveglia tra qualche minuto. */
    fun scheduleSnooze(ctx: Context, payload: AlarmPayload, minutes: Int) {
        val pi = broadcast(ctx, ACTION_RING_NOW, 300 + (payload.title.hashCode() and 0xFFFF)) { payload.toIntent(it) }
        setAlarm(ctx, System.currentTimeMillis() + minutes * 60_000L, pi, showAsAlarmClock = true)
    }

    /** Sveglia di prova tra 5 secondi. */
    fun scheduleTest(ctx: Context) {
        val payload = AlarmPayload("Sveglia di prova ✨", "Se mi senti, funziona tutto!")
        val pi = broadcast(ctx, ACTION_RING_NOW, 299) { payload.toIntent(it) }
        setAlarm(ctx, System.currentTimeMillis() + 5_000L, pi, showAsAlarmClock = false)
    }

    fun payloadFor(events: List<EventItem>): AlarmPayload {
        val first = events.first()
        val time = Instant.ofEpochMilli(first.begin).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
        val title = if (events.size > 1) "${first.title} (+${events.size - 1})" else first.title
        val extra = first.location ?: first.calendarName
        return AlarmPayload(title, listOf("alle $time", extra).filter { it.isNotBlank() }.joinToString(" · "))
    }

    fun morningLines(ctx: Context): List<String> {
        val prefs = Prefs(ctx)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return CalendarRepository(ctx).events(from, to, prefs.hiddenCalendars)
            .filter { it.occursOn(today, zone) }
            .sortedWith(compareBy({ !it.allDay }, { it.begin }))
            .map { e ->
                if (e.allDay) "🌈 ${e.title}"
                else "⏰ ${Instant.ofEpochMilli(e.begin).atZone(zone).toLocalTime().format(timeFmt)}  ${e.title}"
            }
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        AlarmScheduler.rescheduleAll(applicationContext)
        return Result.success()
    }
}
