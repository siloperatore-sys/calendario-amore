package com.calendarietto.app.voice

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Regex con gruppi nominati letti per indice: su Android < 14 Kotlin non supporta groups["nome"].
 */
internal class NRegex(pattern: String) {
    val re = Regex(pattern)
    val idx: Map<String, Int> = run {
        val map = HashMap<String, Int>()
        var count = 0
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\\') { i += 2; continue }
            if (c == '(') {
                if (i + 1 < pattern.length && pattern[i + 1] == '?') {
                    if (i + 3 < pattern.length && pattern[i + 2] == '<' && pattern[i + 3] != '=' && pattern[i + 3] != '!') {
                        count++
                        map[pattern.substring(i + 3, pattern.indexOf('>', i))] = count
                    }
                } else {
                    count++
                }
            }
            i++
        }
        map
    }
}

internal class M(private val m: MatchResult, private val r: NRegex) {
    fun g(name: String): String? = r.idx[name]?.let { m.groups[it]?.value }
}

/** Risultato del riconoscimento di una frase tipo "dentista martedì alle 15". */
data class ParsedEvent(
    val title: String,
    val date: LocalDate,
    /** null = evento di tutto il giorno */
    val time: LocalTime?,
    val durationMinutes: Int,
) {
    val allDay: Boolean get() = time == null
}

/**
 * Trasforma una frase in italiano in un evento.
 * Esempi: "cena da mamma sabato alle 20 e 30", "ricordami di chiamare Luca domani alle 3 del pomeriggio",
 * "compleanno di Giulia il 12 dicembre", "riunione dalle 10 alle 11:30 giovedì", "palestra tra 2 ore per 90 minuti".
 */
object ItalianEventParser {

    private val numberWords = mapOf(
        "uno" to "1", "una" to "1", "un" to "1", "primo" to "1",
        "due" to "2", "tre" to "3", "quattro" to "4", "cinque" to "5", "sei" to "6",
        "sette" to "7", "otto" to "8", "nove" to "9", "dieci" to "10", "undici" to "11",
        "dodici" to "12", "tredici" to "13", "quattordici" to "14", "quindici" to "15",
        "sedici" to "16", "diciassette" to "17", "diciotto" to "18", "diciannove" to "19",
        "venti" to "20", "ventuno" to "21", "ventidue" to "22", "ventitre" to "23",
        "ventiquattro" to "24", "venticinque" to "25", "ventisei" to "26", "ventisette" to "27",
        "ventotto" to "28", "ventinove" to "29", "trenta" to "30", "trentuno" to "31",
        "quaranta" to "40", "quarantacinque" to "45", "cinquanta" to "50",
        "novanta" to "90",
    )

    private val months = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    private val weekdays = mapOf(
        "lunedi" to DayOfWeek.MONDAY, "martedi" to DayOfWeek.TUESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "giovedi" to DayOfWeek.THURSDAY, "venerdi" to DayOfWeek.FRIDAY, "sabato" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY,
    )

    private fun period(name: String) =
        "(?:\\s(?:del|di|della|nel|nella|in)\\s(?<$name>mattina|mattino|pomeriggio|sera|notte))?"

    private val RANGE_RE = NRegex(
        "\\bdalle\\s(?<h1>\\d{1,2})(?:[:.](?<m1>\\d{2}))?\\s(?:alle|fino alle)\\s(?<h2>\\d{1,2})(?:[:.](?<m2>\\d{2}))?" + period("p")
    )
    private val TIME_RE = NRegex(
        "(?:\\b(?:alle|ore|verso le|per le|entro le)\\s(?:ore\\s)?|\\ball')(?<h>\\d{1,2})" +
            "(?:[:.](?<mm>\\d{2})|\\se\\s(?<frac>mezza|mezzo|1 quarto|quarto|3 quarti)\\b|\\se\\s(?<me>\\d{1,2})\\b(?!\\s(?:giorn|settiman|or[ae]\\b|minut)))?" +
            period("p")
    )
    private val BARE_TIME_RE = NRegex("\\b(?<h>\\d{1,2})[:.](?<mm>\\d{2})\\b" + period("p"))
    private val NOON_RE = NRegex("\\b(?:a\\s)?(?<w>mezzogiorno|mezzanotte)\\b")
    private val IN_HOURS_RE = NRegex("\\b(?:tra|fra)\\s(?<n>\\d{1,3})\\s(?<u>ore|ora|minuti|minuto)\\b")
    private val STANDALONE_PERIOD_RE = NRegex("\\b(?:di\\s|del\\s|nel\\s|in\\s)?(?<sp>mattina|mattino|pomeriggio|sera)\\b")

