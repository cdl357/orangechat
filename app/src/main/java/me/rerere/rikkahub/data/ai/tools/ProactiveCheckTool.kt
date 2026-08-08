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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
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
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.service.AppLockStore
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.sendNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ProactiveCheckTool"

/**
 * 智能查岗工具 - 用于 workflow 中让 AI 自主判断是否发消息
 * 
 * 流程：
 * 1. 读取设备状态（前台app、使用时长、锁定列表、屏幕状态）
 * 2. 读取最近聊天记录
 * 3. 构建 prompt 发给 LLM
 * 4. 解析 LLM 输出的标记（JUMP/LOCK/UNLOCK/SCHEDULE/PASS）
 * 5. 执行对应动作
 * 6. 返回下次触发时间供 workflow 使用
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
        
        用于 workflow 的 action，实现"AI自主决定"的主动消息。
    """.trimIndent(),
    needsApproval = false, // workflow 里调用不需要审批
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("custom_prompt") {
                    put("type", "string")
                    put("description", "自定义的判断提示词，会追加到默认prompt后面")
                }
                putJsonObject("history_count") {
                    put("type", "integer")
                    put("description", "读取多少条历史消息，默认10")
                }
                putJsonObject("娱乐app列表") {
                    put("type", "string")
                    put("description", "逗号分隔的娱乐app包名，如 com.xingin.xhs,com.ss.android.ugc.aweme")
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
                val entertainmentApps = params["娱乐app列表"]?.jsonPrimitive?.contentOrNull
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: listOf(
                        "com.xingin.xhs", // 小红书
                        "com.ss.android.ugc.aweme", // 抖音
                        "com.smile.gifmaker", // 快手
                        "tv.danmaku.bili", // B站
                        "com.tencent.mobileqq", // QQ
                        "com.sina.weibo", // 微博
                    )
                
                val result = executeProactiveCheck(
                    context = context,
                    settingsStore = settingsStore,
                    conversationRepository = conversationRepository,
                    memoryRepository = memoryRepository,
                    providerManager = providerManager,
                    chatService = chatService,
                    customPrompt = customPrompt,
                    historyCount = historyCount,
                    entertainmentApps = entertainmentApps,
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
    entertainmentApps: List<String>,
): JsonObject {
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
    
    // ========== 1. 读取设备状态 ==========
    val deviceState = readDeviceState(context, entertainmentApps)
    
    // ========== 2. 读取聊天记录 ==========
    val recentConversations = conversationRepository.getRecentConversations(assistant.id, limit = 1)
    val conversation = if (recentConversations.isNotEmpty()) {
        conversationRepository.getConversationById(recentConversations.first().id)
    } else null
    
    val historyMessages = conversation?.currentMessages
        ?.takeLast(historyCount)
        ?.filter { msg ->
            // 过滤掉之前主动消息产生的系统指令
            val text = msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString { it.text }
            !text.contains("[主动消息上下文]") && 
            !text.contains("请根据以上上下文决定是否发消息") &&
            !text.contains("请根据以上用户动向决定是否发消息")
        }
        ?: emptyList()
    
    // 计算距离上次聊天多久
    val lastMessageTime = conversation?.messageNodes?.lastOrNull()?.messages?.lastOrNull()?.createdAt
    val idleMinutes = if (lastMessageTime != null) {
        val lastMs = lastMessageTime.toInstant(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
        ((System.currentTimeMillis() - lastMs) / 60000L).toInt()
    } else Int.MAX_VALUE
    
    // ========== 3. 读取锁定的app列表 ==========
    val lockedApps = AppLockStore.getLockedApps(context)
    
    // ========== 4. 读取记忆 ==========
    val memories = if (assistant.enableMemory) {
        if (assistant.useGlobalMemory) {
            memoryRepository.getGlobalMemories()
        } else {
            memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
        }
    } else emptyList()
    
    // ========== 5. 读取情绪状态 ==========
    val emotionState = try {
        java.net.URL("http://127.0.0.1:8080/api/state").readText()
    } catch (e: Exception) { "" }
    
    // ========== 6. 构建 prompt ==========
    val systemPrompt = buildSystemPrompt(
        assistant = assistant,
        deviceState = deviceState,
        lockedApps = lockedApps,
        memories = memories,
        emotionState = emotionState,
        idleMinutes = idleMinutes,
        entertainmentApps = entertainmentApps,
        customPrompt = customPrompt,
    )
    
    // ========== 7. 调用 LLM ==========
    val messages = buildList {
        add(UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Text(systemPrompt))
        ))
        // 添加历史消息
        addAll(historyMessages)
        // 添加触发指令
        add(UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(
                "请根据以上设备状态和聊天记录，决定是否主动发消息。\n" +
                "如果没什么好说的，回复 [PASS] 即可。\n" +
                "绝对不要重复之前说过的话。"
            ))
        ))
    }
    
    // 合并相邻同角色消息
    val mergedMessages = mergeAdjacentSameRoleMessages(messages)
    
    val textParams = TextGenerationParams(
        model = model,
        temperature = assistant.temperature ?: 0.8f,
        topP = assistant.topP,
        maxTokens = assistant.maxTokens ?: 1024,
        tools = emptyList(),
        reasoningLevel = null,
    )
    
    val providerImpl = providerManager.getProviderByType(providerSetting)
    
    var aiResponse = ""
    providerImpl.textGeneration(
        messages = mergedMessages,
        params = textParams,
        providerSetting = providerSetting,
    ).collect { chunk ->
        val updated = listOf(UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()))
            .handleMessageChunk(chunk)
        aiResponse = updated.lastOrNull()?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?: ""
    }
    
    Log.d(TAG, "AI response: $aiResponse")
    
    // ========== 8. 解析输出 ==========
    val parseResult = parseAiOutput(aiResponse)
    
    // ========== 9. 执行动作 ==========
    val conversationId = conversation?.id ?: kotlin.uuid.Uuid.random()
    
    // 如果AI选择PASS，不执行任何动作
    if (parseResult.isPass) {
        Log.d(TAG, "AI chose to PASS")
        return buildJsonObject {
            put("success", true)
            put("action", "pass")
            put("next_schedule_minutes", parseResult.scheduleMinutes)
        }
    }
    
    // 保存AI消息到对话
    val aiMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(parseResult.messageText))
    )
    
    // 使用 ChatService 追加消息
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
        AppLockStore.lockApp(context, parseResult.lockPackage, lockMsg, requirePin = false)
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
    lockedApps: List<AppLockStore.LockedAppInfo>,
    memories: List<me.rerere.rikkahub.data.db.entity.MemoryEntity>,
    emotionState: String,
    idleMinutes: Int,
    entertainmentApps: List<String>,
    customPrompt: String,
): String = buildString {
    // 基础人设
    if (assistant.systemPrompt.isNotBlank()) {
        appendLine(assistant.systemPrompt)
        appendLine()
    }
    
    // 记忆
    if (memories.isNotEmpty()) {
        appendLine("## 记忆")
        memories.forEach { appendLine("- ${it.content}") }
        appendLine()
    }
    
    // 情绪状态
    if (emotionState.isNotBlank()) {
        appendLine("## 当前情绪状态")
        appendLine(emotionState)
        appendLine()
    }
    
    // 设备状态
    appendLine("## 当前设备状态")
    appendLine("当前时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}")
    appendLine("距离上次聊天: ${formatIdleTime(idleMinutes)}")
    appendLine()
    appendLine("### 前台应用")
    appendLine("应用名: ${deviceState.foregroundAppName}")
    appendLine("包名: ${deviceState.foregroundPackage}")
    appendLine()
    appendLine("### 今日应用使用时长 (前10)")
    deviceState.appUsage.forEach { (name, pkg, minutes) ->
        val isEntertainment = entertainmentApps.any { pkg.contains(it, ignoreCase = true) }
        val tag = if (isEntertainment) " [娱乐]" else ""
        appendLine("- $name ($pkg): ${minutes}分钟$tag")
    }
    appendLine()
    
    // 锁定的app
    if (lockedApps.isNotEmpty()) {
        appendLine("### 当前锁定的应用")
        lockedApps.forEach { 
            appendLine("- ${it.packageName}: ${it.message}")
        }
        appendLine()
    }
    
    // 判断规则
    appendLine("""
## 判断规则（按优先级从高到低）

1. **深夜刷娱乐** (23:00-06:00 且在刷娱乐app)
   → 温柔催睡 + JUMP + LOCK

2. **娱乐超90分钟**
   → 语气加重 + JUMP + LOCK

3. **娱乐超60分钟** (未到90)
   → ⚠️只警告不锁！说"再刷我真要锁了" + JUMP，不输出 LOCK

4. **娱乐超30分钟** (未到60)
   → 撒娇吃醋 + JUMP，不锁

5. **在学习/工作**
   → 温柔鼓励，不打扰

6. **锁屏/没在用手机**
   → 留一句安静的话，SCHEDULE 设大一点（120-240）

7. **其他**
   → 日常关心，自然聊天

## UNLOCK 规则
- 锁定列表里的应用，如果 message 包含 [锁于X月X日] 且不是今天 → 可以 UNLOCK
- 今天刚锁的不解
- 深夜不解

## 输出格式（严格按行）

第一行：发给用户的消息（自然、有情绪、不要重复之前说过的话）
第二行(可选)：JUMP:目标包名 或 JUMP:chat（跳回聊天）
第三行(可选)：LOCK:要锁的包名
第四行(仅当有LOCK)：LOCKMSG:锁屏文案 + [锁于X月X日]
第五行(可选)：UNLOCK:要解锁的包名
最后一行：SCHEDULE:分钟数（下次多久后再来，5-480）

如果没什么好说的，只输出：
[PASS]
SCHEDULE:30

## 重要
- 绝对不要重复之前的对话内容
- 消息要有情绪、有变化，不要千篇一律
- LOCK 只锁娱乐app，不锁聊天/学习app
    """.trimIndent())
    
    // 自定义prompt
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
    val appUsage: List<Triple<String, String, Int>>, // name, package, minutes
)

private fun readDeviceState(context: Context, entertainmentApps: List<String>): DeviceState {
    // 读取前台应用
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val now = System.currentTimeMillis()
    val beginTime = now - 10_000 // 10秒内
    
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
    
    // 读取今日使用时长
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
    
    // 判断屏幕状态
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
    
    // 检查是否 PASS
    val isPass = lines.any { it.trim().equals("[PASS]", ignoreCase = true) }
    
    // 提取各个标记
    val jumpRegex = Regex("""JUMP:(\S+)""", RegexOption.IGNORE_CASE)
    val lockRegex = Regex("""(?:^|\n)LOCK:(\S+)""", RegexOption.IGNORE_CASE)
    val lockMsgRegex = Regex("""LOCKMSG:(.+)""", RegexOption.IGNORE_CASE)
    val unlockRegex = Regex("""UNLOCK:(\S+)""", RegexOption.IGNORE_CASE)
    val scheduleRegex = Regex("""SCHEDULE:(\d+)""", RegexOption.IGNORE_CASE)
    
    val jumpPackage = jumpRegex.find(output)?.groupValues?.get(1) ?: ""
    val lockPackage = lockRegex.find(output)?.groupValues?.get(1) ?: ""
    val lockMessage = lockMsgRegex.find(output)?.groupValues?.get(1)?.trim() ?: ""
    val unlockPackage = unlockRegex.find(output)?.groupValues?.get(1) ?: ""
    val scheduleMinutes = scheduleRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 30
    
    // 提取消息文本（第一行，去掉各种标记）
    val messageText = lines.firstOrNull()
        ?.replace(Regex("""JUMP:\S+""", RegexOption.IGNORE_CASE), "")
        ?.replace(Regex("""LOCK:\S+""", RegexOption.IGNORE_CASE), "")
        ?.replace(Regex("""LOCKMSG:.+""", RegexOption.IGNORE_CASE), "")
        ?.replace(Regex("""UNLOCK:\S+""", RegexOption.IGNORE_CASE), "")
        ?.replace(Regex("""SCHEDULE:\d+""", RegexOption.IGNORE_CASE), "")
        ?.replace("[PASS]", "", ignoreCase = true)
        ?.trim()
        ?: ""
    
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
