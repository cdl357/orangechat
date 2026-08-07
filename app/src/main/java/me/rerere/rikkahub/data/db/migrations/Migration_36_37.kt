/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 手写迁移（不依赖 AutoMigration/历史 schema 快照，跟之前几次一样的坑：
 * 33 版本之后的 schema 快照文件缺失，AutoMigration 会因找不到快照 JSON 编译失败）。
 * 新增 sticker_item 表：共享表情包库（人机共用一份，AI 通过 name/tags 识别该发哪张）。
 */
object Migration_36_37 : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sticker_item` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `file_path` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `tags` TEXT NOT NULL,
                `added_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
