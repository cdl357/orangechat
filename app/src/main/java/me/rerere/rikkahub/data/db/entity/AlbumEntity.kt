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
 * 相册条目
 * filePath: 图片在设备上的绝对路径
 * savedBy: "sean" | "yuri"
 */
@Entity(tableName = "album_item")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 图片绝对路径 */
    @ColumnInfo("file_path")
    val filePath: String = "",

    /** 备注 */
    @ColumnInfo("caption")
    val caption: String = "",

    /**
     * 画面的客观描述，详细到下次不看图也知道是哪张。
     *
     * 列名叫 photo_desc 而不是 desc —— desc 是 SQL 保留字（ORDER BY ... DESC），
     * 拿来当列名会在某些查询里炸。
     */
    @ColumnInfo("photo_desc", defaultValue = "")
    val photoDesc: String = "",

    /**
     * 第一次看到这张照片时心里那句话。
     *
     * 这个字段的意义在于"谁写的"：自动生成的是识图报告，自己写的才是记忆。
     * 下次她再发同一张，递回来的是这句话，不是一份图片分析。
     */
    @ColumnInfo("impression", defaultValue = "")
    val impression: String = "",

    /** 保存者: "sean" 或 "yuri" */
    @ColumnInfo("saved_by")
    val savedBy: String = "sean",

    /** 所属相册分组 id，0 表示未分组（默认相册） */
    @ColumnInfo("folder_id", defaultValue = "0")
    val folderId: Int = 0,

    /** 来源对话 ID（可选，截图时记录） */
    @ColumnInfo("conversation_id")
    val conversationId: String = "",

    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
