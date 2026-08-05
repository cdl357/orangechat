/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 手写迁移：新增 love_date 表（"我们"页面的纪念日数据）。
 * 原本用 AutoMigration(33,34)，但版本34的schema快照(34.json)从未提交到仓库，
 * 导致后续任何依赖该快照的操作都会失败。改为手写SQL直接描述表结构。
 */
object Migration_33_34 : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `love_date` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `label` TEXT NOT NULL,
                `date_str` TEXT NOT NULL,
                `pinned` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
