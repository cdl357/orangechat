/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.bubble

import kotlinx.serialization.Serializable

/**
 * 气泡皮肤系统。三种模式共存：
 *  - [BubbleSkin.None]      默认，行为和改动前完全一致
 *  - [BubbleSkin.CodeStyle] 纯代码绘制的气泡 + 可选角挂件（永不变形）
 *  - [BubbleSkin.KTheme]    从 .ktheme 导入的精灵图，走九宫格拉伸
 *
 * 序列化说明：存进 DataStore 的 DisplaySetting 里（见 PreferencesStore.kt 的
 * DISPLAY_SETTING key，用 JsonInstant 编解码整个 DisplaySetting）。
 * sealed interface + data object 的多态序列化依赖 kotlinx.serialization
 * >= 1.3.0（data object 支持）；本项目 Kotlin >= 2.0.20（PreferencesStore.kt
 * 使用 kotlin.uuid.Uuid），配套 kotlinx.serialization 满足该前提。
 * 类判别字段为默认的 "type"。
 */
@Serializable
sealed interface BubbleSkin {

    @Serializable
    data object None : BubbleSkin

    @Serializable
    data class CodeStyle(
        val styleId: String,
        /** 用户改过的底色，null = 用预设自带的 */
        val overrideColor: Long? = null,
        val tailEnabled: Boolean = true,
        val charm: CharmConfig? = null,
    ) : BubbleSkin

    @Serializable
    data class KTheme(
        val themeId: Long,
    ) : BubbleSkin
}

/** 角挂件：贴在气泡某个角上的透明底小图，按原比例缩放，绝不拉伸 */
@Serializable
data class CharmConfig(
    val imagePath: String,
    val corner: CharmCorner = CharmCorner.TOP_START,
    /** 显示边长上限（dp），图按原比例缩到能塞进这个方框 */
    val sizeDp: Float = 34f,
    /**
     * 相对该角的偏移（dp）。
     * 语义（对四个角统一）：**正数向气泡内推，负数向外挪**。
     * 即 offsetX 的世界方向随角翻转：
     *   TOP_START     → +x 向右(向内) / +y 向下(向内)
     *   TOP_END       → +x 向左(向内) / +y 向下(向内)
     *   BOTTOM_START  → +x 向右(向内) / +y 向上(向内)
     *   BOTTOM_END    → +x 向左(向内) / +y 向上(向内)
     * 渲染端的符号翻转见 BubbleSkinRenderer.kt 的 BubbleCharm。
     */
    val offsetXDp: Float = -6f,
    val offsetYDp: Float = -14f,
)

@Serializable
enum class CharmCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/**
 * 纯代码气泡样式。全是绘制参数，没有位图，所以任何尺寸下都不会变形。
 *
 * 注意 [strokeDashDp]：非空时描边画虚线，用于「纸质感」那种手账风。
 */
@Serializable
data class BubbleStyle(
    val id: String,
    val name: String,
    val fillColor: Long,
    /** 非空时用 135° 线性渐变，从 fillColor 到这个色 */
    val gradientEndColor: Long? = null,
    val strokeColor: Long? = null,
    val strokeWidthDp: Float = 0f,
    val strokeDashDp: Float? = null,
    val cornerRadiusDp: Float = 18f,
    /** 柔和阴影（Compose shadow），0 = 无 */
    val elevationDp: Float = 0f,
    /** 硬阴影偏移（贴纸/描边糖那种死板的影子），0 = 无 */
    val hardShadowOffsetDp: Float = 0f,
    val hardShadowColor: Long = 0xFF2B2B2B,
    val textColor: Long? = null,
) {
    val hasGradient: Boolean get() = gradientEndColor != null
    val hasStroke: Boolean get() = strokeColor != null && strokeWidthDp > 0f
    val hasHardShadow: Boolean get() = hardShadowOffsetDp > 0f
}

/**
 * 把皮肤里的 CodeStyle 解析成实际绘制用的 BubbleStyle。
 *
 * overrideColor 的语义：替换底色。渐变是按预设色板成对设计的，
 * 只换一头会串色，所以换底色时直接取消渐变（纯色填充）。
 * styleId 找不到对应预设（例如预设被删）时返回 null，调用方应回退到 None 路径。
 */
fun BubbleSkin.CodeStyle.resolveStyle(): BubbleStyle? {
    val preset = BubblePresets.byId(styleId) ?: return null
    return if (overrideColor != null) {
        preset.copy(fillColor = overrideColor, gradientEndColor = null)
    } else {
        preset
    }
}

