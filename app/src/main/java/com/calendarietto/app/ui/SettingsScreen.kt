package com.calendarietto.app.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.calendarietto.app.alarm.AlarmScheduler
import com.calendarietto.app.data.CalendarInfo
import com.calendarietto.app.data.CalendarRepository
import com.calendarietto.app.data.Mascot
import com.calendarietto.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MascotPicker(selected: Mascot, onPick: (Mascot) -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Mascot.ROBOT to "Robottino 🤖", Mascot.UNICORN to "Unicorno 🦄").forEach { (m, label) ->
            val on = m == selected
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (on) paletteFor(m).soft else p.card)
                    .border(if (on) 3.dp else 1.5.dp, if (on) paletteFor(m).primary else p.soft, shape)
                    .clickable { onPick(m) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MascotView(m, Modifier.size(84.dp))
                Spacer(Modifier.height(6.dp))
                Text(label, fontWeight = FontWeight.Bold, color = paletteFor(m).text)
            }
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: Prefs,
    repo: CalendarRepository,
    perms: PermStatus,
    name: String,
    mascot: Mascot,
    onName: (String) -> Unit,
    onMascot: (Mascot) -> Unit,
    onRequestRuntime: () -> Unit,
    onOpenCouple: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var hidden by remember { mutableStateOf(prefs.hiddenCalendars) }
    var silent by remember { mutableStateOf(prefs.silentCalendars) }
    var defaultCal by remember { mutableLongStateOf(prefs.defaultCalendarId) }
    var alarms by remember { mutableStateOf(prefs.alarmsEnabled) }
    var before by remember { mutableIntStateOf(prefs.alarmMinutesBefore) }
    var snooze by remember { mutableIntStateOf(prefs.snoozeMinutes) }
    var morning by remember { mutableStateOf(prefs.morningEnabled) }
    var mh by remember { mutableIntStateOf(prefs.morningHour) }
    var mm by remember { mutableIntStateOf(prefs.morningMinute) }

    LaunchedEffect(Unit) {
        calendars = withContext(Dispatchers.IO) { repo.calendars() }
        if (calendars.none { it.id == defaultCal && it.canWrite }) defaultCal = defaultCalendarId(prefs, calendars)
    }

    fun reschedule() {
        scope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(ctx.applicationContext) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("‹", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)
        }

        SectionTitle("Chi sei? 💕")
        CozyCard {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Il tuo nome") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            MascotPicker(mascot, onMascot)
        }

        SectionTitle("Noi due 💕")
        CozyCard {
            Text("Collega il tuo calendario con quello del tuo amore, tramite email.", color = p.muted)
            Spacer(Modifier.height(10.dp))
            PillButton("💌 Collegati", Modifier.fillMaxWidth(), filled = true, onClick = onOpenCouple)
        }

        SectionTitle("Calendari 📚")
        CozyCard {
            if (calendars.isEmpty()) {
                Text("Nessun calendario trovato. Controlla che l'account Google sia sincronizzato nel telefono.", color = p.muted)
            }
            calendars.forEachIndexed { idx, cal ->
                if (idx > 0) HorizontalDivider(color = p.soft, modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(Color(cal.color or 0xFF000000.toInt()), 14)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    ) {
                        Text(cal.name, fontWeight = FontWeight.Bold)
                        Text(cal.account + if (!cal.canWrite) " · sola lettura" else "", color = p.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val shown = cal.id !in hidden
                    KawaiiChip(if (shown) "👀 Visibile" else "🙈 Nascosto", shown) {
                        hidden = if (shown) hidden + cal.id else hidden - cal.id
                        prefs.hiddenCalendars = hidden
                        reschedule()
                    }
                    val rings = cal.id !in silent
                    KawaiiChip(if (rings) "🔔 Sveglia" else "🔕 Muto", rings) {
                        silent = if (rings) silent + cal.id else silent - cal.id
                        prefs.silentCalendars = silent
                        reschedule()
                    }
                    if (cal.canWrite) {
                        KawaiiChip("⭐ Per la voce", defaultCal == cal.id) {
                            defaultCal = cal.id
                            prefs.defaultCalendarId = cal.id
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "«Per la voce» è il calendario dove finiscono i nuovi impegni: scegliete lo stesso calendario condiviso e li vedrete entrambi.",
                color = p.muted, style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionTitle("Sveglie ⏰")
        CozyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sveglia prima degli impegni", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = alarms, onCheckedChange = {
                    alarms = it
                    prefs.alarmsEnabled = it
                    reschedule()
                })
            }
            if (alarms) {
                Text("Quanto prima?", color = p.muted, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "All'ora esatta", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 ora", 120 to "2 ore").forEach { (m, l) ->
                        KawaiiChip(l, before == m) {
                            before = m
                            prefs.alarmMinutesBefore = m
                            reschedule()
                        }
                    }
                }
                Text("Posticipa di", color = p.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        KawaiiChip("$m min", snooze == m) {
                            snooze = m
                            prefs.snoozeMinutes = m
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PillButton("🔔 Prova la sveglia", filled = true) {
                    AlarmScheduler.scheduleTest(ctx.applicationContext)
                    Toast.makeText(ctx, "Suona tra 5 secondi: puoi anche bloccare il telefono", Toast.LENGTH_LONG).show()
                }
            }
        }

        SectionTitle("Riepilogo del mattino ☀️")
        CozyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ogni mattina gli impegni del giorno", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = morning, onCheckedChange = {
                    morning = it
                    prefs.morningEnabled = it
                    reschedule()
                })
            }
            if (morning) {
                Spacer(Modifier.height(8.dp))
                PillButton("🕗 Alle %02d:%02d".format(mh, mm)) {
                    TimePickerDialog(ctx, { _, h, m ->
                        mh = h; mm = m
                        prefs.morningHour = h; prefs.morningMinute = m
                        reschedule()
                    }, mh, mm, true).show()
                }
            }
        }

        SectionTitle("Permessi 🔐")
        PermissionsCard(perms, onRequestRuntime)

        Spacer(Modifier.height(40.dp))
    }
}
