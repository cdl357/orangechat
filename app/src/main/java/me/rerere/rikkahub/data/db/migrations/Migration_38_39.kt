/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 留言板加"回复"能力：便签可以挂在另一张便签下面。
 * reply_to = 0 表示这是一张独立便签（原贴）。
 *
 * 手写迁移不用 AutoMigration —— 本项目 schema 快照只导出到 33.json，
 * AutoMigration 找不到历史快照会让 KSP 整体编译失败。
 */
object Migration_38_39 : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bulletin_note ADD COLUMN reply_to INTEGER NOT NULL DEFAULT 0")
    }
}