object BubblePresets {

    val MILK_OUTLINE = BubbleStyle(
        id = "milk_outline",
        name = "奶白描边",
        fillColor = 0xFFFFFDFA,
        strokeColor = 0xFFE4DCD2,
        strokeWidthDp = 1f,
        cornerRadiusDp = 20f,
        elevationDp = 2f,
        textColor = 0xFF4A443E,
    )

    val WARM_ORANGE = BubbleStyle(
        id = "warm_orange",
        name = "暖橘渐变",
        fillColor = 0xFFFFE0BE,
        gradientEndColor = 0xFFFFC48A,
        cornerRadiusDp = 18f,
        textColor = 0xFF6B4526,
    )

    val MIST_BLUE = BubbleStyle(
        id = "mist_blue",
        name = "雾蓝",
        fillColor = 0xFFD4E6F1,
        strokeColor = 0xFFA9C9DD,
        strokeWidthDp = 1f,
        cornerRadiusDp = 14f,
        textColor = 0xFF33556B,
    )

    val CANDY_PINK = BubbleStyle(
        id = "candy_pink",
        name = "糖果粉",
        fillColor = 0xFFFFD6E3,
        strokeColor = 0xFFFFFFFF,
        strokeWidthDp = 2.5f,
        cornerRadiusDp = 24f,
        elevationDp = 1f,
        textColor = 0xFF7D3F55,
    )

    val PAPER = BubbleStyle(
        id = "paper",
        name = "纸质感",
        fillColor = 0xFFF5EFE3,
        strokeColor = 0xFFB79E7C,
        strokeWidthDp = 1.5f,
        strokeDashDp = 4f,
        cornerRadiusDp = 9f,
        textColor = 0xFF5A4A33,
    )

    val OUTLINE_CANDY = BubbleStyle(
        id = "outline_candy",
        name = "描边糖",
        fillColor = 0xFFFFF9E8,
        strokeColor = 0xFF3A3226,
        strokeWidthDp = 2f,
        cornerRadiusDp = 16f,
        hardShadowOffsetDp = 3f,
        hardShadowColor = 0xFF3A3226,
        textColor = 0xFF3A3226,
    )

    val MINIMAL = BubbleStyle(
        id = "minimal",
        name = "极简无框",
        fillColor = 0xFFEFEFEF,
        cornerRadiusDp = 22f,
        textColor = 0xFF3C3C3C,
    )

    val ALL: List<BubbleStyle> = listOf(
        MILK_OUTLINE, WARM_ORANGE, MIST_BLUE, CANDY_PINK, PAPER, OUTLINE_CANDY, MINIMAL,
    )

    fun byId(id: String): BubbleStyle? = ALL.firstOrNull { it.id == id }
}

/**
 * .ktheme 解出来的一套皮肤，及其九宫格参数。
 *
 * ⚠️ capLeft/capTop 的语义见 [BubbleSpriteSpec]：**不是左右对称**。
 */
data class KThemeSkin(
    val id: Long,
    val name: String,
    val sendImagePath: String,
    val receiveImagePath: String,
    val sendGroupImagePath: String?,
    val receiveGroupImagePath: String?,
    val bgImagePath: String?,
    val send: BubbleSpriteSpec,
    val receive: BubbleSpriteSpec,
    val sendGroup: BubbleSpriteSpec?,
    val receiveGroup: BubbleSpriteSpec?,
)

/**
 * 单张气泡精灵图的拉伸与内边距参数。
 *
 * capLeft/capTop 直接来自 CSS，单位 pt；实际像素 = 值 × [scale]。
 * capRight/capBottom **不等于** capLeft/capTop，见
 * BubbleSkinRenderer.kt 中 drawNinePatch 的四行公式：
 *
 * ```
 * capL = capLeft × scale
 * capT = capTop  × scale
 * capR = imageWidth  - capL - 1
 * capB = imageHeight - capT - 1
 * ```
 *
 * 即左上角由 CSS 给定，右下角用图的实际尺寸反推，中间可拉伸区只有 1 像素宽。
 *
 * pad* 单位也是 pt；显示时 pt 直接换算成 dp（源像素 = pt × scale，
 * 屏幕像素 = 源像素 × density / scale = pt × density = pt dp）。
 */
data class BubbleSpriteSpec(
    val capLeftPt: Float,
    val capTopPt: Float,
    val padTopPt: Float,
    val padLeftPt: Float,
    val padBottomPt: Float,
    val padRightPt: Float,
    val textColor: Long?,
    val scale: Int,
)
