/*
 * 橘瓣 OrangeChat
 */
package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 相册分组（本子）
 * name: 相册名称，如"她"/"我们俩"/"猫猫"
 * createdBy: "sean" | "yuri"
 */
@Entity(tableName = "album_folder")
data class AlbumFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo("name")
    val name: String = "",

    @ColumnInfo("created_by")
    val createdBy: String = "sean",

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
