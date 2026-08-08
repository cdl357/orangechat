/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.service.AppLockStore
import me.rerere.rikkahub.service.ChatService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ProactiveCheckTool"

/**
 * 智能查岗工具 - 用于 workflow 中让 AI 自主判断是否发消息
 */
fun createProactiveCheckTool(
    context: Context,
    settingsStore: SettingsStore,
    conversationRepository: ConversationRepository,
    memoryRepository: MemoryRepository,
    providerManager: ProviderManager,
    chatService: ChatService,
): Tool = Tool(
    name = "proactive_check",
    description = """
        智能查岗工具，让AI根据用户手机状态自主判断是否发消息。
        
        工具会自动：
        1. 读取前台app、使用时长、锁定列表
        2. 读取最近聊天记录
        3. 让AI判断该说什么、要不要跳转/锁app
        4. 执行AI的决定
        
        返回值包含 next_schedule_minutes，可用于设置下次触发时间。
    """.trimIndent(),
    needsApproval = false,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("custom_prompt") {
                    put("type", "string")
                    put("description", "自定义的判断提示词")
                }
                putJsonObject("history_count") {
                    put("type", "integer")
                    put("description", "读取多少条历史消息，默认10")
                }
            }
        )
    },
    execute = { args ->
        withContext(Dispatchers.IO) {
            try {
                val params = args.jsonObject
                val customPrompt = params["custom_prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                val historyCount = params["history_count"]?.jsonPrimitive?.intOrNull ?: 10
                
                val result = executeProactiveCheck(
                    context = context,
                    settingsStore = settingsStore,
                    conversationRepository = conversationRepository,
                    memoryRepository = memoryRepository,
                    providerManager = providerManager,
                    chatService = chatService,
                    customPrompt = customPrompt,
                    historyCount = historyCount,
                )
                
                listOf(UIMessagePart.Text(result.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "ProactiveCheck failed", e)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                }.toString()))
            }
        }
    }
)

