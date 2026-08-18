package com.schedule.njfu.share

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.schedule.njfu.R
import com.schedule.njfu.data.CourseMapper
import com.schedule.njfu.model.Course
import com.schedule.njfu.ui.weekdayNameRes
import com.schedule.njfu.ui.schedule.WeekGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

/**
 * 「本周课表分享成图」工具模块（Agent C）。
 *
 * 渲染「指定周」课表为 PNG 图片并唤起系统分享面板（android.graphics 层实现，
 * 不依赖 Compose）。排版与课表 UI 保持同一套数据语义：
 *   - 课程摆放完全由 [WeekGrid.cellsFor] 决定（col/row/rowSpan/overlapIndex/overlapCount，
 *     含调休列映射与同格并排的挪动逻辑）；
 *   - 表头星期列沿用 [WeekGrid.mappedDayForColumn] 的调休语义；
 *   - 卡片颜色 = CourseMapper.displayColor(CourseMapper.colorFor(name))；
 *   - 课程名在窄卡片内的分行/截断、Cell→占位矩形等可测逻辑为 internal 纯函数，
 *     由 JVM 单测覆盖（见 app/src/test/.../share/ShareScheduleImageTest.kt）。
 *
 * 分享流程：PNG 写入 MediaStore（content:// URI，不自持文件）→ ACTION_SEND + createChooser。
 * 不使用 FileProvider，不修改 AndroidManifest。
 *
 * @return 是否成功唤起分享；失败时通过 onError 回调给出一条给用户看的提示文案
 *         （回调可能在工作线程执行，调用方如需更新 UI 请自行切主线程）。
 */
suspend fun shareCurrentWeekImage(
    context: Context,
    courses: List<Course>,
    week: Int,
    semesterStart: java.time.LocalDate,
    rowHeightDp: Int = 48,
    shifts: Map<java.time.LocalDate, Int> = emptyMap(),
    onError: ((String) -> Unit)? = null,
): Boolean = withContext(Dispatchers.IO) {
    var bitmap: Bitmap? = null
    var uri: Uri? = null
    try {
        // 1) 渲染 1080px 宽竖版 PNG
        try {
            bitmap = renderScheduleBitmap(
                context = context,
                courses = courses,
                week = week,
                weekDates = weekDatesFor(semesterStart, week),
                shifts = shifts,
                rowHeightDp = rowHeightDp.toFloat(),
            )
        } catch (e: Exception) {
            onError?.invoke(context.getString(R.string.share_error_render_failed))
            return@withContext false
        }
        // 2) 写入 MediaStore，取 content:// URI
        try {
            uri = insertPngToMediaStore(context, bitmap, week)
        } catch (e: Exception) {
            onError?.invoke(context.getString(R.string.share_error_save_failed))
            return@withContext false
        }
        // 3) 唤起系统分享
        try {
            shareViaChooser(context, uri)
        } catch (e: ActivityNotFoundException) {
            onError?.invoke(context.getString(R.string.share_error_no_share_app))
            return@withContext false
        } catch (e: Exception) {
            onError?.invoke(context.getString(R.string.share_error_save_failed))
            return@withContext false
        }
        true
    } finally {
        bitmap?.takeIf { !it.isRecycled }?.recycle()
    }
}

// ---------------------------------------------------------------------------
// 可测纯函数（internal，JVM 单测覆盖；不使用任何 Android 类型）
// ---------------------------------------------------------------------------

/** 占位矩形（dp 单位，纯数据，便于单测；渲染时再换算为 px） */
internal data class ShareRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** 字符串分行结果 */
internal data class PlannedLines(
    val lines: List<String>,
    /** 文本超过 maxLines，末行已补省略号 */
    val truncated: Boolean,
)

/** 课程卡内文字排版结果（字号/名字行/是否留地点行），供 Canvas 直接绘制 */
internal data class CardTextPlan(
    val fontSizeSp: Float,
    val nameLines: List<String>,
    val truncatedName: Boolean,
    val showLocation: Boolean,
)

// ---- 布局常数（dp / sp） ----

