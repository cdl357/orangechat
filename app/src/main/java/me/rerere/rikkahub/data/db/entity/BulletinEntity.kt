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
 * 留言板条目
 * author: "sean" | "yuri"
 */
@Entity(tableName = "bulletin_note")
data class BulletinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 留言内容 */
    @ColumnInfo("content")
    val content: String = "",

    /** 作者: "sean" 或 "yuri" */
    @ColumnInfo("author")
    val author: String = "sean",

    /** 是否折叠（收起来） */
    @ColumnInfo("collapsed")
    val collapsed: Boolean = false,

    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
