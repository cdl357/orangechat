/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
            val stickers = stickerRepository.observeAll().first()
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
            if (sticker == null || !File(sticker.filePath).exists()) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "sticker not found")
                }.toString()))
            } else {
                listOf(UIMessagePart.Image(url = "file://${sticker.filePath}"))
            }
        }
    ),
)
