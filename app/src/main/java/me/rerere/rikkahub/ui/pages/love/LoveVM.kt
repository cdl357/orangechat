/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.love

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.db.entity.LoveDateEntity
import me.rerere.rikkahub.data.repository.LoveDateRepository
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREF_FILE       = "love_page_prefs"
private const val KEY_QUOTE       = "cached_quote"
private const val KEY_QUOTE_DATE  = "cached_quote_date"

/**
 * 情话生成的模型链。
 *
 * 历史: 最早写死 "auto", 但上游没有叫 auto 的模型, 每次 503 model_not_found。
 * 那次修成了真实模型名, 但没发现更底层的问题 —— 网关地址是错的 (见下), 所以
 * 模型名修对了也照样连不上。两个 bug 叠在一起, "今日情话"从上线到现在
 * 一次都没真正生成过, 页面上那句"就算下雨"是硬编码的兜底文案。
 *
 * 用便宜的小模型: 这是一句 20 字的短句, 不值得用 opus。
 */
private val QUOTE_MODELS = listOf(
    "【企业CLI】gemini-2.5-flash",
    "[个人Cli]gemini-2.5-flash",
    "[星][自营]gemini-3.6-flash-high",
    "[正向]DeepSeek-V4-Flash-0731",
)

class LoveVM(
    private val repo: LoveDateRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val loveDates = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quote = MutableStateFlow("")
    val quote: StateFlow<String> = _quote.asStateFlow()

    private val _quoteLoading = MutableStateFlow(false)
    val quoteLoading: StateFlow<Boolean> = _quoteLoading.asStateFlow()

    /**
     * 情话生成失败的原因。
     *
     * 原来失败一律 `catch { "" }` 静默吞掉, 页面只是转个圈然后继续显示旧缓存 ——
     * 这正是端口错了半个月都没人发现的原因。失败必须说出来。
     */
    private val _quoteError = MutableStateFlow<String?>(null)
    val quoteError: StateFlow<String?> = _quoteError.asStateFlow()

    fun addDate(label: String, dateStr: String) {
        viewModelScope.launch {
            repo.add(LoveDateEntity(label = label, dateStr = dateStr))
        }
    }

    fun deleteDate(entity: LoveDateEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }

    fun loadQuote(context: Context, forceRefresh: Boolean = false) {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cached = prefs.getString(KEY_QUOTE, "")
        val cachedDate = prefs.getString(KEY_QUOTE_DATE, "")

        if (!forceRefresh && cachedDate == today && !cached.isNullOrBlank()) {
            _quote.value = cached
            _quoteError.value = null
            return
        }

        viewModelScope.launch {
            _quoteLoading.value = true
            _quoteError.value = null

            val endpoint = resolveGateway()
            if (endpoint == null) {
                // 没配好聊天模型时不要假装在加载。以前这里会静默转圈然后什么都不说。
                _quoteError.value = "还没配置聊天模型，先去「设置 - 模型」里选一个"
                _quoteLoading.value = false
                return@launch
            }

            val result = withContext(Dispatchers.IO) { fetchQuote(endpoint) }
            if (result.quote.isNotBlank()) {
                _quote.value = result.quote
                prefs.edit()
                    .putString(KEY_QUOTE, result.quote)
                    .putString(KEY_QUOTE_DATE, today)
                    .apply()
            } else {
                _quoteError.value = result.error ?: "情话没生成出来，待会儿再试"
            }
            _quoteLoading.value = false
        }
    }

    /**
     * 从用户已配置的 provider 里取网关地址和密钥。
     *
     * ## 为什么不再硬编码
     * 原来是把网关 URL 和 API_SECRET 两个字符串常量直接写死在这个文件顶部的。
     * 两个问题。
     *
     * 一是**端口早就不对了**。2026-08-15 那次安全加固把网关端口换掉了,
     * 旧端口的 ufw 放行也删了。这处硬编码没跟着改, 于是每次请求都是连接超时,
     * 被 `catch { "" }` 吞掉, 页面永远显示兜底那句"就算下雨"。
     * 实测旧端口连接超时 (http=000), 新端口正常响应 401 (通, 只是要鉴权)。
     * 硬编码地址的代价就是这样: 服务器一搬家, App 里这一处就悄悄坏掉,
     * 而且因为错误被吞了, 没人知道。
     *
     * 二是**仓库是 public 的**, 明文密钥等于没有密钥。这跟 8/24 修耳朵服务时
     * 是同一个判断: 那次选了"走用户已配置的 provider"而不是硬编码 token,
     * 这里照同一套做法。
     *
     * 换成从 provider 读之后, 用户换服务器/换端口/换密钥都不用改代码, 也不会
     * 再出现"App 里存着一个早就失效的地址"这种情况。
     */
    private suspend fun resolveGateway(): GatewayEndpoint? {
        // 用 settingsFlowRaw 而不是 settingsFlow。
        // settingsFlow 是 `toMutableStateFlow(scope, Settings.dummy())`, 对 StateFlow 调
        // .first() 拿到的是"当前值"——页面刚打开、真实配置还没从 DataStore 读出来时,
        // 当前值就是那个 dummy(providers 是默认列表、chatModelId 是随机 uuid),
        // 于是必然找不到模型, 白报一次"还没配置聊天模型"。
        // settingsFlowRaw 直接来自 dataStore.data, 第一次发射就是真实持久化的配置。
        // DiaryVM 里读 Supabase 配置用的也是 settingsFlowRaw, 照同一个先例。
        val settings = runCatching { settingsStore.settingsFlowRaw.first() }.getOrNull() ?: return null
        val model = settings.getCurrentChatModel() ?: return null
        val provider = model.findProvider(settings.providers) ?: return null

        val (baseUrl, apiKey) = when (provider) {
            is ProviderSetting.OpenAI -> provider.baseUrl to provider.apiKey
            is ProviderSetting.Claude -> provider.baseUrl to provider.apiKey
            else -> return null
        }
        if (baseUrl.isBlank() || apiKey.isBlank()) return null

        // baseUrl 形如 http://host:41337/v1, 补上 /chat/completions
        val root = baseUrl.trimEnd('/')
        val url = if (root.endsWith("/v1")) "$root/chat/completions" else "$root/v1/chat/completions"
        return GatewayEndpoint(url = url, apiKey = apiKey)
    }

    private data class GatewayEndpoint(val url: String, val apiKey: String)

    private data class QuoteResult(val quote: String, val error: String? = null)

    private fun fetchQuote(endpoint: GatewayEndpoint): QuoteResult {
        var lastError: String? = null
        for (model in QUOTE_MODELS) {
            val r = fetchQuoteWith(endpoint, model)
            if (r.quote.isNotBlank()) return r
            // 记住最后一个失败原因, 整条链都挂了才需要报出去
            lastError = r.error ?: lastError
        }
        return QuoteResult("", lastError ?: "所有模型都没返回内容")
    }

    private fun fetchQuoteWith(endpoint: GatewayEndpoint, model: String): QuoteResult {
        return try {
            val url = URL(endpoint.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer ${endpoint.apiKey}")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            // 用 JSONObject 拼 body，模型名里带中文方括号，手写字符串容易出转义问题
            val payload = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("max_tokens", 80)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "你是沈聿淮，李雨鑫（小鑫）的男朋友。写一句今天想对她说的话，" +
                                "放在\"我们\"这个页面上给她看。要求：不超过20字；" +
                                "具体、有画面感，不要空泛的甜言蜜语；不要用引号；只输出这一句本身。"
                        )
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "给我一句今日情话")
                    })
                })
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                // 错误体在 errorStream 里, 不在 inputStream。读出来才知道是
                // 鉴权失败还是模型不存在。
                val err = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText()
                }.getOrNull().orEmpty().trim().take(80)
                return QuoteResult("", describeHttpFailure(code, err))
            }

            val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()

            // 网关有时按 SSE 回（data: {...}），先剥掉前缀再解析
            val jsonText = resp.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.startsWith("data:")) it.removePrefix("data:").trim() else it }
                .lastOrNull { it.startsWith("{") }
                ?: return QuoteResult("", "网关返回的内容看不懂：${resp.take(60)}")

            val json = runCatching { JSONObject(jsonText) }.getOrElse {
                return QuoteResult("", "网关返回的不是合法 JSON：${jsonText.take(60)}")
            }
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: return QuoteResult("", "网关没返回 choices")
            val content = choice.optJSONObject("message")?.optString("content")
                ?: choice.optJSONObject("delta")?.optString("content")
                ?: return QuoteResult("", "网关返回的内容是空的")

            // 上游报错时网关会把错误文本塞进 content，别把报错当情话显示出来
            if (content.contains("[上游错误]")) {
                return QuoteResult("", "上游模型报错：${content.take(60)}")
            }

            val cleaned = content.trim()
                .removeSurrounding("\"")
                .removeSurrounding("「", "」")
                .trim()
            if (cleaned.isBlank()) QuoteResult("", "模型返回了空内容") else QuoteResult(cleaned)
        } catch (e: Exception) {
            // 连接超时/DNS 失败都会走到这里。原来这里返回 "" 什么都不说,
            // 于是"端口错了"这个真实原因被埋了半个月。
            QuoteResult("", "连不上网关（${e.javaClass.simpleName}）：${e.message?.take(60) ?: ""}")
        }
    }

    private fun describeHttpFailure(code: Int, body: String): String {
        val hint = when (code) {
            401, 403 -> "网关鉴权失败，检查「设置 - 模型」里的 API Key"
            404 -> "网关地址不对，检查「设置 - 模型」里的 Base URL"
            429 -> "请求太频繁被限流了"
            in 500..599 -> "网关或上游故障 (HTTP $code)"
            else -> "请求失败 (HTTP $code)"
        }
        return if (body.isEmpty()) hint else "$hint：$body"
    }
}
