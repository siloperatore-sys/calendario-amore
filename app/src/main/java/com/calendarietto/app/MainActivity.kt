package com.calendarietto.app

import android.database.ContentObserver
import android.graphics.Color as AColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.calendarietto.app.alarm.AlarmScheduler
import com.calendarietto.app.alarm.Notifications
import com.calendarietto.app.data.CalendarRepository
import com.calendarietto.app.data.Prefs
import com.calendarietto.app.ui.CoupleScreen
import com.calendarietto.app.ui.HomeScreen
import com.calendarietto.app.ui.KawaiiTheme
import com.calendarietto.app.ui.OnboardingScreen
import com.calendarietto.app.ui.SettingsScreen
import com.calendarietto.app.ui.paletteFor
import com.calendarietto.app.ui.permStatus
import com.calendarietto.app.ui.runtimePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Screen { WELCOME, HOME, SETTINGS, COUPLE }

class MainActivity : ComponentActivity() {

    private val tick = mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    private val bump = Runnable { tick.intValue++ }
    private var observer: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        Notifications.ensureChannels(this)
        AlarmScheduler.startPeriodicRefresh(this)
        val prefs = Prefs(this)
        val repo = CalendarRepository(this)
        setContent { CalendariettoApp(prefs, repo, tick) }
    }

    override fun onResume() {
        super.onResume()
        registerObserver()
        tick.intValue++
        val app = applicationContext
        Thread { AlarmScheduler.rescheduleAll(app) }.start()
    }

    override fun onPause() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        super.onPause()
    }

    /** Quando arrivano modifiche (anche dall'altro telefono, via Google) ricarico la vista. */
    private fun registerObserver() {
        if (observer != null || !CalendarRepository(this).hasPermission()) return
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(bump)
                handler.postDelayed(bump, 700)
            }
        }
        try {
            contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, obs)
            observer = obs
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun CalendariettoApp(prefs: Prefs, repo: CalendarRepository, tick: MutableIntState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mascot by remember { mutableStateOf(prefs.mascot) }
    var name by remember { mutableStateOf(prefs.name) }
    var screen by remember { mutableStateOf(if (prefs.onboarded && repo.hasPermission()) Screen.HOME else Screen.WELCOME) }
    var perms by remember { mutableStateOf(permStatus(ctx)) }

    LaunchedEffect(tick.intValue) { perms = permStatus(ctx) }

    val onChanged: () -> Unit = {
        tick.intValue++
        scope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(ctx.applicationContext) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        perms = permStatus(ctx)
        onChanged()
    }
    val requestRuntime: () -> Unit = { launcher.launch(runtimePermissions()) }
    val setName: (String) -> Unit = { name = it; prefs.name = it.trim() }
    val setMascot: (com.calendarietto.app.data.Mascot) -> Unit = { mascot = it; prefs.mascot = it }

    KawaiiTheme(mascot) {
        Box(
            Modifier
                .fillMaxSize()
                .background(paletteFor(mascot).background)
                .systemBarsPadding()
                .imePadding(),
        ) {
            when (screen) {
                Screen.WELCOME -> OnboardingScreen(
                    name = name,
                    mascot = mascot,
                    perms = perms,
                    onName = setName,
                    onMascot = setMascot,
                    onRequestRuntime = requestRuntime,
                    onDone = {
                        prefs.onboarded = true
                        screen = Screen.HOME
                        onChanged()
                    },
                )
                Screen.HOME -> HomeScreen(
                    name = name.trim(),
                    mascot = mascot,
                    prefs = prefs,
                    repo = repo,
                    refreshTick = tick.intValue,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenCouple = { screen = Screen.COUPLE },
                    onChanged = onChanged,
                )
                Screen.SETTINGS -> SettingsScreen(
                    prefs = prefs,
                    repo = repo,
                    perms = perms,
                    name = name,
                    mascot = mascot,
                    onName = setName,
                    onMascot = setMascot,
                    onRequestRuntime = requestRuntime,
                    onOpenCouple = { screen = Screen.COUPLE },
                    onBack = {
                        screen = Screen.HOME
                        onChanged()
                    },
                )
                Screen.COUPLE -> CoupleScreen(
                    prefs = prefs,
                    repo = repo,
                    mascot = mascot,
                    onBack = {
                        screen = Screen.HOME
                        onChanged()
                    },
                    onChanged = onChanged,
                )
            }
        }
    }
}