private suspend fun executeProactiveCheck(
    context: Context,
    settingsStore: SettingsStore,
    conversationRepository: ConversationRepository,
    memoryRepository: MemoryRepository,
    providerManager: ProviderManager,
    chatService: ChatService,
    customPrompt: String,
    historyCount: Int,
): kotlinx.serialization.json.JsonObject {
    val settings = settingsStore.settingsFlow.first()
    val assistant = settings.getCurrentAssistant()
    val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
        ?: return buildJsonObject {
            put("success", false)
            put("error", "No model configured")
        }
    
    val providerSetting = model.findProvider(settings.providers)
        ?: return buildJsonObject {
            put("success", false)
            put("error", "No provider found")
        }
    
    // 读取设备状态
    val deviceState = readDeviceState(context)
    
    // 读取聊天记录
    val recentConversations = conversationRepository.getRecentConversations(assistant.id, limit = 1)
    val conversation = if (recentConversations.isNotEmpty()) {
        conversationRepository.getConversationById(recentConversations.first().id)
    } else null
    
    val historyMessages = conversation?.currentMessages
        ?.takeLast(historyCount)
        ?.filter { msg ->
            val text = msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString { it.text }
            !text.contains("[主动消息上下文]") && 
            !text.contains("请根据以上上下文决定是否发消息")
        }
        ?: emptyList()
    
    // 计算距离上次聊天多久
    val lastMessageTime = conversation?.messageNodes?.lastOrNull()?.messages?.lastOrNull()?.createdAt
    val idleMinutes = if (lastMessageTime != null) {
        try {
            val lastMs = java.time.LocalDateTime.parse(lastMessageTime.toString())
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            ((System.currentTimeMillis() - lastMs) / 60000L).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse lastMessageTime", e)
            60 // 默认1小时
        }
    } else Int.MAX_VALUE
    
    // 读取锁定的app列表
    val lockedPackages = AppLockStore.getLockedPackages(context)
    
    // 读取记忆
    val memories: List<AssistantMemory> = if (assistant.enableMemory) {
        if (assistant.useGlobalMemory) {
            memoryRepository.getGlobalMemories()
        } else {
            memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
        }
    } else emptyList()
    
    // 读取情绪状态
    val emotionState = try {
        java.net.URL("http://127.0.0.1:8080/api/state").readText()
    } catch (e: Exception) { "" }
    
    // 构建 prompt
    val systemPrompt = buildSystemPrompt(
        assistant = assistant,
        deviceState = deviceState,
        lockedPackages = lockedPackages,
        memories = memories,
        emotionState = emotionState,
        idleMinutes = idleMinutes,
        customPrompt = customPrompt,
        context = context,
    )
    
    // 调用 LLM
    val messages = buildList {
        add(UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Text(systemPrompt))
        ))
        addAll(historyMessages)
        add(UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(
                "请根据以上设备状态和聊天记录，决定是否主动发消息。\n" +
                "如果没什么好说的，回复 [PASS] 即可。\n" +
                "绝对不要重复之前说过的话。"
            ))
        ))
    }
    
    val mergedMessages = mergeAdjacentSameRoleMessages(messages)
    
    val textParams = TextGenerationParams(
        model = model,
        temperature = assistant.temperature ?: 0.8f,
        topP = assistant.topP,
        maxTokens = assistant.maxTokens ?: 1024,
        tools = emptyList(),
        reasoningLevel = assistant.reasoningLevel,
    )
    
    val providerImpl = providerManager.getProviderByType(providerSetting)
    
    Log.d(TAG, "Calling streamText with ${mergedMessages.size} messages")
    Log.d(TAG, "Provider: ${providerSetting::class.simpleName}, Model: ${model.id}")
    var streamMessages = mergedMessages.toMutableList()
    providerImpl.streamText(
        providerSetting = providerSetting,
        messages = mergedMessages,
        params = textParams,
    ).collect { chunk: MessageChunk ->
        streamMessages = streamMessages.handleMessageChunk(chunk).toMutableList()
    }
    
    Log.d(TAG, "streamText finished, streamMessages size: ${streamMessages.size}")
    val aiResponse = streamMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        ?.parts
        ?.filterIsInstance<UIMessagePart.Text>()
        ?.joinToString("") { it.text }
        ?: ""
    
    
    Log.d(TAG, "AI response: $aiResponse")
    
    // 解析输出
    val parseResult = parseAiOutput(aiResponse)
    
    // 执行动作
    val conversationId = conversation?.id ?: kotlin.uuid.Uuid.random()
    
    if (parseResult.isPass) {
        Log.d(TAG, "AI chose to PASS")
        return buildJsonObject {
            put("success", true)
            put("action", "pass")
            put("next_schedule_minutes", parseResult.scheduleMinutes)
        }
    }
    
    // 如果消息为空，也当作 PASS
    if (parseResult.messageText.isBlank()) {
        Log.d(TAG, "Message is blank, treating as PASS")
        return buildJsonObject {
            put("success", true)
            put("action", "pass")
            put("reason", "empty_message")
            put("raw_response", aiResponse.take(200))
            put("next_schedule_minutes", parseResult.scheduleMinutes)
        }
    }
    
    // 保存AI消息到对话
    val aiMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(parseResult.messageText))
    )
    
    chatService.addProactiveMessage(conversationId, aiMessage)
    
    // 发送通知
    sendProactiveNotification(context, conversationId, assistant.name, parseResult.messageText)
    
    // 执行 JUMP
    if (parseResult.jumpPackage.isNotBlank()) {
        try {
            val jumpIntent = Intent(context, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("conversationId", conversationId.toString())
            }
            context.startActivity(jumpIntent)
            Log.d(TAG, "Jumped to conversation")
        } catch (e: Exception) {
            Log.e(TAG, "Jump failed", e)
        }
    }
    
    // 执行 LOCK
    if (parseResult.lockPackage.isNotBlank()) {
        val lockMsg = parseResult.lockMessage.ifBlank { 
            "被我锁了哦～ [锁于${SimpleDateFormat("M月d日", Locale.CHINA).format(Date())}]" 
        }
        AppLockStore.lockApp(context, parseResult.lockPackage)
        AppLockStore.setLockMessage(context, parseResult.lockPackage, lockMsg)
        AppLockStore.setRequirePin(context, parseResult.lockPackage, false)
        Log.d(TAG, "Locked app: ${parseResult.lockPackage}")
    }
    
    // 执行 UNLOCK
    if (parseResult.unlockPackage.isNotBlank()) {
        AppLockStore.unlockApp(context, parseResult.unlockPackage)
        Log.d(TAG, "Unlocked app: ${parseResult.unlockPackage}")
    }
    
    return buildJsonObject {
        put("success", true)
        put("action", "sent")
        put("message", parseResult.messageText)
        put("jump", parseResult.jumpPackage)
        put("lock", parseResult.lockPackage)
        put("unlock", parseResult.unlockPackage)
        put("next_schedule_minutes", parseResult.scheduleMinutes)
    }
}

