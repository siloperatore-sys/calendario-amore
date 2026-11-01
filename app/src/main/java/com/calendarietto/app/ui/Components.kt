package com.calendarietto.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calendarietto.app.alarm.AlarmScheduler
import com.calendarietto.app.alarm.Notifications
import com.calendarietto.app.data.Mascot

/** Mascotte che dondola piano piano. */
@Composable
fun BouncyMascot(mascot: Mascot, modifier: Modifier = Modifier, mood: Mood = Mood.HAPPY, strong: Boolean = false) {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (strong) 450 else 1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y",
    )
    MascotView(
        mascot,
        modifier.graphicsLayer {
            translationY = -(if (strong) 18f else 6f) * y
            rotationZ = (if (strong) 6f else 2f) * (y - 0.5f)
        },
        mood,
    )
}

@Composable
fun CozyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = p.primary.copy(alpha = 0.18f), spotColor = p.primary.copy(alpha = 0.22f))
            .clip(shape)
            .background(p.card)
            .border(1.5.dp, p.soft, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun KawaiiChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.primary else p.soft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) p.onPrimary else p.text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RoundButton(emoji: String, modifier: Modifier = Modifier, size: Int = 44, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(p.soft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.42f).sp, color = p.text, textAlign = TextAlign.Center)
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg = when {
        !enabled -> p.soft.copy(alpha = 0.6f)
        filled -> p.primary
        else -> p.soft
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled && enabled) p.onPrimary else if (enabled) p.text else p.muted,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

// ---------------- Permessi ----------------

data class PermStatus(
    val calendar: Boolean,
    val notifications: Boolean,
    val fullScreen: Boolean,
    val exact: Boolean,
    val battery: Boolean,
)

fun permStatus(ctx: Context): PermStatus {
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val pm = ctx.getSystemService(PowerManager::class.java)
    return PermStatus(
        calendar = granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR),
        notifications = Notifications.canPost(ctx),
        fullScreen = Notifications.canFullScreen(ctx),
        exact = AlarmScheduler.canExact(ctx),
        battery = pm.isIgnoringBatteryOptimizations(ctx.packageName),
    )
}

fun runtimePermissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
    return list.toTypedArray()
}

private fun Context.openSafely(vararg intents: Intent) {
    for (i in intents) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

@Composable
fun PermissionsCard(status: PermStatus, onRequestRuntime: () -> Unit) {
    val ctx = LocalContext.current
    val pkg = Uri.parse("package:${ctx.packageName}")
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    CozyCard {
        PermissionRow("📅", "Calendario", "Per leggere e aggiungere gli impegni di Google Calendar", status.calendar) {
            onRequestRuntime()
        }
        if (!status.calendar) {
            Text(
                "Se non compare la richiesta, tocca qui e attiva «Calendario» a mano",
                color = LocalPalette.current.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = 38.dp, bottom = 6.dp)
                    .clickable { ctx.openSafely(appDetails) },
            )
        }
        PermissionRow("🔔", "Notifiche", "Per il riepilogo del mattino e le sveglie", status.notifications) {
            onRequestRuntime()
        }
        if (Build.VERSION.SDK_INT >= 34) {
            PermissionRow("📱", "Sveglia a schermo intero", "Così la sveglia si apre anche a telefono bloccato", status.fullScreen) {
                ctx.openSafely(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg), appDetails)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow("⏱️", "Sveglie puntuali", "Per suonare al minuto giusto", status.exact) {
                ctx.openSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg), appDetails)
            }
        }
        PermissionRow("🔋", "Niente risparmio batteria", "Consigliato: alcuni telefoni bloccano le sveglie", status.battery) {
            ctx.openSafely(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
    }
}

@Composable
private fun PermissionRow(emoji: String, title: String, subtitle: String, ok: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !ok, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = p.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        Spacer(Modifier.width(4.dp))
        if (ok) {
            Text("✅", fontSize = 18.sp)
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(p.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Attiva", color = p.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