    private val RELATIVE_DAY_RE = NRegex("\\b(?<w>oggi|stasera|stamattina|stanotte|domattina|domani|dopodomani)\\b")
    private val IN_DAYS_RE = NRegex("\\b(?:tra|fra)\\s(?<n>\\d{1,2})\\s(?<u>giorni|giorno|settimane|settimana|mesi|mese)\\b")
    private val WEEKDAY_RE = NRegex(
        "\\b(?:(?:il|la|questo|questa)\\s)?(?:prossim[oa]\\s)?(?<wd>lunedi|martedi|mercoledi|giovedi|venerdi|sabato|domenica)\\b(?:\\sprossim[oa]\\b)?"
    )
    private val MONTH_DATE_RE = NRegex(
        "(?:\\b(?:il|per il|del)\\s|\\bl')?\\b(?<d>\\d{1,2})\\s(?<mon>${months.joinToString("|")})\\b(?:\\s(?<y>\\d{4})\\b)?"
    )
    private val NUM_DATE_RE = NRegex("(?:\\b(?:il|per il|del)\\s|\\bl')?\\b(?<d>\\d{1,2})[/-](?<mn>\\d{1,2})(?:[/-](?<y>\\d{2,4}))?\\b")
    private val DAY_ONLY_RE = NRegex("(?:\\b(?:il|per il)\\s|\\bl')(?<d>\\d{1,2})\\b(?!\\s(?:or[ae]\\b|minut|giorn|settiman|mes))")

    private val DURATION_RE = NRegex(
        "\\bper\\s(?:(?<n>\\d{1,3})\\s(?<u>ore|ora|minuti|minuto|min)(?<half>\\se\\smezz[ao])?|(?<uo>un'ora)(?<half2>\\se\\smezz[ao])?|(?<mo>mezz'ora|mezzora))\\b"
    )
    private val ALL_DAY_RE = NRegex("\\b(?:tutto il giorno|tutta la giornata|giornata intera)\\b")

    private val leadingFiller = setOf(
        "aggiungi", "aggiungere", "aggiungimi", "metti", "mettimi", "segna", "segnami", "segnare",
        "inserisci", "crea", "creami", "fissa", "fissami", "nuovo", "nuova", "ricordami", "ricorda",
        "ricordarmi", "promemoria", "appuntamento", "evento", "un", "una", "uno", "di", "che", "ho",
        "abbiamo", "c'e", "ce", "il", "lo", "la", "l'", "i", "gli", "le", "al", "nel", "in", "calendario",
        "per", "a", "e", "ok", "allora", "ehi", "please",
    )
    private val trailingFiller = setOf(
        "di", "il", "la", "lo", "l'", "alle", "al", "a", "e", "per", "da", "del", "della", "nel",
        "ore", "verso", "entro", "dalle", "tra", "fra", "questo", "questa",
    )

    private fun deaccent(c: Char): Char = when (c) {
        'à', 'á' -> 'a'
        'è', 'é' -> 'e'
        'ì', 'í' -> 'i'
        'ò', 'ó' -> 'o'
        'ù', 'ú' -> 'u'
        else -> c
    }

