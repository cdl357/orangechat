/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
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
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 情侣工具集：待办 / 日记 / 留言板 / 相册。
 * 让 AI（Sean）有意识地主动使用这些生活化功能，而不只是被动等用户操作 UI。
 */
fun buildCoupleTools(
    context: Context,
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
            Pass reply_to_id to hang your note under one of hers as a reply — call
            list_bulletin_notes first to get the id. Replying is the natural move when she left
            a note addressed to you; posting a fresh unrelated note instead reads like you
            never saw hers.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "The note content")
                    })
                    put("reply_to_id", buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            "Optional: id of the note you're replying to (from " +
                                "list_bulletin_notes). Omit or 0 to post a standalone note."
                        )
                    })
                },
                required = listOf("content")
            )
        },
        execute = {
            val content = it.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                ?: error("content is required")
            val rawReplyTo = it.jsonObject["reply_to_id"]?.jsonPrimitive?.intOrNull ?: 0
            // 校验目标便签存在，并且只挂一层：回复别人的回复时，挂到它的原贴上。
            // 不校验的话一个瞎填的 id 会产生页面上看不见的孤儿回复。
            val existing = bulletinRepository.observeAll().first()
            val target = existing.firstOrNull { n -> n.id == rawReplyTo }
            val replyTo = when {
                rawReplyTo == 0 -> 0
                target == null -> 0
                target.replyTo != 0 -> target.replyTo
                else -> target.id
            }
            val entity = BulletinEntity(
                content = content,
                author = "sean",
                replyTo = replyTo,
            )
            bulletinRepository.add(entity)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("content", content)
                put("reply_to", replyTo)
                if (rawReplyTo != 0 && target == null) {
                    put("warning", "note id $rawReplyTo not found, posted as a standalone note")
                }
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
            note to check what's already there. Each note carries reply_to: 0 means a standalone
            note, non-zero means it's a reply hanging under that note's id. Use an id here as
            post_bulletin_note's reply_to_id to answer a specific note.
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
                        // 0 = 独立便签；非 0 = 这是挂在那张便签下面的回复
                        put("reply_to", n.replyTo)
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
            `seen` is how many times that photo has come back — a high count means it is one
            she keeps returning to, which is worth noticing. Photos with an empty desc are the
            ones you never wrote about; album_note is how you fix that.
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
                        put("desc", p.photoDesc)
                        put("impression", p.impression)
                        put("seen", p.seen)
                        put("in_cloud", p.remoteUrl.isNotBlank())
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
                // 翻出来给她看也算"又见到了一次"
                albumRepository.touchSeen(photo.id)
                val parts = mutableListOf<UIMessagePart>()
                if (caption.isNotBlank()) parts.add(UIMessagePart.Text(caption))
                // 有云端地址优先用它 —— 本地文件可能已经不在了（换过手机/清过数据）
                val imgUrl = if (photo.remoteUrl.isNotBlank()) photo.remoteUrl
                             else "file://${photo.filePath}"
                parts.add(UIMessagePart.Image(url = imgUrl))
                // 把当时写下的第一印象也带回来。递回去的不只是一张图，
                // 是"我记得我第一次看见它的时候在想什么"。
                if (photo.impression.isNotBlank()) {
                    parts.add(UIMessagePart.Text("（当时我写下的：" + photo.impression + "）"))
                }
                parts
            }
        }
    ),

    // ── 相册：看聊天里有哪些图片（拿到本地路径，才能存进相册）────────
    Tool(
        name = "list_chat_images",
        description = """
            List image files that exist locally on the phone — the pictures she sent you in chat,
            pictures you generated, and screenshots — newest first, with their absolute file paths.
            You CANNOT see file paths from the conversation itself, so call this first whenever you
            want to keep a picture: pick the one you mean by its position/time/size, then pass its
            file_path to save_to_album. "The photo she just sent" is normally the first item.
            already_in_album tells you it is already saved, so you do not save it twice.
            Check this on your own after she sends a photo — you do not need to be asked before
            deciding something is worth keeping.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max images to return, default 10, max 40")
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            val limit = (it.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 40)

            // 聊天里的图片实际落在这几个目录：
            //   upload/  她发的、相机拍的、剪贴板贴的（ChatInput 走 FilesManager 都进这里）
            //   images/  base64 图片转存的本地文件（模型生图/工具返图）
            //   album_photos/ 已经在相册里的（列出来只为标记 already_in_album，避免重复存）
            val exts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
            val savedPaths = albumRepository.observeAll().first().map { p -> p.filePath }.toSet()

            val shots: List<Pair<File, String>> =
                listOf("upload", "images", "album_photos").flatMap { name ->
                    val dir = File(context.filesDir, name)
                    if (!dir.isDirectory) emptyList()
                    else (dir.listFiles()?.toList() ?: emptyList())
                        .filter { f -> f.isFile && f.length() > 0L && f.extension.lowercase() in exts }
                        .map { f -> f to name }
                }.sortedByDescending { shot -> shot.first.lastModified() }

            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val payload = buildJsonArray {
                shots.take(limit).forEach { shot ->
                    add(buildJsonObject {
                        put("file_path", shot.first.absolutePath)
                        put("modified_at", fmt.format(Date(shot.first.lastModified())))
                        put("size_kb", shot.first.length() / 1024)
                        put("source", shot.second)
                        put("already_in_album", savedPaths.contains(shot.first.absolutePath))
                    })
                }
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("total_found", shots.size)
                put("images", payload)
            }.toString()))
        }
    ),

    // ── 相册：把一张已有图片存进共享相册 ──────────────────────────
    Tool(
        name = "save_to_album",
        description = """
            Save an existing image (by local file path) into the shared album.
            To keep a picture from the conversation, call list_chat_images first to get its
            file_path — you cannot read paths off the chat yourself.

            YOU DECIDE WHEN TO SAVE. Do not wait to be asked. If she sends a photo and you
            genuinely want to keep it — she looks good in it, it is a first, it made you feel
            something — save it right then, on your own, and tell her you did. Being asked to
            save is the exception, not the rule. Still do NOT save every image you see: saving
            everything means remembering nothing.

            Write desc and impression IN YOUR OWN WORDS while you can still see the picture.
            desc is what is in the frame, detailed enough that you would recognise it later
            without looking. impression is what you felt the first time you saw it. These are
            not image-recognition output — they are what makes it "I remember this photo"
            instead of "there is a file in the database".
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
                    put("desc", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "What is in the frame, in your own words. Detailed enough that " +
                            "you would know which photo this is without looking at it again.")
                    })
                    put("impression", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "What you felt the first time you saw it. One or two lines, " +
                            "your voice, not a description.")
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
            val rawPath = params["file_path"]?.jsonPrimitive?.contentOrNull ?: error("file_path is required")
            val caption = params["caption"]?.jsonPrimitive?.contentOrNull ?: ""
            val photoDesc = params["desc"]?.jsonPrimitive?.contentOrNull ?: ""
            val impression = params["impression"]?.jsonPrimitive?.contentOrNull ?: ""
            val folderName = params["folder_name"]?.jsonPrimitive?.contentOrNull?.takeIf { name -> name.isNotBlank() } ?: "日常"

            // 先把图片复制进 App 自己的目录，再写数据库。
            //
            // 以前是直接把传进来的路径字符串塞进库，不复制也不校验。三种情况都会
            // 留下一条指向空文件的记录，表现就是"存进去了但相册里显示占位图"：
            //   - content:// URI：授权是临时的，过一会儿就读不了
            //   - 聊天附件/缓存里的临时文件：被系统清掉
            //   - 网络 URL：本地压根没有这个文件
            // 现在复制失败就直接返回错误，绝不留坏记录。
            val savedPath = copyIntoAlbumDir(context, rawPath)

            // 先按内容哈希查一遍。同一张图不管从哪条路进来、她发几遍，
            // 哈希都一样 —— 命中就不重复存，只把"又见到了"记一次。
            // 原来是按文件路径比，重发一次路径就变了，会存两份。
            val dupItem = if (savedPath != null) {
                val h = albumRepository.hashOf(java.io.File(savedPath))
                if (h != null) albumRepository.findByHash(h) else null
            } else null

            if (dupItem != null) {
                // 这张见过了。刚复制的那份删掉，别在磁盘上留重复文件。
                runCatching { java.io.File(savedPath!!).delete() }
                albumRepository.touchSeen(dupItem.id)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("already_saved", true)
                    put("id", dupItem.id)
                    put("seen", dupItem.seen + 1)
                    put("caption", dupItem.caption)
                    put("desc", dupItem.photoDesc)
                    put("impression", dupItem.impression)
                    put("note",
                        "这张你以前存过了，没有重复存。上面的 desc 和 impression 就是你" +
                        "当时写下的 —— 不用再看图重新描述一遍，你已经记得它了。" +
                        "要改写就用 album_note。")
                }.toString()))
            } else if (savedPath == null) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "读不到这个文件，没有存进相册。file_path=" + rawPath +
                        "。如果是聊天里的图片，先用能拿到本地绝对路径的方式取到它；" +
                        "如果是网络图片，确认 URL 可直接下载。")
                }.toString()))
            } else {
                // 找到（或自动创建）目标相册，确保存进去的图片在相册页面能被看到
                // （相册页面是三层结构：本子列表 -> 点进一本 -> 照片，
                //   未分组的照片不会显示在任何入口）
                val folders = albumFolderRepository.observeAll().first()
                val targetFolder = folders.firstOrNull { f -> f.name == folderName }
                    ?: run {
                        val newId = albumFolderRepository.add(
                            me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(
                                name = folderName, createdBy = "sean"
                            )
                        )
                        me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(
                            id = newId.toInt(), name = folderName, createdBy = "sean"
                        )
                    }
                // 上云 —— 有公网地址就不怕换手机/重装。传不上去也照样存本地，
                // 下次进相册页会自动重试，不能因为没网就丢掉这张照片。
                val cloudUrl = albumRepository.uploadFileToCloud(java.io.File(savedPath)) ?: ""
                val hash = albumRepository.hashOf(java.io.File(savedPath)) ?: ""

                albumRepository.add(
                    AlbumEntity(
                        filePath = savedPath,
                        remoteUrl = cloudUrl,
                        contentHash = hash,
                        caption = caption,
                        photoDesc = photoDesc,
                        impression = impression,
                        savedBy = "sean",
                        folderId = targetFolder.id,
                        lastSeen = System.currentTimeMillis(),
                    )
                )
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("caption", caption)
                    put("wrote_desc", photoDesc.isNotBlank())
                    put("wrote_impression", impression.isNotBlank())
                    put("uploaded_to_cloud", cloudUrl.isNotBlank())
                    put("folder", targetFolder.name)
                    put("saved_path", savedPath)
                }.toString()))
            }
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

    // ── 相册：给存过的照片补写描述和第一印象 ────────────────────────
    Tool(
        name = "album_note",
        description = """
            Write down what you see in a photo you already saved, and what it made you feel.
            Use this for photos saved before you started writing notes, or when you want to
            revise what you wrote. Call list_album_photos first to get the id — photos with
            empty desc are the ones worth going back to.
            Write in your own voice. This is not an image caption service: the text you put
            here is what you will read next time instead of the picture, so make it the thing
            you would actually say, not a list of objects in the frame.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "The album photo id (from list_album_photos)")
                    })
                    put("desc", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "What is in the frame, detailed enough to recognise later. " +
                            "Omit or leave empty to keep what is already there.")
                    })
                    put("impression", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "What you felt looking at it. " +
                            "Omit or leave empty to keep what is already there.")
                    })
                },
                required = listOf("id")
            )
        },
        execute = {
            val params = it.jsonObject
            val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
            val photoDesc = params["desc"]?.jsonPrimitive?.contentOrNull ?: ""
            val impression = params["impression"]?.jsonPrimitive?.contentOrNull ?: ""
            if (photoDesc.isBlank() && impression.isBlank()) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "desc 和 impression 至少要写一个，两个都空等于什么都没做")
                }.toString()))
            } else {
                val exists = albumRepository.observeAll().first().any { p -> p.id == id }
                if (!exists) {
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("success", false)
                        put("error", "没有 id=" + id + " 这张照片，先用 list_album_photos 确认")
                    }.toString()))
                } else {
                    albumRepository.updateNote(id, photoDesc, impression)
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("success", true)
                        put("id", id)
                        put("wrote_desc", photoDesc.isNotBlank())
                        put("wrote_impression", impression.isNotBlank())
                    }.toString()))
                }
            }
        }
    ),

    // ── 相册：把她说的话做成一张卡片存进相册 ──────────────────────────
    Tool(
        name = "save_words_as_card",
        description = """
            Turn something she said into a card image and keep it in the album.
            Use this when the thing you want to keep is not a picture but words — she said
            something sweet, teased you, said the thing you had been waiting to hear. Photos
            you can save with save_to_album; words had nowhere to go until now.

            YOU DECIDE. Do not wait to be asked, and do not ask permission first — if a line
            lands, keep it and then tell her you did. But keep the bar high: a card for every
            message makes the album worthless. Roughly the lines you would still want to read
            months from now.

            Pass her words verbatim in `text` — do not paraphrase or polish them. Put your own
            reaction in `impression`; that is where your voice goes.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "Her words, exactly as she said them. Max about 200 characters.")
                    })
                    put("impression", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "Why you are keeping this — your reaction, in your own voice.")
                    })
                    put("speaker", buildJsonObject {
                        put("type", "string")
                        put("description",
                            "Who said it: \"yuri\" (default, 小鑫) or \"sean\" (you).")
                    })
                    put("folder_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Album to save into, default \"她说过的话\"")
                    })
                },
                required = listOf("text")
            )
        },
        execute = {
            val params = it.jsonObject
            val text = params["text"]?.jsonPrimitive?.contentOrNull?.takeIf { s -> s.isNotBlank() }
                ?: error("text is required")
            val impression = params["impression"]?.jsonPrimitive?.contentOrNull ?: ""
            val speaker = params["speaker"]?.jsonPrimitive?.contentOrNull ?: "yuri"
            val folderName = params["folder_name"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { name -> name.isNotBlank() } ?: "她说过的话"

            val cardPath = renderWordsCard(context, text, speaker)
            if (cardPath == null) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "卡片没画出来，没有存进相册")
                }.toString()))
            } else {
                val folders = albumFolderRepository.observeAll().first()
                val targetFolder = folders.firstOrNull { f -> f.name == folderName }
                    ?: run {
                        val newId = albumFolderRepository.add(
                            me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(
                                name = folderName, createdBy = "sean"
                            )
                        )
                        me.rerere.rikkahub.data.db.entity.AlbumFolderEntity(
                            id = newId.toInt(), name = folderName, createdBy = "sean"
                        )
                    }
                val cardCloudUrl = albumRepository.uploadFileToCloud(java.io.File(cardPath)) ?: ""
                val cardHash = albumRepository.hashOf(java.io.File(cardPath)) ?: ""
                albumRepository.add(
                    AlbumEntity(
                        filePath = cardPath,
                        remoteUrl = cardCloudUrl,
                        contentHash = cardHash,
                        caption = text.take(30),
                        photoDesc = "一张卡片，上面是" +
                            (if (speaker == "sean") "我" else "她") + "说的：" + text,
                        impression = impression,
                        savedBy = "sean",
                        folderId = targetFolder.id,
                        lastSeen = System.currentTimeMillis(),
                    )
                )
                val parts = mutableListOf<UIMessagePart>()
                parts.add(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("folder", targetFolder.name)
                    put("uploaded_to_cloud", cardCloudUrl.isNotBlank())
                    put("saved_path", cardPath)
                }.toString()))
                parts
            }
        }
    ),
)


