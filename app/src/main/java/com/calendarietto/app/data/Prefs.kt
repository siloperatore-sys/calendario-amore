package com.calendarietto.app.data

import android.content.Context

enum class Mascot { ROBOT, UNICORN }

/** Impostazioni salvate sul telefono (ognuno ha le sue). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("calendarietto", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var mascot: Mascot
        get() = runCatching { Mascot.valueOf(sp.getString("mascot", Mascot.UNICORN.name)!!) }.getOrDefault(Mascot.UNICORN)
        set(v) = sp.edit().putString("mascot", v.name).apply()

    /** Calendari nascosti nell'app (di default si vede tutto). */
    var hiddenCalendars: Set<Long>
        get() = sp.getStringSet("hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("hidden", v.map { it.toString() }.toSet()).apply()

    /** Calendari che NON fanno suonare la sveglia. */
    var silentCalendars: Set<Long>
        get() = sp.getStringSet("silent", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("silent", v.map { it.toString() }.toSet()).apply()

    var defaultCalendarId: Long
        get() = sp.getLong("defaultCal", -1L)
        set(v) = sp.edit().putLong("defaultCal", v).apply()

    var alarmsEnabled: Boolean
        get() = sp.getBoolean("alarms", true)
        set(v) = sp.edit().putBoolean("alarms", v).apply()

    var alarmMinutesBefore: Int
        get() = sp.getInt("alarmBefore", 15)
        set(v) = sp.edit().putInt("alarmBefore", v).apply()

    var snoozeMinutes: Int
        get() = sp.getInt("snooze", 5)
        set(v) = sp.edit().putInt("snooze", v).apply()

    var morningEnabled: Boolean
        get() = sp.getBoolean("morning", true)
        set(v) = sp.edit().putBoolean("morning", v).apply()

    var morningHour: Int
        get() = sp.getInt("morningH", 8)
        set(v) = sp.edit().putInt("morningH", v).apply()

    var morningMinute: Int
        get() = sp.getInt("morningM", 0)
        set(v) = sp.edit().putInt("morningM", v).apply()

    /** Eventi per cui la sveglia è già suonata: "idEvento:inizio". */
    fun firedKeys(): Set<String> = sp.getStringSet("fired", emptySet())!!.toSet()

    fun markFired(keys: Collection<String>, now: Long) {
        val keep = firedKeys().filter { k ->
            val begin = k.substringAfter(':').toLongOrNull() ?: 0L
            begin > now - 3L * 24 * 60 * 60 * 1000
        }.toMutableSet()
        keep.addAll(keys)
        sp.edit().putStringSet("fired", keep).apply()
    }
}
