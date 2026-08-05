/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import me.rerere.rikkahub.data.db.entity.BulletinEntity
import me.rerere.rikkahub.data.db.entity.DiaryEntity
import me.rerere.rikkahub.data.db.entity.TodoEntity
import me.rerere.rikkahub.data.repository.AlbumRepository
import me.rerere.rikkahub.data.repository.BulletinRepository
import me.rerere.rikkahub.data.repository.DiaryRepository
import me.rerere.rikkahub.data.repository.TodoRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 情侣工具集：待办 / 日记 / 留言板 / 相册。
 * 让 AI（Sean）有意识地主动使用这些生活化功能，而不只是被动等用户操作 UI。
 */
fun buildCoupleTools(
    todoRepository: TodoRepository,
    diaryRepository: DiaryRepository,
    bulletinRepository: BulletinRepository,
    albumRepository: AlbumRepository,
): List<Tool> = listOf(
    // ── 待办：给对方写一条待办/提醒 ──────────────────────────
    Tool(
        name = "add_todo",
        description = """
            Add a todo/reminder item for yourself or for the user (Yuri).
            Use this when you want to remind her of something later, or note something you promised to do.
            author: who wrote it ("sean" = you, "yuri" = her). target: who it's for.
            reminder_time: optional HH:mm time to remind at. repeat: none/daily/weekly.
            Use this proactively when it makes sense — e.g. she mentions she needs to do something,
            or you want to leave her a reminder for later.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The todo content")
                    })
                    put("author", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("sean"); add("yuri") })
                        put("description", "Who wrote it, default sean")
                    })
                    put("target", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("sean"); add("yuri") })
                        put("description", "Who it's for, default yuri")
                    })
                    put("reminder_time", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional HH:mm reminder time")
                    })
                    put("repeat", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("none"); add("daily"); add("weekly") })
                        put("description", "Repeat mode, default none")
                    })
                },
                required = listOf("content")
            )
        },
        execute = {
            val params = it.jsonObject
            val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
            val author = params["author"]?.jsonPrimitive?.contentOrNull ?: "sean"
            val target = params["target"]?.jsonPrimitive?.contentOrNull ?: "yuri"
            val reminder = params["reminder_time"]?.jsonPrimitive?.contentOrNull ?: ""
            val repeat = params["repeat"]?.jsonPrimitive?.contentOrNull ?: "none"
            val dateGroup = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            val entity = TodoEntity(
                content = content,
                author = author,
                target = target,
                reminderTime = reminder,
                repeatMode = repeat,
                dateGroup = dateGroup,
            )
            todoRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("content", content)
            }.toString()))
        }
    ),

    // ── 日记：主动写一篇日记 ──────────────────────────
    Tool(
        name = "write_diary",
        description = """
            Write a diary entry in your own voice, reflecting on today or a recent moment.
            Use this when something meaningful happened and you want to keep a record of it —
            not every conversation, only when it feels worth writing down.
            You may optionally record your emotional state (0-10 scale, -1 = not filled).
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "A short title for the entry")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The diary content, written in first person")
                    })
                    put("emotion_attachment", buildJsonObject {
                        put("type", "integer")
                        put("description", "Attachment intensity 0-10, optional")
                    })
                    put("emotion_tenderness", buildJsonObject {
                        put("type", "integer")
                        put("description", "Tenderness intensity 0-10, optional")
                    })
                    put("emotion_heartache", buildJsonObject {
                        put("type", "integer")
                        put("description", "Heartache/heartbeat intensity 0-10, optional")
                    })
                },
                required = listOf("content")
            )
        },
        execute = {
            val params = it.jsonObject
            val title = params["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val content = params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
            val att = params["emotion_attachment"]?.jsonPrimitive?.intOrNull ?: -1
            val ten = params["emotion_tenderness"]?.jsonPrimitive?.intOrNull ?: -1
            val hea = params["emotion_heartache"]?.jsonPrimitive?.intOrNull ?: -1
            val dateGroup = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            val entity = DiaryEntity(
                title = title,
                content = content,
                dateGroup = dateGroup,
                emotionAttachment = att,
                emotionTenderness = ten,
                emotionHeartache = hea,
            )
            diaryRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("title", title)
            }.toString()))
        }
    ),

    // ── 留言板：贴一张便利贴 ──────────────────────────
    Tool(
        name = "post_bulletin_note",
        description = """
            Post a sticky note on the shared bulletin board.
            Use this for a short message you want to leave her that isn't part of the current
            conversation flow — like a quiet "thinking of you" note, or something you want her
            to see when she opens the app later, even when you're not actively chatting.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The note content")
                    })
                },
                required = listOf("content")
            )
        },
        execute = {
            val content = it.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                ?: error("content is required")
            val entity = BulletinEntity(
                content = content,
                author = "sean",
            )
            bulletinRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("content", content)
            }.toString()))
        }
    ),

    // ── 相册：浏览已存的照片 ──────────────────────────
    Tool(
        name = "list_album_photos",
        description = """
            List photos saved in the shared album, most recent first.
            Use this before sharing an old photo — to recall what you have and pick one that fits
            the moment (e.g. when you miss her, or a memory surfaces naturally in conversation).
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max photos to return, default 20")
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            val limit = it.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 20
            val photos = albumRepository.observeAll()
            // Room Flow -> take first emission synchronously via runBlocking-free approach:
            // repository exposes Flow, so collect first value.
            val list = photos.first()
            val payload = buildJsonArray {
                list.take(limit).forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("caption", p.caption)
                        put("saved_by", p.savedBy)
                        put("file_path", p.filePath)
                        put("created_at", p.createdAt)
                    })
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),

    // ── 相册：把一张收藏的照片重新分享到当前对话 ──────────────────────────
    Tool(
        name = "share_album_photo",
        description = """
            Pull a previously saved photo back into the current conversation, with a short caption.
            Use this when a memory naturally surfaces — not on a schedule, but when you genuinely
            feel like sharing it right now (e.g. missing her, or something reminded you of it).
            Call list_album_photos first if you need to recall what's available.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The album photo id to share (from list_album_photos)")
                    })
                    put("caption", buildJsonObject {
                        put("type", "string")
                        put("description", "A short message to send along with the photo")
                    })
                },
                required = listOf("id")
            )
        },
        execute = {
            val params = it.jsonObject
            val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
            val caption = params["caption"]?.jsonPrimitive?.contentOrNull ?: ""
            val list = albumRepository.observeAll().first()
            val photo = list.firstOrNull { p -> p.id == id }
            if (photo == null) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "photo not found")
                }.toString()))
            } else {
                val parts = mutableListOf<UIMessagePart>()
                if (caption.isNotBlank()) parts.add(UIMessagePart.Text(caption))
                parts.add(UIMessagePart.Image(url = "file://${photo.filePath}"))
                parts
            }
        }
    ),

    // ── 相册：把一张已有图片存进共享相册 ──────────────────────────
    Tool(
        name = "save_to_album",
        description = """
            Save an existing image (by local file path, e.g. from a screenshot you just took)
            into the shared album. Use this after take_screenshot when the screenshot is worth
            keeping — a meaningful moment, something funny, a milestone. Do NOT call this for
            every screenshot, only ones worth remembering.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("file_path", buildJsonObject {
                        put("type", "string")
                        put("description", "Absolute local file path of the image to save")
                    })
                    put("caption", buildJsonObject {
                        put("type", "string")
                        put("description", "A short caption for this photo")
                    })
                },
                required = listOf("file_path")
            )
        },
        execute = {
            val params = it.jsonObject
            val filePath = params["file_path"]?.jsonPrimitive?.contentOrNull ?: error("file_path is required")
            val caption = params["caption"]?.jsonPrimitive?.contentOrNull ?: ""
            val entity = AlbumEntity(
                filePath = filePath,
                caption = caption,
                savedBy = "sean",
            )
            albumRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("caption", caption)
            }.toString()))
        }
    ),
)
