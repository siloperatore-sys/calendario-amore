package com.calendarietto.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.calendarietto.app.data.CalendarInfo
import com.calendarietto.app.voice.ParsedEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class EventDraft(
    val title: String,
    val date: LocalDate,
    /** null = tutto il giorno */
    val time: LocalTime?,
    val durationMinutes: Int,
    val calendarId: Long,
) {
    companion object {
        fun from(p: ParsedEvent, calendarId: Long) = EventDraft(p.title, p.date, p.time, p.durationMinutes, calendarId)
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun AddEventDialog(
    initial: EventDraft,
    heard: String?,
    calendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(initial.date) }
    var allDay by remember { mutableStateOf(initial.time == null) }
    var time by remember { mutableStateOf(initial.time ?: LocalTime.of(9, 0)) }
    var duration by remember { mutableIntStateOf(initial.durationMinutes) }
    var calendarId by remember { mutableLongStateOf(initial.calendarId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        shape = RoundedCornerShape(30.dp),
        title = { Text(if (heard != null) "Ho capito così 🎀" else "Nuovo impegno ✨", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (heard != null) {
                    Text("«$heard»", fontStyle = FontStyle.Italic, color = p.muted)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Cosa?") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("📅 ${date.format(dateFmt)}") {
                        DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }
                    if (!allDay) {
                        PillButton("⏰ ${time.format(timeFmt)}") {
                            TimePickerDialog(ctx, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show()
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                    Text("  Tutto il giorno 🌈")
                }
                if (!allDay) {
                    Text("Quanto dura?", color = p.muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15 to "15 min", 30 to "30 min", 60 to "1 ora", 90 to "1 ora e ½", 120 to "2 ore", 180 to "3 ore").forEach { (m, label) ->
                            KawaiiChip(label, duration == m) { duration = m }
                        }
                    }
                    if (duration !in listOf(15, 30, 60, 90, 120, 180)) {
                        Text("Durata riconosciuta: $duration minuti", color = p.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("In quale calendario?", color = p.muted)
                if (calendars.isEmpty()) {
                    Text("Nessun calendario modificabile trovato 😿", color = Color(0xFFD9667F))
                }
                calendars.forEach { cal ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { calendarId = cal.id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = calendarId == cal.id, onClick = { calendarId = cal.id })
                        ColorDot(Color(cal.color or 0xFF000000.toInt()))
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(cal.name)
                            if (cal.account != cal.name) Text(cal.account, color = p.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(EventDraft(title.trim(), date, if (allDay) null else time, duration, calendarId))
                },
                enabled = title.isNotBlank() && calendars.any { it.id == calendarId },
                shape = RoundedCornerShape(50),
            ) { Text("Salva 💾") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
