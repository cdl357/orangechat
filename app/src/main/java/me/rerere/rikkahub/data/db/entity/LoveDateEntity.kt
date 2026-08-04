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
 * 重要日子条目（Love 页面）
 * label:   事件名称
 * dateStr: yyyy-MM-dd
 * pinned:  是否置顶
 */
@Entity(tableName = "love_date")
data class LoveDateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo("label")
    val label: String = "",

    @ColumnInfo("date_str")
    val dateStr: String = "",          // yyyy-MM-dd

    @ColumnInfo("pinned")
    val pinned: Boolean = false,

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
