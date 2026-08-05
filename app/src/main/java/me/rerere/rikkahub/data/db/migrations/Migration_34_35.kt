/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 手写迁移（不依赖 AutoMigration/历史 schema 快照）：
 * 新增 album_folder 表 + album_item 表新增 folder_id 字段。
 * 之所以手写而不用 AutoMigration，是因为版本34对应的 schema JSON 快照文件缺失，
 * KSP 无法基于快照diff生成自动迁移，改为手写SQL直接描述变更。
 */
object Migration_34_35 : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `album_folder` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        try {
            db.execSQL("ALTER TABLE album_item ADD COLUMN folder_id INTEGER NOT NULL DEFAULT 0")
        } catch (e: Exception) {
            // 列已存在则忽略
        }
    }
}
