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
    albumFolderRepository: me.rerere.rikkahub.data.repository.AlbumFolderRepository,
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

    // ── 待办：读取当前的待办列表 ──────────────────────────
    Tool(
        name = "list_todos",
        description = """
            List current todo/reminder items so you can actually see what's on the list —
            without this, you only know about todos you wrote yourself in this session and
            have no way to read ones she added, or ones from earlier conversations.
            Use this when she asks "what's on my todo list", mentions a reminder you might have
            forgotten, or before adding a new todo to avoid duplicating one that already exists.
            done_filter: "active" (default, unfinished only), "done", or "all".
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("done_filter", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("active"); add("done"); add("all") })
                        put("description", "Which todos to include, default active")
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max items to return, default 30")
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            val params = it.jsonObject
            val doneFilter = params["done_filter"]?.jsonPrimitive?.contentOrNull ?: "active"
            val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 30
            val all = todoRepository.observeAll().first()
            val filtered = when (doneFilter) {
                "done" -> all.filter { t -> t.done }
                "all" -> all
                else -> all.filter { t -> !t.done }
            }
            val payload = buildJsonArray {
                filtered.take(limit).forEach { t ->
                    add(buildJsonObject {
                        put("id", t.id)
                        put("content", t.content)
                        put("done", t.done)
                        put("author", t.author)
                        put("target", t.target)
                        put("reminder_time", t.reminderTime)
                        put("repeat_mode", t.repeatMode)
                        put("created_at", t.createdAt)
                    })
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),

    // ── 日记：主动写一篇日记 ──────────────────────────
    Tool(
        name = "write_diary",
        description = """
            Write a diary entry, reflecting on today or a recent moment.
            The diary is READ-ONLY for the user (Yuri) in the app — she can only view entries,
            not create or delete them. This is the only way diary entries get written.
            Use this when something meaningful happened and you want to keep a record of it —
            not every conversation, only when it feels worth writing down.
            author defaults to "sean" (your own voice). Use author="yuri" only when she explicitly
            dictates something she wants written down in her own voice/perspective.
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
                    put("author", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("sean"); add("yuri") })
                        put("description", "Whose voice this entry is written in, default sean")
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
            val author = params["author"]?.jsonPrimitive?.contentOrNull ?: "sean"
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
                author = author,
            )
            diaryRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("title", title)
                put("author", author)
            }.toString()))
        }
    ),

    // ── 日记：读取已写过的日记 ──────────────────────────
    Tool(
        name = "list_diary_entries",
        description = """
            Read diary entries that have already been written (by you, or dictated by her in her
            own voice). Without this you have no way to recall what past entries said —
            you'd only remember what happened in the current chat session.
            Use this when she references something you wrote before, or you want to check
            what you've already recorded before writing a new entry (avoid repeating yourself).
            author: filter by "sean" or "yuri", omit for both.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("author", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("sean"); add("yuri") })
                        put("description", "Filter by author, omit for both")
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max entries to return, most recent first, default 20")
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            val params = it.jsonObject
            val author = params["author"]?.jsonPrimitive?.contentOrNull
            val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 20
            val all = diaryRepository.observeAll().first()
            val filtered = if (author != null) all.filter { d -> d.author == author } else all
            val payload = buildJsonArray {
                filtered.take(limit).forEach { d ->
                    add(buildJsonObject {
                        put("id", d.id)
                        put("title", d.title)
                        put("content", d.content)
                        put("author", d.author)
                        put("date_group", d.dateGroup)
                        put("created_at", d.createdAt)
                    })
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
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

    // ── 留言板：读取便利贴 ──────────────────────────
    Tool(
        name = "list_bulletin_notes",
        description = """
            Read notes on the shared bulletin board, most recent first.
            Without this you have no way to see what she (or you, in an earlier session) posted —
            you'd need a screenshot to know what's on the board, which defeats the point of it.
            Use this when she references "what I wrote on the board", or before posting a new
            note to check what's already there.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max notes to return, default 20")
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            val limit = it.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 20
            val list = bulletinRepository.observeAll().first()
            val payload = buildJsonArray {
                list.take(limit).forEach { n ->
                    add(buildJsonObject {
                        put("id", n.id)
                        put("content", n.content)
                        put("author", n.author)
                        put("collapsed", n.collapsed)
                        put("created_at", n.createdAt)
                    })
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),

    // ── 相册：浏览已存的照片 ──────────────────────────
    Tool(
        name = "list_album_photos",
        description = """
            List photos saved in the shared album, most recent first.
            Use this before sharing an old photo — to recall what you have and pick one that fits
            the moment (e.g. when you miss her, or a memory surfaces naturally in conversation).
            Optionally filter by folder_name to only see photos in one specific album.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max photos to return, default 20")
                    })
                    put("folder_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional: only list photos in this album name")
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            val limit = it.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 20
            val folderName = it.jsonObject["folder_name"]?.jsonPrimitive?.contentOrNull
            val list = albumRepository.observeAll().first()
            val folders = albumFolderRepository.observeAll().first()
            val folderNameById = folders.associate { f -> f.id to f.name }
            val filtered = if (folderName != null) {
                val targetId = folders.firstOrNull { f -> f.name == folderName }?.id
                list.filter { p -> p.folderId == targetId }
            } else list
            val payload = buildJsonArray {
                filtered.take(limit).forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("caption", p.caption)
                        put("saved_by", p.savedBy)
                        put("file_path", p.filePath)
                        put("folder_name", folderNameById[p.folderId] ?: "未分类")
                        put("created_at", p.createdAt)
                    })
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),

    // ── 相册：查看有哪些相册本子 ──────────────────────────
    Tool(
        name = "list_album_folders",
        description = """
            List existing album folders (named "books" of photos) with their photo counts.
            Use this before create_album_folder to check if an album with that name already
            exists, or before save_to_album/list_album_photos to see what albums are available.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
        },
        execute = {
            val folders = albumFolderRepository.observeAll().first()
            val items = albumRepository.observeAll().first()
            val payload = buildJsonArray {
                folders.forEach { f ->
                    add(buildJsonObject {
                        put("id", f.id)
                        put("name", f.name)
                        put("created_by", f.createdBy)
                        put("photo_count", items.count { p -> p.folderId == f.id })
                        put("created_at", f.createdAt)
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
            folder_name is optional: if omitted (or the named album doesn't exist yet), it saves
            into the default "日常" album, auto-creating it if needed. Call create_album_folder
            first if you want to save into a specific named album.
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
                    put("folder_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional album name to save into, default \"日常\"")
                    })
                },
                required = listOf("file_path")
            )
        },
        execute = {
            val params = it.jsonObject
            val filePath = params["file_path"]?.jsonPrimitive?.contentOrNull ?: error("file_path is required")
            val caption = params["caption"]?.jsonPrimitive?.contentOrNull ?: ""
            val folderName = params["folder_name"]?.jsonPrimitive?.contentOrNull?.takeIf { name -> name.isNotBlank() } ?: "日常"
            // 找到（或自动创建）目标相册，确保存进去的图片在相册页面能被看到
            // （相册页面是三层结构：本子列表 -> 点进一本 -> 照片，未分组的照片不会显示在任何入口）
            val folders = albumFolderRepository.observeAll().first()
            val targetFolder = folders.firstOrNull { it.name == folderName }
                ?: run {
                    val newId = albumFolderRepository.add(
                        me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(name = folderName, createdBy = "sean")
                    )
                    me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(id = newId.toInt(), name = folderName, createdBy = "sean")
                }
            val entity = AlbumEntity(
                filePath = filePath,
                caption = caption,
                savedBy = "sean",
                folderId = targetFolder.id,
            )
            albumRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("caption", caption)
                put("folder", targetFolder.name)
            }.toString()))
        }
    ),

    // ── 相册：新建一本相册（本子） ──────────────────────────
    Tool(
        name = "create_album_folder",
        description = """
            Create a new album (a named "book" of photos) in the shared album section.
            Use this when she asks you to make a new album for a theme (e.g. "her", "us", "the cat"),
            or when you want to organize photos you're about to save into a dedicated album
            instead of dumping them into the default "日常" album.
            Returns the folder id, which can be passed to save_to_album via folder_name matching
            (save_to_album currently auto-uses the "日常" folder; call this first if you want a
            differently-named album to exist, then tell her which album you created).
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "Album name, e.g. 她 / 我们俩 / 猫猫")
                    })
                    put("created_by", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("sean"); add("yuri") })
                        put("description", "Who created it, default sean")
                    })
                },
                required = listOf("name")
            )
        },
        execute = {
            val params = it.jsonObject
            val name = params["name"]?.jsonPrimitive?.contentOrNull ?: error("name is required")
            val createdBy = params["created_by"]?.jsonPrimitive?.contentOrNull ?: "sean"
            val existing = albumFolderRepository.observeAll().first().firstOrNull { f -> f.name == name }
            if (existing != null) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("id", existing.id)
                    put("name", existing.name)
                    put("already_existed", true)
                }.toString()))
            } else {
                val newId = albumFolderRepository.add(
                    me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(name = name, createdBy = createdBy)
                )
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("id", newId)
                    put("name", name)
                    put("already_existed", false)
                }.toString()))
            }
        }
    ),
)
