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
        return try {
            val url = URL(GATEWAY_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $API_SECRET")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val body = """
                {
                  "model": "auto",
                  "stream": false,
                  "max_tokens": 60,
                  "messages": [
                    {
                      "role": "system",
                      "content": "你是沈聿淮，李雨鑫的男朋友。生成一句简短的情话送给她，温柔自然，不超过20字，只输出这句话本身，不加引号不加解释。"
                    },
                    {
                      "role": "user",
                      "content": "给我一句今日情话"
                    }
                  ]
                }
            """.trimIndent()

            conn.outputStream.write(body.toByteArray())
            val resp = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(resp)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .trimStart('"')
                .trimEnd('"')
        } catch (e: Exception) {
            ""
        }
    }
}
