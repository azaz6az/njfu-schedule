package com.schedule.njfu.importer.njfu

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.WeekUtils

/**
 * 正方教务系统课表 HTML 解析器（按南林真实页面结构实现，见 test fixtures/njfu_schedule_sample.html）。
 *
 * 真实页面结构：
 *  - `<table id="timetable">`：首行为星期表头；其后每行第一列 `<th>` 为「第N大节」标签（N=一~六），
 *    其余 7 个 `<td>` 对应星期一~星期日；最后一行是「备注」行（跳过）。
 *  - 每个单元格含 3 个变体 div（kbcontent1 / kbcontent / kbcontent 空），取第一个非空 `class="kbcontent"` 的内容；
 *  - 变体 div 内一门课一块，多门课以 `-----` 横线分隔；每块内 `<font title=...>` 标注：
 *      无 title=课程名，教师/周次(节次)/教学楼/教室；`通知单编号`/`班级`/`备注` 等隐藏字段忽略。
 */
object JwxtParser {

    /**
     * 页面是否含"跳转登录"标记（会话失效：未登录时 jsxsd 返回 200 + JS 跳转脚本，而非 302）。
     * 要求 JS 跳转与登录地址同时出现，避免误伤页面正文/注释中仅提及登录地址的课表页。
     */
    fun isLoginRedirect(html: String): Boolean =
        html.contains("window.location.href") && html.contains("authserver/login")

    /** 页面是否像课表页（含表格与星期表头特征），用于区分"空课表"与"接口异常" */
    fun looksLikeSchedulePage(html: String): Boolean =
        html.contains("<table", ignoreCase = true) &&
            (html.contains("星期") || html.contains("周一") || html.contains("节次"))

    fun parseSchedule(html: String): List<Course> {
        val courses = mutableListOf<Course>()
        val table = Regex("<table[^>]*id=\"timetable\"[^>]*>(.*?)</table>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return courses
        val rows = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(table).toList()
        for (row in rows) {
            val inner = row.groupValues[1]
            val labelHtml = Regex("<th[^>]*>(.*?)</th>", RegexOption.DOT_MATCHES_ALL)
                .find(inner)?.groupValues?.get(1)
            val label = labelHtml?.let { cleanText(it) } ?: ""
            val blockMatch = Regex("^第([一二三四五六七八九十]+)大节$").find(label) ?: continue
            val block = WeekUtils.chineseToInt(blockMatch.groupValues[1]) ?: continue
            val startPeriod = block * 2 - 1
            val endPeriod = block * 2
            val tds = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                .findAll(inner).toList()
            for ((idx, td) in tds.withIndex()) {
                if (idx >= 7) break
                parseCell(td.groupValues[1], dayOfWeek = idx + 1, startPeriod, endPeriod, courses)
            }
        }
        return courses
    }

    /** 从单元格 HTML 提取课程（一块一 Course，块间以 `-----` 分隔） */
    private fun parseCell(
        cellHtml: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        out: MutableList<Course>,
    ) {
        val content = visibleKbContent(cellHtml) ?: return
        for (block in content.split(Regex("-{5,}"))) {
            var name = ""
            var teacher = ""
            var weekText = ""
            var building = ""
            var room = ""
            val fonts = Regex("<font([^>]*)>(.*?)</font>", RegexOption.DOT_MATCHES_ALL)
                .findAll(block).toList()
            for (f in fonts) {
                val attrs = f.groupValues[1]
                val text = cleanText(f.groupValues[2])
                val title = Regex("title=(\"([^\"]*)\"|'([^']*)')").find(attrs)
                    ?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3] } } ?: ""
                when {
                    title.isEmpty() && name.isEmpty() && text.isNotEmpty() -> name = text
                    title == "教师" -> teacher = text
                    title == "周次(节次)" -> weekText = text
                    title == "教学楼" -> building = text.trim('[', '【', '】', ']')
                    title == "教室" -> room = text
                }
            }
            if (name.isBlank()) continue
            val location = listOf(building, room).filter { it.isNotBlank() }.joinToString(" ")
            out += Course(
                name = name,
                teacher = teacher,
                location = location,
                dayOfWeek = dayOfWeek,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weeks = WeekUtils.parseWeeksText(weekText),
                color = 0,
            )
        }
    }

    /**
     * 取单元格里第一个非空的 `class="kbcontent"` div 内容；没有则退回 `kbcontent1`（简版：课程名/周次/教室）。
     * 原始页面三个变体 div 均 display:none，由页面 JS 切换显示，故不能依赖 style 判断——
     * 直接按 class 取第一个非空变体。
     */
    private fun visibleKbContent(cellHtml: String): String? {
        val bodies = linkedMapOf<String, String>()
        val chunks = Regex("(?=<div id=\")").split(cellHtml)
        for (chunk in chunks) {
            // 与属性顺序无关：分别提取 div 的 id、class、style，不强制 style 在 class 之前
            val open = Regex("<div\\b[^>]*>").find(chunk) ?: continue
            val openTag = open.value
            val idAttr = Regex("(?i)\\bid\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").find(openTag)
                ?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3] } }
            val clsAttr = Regex("(?i)\\bclass\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").find(openTag)
                ?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3] } }
            // style 属性不参与匹配逻辑，仅作兼容性提取（属性顺序无关）
            val styleAttr = Regex("(?i)\\bstyle\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").find(openTag)
                ?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3] } }
            // 三个属性都必须存在（原页面 div 均带 style），但不再要求固定顺序
            if (idAttr == null || clsAttr == null || styleAttr == null) continue
            val cls = clsAttr
            if (bodies.containsKey(cls)) continue
            val body = chunk
                .substring(open.range.last + 1)
                .replace(Regex("</div>\\s*$"), "")
            bodies[cls] = body
        }
        for (cls in listOf("kbcontent", "kbcontent1")) {
            val b = bodies[cls] ?: continue
            if (cleanText(b).isNotEmpty()) return b
        }
        return null
    }

    /** 去标签 + 常见实体反转义 */
    private fun cleanText(s: String): String =
        s.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
}