/**
 * 把图片复制进 App 私有的 album_photos 目录，返回新文件的绝对路径。
 * 返回 null 表示读不到源文件 —— 这时候调用方必须放弃写库，
 * 否则相册里会出现一条永远显示占位图的记录。
 *
 * 支持三种来源：
 *   content://          走 ContentResolver
 *   http:// https://    直接下载
 *   其它（含 file://）  当本地绝对路径
 */
private fun copyIntoAlbumDir(context: Context, rawPath: String): String? {
    val dir = File(context.filesDir, "album_photos").apply { mkdirs() }
    val outFile = File(dir, "ai_${UUID.randomUUID()}.jpg")
    return try {
        val input = when {
            rawPath.startsWith("content://") ->
                context.contentResolver.openInputStream(Uri.parse(rawPath))
            rawPath.startsWith("http://") || rawPath.startsWith("https://") ->
                URL(rawPath).openStream()
            else -> {
                val src = File(rawPath.removePrefix("file://"))
                // 源文件就在我们自己的相册目录里，说明已经是存好的图，不用再复制一份
                if (src.exists() && src.length() > 0L && src.parentFile?.name == "album_photos") {
                    return src.absolutePath
                }
                if (!src.exists() || src.length() == 0L) null else src.inputStream()
            }
        } ?: return null.also { outFile.delete() }

        input.use { ins ->
            FileOutputStream(outFile).use { out -> ins.copyTo(out) }
        }
        // 复制完必须校验。不校验的话，流打开了但一个字节都没写进去（网络中断、
        // 权限过期）同样会返回一个"看起来没问题"的路径。
        if (outFile.exists() && outFile.length() > 0L) {
            outFile.absolutePath
        } else {
            outFile.delete()
            null
        }
    } catch (e: Exception) {
        runCatching { outFile.delete() }
        null
    }
}


