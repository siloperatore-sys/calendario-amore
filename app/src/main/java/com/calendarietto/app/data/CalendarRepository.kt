package com.calendarietto.app.data

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val color: Int,
    val canWrite: Boolean,
)

data class EventItem(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?,
    val calendarName: String,
) {
    val key: String get() = "$eventId:$begin"

    /** Giorno di inizio (gli eventi "tutto il giorno" sono salvati in UTC). */
    fun startDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    fun occursOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        return if (allDay) {
            val s = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            val e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
            !day.isBefore(s) && (day.isBefore(e) || s == e && day == s)
        } else {
            val ds = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val de = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            begin < de && (end > ds || begin >= ds)
        }
    }
}

/**
 * Legge e scrive il calendario del telefono: contiene tutti gli account Google sincronizzati
 * e i calendari condivisi, e le modifiche tornano su Google Calendar da sole.
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = mutableListOf<CalendarInfo>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.VISIBLE} = 1", null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendario",
                        account = c.getString(2) ?: "",
                        accountType = c.getString(3) ?: "",
                        color = c.getInt(4),
                        canWrite = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun events(fromMillis: Long, toMillis: Long, hidden: Set<Long> = emptySet()): List<EventItem> {
        if (!hasPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val out = mutableListOf<EventItem>()
        try {
            context.contentResolver.query(
                uri, projection,
                "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val calId = c.getLong(1)
                    if (calId in hidden) continue
                    out += EventItem(
                        eventId = c.getLong(0),
                        calendarId = calId,
                        title = c.getString(2)?.takeIf { it.isNotBlank() } ?: "(senza titolo)",
                        begin = c.getLong(3),
                        end = c.getLong(4),
                        allDay = c.getInt(5) == 1,
                        color = c.getInt(6),
                        location = c.getString(7)?.takeIf { it.isNotBlank() },
                        calendarName = c.getString(8) ?: "",
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun insertEvent(calendar: CalendarInfo, title: String, startMillis: Long, endMillis: Long, allDay: Boolean): Long? {
        if (!hasPermission()) return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.id)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            requestSync(calendar)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun deleteEvent(eventId: Long, calendar: CalendarInfo?): Boolean = try {
        val n = context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null,
        )
        calendar?.let { requestSync(it) }
        n > 0
    } catch (_: Exception) {
        false
    }

    /** Chiede ad Android di sincronizzare subito con Google. */
    fun requestSync(calendar: CalendarInfo) {
        try {
            val extras = Bundle().apply {
                putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            android.content.ContentResolver.requestSync(
                Account(calendar.account, calendar.accountType), CalendarContract.AUTHORITY, extras,
            )
        } catch (_: Exception) {
        }
    }

    fun syncAll() {
        calendars().distinctBy { it.account to it.accountType }.forEach { requestSync(it) }
    }
}
