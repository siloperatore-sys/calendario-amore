package com.calendarietto.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarietto.app.data.CalendarInfo
import com.calendarietto.app.data.CalendarRepository
import com.calendarietto.app.data.EventItem
import com.calendarietto.app.data.Mascot
import com.calendarietto.app.data.Prefs
import com.calendarietto.app.voice.ItalianEventParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)
private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val hm = DateTimeFormatter.ofPattern("HH:mm")

private fun String.cap() = replaceFirstChar { it.titlecase(Locale.ITALIAN) }
private fun eventColor(e: EventItem) = Color(e.color or 0xFF000000.toInt())

fun defaultCalendarId(prefs: Prefs, calendars: List<CalendarInfo>): Long {
    val writable = calendars.filter { it.canWrite }
    // Prima i calendari Google: quelli «solo telefono» non arrivano mai su Google Calendar.
    return writable.firstOrNull { it.id == prefs.defaultCalendarId }?.id
        ?: writable.firstOrNull { it.isGoogle && it.name.equals(it.account, ignoreCase = true) }?.id
        ?: writable.firstOrNull { it.isGoogle }?.id
        ?: writable.firstOrNull { it.name == it.account }?.id
        ?: writable.firstOrNull()?.id
        ?: -1L
}

@Composable
fun HomeScreen(
    name: String,
    mascot: Mascot,
    prefs: Prefs,
    repo: CalendarRepository,
    refreshTick: Int,
    onOpenSettings: () -> Unit,
    onOpenCouple: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var todayCount by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<EventDraft?>(null) }
    var heard by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<EventItem?>(null) }
    var askDelete by remember { mutableStateOf<EventItem?>(null) }
    var newShared by remember { mutableStateOf<CalendarInfo?>(null) }
    var syncProblem by remember { mutableStateOf<CalendarRepository.SyncProblem?>(null) }
    var noGoogle by remember { mutableStateOf(false) }

    val gridStart = remember(month) { month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val gridDays = remember(gridStart) { (0 until 42).map { gridStart.plusDays(it.toLong()) } }

    LaunchedEffect(month, refreshTick) {
        val from = gridStart.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = gridStart.plusDays(43).atStartOfDay(zone).toInstant().toEpochMilli()
        val hidden = prefs.hiddenCalendars
        val today = LocalDate.now()
        val result = withContext(Dispatchers.IO) {
            val ev = repo.events(from, to, hidden)
            val tFrom = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val tTo = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val tc = repo.events(tFrom - 86_400_000L, tTo + 86_400_000L, hidden).count { it.occursOn(today, zone) }
            Triple(ev, repo.calendars(), tc)
        }
        events = result.first
        calendars = result.second
        todayCount = result.third

        // Calendario di coppia appena creato da me: quando arriva sul telefono diventa quello «per la voce».
        // Calendario condiviso dal partner: lo propongo una volta sola.
        val all = withContext(Dispatchers.IO) { repo.allCalendars() }
        syncProblem = withContext(Dispatchers.IO) { repo.syncProblems() }.takeIf { it.any }
        noGoogle = all.none { it.isGoogle }
        val pending = prefs.pendingCoupleSyncId
        if (pending.isNotBlank()) {
            all.firstOrNull { it.syncId == pending }?.let { mine ->
                prefs.defaultCalendarId = mine.id
                prefs.pendingCoupleSyncId = ""
                prefs.knownShared = prefs.knownShared + mine.id
            }
        }
        newShared = all.firstOrNull { it.isShared && it.id !in prefs.knownShared }
    }

    val byDay = remember(events, gridDays) { gridDays.associateWith { d -> events.filter { it.occursOn(d, zone) } } }
    val dayEvents = byDay[selected] ?: events.filter { it.occursOn(selected, zone) }
    val writable = calendars.filter { it.canWrite }

    fun goTo(m: YearMonth) {
        month = m
        selected = if (m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            val parsed = ItalianEventParser.parse(text)
            heard = text
            draft = EventDraft.from(parsed, defaultCalendarId(prefs, calendars))
        }
    }

    fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "it-IT")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dimmi l'impegno ✨  es. «dentista martedì alle 15»")
        try {
            voice.launch(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Manca il riconoscimento vocale: installa o aggiorna l'app Google", Toast.LENGTH_LONG).show()
        }
    }

    val greeting = when (LocalTime.now().hour) {
        in 5..12 -> "Buongiorno"
        in 13..17 -> "Buon pomeriggio"
        else -> "Buonasera"
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // --- Intestazione ---
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BouncyMascot(mascot, Modifier.size(58.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (name.isBlank()) "$greeting!" else "$greeting, $name!",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            when (todayCount) {
                                0 -> "Oggi niente impegni ☁️"
                                1 -> "Oggi hai 1 impegno 💕"
                                else -> "Oggi hai $todayCount impegni 💕"
                            },
                            color = p.muted,
                        )
                    }
                    RoundButton("🔄") {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.syncAll() }
                            Toast.makeText(ctx, "Sincronizzo con Google… ✨", Toast.LENGTH_SHORT).show()
                            delay(2500)
                            onChanged()
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    RoundButton("💕", onClick = onOpenCouple)
                    Spacer(Modifier.width(8.dp))
                    RoundButton("⚙️", onClick = onOpenSettings)
                }
            }

            // --- Avvisi sincronizzazione ---
            if (noGoogle) {
                item {
                    WarningCard(
                        "Sul telefono non c'è un account Google con il calendario: gli impegni restano solo qui.",
                        "➕ Aggiungi account Google",
                    ) {
                        try {
                            ctx.startActivity(
                                Intent(android.provider.Settings.ACTION_ADD_ACCOUNT)
                                    .putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google")),
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            syncProblem?.let { sp ->
                item {
                    WarningCard(
                        if (sp.masterOff) "La sincronizzazione automatica del telefono è spenta: niente arriva su Google."
                        else "La sincronizzazione del calendario Google è spenta per ${sp.accounts.joinToString()}.",
                        if (sp.masterOff) "⚙️ Apri impostazioni" else "🔄 Attiva sincronizzazione",
                    ) {
                        if (sp.masterOff) {
                            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) } catch (_: Exception) {}
                        } else {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { repo.enableCalendarSync(sp.accounts) }
                                if (!ok) {
                                    try { ctx.startActivity(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) } catch (_: Exception) {}
                                }
                                Toast.makeText(ctx, if (ok) "Sincronizzazione attivata ✨" else "Attivala tu da Impostazioni → Account", Toast.LENGTH_LONG).show()
                                delay(1500)
                                onChanged()
                            }
                        }
                    }
                }
            }

            // --- Mese ---
            item {
                CozyCard(
                    Modifier.pointerInput(month) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (total > 120f) goTo(month.minusMonths(1)) else if (total < -120f) goTo(month.plusMonths(1))
                                total = 0f
                            },
                        ) { _, dx -> total += dx }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundButton("‹", size = 38) { goTo(month.minusMonths(1)) }
                        Text(
                            month.format(monthFmt).cap(),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { goTo(YearMonth.now()) },
                        )
                        RoundButton("›", size = 38) { goTo(month.plusMonths(1)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = p.muted, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val rows = if (gridDays[35].month != month.month) 5 else 6
                    for (r in 0 until rows) {
                        Row {
                            for (c in 0 until 7) {
                                val day = gridDays[r * 7 + c]
                                DayCell(
                                    modifier = Modifier.weight(1f),
                                    day = day,
                                    inMonth = day.month == month.month,
                                    isToday = day == LocalDate.now(),
                                    isSelected = day == selected,
                                    dots = (byDay[day] ?: emptyList()).map { eventColor(it) }.distinct().take(3),
                                ) {
                                    selected = day
                                    if (day.month != month.month) month = YearMonth.from(day)
                                }
                            }
                        }
                    }
                }
            }

            // --- Giorno selezionato ---
            item {
                Text(
                    selected.format(dayFmt).cap(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                )
            }
            if (dayEvents.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MascotView(mascot, Modifier.size(96.dp), Mood.SLEEPY)
                        Text("Giornata libera! ☁️", style = MaterialTheme.typography.titleMedium)
                        Text("Tocca il microfono e dimmi un impegno", color = p.muted)
                    }
                }
            } else {
                items(dayEvents, key = { it.key }) { e ->
                    EventCard(e) { detail = e }
                }
            }
        }

        // --- Pulsanti ---
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoundButton("＋", size = 52) {
                heard = null
                val now = LocalTime.now()
                val next = if (now.hour >= 22) LocalTime.of(9, 0) else LocalTime.of(now.hour + 1, 0)
                draft = EventDraft("", selected, next, 60, defaultCalendarId(prefs, calendars))
            }
            MicButton { startVoice() }
        }
    }

    // --- Finestre ---
    draft?.let { d ->
        AddEventDialog(
            initial = d,
            heard = heard,
            calendars = writable,
            onDismiss = { draft = null },
            onSave = { ed ->
                val cal = writable.firstOrNull { it.id == ed.calendarId }
                draft = null
                if (cal != null) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val (start, end) = if (ed.time == null) {
                                val s = ed.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                                s to s + 86_400_000L
                            } else {
                                val s = ed.date.atTime(ed.time).atZone(zone).toInstant().toEpochMilli()
                                s to s + ed.durationMinutes * 60_000L
                            }
                            val r = repo.insertEvent(cal, ed.title, start, end, ed.time == null) != null
                            com.calendarietto.app.alarm.ChangeWatcher.check(ctx.applicationContext, notify = false)
                            r
                        }
                        Toast.makeText(ctx, if (ok) "Fatto! Segnato in «${cal.name}» ✨" else "Ops, non sono riuscito a salvarlo 😿", Toast.LENGTH_SHORT).show()
                        selected = ed.date
                        month = YearMonth.from(ed.date)
                        onChanged()
                    }
                }
            },
        )
    }

    detail?.let { e ->
        val cal = calendars.firstOrNull { it.id == e.calendarId }
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = p.card,
            shape = RoundedCornerShape(30.dp),
            title = { Text(e.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(timeLabel(e, zone, withDate = true))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(eventColor(e))
                        Text("  ${e.calendarName}", color = p.muted)
                    }
                    e.location?.let { Text("📍 $it", color = p.muted) }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Chiudi") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val i = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
                        try { ctx.startActivity(i) } catch (_: Exception) {}
                        detail = null
                    }) { Text("Apri") }
                    if (cal?.canWrite == true) {
                        TextButton(onClick = { askDelete = e; detail = null }) { Text("Elimina", color = Color(0xFFD9667F)) }
                    }
                }
            },
        )
    }

    newShared?.let { cal ->
        AlertDialog(
            onDismissRequest = {
                prefs.knownShared = prefs.knownShared + cal.id
                newShared = null
            },
            containerColor = p.card,
            shape = RoundedCornerShape(30.dp),
            title = { Text("💌 Calendario condiviso!", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MascotView(mascot, Modifier.size(90.dp), Mood.EXCITED)
                    val who = cal.ownerLabel.ifBlank { "Qualcuno" }
                    Text(
                        if (cal.canWrite) "$who ha condiviso con te «${cal.name}». Lo usiamo come il vostro calendario in comune?"
                        else "$who ti fa vedere il calendario «${cal.name}». Lo mostro qui insieme ai tuoi?",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.knownShared = prefs.knownShared + cal.id
                    if (cal.canWrite) prefs.defaultCalendarId = cal.id
                    prefs.hiddenCalendars = prefs.hiddenCalendars - cal.id
                    newShared = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.enableCalendar(cal) }
                        Toast.makeText(ctx, "Collegati! 💕", Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                }) { Text(if (cal.canWrite) "Sì, usalo 💕" else "Sì, mostralo 👀") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.knownShared = prefs.knownShared + cal.id
                    newShared = null
                }) { Text("Non ora") }
            },
        )
    }

    askDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { askDelete = null },
            containerColor = p.card,
            shape = RoundedCornerShape(30.dp),
            title = { Text("Eliminare «${e.title}»?") },
            text = { Text("Sparisce anche da Google Calendar, per tutti quelli che condividono il calendario.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = null
                    val cal = calendars.firstOrNull { it.id == e.calendarId }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repo.deleteEvent(e.eventId, cal)
                            com.calendarietto.app.alarm.ChangeWatcher.check(ctx.applicationContext, notify = false)
                        }
                        onChanged()
                    }
                }) { Text("Elimina", color = Color(0xFFD9667F)) }
            },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text("Annulla") } },
        )
    }
}