    private fun normalizeToken(raw: String): String {
        var t = raw.lowercase(Locale.ITALIAN).replace('’', '\'').replace('`', '\'')
        t = t.map { deaccent(it) }.joinToString("")
        t = t.trim('.', ',', ';', '!', '?', '"', '(', ')', '«', '»')
        val apo = t.indexOf('\'')
        return if (apo in 0 until t.length - 1) {
            val head = t.substring(0, apo + 1)
            val tail = t.substring(apo + 1)
            head + (numberWords[tail] ?: tail)
        } else {
            numberWords[t] ?: t
        }
    }

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParsedEvent {
        val orig = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val norm = orig.map { normalizeToken(it) }
        val n = orig.size
        val starts = IntArray(n)
        val sb = StringBuilder()
        norm.forEachIndexed { i, t ->
            if (i > 0) sb.append(' ')
            starts[i] = sb.length
            sb.append(t)
        }
        val s = sb.toString()
        val consumed = BooleanArray(n) { norm[it].isEmpty() }

        fun consume(r: IntRange) {
            for (i in 0 until n) {
                if (norm[i].isEmpty()) continue
                val a = starts[i]
                val b = a + norm[i].length - 1
                if (a <= r.last && b >= r.first) consumed[i] = true
            }
        }

        fun find(re: NRegex, accept: (M) -> Boolean = { true }): M? {
            var m = re.re.find(s)
            while (m != null) {
                val w = M(m, re)
                if (accept(w)) {
                    consume(m.range)
                    return w
                }
                m = m.next()
            }
            return null
        }

        val today = now.toLocalDate()
        var date: LocalDate? = null
        var hour: Int? = null
        var minute = 0
        var periodWord: String? = null
        var hintPeriod: String? = null
        var fixedTime: LocalTime? = null
        var duration: Int? = null

        // --- Orari ---
        val range = find(RANGE_RE) { m -> (m.g("h1")!!.toInt() <= 24) && (m.g("h2")!!.toInt() <= 24) }
        var endHour: Int? = null
        var endMinute = 0
        if (range != null) {
            hour = range.g("h1")!!.toInt()
            minute = range.g("m1")?.toInt() ?: 0
            endHour = range.g("h2")!!.toInt()
            endMinute = range.g("m2")?.toInt() ?: 0
            periodWord = range.g("p")
        } else {
            val t = find(TIME_RE) { m ->
                val h = m.g("h")!!.toInt()
                val me = m.g("mm")?.toInt() ?: m.g("me")?.toInt() ?: 0
                h <= 24 && me < 60
            }
            if (t != null) {
                hour = t.g("h")!!.toInt()
                minute = t.g("mm")?.toInt()
                    ?: t.g("me")?.toInt()
                    ?: when (t.g("frac")) {
                        "mezza", "mezzo" -> 30
                        "quarto", "1 quarto" -> 15
                        "3 quarti" -> 45
                        else -> 0
                    }
                periodWord = t.g("p")
            } else {
                val noon = find(NOON_RE)
                if (noon != null) {
                    fixedTime = if (noon.g("w") == "mezzogiorno") LocalTime.NOON else LocalTime.MIDNIGHT
                } else {
                    val inHours = find(IN_HOURS_RE)
                    if (inHours != null) {
                        val q = inHours.g("n")!!.toLong()
                        val target = if (inHours.g("u")!!.startsWith("or")) now.plusHours(q) else now.plusMinutes(q)
                        date = target.toLocalDate()
                        fixedTime = target.toLocalTime().withSecond(0).withNano(0)
                    } else {
                        val bare = find(BARE_TIME_RE) { m -> m.g("h")!!.toInt() <= 24 && m.g("mm")!!.toInt() < 60 }
                        if (bare != null) {
                            hour = bare.g("h")!!.toInt()
                            minute = bare.g("mm")!!.toInt()
                            periodWord = bare.g("p")
                        }
                    }
                }
            }
        }

        // --- Date ---
        val rel = find(RELATIVE_DAY_RE)
        if (rel != null) {
            when (rel.g("w")) {
                "oggi" -> date = today
                "stasera" -> { date = today; hintPeriod = "sera" }
                "stamattina" -> { date = today; hintPeriod = "mattina" }
                "stanotte" -> { date = today; hintPeriod = "notte" }
                "domattina" -> { date = today.plusDays(1); hintPeriod = "mattina" }
                "domani" -> date = today.plusDays(1)
                "dopodomani" -> date = today.plusDays(2)
            }
        }
        if (date == null) {
            find(IN_DAYS_RE)?.let { m ->
                val q = m.g("n")!!.toLong()
                val u = m.g("u")!!
                date = when {
                    u.startsWith("giorn") -> today.plusDays(q)
                    u.startsWith("settiman") -> today.plusWeeks(q)
                    else -> today.plusMonths(q)
                }
            }
        }
        if (date == null) {
            find(MONTH_DATE_RE) { m ->
                val d = m.g("d")!!.toInt()
                d in 1..31
            }?.let { m ->
                val d = m.g("d")!!.toInt()
                val mon = months.indexOf(m.g("mon")!!) + 1
                val y = m.g("y")?.toInt()
                date = buildDate(today, d, mon, y)
            }
        }
        if (date == null) {
            find(NUM_DATE_RE) { m ->
                val d = m.g("d")!!.toInt()
                val mn = m.g("mn")!!.toInt()
                d in 1..31 && mn in 1..12
            }?.let { m ->
                val yRaw = m.g("y")?.toInt()
                val y = yRaw?.let { if (it < 100) 2000 + it else it }
                date = buildDate(today, m.g("d")!!.toInt(), m.g("mn")!!.toInt(), y)
            }
        }
        // Il giorno della settimana conta solo se non c'è una data esatta ("lunedì 9 novembre")
        val weekdayMatch = find(WEEKDAY_RE)
        if (date == null && weekdayMatch != null) {
            date = today.with(TemporalAdjusters.next(weekdays.getValue(weekdayMatch.g("wd")!!)))
        }
        if (date == null) {
            find(DAY_ONLY_RE) { m -> m.g("d")!!.toInt() in 1..31 }?.let { m ->
                val d = m.g("d")!!.toInt()
                var candidate = safeDate(today.year, today.monthValue, d)
                if (candidate.isBefore(today)) {
                    val next = today.plusMonths(1)
                    candidate = safeDate(next.year, next.monthValue, d)
                }
                date = candidate
            }
        }

        // Periodo da solo ("domani sera", "sabato pomeriggio")
        val standalone = find(STANDALONE_PERIOD_RE)?.g("sp")

        // --- Durata / tutto il giorno ---
        find(DURATION_RE)?.let { m ->
            duration = when {
                m.g("mo") != null -> 30
                m.g("uo") != null -> if (m.g("half2") != null) 90 else 60
                else -> {
                    val q = m.g("n")!!.toInt()
                    val u = m.g("u")!!
                    if (u.startsWith("or")) q * 60 + (if (m.g("half") != null) 30 else 0) else q
                }
            }
        }
        val allDay = find(ALL_DAY_RE) != null

        // --- Composizione orario ---
        var time: LocalTime? = fixedTime
        val effectivePeriod = periodWord ?: standalone ?: hintPeriod
        if (time == null && hour != null) {
            val h = adjustHour(hour, effectivePeriod)
            time = LocalTime.of(h % 24, minute)
            if (endHour != null) {
                var eh = adjustHour(endHour, effectivePeriod)
                if (eh < h && endHour < 12 && eh + 12 > h) eh += 12
                val startMin = h * 60 + minute
                var endMin = (eh % 24) * 60 + endMinute
                if (endMin <= startMin) endMin += 24 * 60
                duration = endMin - startMin
            }
        } else if (time == null && effectivePeriod != null && rel?.g("w") != "stanotte") {
            time = when (effectivePeriod) {
                "mattina", "mattino" -> LocalTime.of(9, 0)
                "pomeriggio" -> LocalTime.of(15, 0)
                "sera" -> LocalTime.of(20, 0)
                else -> null
            }
        }
        if (allDay) time = null

        val finalDate = date ?: if (time != null && !time.isAfter(now.toLocalTime())) today.plusDays(1) else today

        return ParsedEvent(
            title = buildTitle(orig, consumed),
            date = finalDate,
            time = time,
            durationMinutes = duration ?: 60,
        )
    }