internal const val SHARE_PADDING_DP = 16f
internal const val SHARE_TITLE_AREA_HEIGHT_DP = 48f
internal const val SHARE_HEADER_ROW_HEIGHT_DP = 34f
internal const val SHARE_LABEL_COL_WIDTH_DP = 40f
internal const val SHARE_TITLE_FONT_SP = 26f
internal const val SHARE_HEADER_FONT_SP = 13f
internal const val SHARE_LABEL_FONT_SP = 11f
internal const val SHARE_CARD_TEXT_PAD_DP = 4f
internal const val SHARE_META_ROW_HEIGHT_DP = 14f
internal const val SHARE_NAME_FONT_MAX_SP = 12f
internal const val SHARE_NAME_FONT_MIN_SP = 9f
/** 课程名行高系数（textSize * 系数 估算整块高度，与预算计算同源） */
internal const val SHARE_NAME_LINE_HEIGHT_FACTOR = 1.3f

/** 候选字号，从大到小尝试（与课表 UI 的策略一致：优先大字号，放不下再缩小） */
internal val SHARE_FONT_CANDIDATES = listOf(12f, 11f, 10f, 9f)

/** 汉字/全角按 1 个字宽，ASCII 按 0.55（与 CourseCardLayout 估算同口径） */
internal const val SHARE_CHAR_UNIT_CJK = 1.0f
internal const val SHARE_CHAR_UNIT_ASCII = 0.55f

/**
 * 指定周的 7 天日期（周一到周日）。
 * [semesterStart] 与 WeekUtils.currentWeek 同样先归一化到所在周周一，再以周一为周边界，
 * 保证任意一天作为 semesterStart 时同一周结果一致。
 */
