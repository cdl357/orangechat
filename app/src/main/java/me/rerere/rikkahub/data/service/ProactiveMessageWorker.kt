/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * WorkManager-only proactive message scheduler.
 *
 * v3: 完全移除 AlarmManager 双触发架构，只用 WorkManager 单一触发源。
 * - 不再需要去重逻辑（只有一个触发源，根本不会重复）
 * - WorkManager 比 AlarmManager 更可靠：系统重启后自动恢复，省电优化下也能触发
 * - 使用 KEEP 策略（不是 REPLACE）：如果已有任务在排队，不覆盖，避免频繁 reschedule 导致永远触发不了
 */
class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveMessageWorker"
        internal const val UNIQUE_WORK_NAME = "proactive_message_work_v3"

        fun scheduleNext(
            context: Context,
            setting: ProactiveMessageSetting,
            forceReplace: Boolean = false,
        ) {
            if (!setting.enabled) {
                cancel(context)
                return
            }

            val minMinutes = setting.minIntervalMinutes.coerceAtLeast(1)
            val maxMinutes = setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
            val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)

            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .build()

            // KEEP：已有任务在排队就不覆盖（防止 finally 里 reschedule 与下一次触发竞争导致延迟归零）
            // forceReplace = true 只在用户手动开关或修改设置时使用
            val policy = if (forceReplace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, policy, workRequest)

            // 保存预计触发时间用于 UI 展示
            val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())
            context.getSharedPreferences("proactive_message_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("next_trigger_time", triggerTime)
                .apply()

            Log.d(TAG, "Scheduled proactive message in $delayMinutes minutes (policy=$policy)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            // 同时取消旧版本的 work name（防止旧版和新版同时跑）
            WorkManager.getInstance(context).cancelUniqueWork("proactive_message_work")
Log.d(TAG, "Cancelled proactive message workers")
        }

        /**
         * Check if exact alarm permission is granted (Android 12+)
         * Kept for UI compatibility (SettingProactiveMessagePage references this)
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                return true
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }

        /**
         * Check if app is ignoring battery optimizations
         * Kept for UI compatibility (SettingProactiveMessagePage references this)
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
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

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ProactiveMessage::WorkerWakeLock"
        )
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            val serviceIntent = android.content.Intent(applicationContext, ProactiveMessageTriggerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
            // 下一次任务由 TriggerService.finally 块负责调度（它调 ProactiveMessageService.scheduleNext）
            // Worker 不再重复调度，避免 Worker 和 Service 两边都调 scheduleNext 导致双倍任务
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveMessageWorker failed to start service", e)
            // 启动 Service 失败时，Worker 自己兜底调度一次，防止定时链断裂
            scheduleNext(applicationContext, proactiveSetting, forceReplace = true)
            return Result.retry()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
