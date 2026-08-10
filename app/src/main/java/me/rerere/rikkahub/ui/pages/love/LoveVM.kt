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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val GATEWAY_URL     = "http://134.175.7.196:10000/v1/chat/completions"
private const val API_SECRET      = "shenyuhuailiyuxin0709bendansyhsxdw"

// 情话生成的模型链。原来写死 "auto"，但聚梦上游没有叫 auto 的模型，
// 每次都返回 503 model_not_found → catch 吞掉 → 页面永远显示硬编码那句兜底文案，
// 也就是说"今日情话"从上线到现在一次都没真正生成过。
// 改成按顺序试真实模型名，中转站里某个模型的号挂了还能换下一个。
private val QUOTE_MODELS = listOf(
    "【企业CLI】gemini-2.5-flash",
    "[个人Cli]gemini-2.5-flash",
    "[K2]claude-sonnet-4-6",
    "[正向]deepseek-v4-flash",
)

class LoveVM(private val repo: LoveDateRepository) : ViewModel() {

    val loveDates = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quote = MutableStateFlow("")
    val quote: StateFlow<String> = _quote.asStateFlow()

    private val _quoteLoading = MutableStateFlow(false)
    val quoteLoading: StateFlow<Boolean> = _quoteLoading.asStateFlow()

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
            return
        }

        viewModelScope.launch {
            _quoteLoading.value = true
            val result = withContext(Dispatchers.IO) { fetchQuote() }
            if (result.isNotBlank()) {
                _quote.value = result
                prefs.edit()
                    .putString(KEY_QUOTE, result)
                    .putString(KEY_QUOTE_DATE, today)
                    .apply()
            }
            _quoteLoading.value = false
        }
    }

    private fun fetchQuote(): String {
        for (model in QUOTE_MODELS) {
            val r = fetchQuoteWith(model)
            if (r.isNotBlank()) return r
        }
        return ""
    }

    private fun fetchQuoteWith(model: String): String {
        return try {
            val url = URL(GATEWAY_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $API_SECRET")
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

            if (conn.responseCode !in 200..299) return ""
            val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()

            // 网关有时按 SSE 回（data: {...}），先剥掉前缀再解析
            val jsonText = resp.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.startsWith("data:")) it.removePrefix("data:").trim() else it }
                .lastOrNull { it.startsWith("{") } ?: return ""

            val json = JSONObject(jsonText)
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return ""
            val content = choice.optJSONObject("message")?.optString("content")
                ?: choice.optJSONObject("delta")?.optString("content")
                ?: return ""

            // 上游报错时网关会把错误文本塞进 content，别把报错当情话显示出来
            if (content.contains("[上游错误]") || content.contains("error")) return ""

            content.trim()
                .removeSurrounding("\"")
                .removeSurrounding("「", "」")
                .trim()
        } catch (e: Exception) {
            ""
        }
    }
}
