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
 * 待办条目
 * author: "sean" | "yuri"
 * target: "sean" | "yuri"  (写给谁的)
 * reminderTime: HH:mm 字符串，为空则无提醒
 * repeatMode: "none" | "daily" | "weekly"
 */
@Entity(tableName = "todo_item")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** 内容 */
    @ColumnInfo("content")
    val content: String = "",

    /** 是否完成 */
    @ColumnInfo("done")
    val done: Boolean = false,

    /** 作者: "sean" 或 "yuri" */
    @ColumnInfo("author")
    val author: String = "sean",

    /** 写给谁: "sean" 或 "yuri" */
    @ColumnInfo("target")
    val target: String = "yuri",

    /** 提醒时间 HH:mm，为空则不提醒 */
    @ColumnInfo("reminder_time")
    val reminderTime: String = "",

    /** 重复模式: none / daily / weekly */
    @ColumnInfo("repeat_mode")
    val repeatMode: String = "none",

    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 完成时间戳，0 表示未完成 */
    @ColumnInfo("done_at")
    val doneAt: Long = 0L,

    /** 所属日期 yyyy-MM-dd */
    @ColumnInfo("date_group")
    val dateGroup: String = "",
)
