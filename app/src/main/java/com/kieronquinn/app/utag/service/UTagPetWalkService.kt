package com.kieronquinn.app.utag.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.model.petwalk.PetWalkGeometry
import com.kieronquinn.app.utag.repositories.NotificationRepository
import com.kieronquinn.app.utag.repositories.NotificationRepository.NotificationChannel
import com.kieronquinn.app.utag.repositories.NotificationRepository.NotificationId
import com.kieronquinn.app.utag.repositories.NotificationRepository.PendingIntentId
import com.kieronquinn.app.utag.repositories.PetWalkRepository
import com.kieronquinn.app.utag.repositories.SettingsRepository
import com.kieronquinn.app.utag.ui.activities.MainActivity
import com.kieronquinn.app.utag.utils.extensions.broadcastReceiverAsFlow
import com.kieronquinn.app.utag.utils.extensions.whenCreated
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Samples the phone and the selected tag while a local pet walk is open.
 * Does not call the SmartThings pet-walking flag. That flag is what blocks Find my Device.
 */
class UTagPetWalkService: LifecycleService() {

    companion object {
        const val ACTION_STOP = "com.kieronquinn.app.utag.petwalk.STOP"
        const val SAMPLE_INTERVAL_MS = 30_000L
        private const val EXTRA_WALK_ID = "walk_id"

        fun start(context: Context, walkId: Long) {
            val intent = Intent(context, UTagPetWalkService::class.java).apply {
                putExtra(EXTRA_WALK_ID, walkId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.sendBroadcast(stopIntent(context))
            context.stopService(Intent(context, UTagPetWalkService::class.java))
        }

        private fun stopIntent(context: Context): Intent {
            return Intent(ACTION_STOP).apply {
                setPackage(context.packageName)
            }
        }
    }

    private val petWalkRepository by inject<PetWalkRepository>()
    private val settingsRepository by inject<SettingsRepository>()
    private val notificationRepository by inject<NotificationRepository>()

    private var recordingJob: Job? = null
    private var currentWalkId: Long? = null

    override fun onCreate() {
        super.onCreate()
        setupStopListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!promoteToForeground(getString(R.string.notification_pet_walk_starting))) {
            stopSelf()
            return START_NOT_STICKY
        }
        val requested = intent?.getLongExtra(EXTRA_WALK_ID, -1L) ?: -1L
        lifecycleScope.launch {
            val walkId = if (requested > 0L) {
                requested
            } else {
                petWalkRepository.activeWalk()?.id
            }
            if (walkId == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            begin(walkId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        recordingJob?.cancel()
        super.onDestroy()
    }

    private fun setupStopListener() = whenCreated {
        broadcastReceiverAsFlow(IntentFilter(ACTION_STOP)).collect {
            val walkId = currentWalkId
            recordingJob?.cancel()
            if (walkId != null) {
                petWalkRepository.stopWalk(walkId)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun begin(walkId: Long) {
        if (recordingJob?.isActive == true && currentWalkId == walkId) return
        recordingJob?.cancel()
        currentWalkId = walkId
        recordingJob = lifecycleScope.launch {
            record(walkId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun record(walkId: Long) {
        while (currentCoroutineContext().isActive) {
            val walk = petWalkRepository.activeWalk()
            if (walk == null || walk.id != walkId || walk.endedAt != null) return
            if (!settingsRepository.petWalkingExperimentalEnabled.get()) {
                petWalkRepository.stopWalk(walkId)
                return
            }
            try {
                val draft = petWalkRepository.captureDraft(walk.deviceId)
                petWalkRepository.recordSample(walkId, draft)
                val summary = petWalkRepository.snapshot(walkId)?.summary
                val content = if (summary == null) {
                    walk.deviceLabel
                } else {
                    getString(
                        R.string.notification_pet_walk_content,
                        walk.deviceLabel,
                        PetWalkGeometry.formatDistance(summary.phoneDistanceMetres),
                        summary.locatedTagSamples
                    )
                }
                promoteToForeground(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the walk open. The next interval tries again.
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun promoteToForeground(content: String): Boolean {
        val notification = notificationRepository.createNotification(NotificationChannel.PET_WALK) {
            it.setContentTitle(getString(R.string.notification_pet_walk_title))
            it.setContentText(content)
            it.setSmallIcon(R.drawable.ic_notification)
            it.setOngoing(true)
            it.setContentIntent(openAppIntent())
            it.addAction(
                0,
                getString(R.string.notification_pet_walk_stop),
                stopPendingIntent()
            )
        }
        return try {
            startForeground(
                NotificationId.PET_WALK.ordinal,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            PendingIntentId.PET_WALK.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            PendingIntentId.PET_WALK_STOP.ordinal,
            Intent(ACTION_STOP).apply { setPackage(packageName) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

}