private fun buildSystemPrompt(
    assistant: me.rerere.rikkahub.data.model.Assistant,
    deviceState: DeviceState,
    lockedPackages: Set<String>,
    memories: List<AssistantMemory>,
    emotionState: String,
    idleMinutes: Int,
    customPrompt: String,
    context: Context,
): String = buildString {
    if (assistant.systemPrompt.isNotBlank()) {
        appendLine(assistant.systemPrompt)
        appendLine()
    }
    
    if (memories.isNotEmpty()) {
        appendLine("## 记忆")
        memories.forEach { appendLine("- ${it.content}") }
        appendLine()
    }
    
    if (emotionState.isNotBlank()) {
        appendLine("## 当前情绪状态")
        appendLine(emotionState)
        appendLine()
    }
    
    appendLine("## 当前设备状态")
    appendLine("当前时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss E", Locale.CHINA).format(Date())}")
    appendLine("距离上次聊天: ${formatIdleTime(idleMinutes)}")
    appendLine()
    appendLine("### 前台应用")
    appendLine("应用名: ${deviceState.foregroundAppName}")
    appendLine("包名: ${deviceState.foregroundPackage}")
    appendLine()
    appendLine("### 今日应用使用时长")
    deviceState.appUsage.forEach { (name, pkg, minutes) ->
        appendLine("- $name ($pkg): ${minutes}分钟")
    }
    appendLine()
    
    if (lockedPackages.isNotEmpty()) {
        appendLine("### 当前锁定的应用")
        lockedPackages.forEach { pkg ->
            val msg = AppLockStore.getLockMessage(context, pkg) ?: ""
            appendLine("- $pkg: $msg")
        }
        appendLine()
    }
    
    appendLine("""
## 输出格式（严格按行，每行一个内容）

第一行：发给用户的消息（自然、有温度、不要太长）
第二行(可选)：JUMP:chat
第三行(可选)：LOCK:包名
第四行(仅当有LOCK)：LOCKMSG:锁屏文案 [锁于X月X日]
第五行(可选)：UNLOCK:包名
最后一行：SCHEDULE:分钟数（5-480，下次多久后再来）

如果没什么好说的，就只输出：
[PASS]
SCHEDULE:30

## 重要
- 消息要自然，像正常聊天一样
- 不要重复之前说过的话
- 根据时间和状态调整语气
    """.trimIndent())
    
    if (customPrompt.isNotBlank()) {
        appendLine()
        appendLine("## 额外要求")
        appendLine(customPrompt)
    }
}

private data class DeviceState(
    val foregroundAppName: String,
    val foregroundPackage: String,
    val isScreenOn: Boolean,
    val appUsage: List<Triple<String, String, Int>>,
)

