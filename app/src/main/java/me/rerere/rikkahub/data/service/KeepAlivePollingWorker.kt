/*
 * 橘瓣 OrangeChat - Sean 主动消息轮询
 * 每 15 分钟检查 Supabase chat_messages 是否有新的 assistant 消息
 * 有则注入本地对话并发送通知
 */
package me.rerere.rikkahub.data.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.sendNotification
import org.json.JSONArray
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

private const val TAG = "KeepAlivePolling"
private const val PREFS_NAME = "keepalive_polling_prefs"
private const val KEY_LAST_SEEN_ID = "last_seen_message_id"
private const val WORK_NAME = "keepalive_polling_v1"

private const val SUPA_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
private const val SUPA_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"
private const val ASSISTANT_ID = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e"

class KeepAlivePollingWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params), KoinComponent {

    private val settingsStore: SettingsStore by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val chatService: ChatService by inject()

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAlivePollingWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "KeepAlive polling scheduled (15 min)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "Polling check started")
        return try {
            runBlocking { checkForNewMessages() }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Polling failed", e)
            Result.retry()
        }
    }

    private suspend fun checkForNewMessages() {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenId = prefs.getLong(KEY_LAST_SEEN_ID, 0L)

        // 查 Supabase 是否有比 lastSeenId 新的 assistant 消息
        val urlStr = "$SUPA_URL/rest/v1/chat_messages" +
            "?assistant_id=eq.$ASSISTANT_ID" +
            "&role=eq.assistant" +
            "&id=gt.$lastSeenId" +
            "&order=id.asc" +
            "&limit=5" +
            "&select=id,content,created_at"

        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPA_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPA_KEY")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val body = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(body)

        if (arr.length() == 0) {
            Log.d(TAG, "No new messages")
            return
        }

        Log.d(TAG, "Found ${arr.length()} new message(s)")

        val settings = settingsStore.settingsFlowRaw.first()
        val assistantId = settings.assistantId

        // 找最近的对话
        val recentConvs = conversationRepo.getRecentConversations(assistantId, limit = 1)
        val convId = recentConvs.firstOrNull()?.id ?: return
        val conversation = conversationRepo.getConversationById(convId) ?: return

        var newLastSeenId = lastSeenId
        var latestContent = ""

        for (i in 0 until arr.length()) {
            val msg = arr.getJSONObject(i)
            val id = msg.getLong("id")
            val content = msg.optString("content", "")

            if (content.isBlank()) continue

            // 构建 UIMessage
            val uiMessage = UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text(content))
            )

            // 追加到本地对话
            val updatedConv = conversation.copy(
                messageNodes = conversation.messageNodes + uiMessage.toMessageNode()
            )
            chatService.updateConversationState(convId) { updatedConv }
            chatService.saveConversation(convId, updatedConv)

            newLastSeenId = maxOf(newLastSeenId, id)
            latestContent = content
            Log.d(TAG, "Injected message #$id: ${content.take(40)}")
        }

        // 更新 last_seen_id
        prefs.edit().putLong(KEY_LAST_SEEN_ID, newLastSeenId).apply()

        // 发通知
        if (latestContent.isNotBlank()) {
            val intent = Intent(applicationContext, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("conversationId", convId.toString())
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                9901,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            applicationContext.sendNotification(
                channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                notificationId = 9901
            ) {
                title = "Sean"
                content = latestContent.take(100)
                autoCancel = true
                useDefaults = true
                contentIntent = pendingIntent
                useBigTextStyle = true
            }
            Log.d(TAG, "Notification sent: ${latestContent.take(40)}")
        }
    }
}
