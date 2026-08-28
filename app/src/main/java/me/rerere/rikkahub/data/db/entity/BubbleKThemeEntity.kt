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
 * .ktheme 皮肤库条目（表 bubble_ktheme_skin，42→43 迁移建表）。
 *
 * BubbleSpriteSpec 是嵌套数据类，Room 不便直接映射，这里拍平成列：
 * send_* / receive_* 为必填，*_group_* 可空（可选精灵）；scale 一套皮肤
 * 一个（.ktheme 只有一个 scale 整数）。列名与 Migration_42_43 的
 * CREATE TABLE 一一对应。
 */
@Entity(tableName = "bubble_ktheme_skin")
data class BubbleKThemeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo("name")
    val name: String = "",

    @ColumnInfo("send_image_path")
    val sendImagePath: String = "",

    @ColumnInfo("receive_image_path")
    val receiveImagePath: String = "",

    @ColumnInfo("send_group_image_path")
    val sendGroupImagePath: String? = null,

    @ColumnInfo("receive_group_image_path")
    val receiveGroupImagePath: String? = null,

    @ColumnInfo("bg_image_path")
    val bgImagePath: String? = null,

    // send 精灵
    @ColumnInfo("send_cap_left_pt")
    val sendCapLeftPt: Float = 0f,

    @ColumnInfo("send_cap_top_pt")
    val sendCapTopPt: Float = 0f,

    @ColumnInfo("send_pad_top_pt")
    val sendPadTopPt: Float = 0f,

    @ColumnInfo("send_pad_left_pt")
    val sendPadLeftPt: Float = 0f,

    @ColumnInfo("send_pad_bottom_pt")
    val sendPadBottomPt: Float = 0f,

    @ColumnInfo("send_pad_right_pt")
    val sendPadRightPt: Float = 0f,

    @ColumnInfo("send_text_color")
    val sendTextColor: Long? = null,

    // receive 精灵
    @ColumnInfo("receive_cap_left_pt")
    val receiveCapLeftPt: Float = 0f,

    @ColumnInfo("receive_cap_top_pt")
    val receiveCapTopPt: Float = 0f,

    @ColumnInfo("receive_pad_top_pt")
    val receivePadTopPt: Float = 0f,

    @ColumnInfo("receive_pad_left_pt")
    val receivePadLeftPt: Float = 0f,

    @ColumnInfo("receive_pad_bottom_pt")
    val receivePadBottomPt: Float = 0f,

    @ColumnInfo("receive_pad_right_pt")
    val receivePadRightPt: Float = 0f,

    @ColumnInfo("receive_text_color")
    val receiveTextColor: Long? = null,

    // send_group 精灵（可空：全空 = 没有）
    @ColumnInfo("send_group_cap_left_pt")
    val sendGroupCapLeftPt: Float? = null,

    @ColumnInfo("send_group_cap_top_pt")
    val sendGroupCapTopPt: Float? = null,

    @ColumnInfo("send_group_pad_top_pt")
    val sendGroupPadTopPt: Float? = null,

    @ColumnInfo("send_group_pad_left_pt")
    val sendGroupPadLeftPt: Float? = null,

    @ColumnInfo("send_group_pad_bottom_pt")
    val sendGroupPadBottomPt: Float? = null,

    @ColumnInfo("send_group_pad_right_pt")
    val sendGroupPadRightPt: Float? = null,

    @ColumnInfo("send_group_text_color")
    val sendGroupTextColor: Long? = null,

    // receive_group 精灵（可空：全空 = 没有）
    @ColumnInfo("receive_group_cap_left_pt")
    val receiveGroupCapLeftPt: Float? = null,

    @ColumnInfo("receive_group_cap_top_pt")
    val receiveGroupCapTopPt: Float? = null,

    @ColumnInfo("receive_group_pad_top_pt")
    val receiveGroupPadTopPt: Float? = null,

    @ColumnInfo("receive_group_pad_left_pt")
    val receiveGroupPadLeftPt: Float? = null,

    @ColumnInfo("receive_group_pad_bottom_pt")
    val receiveGroupPadBottomPt: Float? = null,

    @ColumnInfo("receive_group_pad_right_pt")
    val receiveGroupPadRightPt: Float? = null,

    @ColumnInfo("receive_group_text_color")
    val receiveGroupTextColor: Long? = null,

    @ColumnInfo("scale")
    val scale: Int = 1,

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
