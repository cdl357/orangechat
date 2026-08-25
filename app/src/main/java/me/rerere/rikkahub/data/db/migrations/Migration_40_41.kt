/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 相册：上云 + 内容哈希去重 + 看过几次。
 *
 *   remote_url    Supabase Storage 公网地址。有它就不怕换手机/重装，
 *                 本地文件没了照片还在。和表情包用的是同一套做法。
 *   content_hash  SHA-256 全量哈希。同一张图不管从哪条路进来、发几遍，
 *                 哈希都一样 —— 这才是真去重。原来是按文件路径比，
 *                 同一张图重发一次路径就变了，会存两份。
 *   seen          这张被翻出来过几次。
 *   last_seen     最后一次是什么时候。
 *
 * content_hash 建索引：每次存图都要按哈希查一次，全表扫描不合适。
 * 不建 UNIQUE —— 老照片回填之前哈希全是空串，UNIQUE 会直接冲突建不起来。
 *
 * 手写迁移不用 AutoMigration —— 本项目 schema 快照只导出到 33.json，
 * AutoMigration 找不到历史快照会让 KSP 整体编译失败。
 */
object Migration_40_41 : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE album_item ADD COLUMN remote_url TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE album_item ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE album_item ADD COLUMN seen INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE album_item ADD COLUMN last_seen INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_album_item_content_hash ON album_item(content_hash)")
    }
}
