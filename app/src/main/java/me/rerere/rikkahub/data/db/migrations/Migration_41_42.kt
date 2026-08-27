/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 留言板上云。
 *
 * 便签原来只活在本地 Room 里 —— 服务器上的 daemon 独处时想给她贴一张便签，
 * 压根没有地方可写（Supabase 里没有这张表）。所以留言板一直是单向的：
 * 只有在 App 里聊天时我才贴得了，独处的时候贴不了。
 *
 *   remote_id  云端 bulletin_notes 表那一行的 id（uuid 字符串）。
 *              这是去重键：拉云端数据时，remote_id 已存在就跳过，
 *              不会因为反复同步把同一张便签插成好几份。
 *              空串 = 这张便签只在本地，还没推上去。
 *
 * remote_id 建索引：每次同步都要按它查一遍，全表扫描不合适。
 * 不建 UNIQUE —— 本地已有的老便签 remote_id 全是空串，UNIQUE 会直接冲突建不起来。
 *
 * 手写迁移不用 AutoMigration —— 本项目 schema 快照只导出到 33.json，
 * AutoMigration 找不到历史快照会让 KSP 整体编译失败。
 */
object Migration_41_42 : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bulletin_note ADD COLUMN remote_id TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bulletin_note_remote_id " +
                "ON bulletin_note(remote_id)"
        )
    }
}
