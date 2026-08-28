/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.components.bubble

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.bubble.BubbleSpriteSpec
import me.rerere.rikkahub.data.bubble.BubbleStyle
import me.rerere.rikkahub.data.bubble.CharmConfig
import me.rerere.rikkahub.data.bubble.CharmCorner
import kotlin.math.roundToInt

/**
 * 纯代码气泡背景：纯色 / 135° 线性渐变 / 实线或虚线描边 / 柔和阴影 / 硬阴影 / 气泡尾巴。
 *
 * 全部用 drawWithCache 绘制，不用位图，任何尺寸不变形。
 * 圆角一律取 [BubbleStyle.cornerRadiusDp]，**不使用**外部传入的圆角。
 *
 * ⚠️ 调用方注意：本组件自带 [BubbleStyle.elevationDp] 柔和阴影与
 * [BubbleStyle.hardShadowOffsetDp] 硬阴影，两者都会画到自身边界之外，
 * 因此**不要把它放进带 clip() 的层里**，否则阴影/硬阴影会被裁掉
 * （见 ChatMessage.kt 皮肤分支的分层结构：背景层在裁剪盒之外）。
 *
 * @param alpha 整体不透明度（承接用户的 chatBubbleTransparency 设置），1 = 不透明
 * @param tailEnabled 是否画尾巴
 * @param tailStartSide 尾巴在起始侧（助手气泡，左）还是结束侧（用户气泡，右）
 */
@Composable
fun CodeStyleBubbleBackground(
    style: BubbleStyle,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    tailEnabled: Boolean = false,
    tailStartSide: Boolean = false,
) {
    val shape = RoundedCornerShape(style.cornerRadiusDp.dp)
    val shadowModifier = if (style.elevationDp > 0f) {
        Modifier.shadow(elevation = style.elevationDp.dp, shape = shape)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(shadowModifier)) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val radiusPx = style.cornerRadiusDp.dp.toPx()
                    val cornerRadius = CornerRadius(radiusPx, radiusPx)
                    val safeAlpha = alpha.coerceIn(0f, 1f)

                    // 135°（CSS 角度）= 左上 → 右下
                    val startColor = Color(style.fillColor).copy(alpha = safeAlpha)
                    val bodyBrush: Brush = if (style.hasGradient && style.gradientEndColor != null) {
                        val endColor = Color(style.gradientEndColor).copy(alpha = safeAlpha)
                        Brush.linearGradient(
                            colors = listOf(startColor, endColor),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                    } else {
                        SolidColor(startColor)
                    }

                    val strokeBrush = style.strokeColor?.let {
                        SolidColor(Color(it).copy(alpha = safeAlpha))
                    }
                    val strokeStyle: Stroke = if (style.hasStroke) {
                        val widthPx = style.strokeWidthDp.dp.toPx()
                        if (style.strokeDashDp != null && style.strokeDashDp > 0f) {
                            val dashPx = style.strokeDashDp.dp.toPx()
                            Stroke(
                                width = widthPx,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)),
                            )
                        } else {
                            Stroke(width = widthPx)
                        }
                    } else {
                        Stroke(width = 0f)
                    }

                    val hardShadowOffsetPx = style.hardShadowOffsetDp.dp.toPx()
                    val hardShadowColor =
                        Color(style.hardShadowColor).copy(alpha = safeAlpha)

                    // 尾巴：贴着底边的小三角，画完本体后补画（同 brush，无接缝）
                    val tailWidthPx = 10.dp.toPx()
                    val tailHeightPx = 7.dp.toPx()

                    onDrawBehind {
                        // 1. 硬阴影：先画偏移的实心圆角矩形，本体再盖上去
                        if (style.hasHardShadow) {
                            drawRoundRect(
                                color = hardShadowColor,
                                topLeft = Offset(hardShadowOffsetPx, hardShadowOffsetPx),
                                size = size,
                                cornerRadius = cornerRadius,
                            )
                        }
                        // 2. 本体（含尾巴）
                        if (tailEnabled) {
                            val tailPath = Path().apply {
                                val x0 = if (tailStartSide) {
                                    radiusPx
                                } else {
                                    this@onDrawBehind.size.width - radiusPx - tailWidthPx
                                }
                                val y = this@onDrawBehind.size.height - 1f
                                moveTo(x0, y)
                                val apexX = if (tailStartSide) x0 else x0 + tailWidthPx
                                lineTo(apexX, y + tailHeightPx)
                                lineTo(x0 + tailWidthPx, y)
                                close()
                            }
                            drawPath(tailPath, brush = bodyBrush)
                        }
                        drawRoundRect(brush = bodyBrush, cornerRadius = cornerRadius)
                        // 3. 描边
                        if (style.hasStroke && strokeBrush != null) {
                            drawRoundRect(
                                brush = strokeBrush,
                                cornerRadius = cornerRadius,
                                style = strokeStyle,
                            )
                        }
                    }
                }
        )
    }
}

