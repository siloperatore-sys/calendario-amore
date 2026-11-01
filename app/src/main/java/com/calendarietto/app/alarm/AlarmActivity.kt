package com.calendarietto.app.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarietto.app.data.Mascot
import com.calendarietto.app.data.Prefs
import com.calendarietto.app.ui.BouncyMascot
import com.calendarietto.app.ui.KawaiiTheme
import com.calendarietto.app.ui.Mood
import com.calendarietto.app.ui.PillButton
import com.calendarietto.app.ui.paletteFor
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** La schermata che si apre quando suona la sveglia, anche a telefono bloccato. */
class AlarmActivity : ComponentActivity() {

    private var payload by mutableStateOf(AlarmPayload("Promemoria", ""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        payload = AlarmService.current.value ?: AlarmPayload.from(intent)
        val mascot = Prefs(this).mascot

        setContent {
            KawaiiTheme(mascot) {
                val ringing by AlarmService.ringing.collectAsState()
                var seenRinging by remember { mutableStateOf(false) }
                LaunchedEffect(ringing) {
                    if (ringing) seenRinging = true
                    else if (seenRinging) {
                        delay(300)
                        finish()
                    }
                }
                AlarmScreen(
                    mascot = mascot,
                    payload = payload,
                    onSnooze = {
                        startService(payload.toIntent(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_SNOOZE)))
                        finish()
                    },
                    onStop = {
                        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
                        finish()
                    },
                    snoozeMinutes = Prefs(this).snoozeMinutes,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        payload = AlarmService.current.value ?: AlarmPayload.from(intent)
    }
}

@androidx.compose.runtime.Composable
private fun AlarmScreen(mascot: Mascot, payload: AlarmPayload, snoozeMinutes: Int, onSnooze: () -> Unit, onStop: () -> Unit) {
    val p = paletteFor(mascot)
    val now = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.soft, p.background, p.blush.copy(alpha = 0.5f)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(now, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = p.primary)
            Spacer(Modifier.height(12.dp))
            BouncyMascot(mascot, Modifier.size(200.dp), Mood.EXCITED, strong = true)
            Spacer(Modifier.height(20.dp))
            Text("Ehi! È quasi ora ✨", color = p.muted, style = MaterialTheme.typography.titleMedium)
            Text(
                payload.title,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = p.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (payload.subtitle.isNotBlank()) {
                Text(payload.subtitle, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(36.dp))
            PillButton("✨ Ho capito!", Modifier.fillMaxWidth(), filled = true, onClick = onStop)
            Spacer(Modifier.height(12.dp))
            PillButton("💤 Ancora $snoozeMinutes minuti", Modifier.fillMaxWidth(), onClick = onSnooze)
        }
    }
}
