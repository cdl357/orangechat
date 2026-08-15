/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.StickerRepository
import java.io.File

/**
 * 共享表情包库工具：list_stickers 查看有哪些表情包（含名字/标签），
 * send_sticker 挑一个发出去。库跟"我们"页面里小鑫手动加的是同一份数据，
 * 双方共用，不是两套独立的东西。
 *
 * 表情包存储在 Supabase Storage 云端，通过 remoteUrl 访问。
 * 不再依赖本地文件——换手机/重装都不会丢。
 */
fun buildStickerTools(
    stickerRepository: StickerRepository,
): List<Tool> = listOf(
    Tool(
        name = "list_stickers",
        description = """
            List all stickers in the shared sticker library, with their name and tags.
            Use this to see what's available before send_sticker, so you can pick one
            that actually matches the emotion/context (e.g. tags containing "委屈" for sulking).
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
        },
        execute = {
            // 只列出有效的（有远程 URL 或本地文件存在的）
            val stickers = stickerRepository.observeAll().first().filter { s ->
                s.remoteUrl.isNotBlank() || (File(s.filePath).exists() && File(s.filePath).length() > 0L)
            }
            val payload = buildJsonArray {
                stickers.forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id)
                        put("name", s.name)
                        put("tags", s.tags)
                        put("added_by", s.addedBy)
                    })
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),

    Tool(
        name = "send_sticker",
        description = """
            Send a sticker from the shared sticker library into the current conversation.
            Provide the sticker id (from list_stickers). Call list_stickers first if you
            don't already know the id.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The sticker id to send (from list_stickers)")
                    })
                },
                required = listOf("id")
            )
        },
        execute = {
            val id = it.jsonObject["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
            val stickers = stickerRepository.observeAll().first()
            val sticker = stickers.firstOrNull { s -> s.id == id }

            if (sticker == null) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "sticker id not found in library")
                }.toString()))
            } else {
                // 优先用远程 URL，回退本地文件
                val url = sticker.remoteUrl.ifBlank { null }
                    ?: "file://${sticker.filePath}".takeIf {
                        File(sticker.filePath).exists() && File(sticker.filePath).length() > 0L
                    }
                if (url != null) {
                    listOf(UIMessagePart.Image(url = url))
                } else {
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("success", false)
                        put("error", "sticker file is missing and no cloud URL available; ask her to re-add it")
                    }.toString()))
                }
            }
        }
    ),

    Tool(
        name = "clean_broken_stickers",
        description = "Delete sticker records whose image file is missing or empty (broken/blank stickers). Use this when the user says her stickers have missing/blank images.",
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
        },
        execute = {
            val count = stickerRepository.cleanBroken()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("cleaned", count)
                put("message", if (count > 0) "已清理 " + count + " 个坏掉的表情包" else "没有坏掉的表情包，都很健康")
            }.toString()))
        }
    ),
)