/**
 * .ktheme 精灵图九宫格背景。
 *
 * 四角不拉、上下边横向拉、左右边纵向拉、中间双向拉。
 * cap 语义**非对称**（源像素坐标）：
 *
 * ```
 * val capL = (spec.capLeftPt * spec.scale).roundToInt().coerceIn(0, imgW - 1)
 * val capT = (spec.capTopPt * spec.scale).roundToInt().coerceIn(0, imgH - 1)
 * val capR = imgW - capL - 1
 * val capB = imgH - capT - 1
 * ```
 *
 * 右/下由图的实际尺寸反推，中间可拉伸区只有 1 像素宽。
 * 显示换算：源像素 → 屏幕像素 的比例 = density / scale，
 * 因此角落显示尺寸 = capLeftPt dp（pt 值在屏幕上就是这么多 dp）。
 *
 * @param alpha 整体不透明度（承接 chatBubbleTransparency）
 */
@Composable
fun NinePatchBubbleBackground(
    imagePath: String,
    spec: BubbleSpriteSpec,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    var bitmap by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image == null) {
        // 图还没解出来（或文件丢了）：占位透明，不 crash，不拉伸别的图顶包
        Spacer(modifier = modifier)
        return
    }
    val safeScale = if (spec.scale > 0) spec.scale else 1
    Spacer(
        modifier = modifier.drawWithCache {
            val imgW = image.width
            val imgH = image.height
            if (imgW <= 0 || imgH <= 0) {
                return@drawWithCache onDrawBehind { /* 空图 */ }
            }
            // ===== 第四节：cap 非对称公式（左右不对称、上下不对称） =====
            val capL = (spec.capLeftPt * safeScale).roundToInt().coerceIn(0, imgW - 1)
            val capT = (spec.capTopPt * safeScale).roundToInt().coerceIn(0, imgH - 1)
            val capR = imgW - capL - 1
            val capB = imgH - capT - 1

            val densityFactor = density / safeScale
            var edgeL = capL * densityFactor
            var edgeT = capT * densityFactor
            var edgeR = capR * densityFactor
            var edgeB = capB * densityFactor
            // 防御：气泡比四角还小的时候按比例缩角，避免中间区出现负宽
            val minW = edgeL + edgeR
            if (size.width in 0.01f..minW) {
                val k = size.width / minW
                edgeL *= k; edgeR *= k; edgeT *= k; edgeB *= k
            }
            val minH = edgeT + edgeB
            if (size.height in 0.01f..minH) {
                val k = size.height / minH
                edgeL *= k; edgeR *= k; edgeT *= k; edgeB *= k
            }

            val srcX = intArrayOf(0, capL, imgW - capR, imgW)
            val srcY = intArrayOf(0, capT, imgH - capB, imgH)
            val dstX = floatArrayOf(0f, edgeL, size.width - edgeR, size.width)
            val dstY = floatArrayOf(0f, edgeT, size.height - edgeB, size.height)

            onDrawBehind {
                val safeAlpha = alpha.coerceIn(0f, 1f)
                for (iy in 0 until 3) {
                    for (ix in 0 until 3) {
                        val sx0 = srcX[ix]; val sx1 = srcX[ix + 1]
                        val sy0 = srcY[iy]; val sy1 = srcY[iy + 1]
                        val dx0 = dstX[ix]; val dx1 = dstX[ix + 1]
                        val dy0 = dstY[iy]; val dy1 = dstY[iy + 1]
                        if (sx1 - sx0 <= 0 || sy1 - sy0 <= 0) continue
                        if (dx1 - dx0 <= 0.5f || dy1 - dy0 <= 0.5f) continue
                        drawImage(
                            image = image,
                            srcOffset = IntOffset(sx0, sy0),
                            srcSize = IntSize(sx1 - sx0, sy1 - sy0),
                            dstOffset = IntOffset(dx0.roundToInt(), dy0.roundToInt()),
                            dstSize = IntSize(
                                (dx1 - dx0).roundToInt(),
                                (dy1 - dy0).roundToInt(),
                            ),
                            alpha = safeAlpha,
                        )
                    }
                }
            }
        }
    )
}

/**
 * 角挂件：贴在气泡某个角上的透明底小图。
 *
 * - [CharmConfig.sizeDp] 是**边长上限**：图按原比例缩到能塞进这个方框
 *   （ContentScale.Fit，绝不 FillBounds，挂件被拉变形整个功能就废了）。
 * - 用 Box 的 align + offset 定位到指定角。
 * - **画在气泡裁剪区域之外**：本组件自身链路上没有任何 clip()，
 *   且要求调用方把它放在带 clip() 的裁剪盒**外面**（挂件的猫头要能探出气泡边界）。
 *   ChatMessage.kt 皮肤分支中它位于 `Box(matchParentSize())`（无裁剪）里、
 *   与被 clip 的气泡盒平级，保证不被裁。
 * - offset 语义见 [CharmConfig]：正数向气泡内推，负数向外挪（方向随角翻转）。
 */
@Composable
fun BoxScope.BubbleCharm(
    charm: CharmConfig,
    modifier: Modifier = Modifier,
) {
    val (align, signX, signY) = when (charm.corner) {
        CharmCorner.TOP_START -> Triple(Alignment.TopStart, 1, 1)
        CharmCorner.TOP_END -> Triple(Alignment.TopEnd, -1, 1)
        CharmCorner.BOTTOM_START -> Triple(Alignment.BottomStart, 1, -1)
        CharmCorner.BOTTOM_END -> Triple(Alignment.BottomEnd, -1, -1)
    }
    AsyncImage(
        model = charm.imagePath,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .align(align)
            .size(charm.sizeDp.dp)
            .offset(
                x = (signX * charm.offsetXDp).dp,
                y = (signY * charm.offsetYDp).dp,
            ),
    )
}
