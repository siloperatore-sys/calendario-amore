package com.calendarietto.app.alarm

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.calendarietto.app.data.CalendarRepository
import com.calendarietto.app.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration

/** Un impegno così com'era all'ultimo controllo. */
data class EventSnap(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val rrule: String,
) {
    val signature: String get() = listOf(title, start, end, allDay, location, rrule).joinToString("|")
}

enum class ChangeKind { ADDED, CHANGED, REMOVED }

data class EventChange(val kind: ChangeKind, val event: EventSnap, val calendarName: String)

/**
 * Si accorge quando un impegno viene aggiunto, modificato o cancellato
 * (anche dall'altra persona, quando arriva la sincronizzazione da Google) e manda una notifica.
 */
object ChangeWatcher {
    private const val FILE = "snapshot.json"
    private const val WORK = "calendar-changes"
    private val lock = Any()

    /** Resta in ascolto delle modifiche al calendario, anche ad app chiusa. */
    fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(4))
            .setTriggerContentMaxDelay(Duration.ofSeconds(30))
            .build()
        val req = OneTimeWorkRequestBuilder<ChangeWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, policy, req)
    }

    private fun readEvents(ctx: Context): Pair<Map<Long, EventSnap>, Map<Long, String>>? {
        val repo = CalendarRepository(ctx)
        if (!repo.hasPermission()) return null
        val prefs = Prefs(ctx)
        val hidden = prefs.hiddenCalendars
        val cals = repo.calendars().filter { it.id !in hidden }.associate { it.id to it.name }
        val since = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        val out = HashMap<Long, EventSnap>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.RRULE,
        )
        try {
            ctx.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection,
                "${CalendarContract.Events.DELETED} = 0 AND (${CalendarContract.Events.DTSTART} >= ? OR ${CalendarContract.Events.RRULE} IS NOT NULL)",
                arrayOf(since.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val calId = c.getLong(1)
                    if (calId !in cals) continue
                    val e = EventSnap(
                        id = c.getLong(0),
                        calendarId = calId,
                        title = c.getString(2) ?: "",
                        start = c.getLong(3),
                        end = if (c.isNull(4)) 0L else c.getLong(4),
                        allDay = c.getInt(5) == 1,
                        location = c.getString(6) ?: "",
                        rrule = c.getString(7) ?: "",
                    )
                    out[e.id] = e
                }
            }
        } catch (_: Exception) {
            return null
        }
        return out to cals
    }

    private fun load(ctx: Context): Pair<Map<Long, EventSnap>, Set<Long>>? {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val cals = HashSet<Long>()
            val ca = o.getJSONArray("cals")
            for (i in 0 until ca.length()) cals += ca.getLong(i)
            val evs = HashMap<Long, EventSnap>()
            val ea = o.getJSONArray("events")
            for (i in 0 until ea.length()) {
                val e = ea.getJSONObject(i)
                val s = EventSnap(
                    e.getLong("id"), e.getLong("cal"), e.optString("t"), e.getLong("s"), e.optLong("e"),
                    e.optBoolean("a"), e.optString("l"), e.optString("r"),
                )
                evs[s.id] = s
            }
            evs to cals
        } catch (_: Exception) {
            null
        }
    }

    private fun save(ctx: Context, events: Map<Long, EventSnap>, cals: Set<Long>) {
        val ea = JSONArray()
        events.values.forEach { e ->
            ea.put(
                JSONObject().put("id", e.id).put("cal", e.calendarId).put("t", e.title).put("s", e.start)
                    .put("e", e.end).put("a", e.allDay).put("l", e.location).put("r", e.rrule),
            )
        }
        val o = JSONObject().put("cals", JSONArray(cals.toList())).put("events", ea)
        File(ctx.filesDir, FILE).writeText(o.toString())
    }

    /**
     * Confronta il calendario con l'ultima volta.
     * notify = false si usa dopo una modifica fatta da questo telefono, per non avvisare sé stessi.
     */
    fun check(ctx: Context, notify: Boolean = true): List<EventChange> {
        synchronized(lock) { return checkLocked(ctx, notify) }
    }

    private fun checkLocked(ctx: Context, notify: Boolean): List<EventChange> {
        val now = readEvents(ctx) ?: return emptyList()
        val (events, cals) = now
        val before = load(ctx)
        save(ctx, events, cals.keys)
        if (before == null || !notify || !Prefs(ctx).changeNotify) return emptyList()
        val (old, oldCals) = before
        val changes = mutableListOf<EventChange>()
        for ((id, e) in events) {
            if (e.calendarId !in oldCals) continue // calendario appena collegato: niente raffica di avvisi
            val prev = old[id]
            when {
                prev == null -> changes += EventChange(ChangeKind.ADDED, e, cals[e.calendarId] ?: "")
                prev.signature != e.signature -> changes += EventChange(ChangeKind.CHANGED, e, cals[e.calendarId] ?: "")
            }
        }
        val windowStart = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        for ((id, e) in old) {
            if (id in events || e.calendarId !in cals) continue
            if (e.rrule.isBlank() && e.start < windowStart) continue // solo uscito dalla finestra di date
            changes += EventChange(ChangeKind.REMOVED, e, cals[e.calendarId] ?: "")
        }
        if (changes.isNotEmpty()) Notifications.showChanges(ctx, changes)
        return changes
    }
}

class ChangeWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        try {
            ChangeWatcher.check(applicationContext)
            AlarmScheduler.rescheduleAll(applicationContext)
        } finally {
            // Si rimette in ascolto per la prossima modifica.
            ChangeWatcher.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
