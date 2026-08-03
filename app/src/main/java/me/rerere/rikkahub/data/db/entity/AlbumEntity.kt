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

    /** 保存者: "sean" 或 "yuri" */
    @ColumnInfo("saved_by")
    val savedBy: String = "sean",

    /** 来源对话 ID（可选，截图时记录） */
    @ColumnInfo("conversation_id")
    val conversationId: String = "",

    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
