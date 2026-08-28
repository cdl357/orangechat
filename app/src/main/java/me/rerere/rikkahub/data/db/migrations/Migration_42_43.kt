/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 气泡皮肤系统：ktheme 皮肤库建表。
 *
 * 只加表不删表，覆盖安装平滑升级；老用户不导入 .ktheme 时这张表一直是空表，
 * 不影响任何现有功能。
 *
 * 手写迁移不用 AutoMigration —— 本项目 schema 快照只导出到 33.json，
 * AutoMigration 找不到 42/43 的历史快照会让 KSP 整体编译失败
 * （同 Migration_41_42 的注释，ref/Migration_41_42.kt:26-27）。
 *
 * CREATE TABLE 的列名/类型/非空与 BubbleKThemeEntity 逐列对齐
 * （Room 校验建表结果与实体期望的 schema 一致）。不写 DEFAULT 子句：
 * 实体侧没声明 @ColumnInfo(defaultValue)，避免默认值校验差异；
 * 新表没有存量行，不需要 ALTER 那种 NOT NULL DEFAULT。
 */
object Migration_42_43 : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bubble_ktheme_skin` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `send_image_path` TEXT NOT NULL,
                `receive_image_path` TEXT NOT NULL,
                `send_group_image_path` TEXT,
                `receive_group_image_path` TEXT,
                `bg_image_path` TEXT,
                `send_cap_left_pt` REAL NOT NULL,
                `send_cap_top_pt` REAL NOT NULL,
                `send_pad_top_pt` REAL NOT NULL,
                `send_pad_left_pt` REAL NOT NULL,
                `send_pad_bottom_pt` REAL NOT NULL,
                `send_pad_right_pt` REAL NOT NULL,
                `send_text_color` INTEGER,
                `receive_cap_left_pt` REAL NOT NULL,
                `receive_cap_top_pt` REAL NOT NULL,
                `receive_pad_top_pt` REAL NOT NULL,
                `receive_pad_left_pt` REAL NOT NULL,
                `receive_pad_bottom_pt` REAL NOT NULL,
                `receive_pad_right_pt` REAL NOT NULL,
                `receive_text_color` INTEGER,
                `send_group_cap_left_pt` REAL,
                `send_group_cap_top_pt` REAL,
                `send_group_pad_top_pt` REAL,
                `send_group_pad_left_pt` REAL,
                `send_group_pad_bottom_pt` REAL,
                `send_group_pad_right_pt` REAL,
                `send_group_text_color` INTEGER,
                `receive_group_cap_left_pt` REAL,
                `receive_group_cap_top_pt` REAL,
                `receive_group_pad_top_pt` REAL,
                `receive_group_pad_left_pt` REAL,
                `receive_group_pad_bottom_pt` REAL,
                `receive_group_pad_right_pt` REAL,
                `receive_group_text_color` INTEGER,
                `scale` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