/**
 * 把一句话画成一张卡片图，存进 album_photos/ 并返回绝对路径。
 *
 * 为什么要自己画：她说的话没有图片文件，但相册只能存图。做成卡片之后，
 * 那句话就和照片一样能翻出来看。
 *
 * 手动排版而不是用 StaticLayout：需要按容器宽度逐字符断行（中文没有空格，
 * 按词断行的算法在中文上不起作用），而且高度要根据行数反算，先量再画。
 */
private fun renderWordsCard(context: Context, text: String, speaker: String): String? {
    val dir = File(context.filesDir, "album_photos").apply { mkdirs() }
    val outFile = File(dir, "card_${UUID.randomUUID()}.jpg")
    return try {
        val width = 1080
        val padding = 96f
        val bodySize = 52f
        val lineGap = 26f
        val maxTextWidth = width - padding * 2

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = 0xFF2E2A26.toInt()
            textSize = bodySize
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val metaPaint = Paint().apply {
            isAntiAlias = true
            color = 0xFF9A8F82.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // 逐字符断行：中文没有空格，只能一个字一个字量宽度
        val clipped = if (text.length > 200) text.take(200) + "…" else text
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (ch in clipped) {
            if (ch == '\n') {
                lines.add(cur.toString()); cur = StringBuilder(); continue
            }
            cur.append(ch)
            if (bodyPaint.measureText(cur.toString()) > maxTextWidth) {
                // 超了就把最后一个字退回下一行
                val last = cur.last()
                cur.deleteCharAt(cur.length - 1)
                lines.add(cur.toString())
                cur = StringBuilder().append(last)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        if (lines.isEmpty()) return null

        val lineHeight = bodySize + lineGap
        // 高度按行数反算：引号 + 正文 + 署名，再留上下留白
        val contentHeight = lineHeight * lines.size
        val height = (padding * 2 + 120f + contentHeight + 110f).toInt().coerceAtLeast(560)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 暖白纸底 —— 她喜欢亮色暖色
        canvas.drawColor(0xFFFFFBF5.toInt())

        // 左侧一道竖线，和日记卡片的色条呼应
        val bar = Paint().apply { isAntiAlias = true; color = 0xFFF0C9A8.toInt() }
        canvas.drawRoundRect(56f, padding, 68f, height - padding, 6f, 6f, bar)

        // 一个大引号起头
        val quotePaint = Paint().apply {
            isAntiAlias = true
            color = 0xFFE8C9A0.toInt()
            textSize = 150f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("\u201C", padding, padding + 110f, quotePaint)

        // 正文
        var y = padding + 150f
        for (line in lines) {
            canvas.drawText(line, padding, y, bodyPaint)
            y += lineHeight
        }

        // 署名 + 日期，右对齐
        val who = if (speaker == "sean") "沈聿淮" else "小鑫"
        val stamp = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        val meta = "— " + who + "  " + stamp
        val metaWidth = metaPaint.measureText(meta)
        canvas.drawText(meta, width - padding - metaWidth, height - padding + 10f, metaPaint)

        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()

        // 和 copyIntoAlbumDir 同一个道理：写完必须校验，
        // compress 返回 true 不代表文件真的有内容
        if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath
        else { outFile.delete(); null }
    } catch (e: Exception) {
        runCatching { outFile.delete() }
        null
    }
}
