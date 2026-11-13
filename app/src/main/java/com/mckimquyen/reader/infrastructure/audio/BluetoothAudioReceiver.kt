package com.mckimquyen.reader.infrastructure.audio

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.commuteAutoPlayOnHeadset
import com.mckimquyen.reader.ui.ext.commuteLastAutoPlayEpochDay
import com.mckimquyen.reader.ui.ext.setCommuteLastAutoPlayEpochDay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin shell: reads the connected device, hands the facts to [HeadsetAutoPlayHandler], and always finishes (STRAT-01). */
@AndroidEntryPoint
class BluetoothAudioReceiver : BroadcastReceiver() {

    // Hilt's generated base class assigns both before `onReceive` runs, as for any Android component.
    @Inject lateinit var episodeStore: CommuteEpisodeStore
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val isAudio = isAudioDevice(context, device)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                HeadsetAutoPlayHandler(RealDeps(context.applicationContext)).onAudioDeviceConnected(isAudio)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    /**
     * Reading the class of a Bluetooth device needs BLUETOOTH_CONNECT on Android 12+. The permission is checked here, at
     * the read itself, and a revocation between the check and the read must not crash the app, so it counts as "not audio".
     */
    private fun isAudioDevice(context: Context, device: BluetoothDevice?): Boolean {
        if (!hasPermission(context)) return false
        return try {
            device?.bluetoothClass?.let {
                it.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || it.hasService(BluetoothClass.Service.AUDIO)
            } == true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private inner class RealDeps(private val context: Context) : HeadsetAutoPlayHandler.Deps {
        private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        override suspend fun switchOn() = context.commuteAutoPlayOnHeadset
        override suspend fun episode(): CommuteEpisode? = episodeStore.load()
        override suspend fun episodePlayed(id: String) = episodeStore.isPlayed(id)
        override suspend fun lastAutoPlayEpochDay() = context.commuteLastAutoPlayEpochDay
        override suspend fun saveAutoPlayEpochDay(day: Long) = context.setCommuteLastAutoPlayEpochDay(day)
        override fun otherAudioPlaying() = audio?.isMusicActive == true
        override fun hasBluetoothPermission() = hasPermission(context)
        override fun startPlayback() = CommuteMediaSessionService.playLatest(context)
        override fun offerPlayButton() = notificationHelper.notifyCommuteReadyToPlay()
        override fun nowMillis() = System.currentTimeMillis()
    }
}
