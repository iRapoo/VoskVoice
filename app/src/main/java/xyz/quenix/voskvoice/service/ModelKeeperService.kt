package xyz.quenix.voskvoice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import xyz.quenix.voskvoice.R
import xyz.quenix.voskvoice.settings.VoiceSettings
import xyz.quenix.voskvoice.speech.ModelCache
import xyz.quenix.voskvoice.speech.ModelStore
import xyz.quenix.voskvoice.ui.MainActivity

/**
 * Keeps a speech model loaded all the time.
 *
 * Loading a Vosk model takes 5-8 s on a watch CPU (its graphs are rebuilt on load), which is too
 * long to wait every time you tap the microphone. A foreground service keeps our process alive,
 * so the model loaded here stays in memory and voice input starts instantly. The price is
 * ~170 MB of RAM, hence the switch on the setup screen.
 *
 * It never touches the microphone itself; recording happens only on the "speak now" screen.
 */
class ModelKeeperService : Service() {

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        ModelCache.keepLoaded = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val language = VoiceSettings(this).languageFor(null)
        Thread({ ModelCache.preload(this, language) }, "model-preload").start()
        return START_STICKY
    }

    override fun onDestroy() {
        ModelCache.keepLoaded = false
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ModelCache.onTrimMemory(level)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.keeper_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val openSettings = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.keeper_text))
            .setContentIntent(openSettings)
            .setOngoing(true)
            .setLocalOnly(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Starts the service after a reboot or an app update, if the setting is on. */
    class Restarter : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                sync(context)
            }
        }
    }

    companion object {
        private const val TAG = "ModelKeeper"
        private const val CHANNEL = "keeper"
        private const val NOTIFICATION_ID = 1

        /** Starts or stops the service to match the setting and the installed models. */
        fun sync(context: Context) {
            val intent = Intent(context, ModelKeeperService::class.java)
            val wanted = VoiceSettings(context).keepLoaded && ModelStore.installed(context).isNotEmpty()
            try {
                if (wanted) context.startForegroundService(intent) else context.stopService(intent)
            } catch (e: IllegalStateException) {
                // Background start not allowed (Android 12+ outside of the allowed cases).
                Log.w(TAG, "Cannot ${if (wanted) "start" else "stop"} keeper", e)
            }
        }
    }
}
