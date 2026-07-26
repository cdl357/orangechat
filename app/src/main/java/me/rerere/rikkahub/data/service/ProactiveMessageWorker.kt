/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * WorkManager-based fallback for proactive message scheduling.
 * More reliable than AlarmManager on devices with aggressive battery optimization.
 * 
 * FIX: Added deduplication check — if AlarmManager already triggered within the
 * expected window, WorkManager skips its trigger to prevent duplicate messages.
 */
class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveMessageWorker"
        private const val UNIQUE_WORK_NAME = "proactive_message_work"

        fun scheduleNext(context: Context, setting: me.rerere.rikkahub.data.datastore.ProactiveMessageSetting) {
            if (!setting.enabled) {
                cancel(context)
                return
            }

            val minMinutes = setting.minIntervalMinutes.coerceAtLeast(1)
            val maxMinutes = setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
            val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)

            // Add extra buffer (2 minutes) so WorkManager fires AFTER AlarmManager
            // This ensures AlarmManager is the primary trigger and WorkManager only
            // fires if AlarmManager was killed by the system
            val bufferMinutes = 2
            val totalDelayMinutes = delayMinutes + bufferMinutes

            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInitialDelay(totalDelayMinutes.toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            // Also save trigger time to SharedPreferences for UI display
            // Use the original delayMinutes (without buffer) for display purposes
            val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())
            context.getSharedPreferences("proactive_message_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("next_trigger_time", triggerTime)
                .apply()

            Log.d(TAG, "Scheduled WorkManager proactive message in $totalDelayMinutes minutes (delay=$delayMinutes + buffer=$bufferMinutes)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d(TAG, "Cancelled WorkManager proactive message")
        }

        /**
         * Check if exact alarm permission is granted (Android 12+)
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }

        /**
         * Check if app is ignoring battery optimizations
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ProactiveMessageWorker triggered")

        val settingsStore = org.koin.core.context.GlobalContext.get().get<SettingsStore>()
        val settings = settingsStore.settingsFlow.first()
        val proactiveSetting = settings.proactiveMessageSetting

        if (!proactiveSetting.enabled) {
            Log.d(TAG, "Proactive message disabled, skipping")
            return Result.success()
        }

        // === DEDUPLICATION CHECK ===
        // If AlarmManager already triggered recently (within minInterval), skip this trigger.
        // This prevents the duplicate message problem where both AlarmManager and WorkManager
        // fire within a short window.
        val prefs = applicationContext.getSharedPreferences(
            ProactiveMessageService.PREFS_NAME, Context.MODE_PRIVATE
        )
        val lastTriggeredTime = prefs.getLong("last_triggered_time", 0L)
        val minIntervalMs = proactiveSetting.minIntervalMinutes.coerceAtLeast(1) * 60 * 1000L
        val timeSinceLastTrigger = System.currentTimeMillis() - lastTriggeredTime

        if (timeSinceLastTrigger < minIntervalMs) {
            Log.d(TAG, "AlarmManager already triggered ${timeSinceLastTrigger/1000}s ago (< minInterval ${minIntervalMs/1000}s), skipping WorkManager trigger")
            // Still schedule the next one
            scheduleNext(applicationContext, proactiveSetting)
            return Result.success()
        }
        // === END DEDUPLICATION CHECK ===

        // Acquire a wake lock for the duration of the work
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ProactiveMessage::WorkerWakeLock"
        )
        wakeLock.acquire(5 * 60 * 1000L) // 5 minutes max

        try {
            // Delegate to the existing trigger service logic
            // Start the foreground service which handles the actual AI generation
            val serviceIntent = android.content.Intent(applicationContext, ProactiveMessageTriggerService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }

            // Schedule the next trigger via WorkManager
            scheduleNext(applicationContext, proactiveSetting)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveMessageWorker failed", e)
            // Schedule next even on failure
            scheduleNext(applicationContext, proactiveSetting)
            return Result.retry()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
