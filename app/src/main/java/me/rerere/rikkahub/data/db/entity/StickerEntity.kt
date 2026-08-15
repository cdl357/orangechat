/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 共享表情包库条目。
 * name/tags 是给 AI 识别用的：AI 通过 list_stickers 看到每张表情包的名字和标签，
 * 才知道"委屈"该发哪张、"开心"该发哪张，而不是瞎猜文件名。
 * addedBy: "sean" | "yuri" —— 谁加进来的，但双方都能看到全部、都能用全部（真正共享）。
 *
 * remoteUrl: 上传到 Supabase Storage 后的公网 URL。
 * 表情包不再依赖本地文件——换手机/重装/清数据都不会丢图。
 * filePath 保留做兼容：旧数据迁移期间还没上传的用 filePath 显示。
 */
@Entity(tableName = "sticker_item")
data class StickerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo("file_path")
    val filePath: String = "",

    /** 表情包名字，如"委屈" */
    @ColumnInfo("name")
    val name: String = "",

    /** 标签，逗号分隔，如"委屈,难过,撒娇" */
    @ColumnInfo("tags")
    val tags: String = "",

    @ColumnInfo("added_by")
    val addedBy: String = "sean",

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Supabase Storage 公网 URL，有这个就不再依赖本地 filePath */
    @ColumnInfo("remote_url")
    val remoteUrl: String = "",
)