    private fun adjustHour(h: Int, period: String?): Int = when {
        h == 24 -> 0
        h >= 13 -> h
        period == "pomeriggio" || period == "sera" -> if (h < 12) h + 12 else h
        period == "notte" -> when (h) {
            in 8..11 -> h + 12
            12 -> 0
            else -> h
        }
        period == "mattina" || period == "mattino" -> h
        h in 1..7 -> h + 12 // "alle 3" per un appuntamento = 15:00
        else -> h
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate {
        val first = LocalDate.of(y, m, 1)
        return first.withDayOfMonth(d.coerceAtMost(first.lengthOfMonth()))
    }

    private fun buildDate(today: LocalDate, d: Int, m: Int, y: Int?): LocalDate {
        if (y != null) return safeDate(y, m, d)
        val candidate = safeDate(today.year, m, d)
        return if (candidate.isBefore(today)) safeDate(today.year + 1, m, d) else candidate
    }

    private fun buildTitle(orig: List<String>, consumed: BooleanArray): String {
        val kept = orig.filterIndexed { i, _ -> !consumed[i] }.toMutableList()
        fun key(t: String) = normalizeTokenForFiller(t)
        while (kept.isNotEmpty() && key(kept.first()) in leadingFiller) kept.removeAt(0)
        while (kept.isNotEmpty() && key(kept.last()) in trailingFiller) kept.removeAt(kept.lastIndex)
        val title = kept.joinToString(" ").trim(' ', ',', '.', ';', ':', '!', '?', '-')
        if (title.isBlank()) return "Appuntamento"
        return title.replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    }

    private fun normalizeTokenForFiller(raw: String): String =
        raw.lowercase(Locale.ITALIAN).replace('’', '\'').map { deaccent(it) }.joinToString("")
            .trim('.', ',', ';', '!', '?', ':', '"')
}