private fun readDeviceState(context: Context): DeviceState {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val now = System.currentTimeMillis()
    val beginTime = now - 10_000
    
    val recentStats = usm.queryUsageStats(
        android.app.usage.UsageStatsManager.INTERVAL_DAILY,
        beginTime, now
    ).filter { it.lastTimeUsed > beginTime }
        .maxByOrNull { it.lastTimeUsed }
    
    val foregroundPackage = recentStats?.packageName ?: ""
    val foregroundAppName = try {
        if (foregroundPackage.isNotBlank()) {
            val appInfo = context.packageManager.getApplicationInfo(foregroundPackage, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } else "未知"
    } catch (e: Exception) { foregroundPackage }
    
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    
    val todayStats = usm.queryUsageStats(
        android.app.usage.UsageStatsManager.INTERVAL_DAILY,
        cal.timeInMillis, now
    ).filter { it.totalTimeInForeground > 0 }
        .sortedByDescending { it.totalTimeInForeground }
        .take(10)
    
    val appUsage = todayStats.map { stat ->
        val name = try {
            val appInfo = context.packageManager.getApplicationInfo(stat.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) { stat.packageName }
        Triple(name, stat.packageName, (stat.totalTimeInForeground / 60000).toInt())
    }
    
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val isScreenOn = powerManager.isInteractive
    
    return DeviceState(
        foregroundAppName = foregroundAppName,
        foregroundPackage = foregroundPackage,
        isScreenOn = isScreenOn,
        appUsage = appUsage,
    )
}

private data class ParseResult(
    val isPass: Boolean,
    val messageText: String,
    val jumpPackage: String,
    val lockPackage: String,
    val lockMessage: String,
    val unlockPackage: String,
    val scheduleMinutes: Int,
)

private fun parseAiOutput(output: String): ParseResult {
    val lines = output.trim().lines()
    
    val isPass = lines.any { it.trim().equals("[PASS]", ignoreCase = true) }
    
    val jumpRegex = Regex("""JUMP:(\S+)""", RegexOption.IGNORE_CASE)
    val lockRegex = Regex("""LOCK:(\S+)""", RegexOption.IGNORE_CASE)
    val lockMsgRegex = Regex("""LOCKMSG:(.+)""", RegexOption.IGNORE_CASE)
    val unlockRegex = Regex("""UNLOCK:(\S+)""", RegexOption.IGNORE_CASE)
    val scheduleRegex = Regex("""SCHEDULE:(\d+)""", RegexOption.IGNORE_CASE)
    
    val jumpPackage = jumpRegex.find(output)?.groupValues?.get(1) ?: ""
    val lockPackage = lockRegex.find(output)?.groupValues?.get(1) ?: ""
    val lockMessage = lockMsgRegex.find(output)?.groupValues?.get(1)?.trim() ?: ""
    val unlockPackage = unlockRegex.find(output)?.groupValues?.get(1) ?: ""
    val scheduleMinutes = scheduleRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 30
    
    // 提取消息：过滤掉所有标记行，剩下的就是消息内容
    val messageText = lines
        .filter { line ->
            val trimmed = line.trim()
            !trimmed.equals("[PASS]", ignoreCase = true) &&
            !trimmed.startsWith("JUMP:", ignoreCase = true) &&
            !trimmed.startsWith("LOCK:", ignoreCase = true) &&
            !trimmed.startsWith("LOCKMSG:", ignoreCase = true) &&
            !trimmed.startsWith("UNLOCK:", ignoreCase = true) &&
            !trimmed.startsWith("SCHEDULE:", ignoreCase = true) &&
            trimmed.isNotEmpty()
        }
        .joinToString("\n")
        .trim()
    
    return ParseResult(
        isPass = isPass,
        messageText = messageText,
        jumpPackage = if (jumpPackage == "chat") "" else jumpPackage,
        lockPackage = lockPackage,
        lockMessage = lockMessage,
        unlockPackage = unlockPackage,
        scheduleMinutes = scheduleMinutes.coerceIn(5, 480),
    )
}

private fun formatIdleTime(minutes: Int): String = when {
    minutes == Int.MAX_VALUE -> "很久没有聊天了"
    minutes > 24 * 60 -> "${minutes / (24 * 60)}天${(minutes % (24 * 60)) / 60}小时"
    minutes > 60 -> "${minutes / 60}小时${minutes % 60}分钟"
    else -> "${minutes}分钟"
}

private fun mergeAdjacentSameRoleMessages(messages: List<UIMessage>): List<UIMessage> {
    if (messages.size < 2) return messages
    return messages.fold(emptyList()) { acc, msg ->
        val prev = acc.lastOrNull()
        if (prev != null && prev.role == msg.role) {
            acc.dropLast(1) + prev.copy(parts = prev.parts + msg.parts)
        } else {
            acc + msg
        }
    }
}

private fun sendProactiveNotification(
    context: Context,
    conversationId: kotlin.uuid.Uuid,
    senderName: String,
    content: String,
) {
    val intent = Intent(context, RouteActivity::class.java).apply {
        putExtra("conversationId", conversationId.toString())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        context, conversationId.hashCode(), intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    
    val notification = androidx.core.app.NotificationCompat.Builder(
        context, 
        me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
    )
        .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
        .setContentTitle(senderName)
        .setContentText(content)
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    manager.notify(System.currentTimeMillis().toInt(), notification)
}
