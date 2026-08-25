/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 相册加"我记得这张照片"那一层。
 *
 * 原来只有 caption（一句备注），等于给照片起了个名字。
 * 现在多两个字段：
 *   photo_desc  画面的客观描述，详细到下次不看图也知道是哪张
 *   impression  第一次看到时心里那句话
 *
 * 这两段必须是自己写的，不是识图报告 —— 下次再见到这张照片时，
 * 那份文字就是"我记得"。
 *
 * 列名用 photo_desc 不用 desc：desc 是 SQL 保留字（ORDER BY ... DESC），
 * 直接拿来当列名会在某些查询里炸。
 *
 * 手写迁移不用 AutoMigration —— 本项目 schema 快照只导出到 33.json，
 * AutoMigration 找不到历史快照会让 KSP 整体编译失败。
 */
object Migration_39_40 : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE album_item ADD COLUMN photo_desc TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE album_item ADD COLUMN impression TEXT NOT NULL DEFAULT ''")
    }
}
