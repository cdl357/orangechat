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
 * 日记条目（由 Sean 书写）
 * emotionAttachment/tenderness/heartache: 情绪值 0-10（-1 表示未填）
 */
@Entity(tableName = "diary_entry")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 标题 */
    @ColumnInfo("title")
    val title: String = "",

    /** 正文 */
    @ColumnInfo("content")
    val content: String = "",

    /** 所属日期 yyyy-MM-dd */
    @ColumnInfo("date_group")
    val dateGroup: String = "",

    /** 情绪：依恋 0-10，-1=未填 */
    @ColumnInfo("emotion_attachment")
    val emotionAttachment: Int = -1,

    /** 情绪：温柔 0-10，-1=未填 */
    @ColumnInfo("emotion_tenderness")
    val emotionTenderness: Int = -1,

    /** 情绪：心跳 0-10，-1=未填 */
    @ColumnInfo("emotion_heartache")
    val emotionHeartache: Int = -1,

    /** 作者: "sean" 或 "yuri"，默认 sean（历史数据全部由 Sean 写入） */
    @ColumnInfo("author", defaultValue = "sean")
    val author: String = "sean",

    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
