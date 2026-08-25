/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 相册条目
 * filePath: 图片在设备上的绝对路径
 * savedBy: "sean" | "yuri"
 */
@Entity(
    tableName = "album_item",
    indices = [Index(value = ["content_hash"])],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 图片绝对路径 */
    @ColumnInfo("file_path")
    val filePath: String = "",

    /**
     * Supabase Storage 公网地址。有它就不怕换手机/重装 —— 本地文件没了照片还在。
     * 空串表示还没上传（上传失败会下次启动重试）。
     */
    @ColumnInfo("remote_url", defaultValue = "")
    val remoteUrl: String = "",

    /**
     * 图片内容的 SHA-256 全量哈希。
     *
     * 用来做真去重：同一张图不管从哪条路进来、发几遍，哈希都一样。
     * 原来是按 filePath 比，同一张图重发一次路径就变了，会存两份。
     * 空串 = 老数据还没回填。
     */
    @ColumnInfo("content_hash", defaultValue = "")
    val contentHash: String = "",

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

    /** 这张被翻出来看过几次 */
    @ColumnInfo("seen", defaultValue = "1")
    val seen: Int = 1,

    /** 最后一次看见是什么时候（0 = 没记录过） */
    @ColumnInfo("last_seen", defaultValue = "0")
    val lastSeen: Long = 0L,

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
