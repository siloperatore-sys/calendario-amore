package com.calendarietto.app.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import com.calendarietto.app.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

/** Fa suonare e vibrare la sveglia finché non la spegni (massimo 2 minuti). */
class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.calendarietto.app.action.RING"
        const val ACTION_STOP = "com.calendarietto.app.action.STOP"
        const val ACTION_SNOOZE = "com.calendarietto.app.action.SNOOZE"
        private const val MAX_RING_MS = 2 * 60 * 1000L

        val ringing = MutableStateFlow(false)
        val current = MutableStateFlow<AlarmPayload?>(null)
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        current.value?.let { Notifications.showMissed(this, it) }
        stopRinging()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_SNOOZE -> {
                val p = current.value ?: AlarmPayload.from(intent)
                AlarmScheduler.scheduleSnooze(this, p, Prefs(this).snoozeMinutes)
                stopRinging()
            }
            else -> {
                val p = AlarmPayload.from(intent)
                current.value = p
                ringing.value = true
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
                try {
                    ServiceCompat.startForeground(this, Notifications.ID_ALARM, Notifications.alarmNotification(this, p), type)
                } catch (_: Exception) {
                    Notifications.showAlarmFallback(this, p)
                }
                startSound()
                startVibration()
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, MAX_RING_MS)
            }
        }
        return START_NOT_STICKY
    }

    private fun startSound() {
        if (player != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player?.release()
            player = null
        }
    }

    private fun startVibration() {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            vibrator = v
        } catch (_: Exception) {
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        ringing.value = false
        current.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Notifications.cancelAlarm(this)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player?.release()
        player = null
        vibrator?.cancel()
        ringing.value = false
        super.onDestroy()
    }
}
