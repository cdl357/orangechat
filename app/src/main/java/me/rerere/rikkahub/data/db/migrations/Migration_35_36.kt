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
 * diary_entry 表新增 author 字段。
 * 之前日记本没有区分作者，DiaryPage 的 Sean/Yuri tab 只是 UI 状态，从未真正按作者过滤，
 * 导致切 tab 显示的都是同一份全量数据。这里补上字段，默认值 "sean"
 * （历史数据全部由 Sean 写入，回填为 sean 符合实际情况）。
 */
object Migration_35_36 : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE diary_entry ADD COLUMN author TEXT NOT NULL DEFAULT 'sean'")
        } catch (e: Exception) {
            // 列已存在则忽略
        }
    }
}
