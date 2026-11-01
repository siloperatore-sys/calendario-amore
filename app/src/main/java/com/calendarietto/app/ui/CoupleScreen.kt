package com.calendarietto.app.ui

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calendarietto.app.data.CalendarInfo
import com.calendarietto.app.data.CalendarRepository
import com.calendarietto.app.data.GoogleCalendarApi
import com.calendarietto.app.data.Mascot
import com.calendarietto.app.data.Prefs
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private sealed class InviteState {
    data object Idle : InviteState()
    data class Working(val text: String) : InviteState()
    data class Done(val calendarId: String, val email: String, val name: String) : InviteState()
    data class Failed(val text: String) : InviteState()
}

private fun friendlyError(e: Throwable): String = when {
    e is ApiException && e.statusCode == 10 ->
        "Google non riconosce ancora l'app: manca la configurazione da fare una volta sola (guida nel README, sezione «Collegarsi in due»)."
    e is ApiException && (e.statusCode == 12501 || e.statusCode == 16) -> "Accesso annullato."
    e is ApiException -> "Google ha risposto con l'errore ${e.statusCode}. Riprova tra poco."
    e is GoogleCalendarApi.ApiError && e.reason == "accessNotConfigured" ->
        "Nel progetto Google manca l'attivazione della «Google Calendar API»."
    e is GoogleCalendarApi.ApiError && e.code == 403 -> "Google ha negato il permesso: ${e.message}"
    e is GoogleCalendarApi.ApiError -> "Errore da Google Calendar: ${e.message}"
    e is java.io.IOException -> "Niente connessione a internet?"
    else -> "Qualcosa è andato storto: ${e.message}"
}

