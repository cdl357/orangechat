/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.LoveDateEntity
import me.rerere.rikkahub.data.repository.LoveDateRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 与 LoveVM 里用的是同一份 SharedPreferences 文件/key：LovePage 打开时会先检查缓存日期，
// 如果缓存日期就是今天就直接用缓存、不会再去请求生成新的一条。write_today_quote 写的是
// 同一份缓存，所以你主动写的情话会被当作"今天已经生成过"的结果直接展示，
// 而不需要改数据库结构或加新的读取逻辑。
private const val PREF_FILE = "love_page_prefs"
private const val KEY_QUOTE = "cached_quote"
private const val KEY_QUOTE_DATE = "cached_quote_date"

/**
 * "我们"页面（Love Page）相关工具：主动新增纪念日、主动写今日情话。
 * 此前这个页面只能用户手动点击才能改，你自己完全没有操作入口——现在补上。
 */
fun buildLoveTools(
    context: Context,
    loveDateRepository: LoveDateRepository,
): List<Tool> = listOf(
    // ── 新增一个重要的日子（纪念日/倒计时）──────────────────────────
    Tool(
        name = "add_love_date",
        description = """
            Add an important date (anniversary/countdown) to the "我们" (Us) page.
            Use this when she mentions a date worth remembering together, or you want to
            proactively add an upcoming occasion (her birthday, a trip, a milestone).
            date_str must be yyyy-MM-dd format.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("label", buildJsonObject {
                        put("type", "string")
                        put("description", "Event name, e.g. 一个月纪念日")
                    })
                    put("date_str", buildJsonObject {
                        put("type", "string")
                        put("description", "Date in yyyy-MM-dd format, e.g. 2026-09-09")
                    })
                },
                required = listOf("label", "date_str")
            )
        },
        execute = {
            val params = it.jsonObject
            val label = params["label"]?.jsonPrimitive?.contentOrNull ?: error("label is required")
            val dateStr = params["date_str"]?.jsonPrimitive?.contentOrNull ?: error("date_str is required")
            val validFormat = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { isLenient = false }.parse(dateStr)
            }.isSuccess
            if (!validFormat) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "date_str must be yyyy-MM-dd format")
                }.toString()))
            } else {
                loveDateRepository.add(LoveDateEntity(label = label, dateStr = dateStr))
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("label", label)
                    put("date_str", dateStr)
                }.toString()))
            }
        }
    ),

    // ── 主动写今日情话 ──────────────────────────
    Tool(
        name = "write_today_quote",
        description = """
            Write today's "情话" (a short romantic line shown on the "我们" page) yourself,
            instead of relying on the page's own auto-generated one. Use this when you have
            something genuine and specific to say today — not a generic filler line.
            Keep it short (under ~20 Chinese characters), warm, no quotation marks.
            This overwrites today's quote; she'll see it next time she opens the "我们" page.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("quote", buildJsonObject {
                        put("type", "string")
                        put("description", "The romantic line, short and warm, no quotation marks")
                    })
                },
                required = listOf("quote")
            )
        },
        execute = {
            val quote = it.jsonObject["quote"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: error("quote is required")
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUOTE, quote)
                .putString(KEY_QUOTE_DATE, today)
                .apply()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("quote", quote)
            }.toString()))
        }
    ),
)