private fun timeLabel(e: EventItem, zone: ZoneId, withDate: Boolean = false): String {
    if (e.allDay) return "🌈 Tutto il giorno"
    val s = Instant.ofEpochMilli(e.begin).atZone(zone)
    val en = Instant.ofEpochMilli(e.end).atZone(zone)
    val datePart = if (withDate) s.toLocalDate().format(dayFmt).cap() + " · " else ""
    val endPart = if (en.toLocalDate() != s.toLocalDate()) en.format(DateTimeFormatter.ofPattern("d/M HH:mm")) else en.format(hm)
    return "⏰ $datePart${s.format(hm)} – $endPart"
}

@Composable
private fun DayCell(
    modifier: Modifier,
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    dots: List<Color>,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    var m = modifier
        .aspectRatio(0.92f)
        .padding(2.dp)
        .clip(shape)
        .background(if (isSelected) p.primary else Color.Transparent)
    if (isToday && !isSelected) m = m.border(2.dp, p.primary, shape)
    Column(
        m.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            color = when {
                isSelected -> p.onPrimary
                !inMonth -> p.muted.copy(alpha = 0.45f)
                else -> p.text
            },
            fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            dots.forEach { c ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else c),
                )
            }
        }
    }
}

@Composable
private fun EventCard(e: EventItem, onClick: () -> Unit) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    CozyCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(7.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(eventColor(e)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(timeLabel(e, zone), color = p.primary, style = MaterialTheme.typography.labelLarge)
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                val extra = listOfNotNull(e.calendarName.takeIf { it.isNotBlank() }, e.location?.let { "📍 $it" })
                if (extra.isNotEmpty()) {
                    Text(extra.joinToString("  ·  "), color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(1f, 1.25f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.35f }
                .clip(CircleShape)
                .background(p.accent),
        )
        Box(
            Modifier
                .size(72.dp)
                .shadow(10.dp, CircleShape, ambientColor = p.primary, spotColor = p.primary)
                .clip(CircleShape)
                .background(p.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎤", fontSize = 30.sp)
        }
    }
}

@Composable
private fun WarningCard(text: String, button: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    CozyCard {
        Text("⚠️ $text", color = p.text)
        Spacer(Modifier.height(8.dp))
        PillButton(button, Modifier.fillMaxWidth(), filled = true, onClick = onClick)
    }
}