internal fun weekDatesFor(semesterStart: LocalDate, week: Int): List<LocalDate> {
    val startMonday = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monday = startMonday.plusWeeks((week - 1).coerceAtLeast(0).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

/**
 * 整图布局预算（dp 单位）：1080px 宽按 density 换算成逻辑宽度，
 * 其余尺寸完全由固定常数与 rowHeightDp 推导，纯计算便于单测。
 */
internal fun computeShareLayout(
    imageWidthPx: Int,
    density: Float,
    rowHeightDp: Float,
): ShareLayout {
    require(imageWidthPx > 0) { "imageWidthPx must be positive" }
    require(density > 0f) { "density must be positive" }
    require(rowHeightDp > 0f) { "rowHeightDp must be positive" }
    val widthDp = imageWidthPx / density
    val gridLeft = SHARE_PADDING_DP + SHARE_LABEL_COL_WIDTH_DP
    val dayColWidth = (widthDp - gridLeft - SHARE_PADDING_DP) / 7f
    val gridTop = SHARE_PADDING_DP + SHARE_TITLE_AREA_HEIGHT_DP + SHARE_HEADER_ROW_HEIGHT_DP
    val gridHeight = WeekGrid.MAX_PERIODS * rowHeightDp
    val heightDp = gridTop + gridHeight + SHARE_PADDING_DP
    return ShareLayout(
        widthDp = widthDp,
        heightDp = heightDp,
        paddingDp = SHARE_PADDING_DP,
        titleAreaHeightDp = SHARE_TITLE_AREA_HEIGHT_DP,
        headerRowHeightDp = SHARE_HEADER_ROW_HEIGHT_DP,
        labelColWidthDp = SHARE_LABEL_COL_WIDTH_DP,
        gridLeftDp = gridLeft,
        gridTopDp = gridTop,
        dayColWidthDp = dayColWidth,
        rowHeightDp = rowHeightDp,
        gridHeightDp = gridHeight,
    )
}

internal data class ShareLayout(
    val widthDp: Float,
    val heightDp: Float,
    val paddingDp: Float,
    val titleAreaHeightDp: Float,
    val headerRowHeightDp: Float,
    val labelColWidthDp: Float,
    val gridLeftDp: Float,
    val gridTopDp: Float,
    val dayColWidthDp: Float,
    val rowHeightDp: Float,
    val gridHeightDp: Float,
)

/**
 * Cell → 占位矩形（dp）：x 按 overlapIndex/overlapCount 在列内并排切分，
 * y 按 row * rowHeight，高按 rowSpan * rowHeight —— 与课表 UI 的挪动逻辑一致。
 */
internal fun cardRect(layout: ShareLayout, cell: WeekGrid.Cell): ShareRect {
    val split = cell.overlapCount.coerceAtLeast(1)
    val slotWidth = layout.dayColWidthDp / split
    val left = layout.gridLeftDp + cell.col * layout.dayColWidthDp +
        cell.overlapIndex.coerceAtLeast(0) * slotWidth
    val top = layout.gridTopDp + cell.row * layout.rowHeightDp
    return ShareRect(
        left = left,
        top = top,
        right = left + slotWidth,
        bottom = top + cell.rowSpan * layout.rowHeightDp,
    )
}

/** 单字符宽度单位：汉字/全角 = 1，ASCII/半角 ≈ 0.55 */
internal fun charUnit(c: Char): Float =
    if (c.code > 0x2E7F) SHARE_CHAR_UNIT_CJK else SHARE_CHAR_UNIT_ASCII

/** 文本总宽度单位数（用于不依赖 Paint 的长度估算；sumOf 无 Float 重载，用 fold 累加） */
internal fun effectiveUnits(text: String): Float = text.fold(0f) { acc, c -> acc + charUnit(c) }

/**
 * 贪心分行：每行最多 [maxUnits] 个宽度单位，不足一行的整字不拆；
 * 单个字符本身超过上限时仍独占一行（保证推进，不丢字符）。
 */
internal fun wrapGreedy(text: String, maxUnits: Int): List<String> {
    require(maxUnits >= 1) { "maxUnits must be >= 1" }
    if (text.isEmpty()) return listOf("")
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var units = 0f
    for (c in text) {
        val u = charUnit(c)
        if (buf.isNotEmpty() && units + u > maxUnits) {
            out += buf.toString()
            buf.setLength(0)
            units = 0f
        }
        buf.append(c)
        units += u
    }
    out += buf.toString()
    return out
}

/** 取 [text] 前缀，宽不超过 [maxUnits] 个单位 */
internal fun truncateToUnits(text: String, maxUnits: Int): String {
    if (maxUnits <= 0 || text.isEmpty()) return ""
    val buf = StringBuilder()
    var units = 0f
    for (c in text) {
        val u = charUnit(c)
        if (units + u > maxUnits) break
        buf.append(c)
        units += u
    }
    return buf.toString()
}

/**
 * 文本分行 + 行数截断：先按 [maxUnits] 贪心分行，若行数超过 [maxLines]，
 * 保留前 maxLines-1 行，末行截到（maxUnits-1）单位并补省略号「…」。
 */
internal fun planTextLines(text: String, maxUnits: Int, maxLines: Int): PlannedLines {
    require(maxUnits >= 1) { "maxUnits must be >= 1" }
    require(maxLines >= 1) { "maxLines must be >= 1" }
    if (text.isEmpty()) return PlannedLines(listOf(""), false)
    val wrapped = wrapGreedy(text, maxUnits)
    if (wrapped.size <= maxLines) return PlannedLines(wrapped, false)
    val kept = wrapped.take(maxLines - 1).toMutableList()
    val budget = maxUnits - 1 // 「…」占 1 个单位
    kept += if (budget <= 0) "…" else truncateToUnits(wrapped[maxLines - 1], budget) + "…"
    return PlannedLines(kept, true)
}

/**
 * 课程卡内文字排版：优先大字号完整显示课程名，放不下时缩小字号；
 * 高度足够则保留地点小字行；极小卡片回退 9sp 按高度裁行、末行省略号。
 * [textWidthDp]/[textHeightDp] 为卡片内文字区域（已扣除内边距）。
 */
internal fun planCardText(
    name: String,
    location: String,
    textWidthDp: Float,
    textHeightDp: Float,
): CardTextPlan {
    require(textWidthDp > 0f) { "textWidthDp must be positive" }
    require(textHeightDp > 0f) { "textHeightDp must be positive" }
    val maxMeta = if (location.isNotBlank()) 1 else 0
    for (f in SHARE_FONT_CANDIDATES) {
        val maxUnits = (textWidthDp / f).toInt().coerceAtLeast(1)
        val lines = wrapGreedy(name, maxUnits)
        val nameHeight = lines.size * f * SHARE_NAME_LINE_HEIGHT_FACTOR
        // 同字号下优先保留地点行
        for (k in maxMeta downTo 0) {
            if (nameHeight + k * SHARE_META_ROW_HEIGHT_DP <= textHeightDp) {
                return CardTextPlan(f, lines, false, k == 1)
            }
        }
    }
    // 全字号都放不下（极小卡片）：9sp、按高度裁行、末行省略号
    val maxUnits = (textWidthDp / SHARE_NAME_FONT_MIN_SP).toInt().coerceAtLeast(1)
    val maxLines = (textHeightDp / (SHARE_NAME_FONT_MIN_SP * SHARE_NAME_LINE_HEIGHT_FACTOR))
        .toInt().coerceAtLeast(1)
    val plan = planTextLines(name, maxUnits, maxLines)
    return CardTextPlan(SHARE_NAME_FONT_MIN_SP, plan.lines, plan.truncated, false)
}

// ---------------------------------------------------------------------------
// 渲染（android.graphics，尽力而为，不经单测）
// ---------------------------------------------------------------------------

/** 图宽（px），契约固定 */
private const val IMAGE_WIDTH_PX = 1080

// scaledDensity 在 API 34+ 标记废弃，但画布按系统字体缩放渲染 sp 文本仍需它
@Suppress("DEPRECATION")
private fun renderScheduleBitmap(
    context: Context,
    courses: List<Course>,
    week: Int,
    weekDates: List<LocalDate>,
    shifts: Map<LocalDate, Int>,
    rowHeightDp: Float,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val scaledDensity = context.resources.displayMetrics.scaledDensity
    val layout = computeShareLayout(IMAGE_WIDTH_PX, density, rowHeightDp)
    fun dp(v: Float) = v * density
    fun sp(v: Float) = v * scaledDensity

    val heightPx = kotlin.math.ceil(layout.heightDp * density).toInt()
    val bitmap = Bitmap.createBitmap(IMAGE_WIDTH_PX, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val gridLeft = dp(layout.gridLeftDp)
    val gridTop = dp(layout.gridTopDp)
    val gridRight = dp(layout.gridLeftDp + layout.dayColWidthDp * 7)
    val gridBottom = dp(layout.gridTopDp + layout.gridHeightDp)
    val gridLineColor = 0xFFE3E6EA.toInt()
    val textDark = 0xFF1B1F23.toInt()

    // 背景
    canvas.drawColor(Color.WHITE)

    // 标题（「第 N 周课表」）
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textDark
        textSize = sp(SHARE_TITLE_FONT_SP)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val titleText = context.getString(R.string.share_week_title, week)
    val titleAreaTop = dp(layout.paddingDp)
    val titleAreaBottom = dp(layout.paddingDp + layout.titleAreaHeightDp)
    canvas.drawText(
        titleText,
        dp(layout.widthDp) / 2f,
        centeredBaseline(titleAreaTop, titleAreaBottom, titlePaint),
        titlePaint,
    )

    // 表头底纹 + 网格线
    val headerBg = Paint().apply { color = 0xFFF4F6F8.toInt() }
    val headerTop = dp(layout.gridTopDp - layout.headerRowHeightDp)
    canvas.drawRect(gridLeft, headerTop, gridRight, gridTop, headerBg)
    val linePaint = Paint().apply { color = gridLineColor; strokeWidth = 1f }
    for (r in 0..WeekGrid.MAX_PERIODS) {
        val y = gridTop + r * dp(layout.rowHeightDp)
        canvas.drawLine(gridLeft, y, gridRight, y, linePaint)
    }
    for (c in 0..7) {
        val x = gridLeft + c * dp(layout.dayColWidthDp)
        canvas.drawLine(x, headerTop, x, gridBottom, linePaint)
    }

    // 表头星期（沿用 WeekGrid 的调休列映射：某列显示映射后的星期）
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textDark
        textSize = sp(SHARE_HEADER_FONT_SP)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    for (col in 0..6) {
        val res = weekdayNameRes(WeekGrid.mappedDayForColumn(col, weekDates, shifts))
        val label = if (res == 0) "" else context.getString(res)
        val cx = gridLeft + (col + 0.5f) * dp(layout.dayColWidthDp)
        canvas.drawText(label, cx, centeredBaseline(headerTop, gridTop, headerPaint), headerPaint)
    }

    // 左侧节次标签 1..12
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textDark
        textSize = sp(SHARE_LABEL_FONT_SP)
        textAlign = Paint.Align.CENTER
    }
    val labelCx = dp(layout.paddingDp + layout.labelColWidthDp / 2f)
    for (period in 1..WeekGrid.MAX_PERIODS) {
        val top = gridTop + (period - 1) * dp(layout.rowHeightDp)
        val bottom = top + dp(layout.rowHeightDp)
        canvas.drawText(period.toString(), labelCx, centeredBaseline(top, bottom, labelPaint), labelPaint)
    }

    // 课程卡片（按 WeekGrid.cellsFor 摆放）
    val cells = WeekGrid.cellsFor(courses, week, weekDates, shifts)
    val cardInset = dp(2f)
    for (cell in cells) {
        drawCourseCard(
            canvas = canvas,
            cell = cell,
            layout = layout,
            density = density,
            scaledDensity = scaledDensity,
            cardInset = cardInset,
            dp = ::dp,
            sp = ::sp,
        )
    }
    return bitmap
}

/** 卡片背景 + 课程名/地点文字 */
private fun drawCourseCard(
    canvas: Canvas,
    cell: WeekGrid.Cell,
    layout: ShareLayout,
    density: Float,
    scaledDensity: Float,
    cardInset: Float,
    dp: (Float) -> Float,
    sp: (Float) -> Float,
) {
    val slot = cardRect(layout, cell)
    val card = RectF(
        dp(slot.left) + cardInset,
        dp(slot.top) + cardInset,
        dp(slot.right) - cardInset,
        dp(slot.bottom) - cardInset,
    )
    if (card.width() <= 0f || card.height() <= 0f) return

    val bg = CourseMapper.displayColor(CourseMapper.colorFor(cell.course.name))
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
    canvas.drawRoundRect(card, dp(6f), dp(6f), fill)

    // 白字对比度不足时用深色字
    val textColor = if (CourseMapper.whiteTextContrastOk(bg)) Color.WHITE else 0xFF1B1F23.toInt()

    val innerWidthDp = (slot.width - 2 * SHARE_CARD_TEXT_PAD_DP).coerceAtLeast(8f)
    val innerHeightDp = (slot.height - 2 * SHARE_CARD_TEXT_PAD_DP).coerceAtLeast(8f)
    val plan = planCardText(cell.course.name, cell.course.location, innerWidthDp, innerHeightDp)

    val innerLeft = card.left + dp(SHARE_CARD_TEXT_PAD_DP)
    val innerRight = card.right - dp(SHARE_CARD_TEXT_PAD_DP)
    val innerTop = card.top + dp(SHARE_CARD_TEXT_PAD_DP)
    val innerBottom = card.bottom - dp(SHARE_CARD_TEXT_PAD_DP)
    val metaReserve = if (plan.showLocation) dp(SHARE_META_ROW_HEIGHT_DP) else 0f

    // 课程名（按 plan 行数绘制，超出区域的行不画）
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sp(plan.fontSizeSp)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val fm = namePaint.fontMetrics
    val lineHeight = fm.descent - fm.ascent
    val maxDrawLines = ((innerBottom - innerTop - metaReserve) / lineHeight).toInt().coerceAtLeast(1)
    val drawLines = plan.nameLines.size.coerceAtMost(maxDrawLines)
    var textTop = innerTop + ((innerBottom - innerTop - metaReserve) - drawLines * lineHeight) / 2f
    val maxTextWidth = innerRight - innerLeft
    for (i in 0 until drawLines) {
        val line = fitToWidth(namePaint, plan.nameLines[i], maxTextWidth)
        canvas.drawText(line, card.centerX(), textTop - fm.ascent, namePaint)
        textTop += lineHeight
    }

    // 地点小字（单行，超宽省略）
    if (plan.showLocation && cell.course.location.isNotBlank()) {
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = 210
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
        }
        val metaFm = metaPaint.fontMetrics
        val baseline = card.bottom - dp(3f) - metaFm.descent
        canvas.drawText(fitToWidth(metaPaint, cell.course.location, maxTextWidth), card.centerX(), baseline, metaPaint)
    }
}

/** 块内垂直居中的文本基线 y */
private fun centeredBaseline(top: Float, bottom: Float, paint: Paint): Float {
    val fm = paint.fontMetrics
    val h = fm.descent - fm.ascent
    return top + (bottom - top - h) / 2f - fm.ascent
}

/** 绘制时按真实测量兜底截断：超宽则逐字裁剪并补省略号（尽力而为） */
private fun fitToWidth(paint: Paint, text: String, maxWidthPx: Float): String {
    if (maxWidthPx <= 0f) return ""
    if (paint.measureText(text) <= maxWidthPx) return text
    var s = text
    while (s.isNotEmpty() && paint.measureText(s + "…") > maxWidthPx) {
        s = s.dropLast(1)
    }
    return s + "…"
}

// ---------------------------------------------------------------------------
// MediaStore 保存 + 系统分享
// ---------------------------------------------------------------------------

private fun insertPngToMediaStore(context: Context, bitmap: Bitmap, week: Int): Uri {
    val resolver = context.contentResolver
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, context.getString(R.string.share_image_display_name, week, stamp))
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Schedule")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val uri = resolver.insert(collection, values)
        ?: throw IOException("MediaStore insert returned null")
    try {
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("PNG compress failed")
            }
            out.flush()
        } ?: throw IOException("Cannot open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
    return uri
}

private fun shareViaChooser(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}