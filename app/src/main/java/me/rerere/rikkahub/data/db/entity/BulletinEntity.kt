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
 * 留言板条目
 * author: "sean" | "yuri"
 */
@Entity(
    tableName = "bulletin_note",
    indices = [Index(value = ["remote_id"])],
)
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

    /**
     * 回复目标便签的 id。0 = 这是一张独立便签（原贴）。
     * 回复只挂一层：回复的回复也挂在同一个原贴下面，不做无限嵌套。
     */
    @ColumnInfo("reply_to")
    val replyTo: Int = 0,

    /**
     * 云端 `bulletin_notes` 表那一行的 id。
     *
     * 服务器上的 daemon 独处时会往云端写便签，App 拉下来时靠这个字段去重：
     * remote_id 已经存在就跳过，反复同步不会插成好几份。
     * 空串 = 这张便签目前只在本地。
     */
    @ColumnInfo("remote_id", defaultValue = "")
    val remoteId: String = "",

    /** 创建时间戳 */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