@Composable
fun CoupleScreen(
    prefs: Prefs,
    repo: CalendarRepository,
    mascot: Mascot,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var account by remember { mutableStateOf("") }
    var partner by remember { mutableStateOf(prefs.partnerEmail) }
    var calName by remember { mutableStateOf(prefs.coupleCalendarName) }
    var sharePersonal by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<InviteState>(InviteState.Idle) }
    var shared by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        val (acc, sh) = withContext(Dispatchers.IO) {
            repo.googleAccounts() to repo.allCalendars().filter { it.isGoogle && it.isShared && it.canWrite }
        }
        accounts = acc
        if (account.isBlank() || account !in acc) account = acc.firstOrNull() ?: ""
        shared = sh
    }

    fun runInvite(token: String) {
        val email = partner.trim()
        val name = calName.trim().ifBlank { "Noi due 💕" }
        scope.launch {
            try {
                state = InviteState.Working("Preparo il calendario «$name»…")
                val calId = withContext(Dispatchers.IO) {
                    GoogleCalendarApi.findOwnedCalendar(token, name)
                        ?: GoogleCalendarApi.createCalendar(token, name, ZoneId.systemDefault().id)
                }
                state = InviteState.Working("Invito $email…")
                withContext(Dispatchers.IO) {
                    GoogleCalendarApi.share(token, calId, email, "writer")
                    if (sharePersonal) GoogleCalendarApi.share(token, account, email, "reader")
                }
                prefs.partnerEmail = email
                prefs.coupleCalendarName = name
                prefs.pendingCoupleSyncId = calId
                withContext(Dispatchers.IO) { repo.syncAll() }
                state = InviteState.Done(calId, email, name)
                delay(4000)
                onChanged()
                refresh++
            } catch (e: Exception) {
                state = InviteState.Failed(friendlyError(e))
            }
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) {
            state = InviteState.Failed("Accesso a Google annullato.")
            return@rememberLauncherForActivityResult
        }
        try {
            val result = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(res.data)
            val token = result.accessToken
            if (token.isNullOrBlank()) state = InviteState.Failed("Google non ha dato il permesso.") else runInvite(token)
        } catch (e: Exception) {
            state = InviteState.Failed(friendlyError(e))
        }
    }

    fun startInvite() {
        val email = partner.trim()
        if (!email.contains("@") || !email.contains(".")) {
            state = InviteState.Failed("Controlla l'email del tuo amore 💌")
            return
        }
        if (email.equals(account, ignoreCase = true)) {
            state = InviteState.Failed("Questa è la tua email! Scrivi quella dell'altra persona.")
            return
        }
        if (account.isBlank()) {
            state = InviteState.Failed("Sul telefono non c'è un account Google con il calendario sincronizzato.")
            return
        }
        state = InviteState.Working("Chiedo il permesso a Google…")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleCalendarApi.SCOPE)))
            .setAccount(Account(account, "com.google"))
            .build()
        Identity.getAuthorizationClient(ctx).authorize(request)
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    consent.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    val token = r.accessToken
                    if (token.isNullOrBlank()) state = InviteState.Failed("Google non ha dato il permesso.") else runInvite(token)
                }
            }
            .addOnFailureListener { e -> state = InviteState.Failed(friendlyError(e)) }
    }

    fun shareLink(calId: String, name: String) {
        val text = "Ti ho invitato nel nostro calendario «$name» su Calendarietto 💕\n\n" +
            "1. Installa Calendarietto (te lo mando a parte)\n" +
            "2. Apri questo link per aggiungere il calendario al tuo Google Calendar:\n" +
            GoogleCalendarApi.addCalendarLink(calId) + "\n" +
            "3. Nell'app tocca 💕 → «Cerca calendari condivisi»"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            ctx.startActivity(Intent.createChooser(send, "Manda il link"))
        } catch (_: Exception) {
        }
    }

    fun useShared(cal: CalendarInfo) {
        scope.launch {
            withContext(Dispatchers.IO) { repo.enableCalendar(cal) }
            prefs.defaultCalendarId = cal.id
            prefs.hiddenCalendars = prefs.hiddenCalendars - cal.id
            prefs.knownShared = prefs.knownShared + cal.id
            Toast.makeText(ctx, "Collegati! «${cal.name}» è il vostro calendario 💕", Toast.LENGTH_LONG).show()
            onChanged()
            refresh++
        }
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
            Text("Noi due 💕", style = MaterialTheme.typography.headlineMedium)
        }
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyMascot(mascot, Modifier.size(64.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Un calendario in comune: quello che aggiunge uno compare subito anche sul telefono dell'altro.",
                color = p.muted,
            )
        }

        // ---------- Invita ----------
        SectionTitle("Invita il tuo amore ✉️")
        CozyCard {
            if (accounts.size > 1) {
                Text("Il tuo account Google", color = p.muted)
                accounts.forEach { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { account = a },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = account == a, onClick = { account = a })
                        Text(a)
                    }
                }
                Spacer(Modifier.height(6.dp))
            } else if (accounts.size == 1) {
                Text("Dal tuo account: ${accounts.first()}", color = p.muted)
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = partner,
                onValueChange = { partner = it },
                label = { Text("Email Google dell'altra persona") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = calName,
                onValueChange = { calName = it },
                label = { Text("Nome del calendario in comune") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable { sharePersonal = !sharePersonal },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = sharePersonal, onCheckedChange = { sharePersonal = it })
                Column {
                    Text("Fagli vedere anche il mio calendario personale")
                    Text("Solo in lettura: può guardare ma non modificare", color = p.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            val working = state is InviteState.Working
            PillButton(
                if (working) "Un attimo… ⏳" else "💌 Invia invito",
                Modifier.fillMaxWidth(),
                filled = true,
                enabled = !working,
            ) { startInvite() }

            when (val s = state) {
                is InviteState.Working -> Text(s.text, color = p.muted, modifier = Modifier.padding(top = 10.dp))
                is InviteState.Failed -> Text(s.text, color = Color(0xFFD9667F), modifier = Modifier.padding(top = 10.dp))
                is InviteState.Done -> {
                    Text(
                        "Fatto! 🎉 Ho creato «${s.name}» e invitato ${s.email}. Riceverà un'email da Google: deve toccare «Aggiungi questo calendario». " +
                            "Per fare prima, mandagli anche il link.",
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton("📲 Manda il link (WhatsApp, SMS…)", Modifier.fillMaxWidth()) { shareLink(s.calendarId, s.name) }
                }
                InviteState.Idle -> {}
            }
        }

        // ---------- Ricevi ----------
        SectionTitle("Hai ricevuto un invito? 💝")
        CozyCard {
            Text("1. Apri l'email di Google Calendar (o il link che ti hanno mandato)")
            Text("2. Tocca «Aggiungi questo calendario»")
            Text("3. Torna qui e cerca:")
            Spacer(Modifier.height(10.dp))
            PillButton(if (searching) "Cerco… 🔎" else "🔎 Cerca calendari condivisi", Modifier.fillMaxWidth(), filled = true, enabled = !searching) {
                searching = true
                scope.launch {
                    withContext(Dispatchers.IO) { repo.syncAll() }
                    delay(5000)
                    refresh++
                    searching = false
                }
            }
            Spacer(Modifier.height(8.dp))
            if (shared.isEmpty()) {
                Text(
                    "Ancora nessun calendario condiviso con te. Se hai appena accettato l'invito, aspetta un minuto e riprova.",
                    color = p.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            shared.forEach { cal ->
                val active = cal.visible && cal.syncEvents && prefs.defaultCalendarId == cal.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ColorDot(Color(cal.color or 0xFF000000.toInt()), 14)
                    Column(Modifier.weight(1f)) {
                        Text(cal.name, fontWeight = FontWeight.Bold)
                        Text("di ${cal.ownerAccount}", color = p.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (active) Text("✅ Collegati") else KawaiiChip("💕 Usa questo", false) { useShared(cal) }
                }
            }
        }

        SectionTitle("Come funziona 🧸")
        CozyCard {
            Text(
                "Il calendario in comune vive su Google Calendar: tutti e due potete aggiungere, spostare e cancellare impegni, " +
                    "e i telefoni si aggiornano da soli (di solito in pochi secondi, al massimo qualche minuto). " +
                    "Le sveglie suonano su entrambi i telefoni, ognuno con i suoi tempi.",
                color = p.muted,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
