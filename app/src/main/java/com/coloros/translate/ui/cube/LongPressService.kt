package com.coloros.translate.ui.cube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.amar.snapmapper.R
import com.amar.snapmapper.SnapKeyService

/**
 * Long press on phones whose gesture app (seen: com.oplus.gesture 8.6.0 on the Find X9) starts a
 * service instead of [LongPressActivity]: its Translate scene has longPressActionType = "service"
 * and calls startForegroundService with action oplus.intent.action.GLOBAL_TRANSLATION and the
 * extra extra_from_package = com.oplus.gesture.
 *
 * Android kills the app if a service started that way never calls startForeground, so this goes
 * foreground for an instant and stops. The notification is gone before Android would show it.
 */
class LongPressService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Snap Key long press", NotificationManager.IMPORTANCE_MIN)
            )
            val n = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_island_snap).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            else startForeground(1, n)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
        }
        if (intent?.getStringExtra("extra_from_package") == KeyMessengerService.GESTURE_PKG) {
            SnapKeyService.snapLongPress(this)
        } else {
            Log.w(TAG, "ignored start without the gesture app's extra")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "SnapKeyLongPress"
        private const val CHANNEL = "long_press"
    }
}
