package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.CasLoginClient
import java.net.URLEncoder

/**
 * 正方 jwglxt 新版教务系统（教学管理信息服务平台，方正 zfsoft）连接配置。
 *
 * 全国大量高校使用同一套「方正教育信息服务平台」，界面与接口结构一致，
 * 仅部署域名、路径前缀与具体接口名不同（常见前缀 `/jwglxt`，也有部署在根路径的学校）。
 * 2026-08 在广西大学实测：课表数据接口为 `kbcx/xskbcx_cxXsgrkb.html`（GET，URL 带 xnm/xqm），
 * 考试接口为 `kwgl/kscx_cxXsksxxIndex.html?doType=query`（POST），旧文档常写的
 * `kbcx/xskbcx_cxXsKb`/`kscx_cxXsksxxDg.html` 在部分学校已不存在（404）。
 * 本配置即「批量适配」的关键：新增一所正方学校只需提供学校名 + 教务地址，
 * 接口由通用逻辑推导（见 [com.schedule.njfu.importer.gxu.GxuAdapter]）。
 */
data class ZfJwglxtConfig(
    /** 教务系统根地址，如 https://jwxt2018.gxu.edu.cn */
    val baseUrl: String,
    /** 部署路径前缀：常见为 /jwglxt；部署在根路径的学校为 ""（如广东海洋大学） */
    val contextPath: String,
    /** 课表数据接口相对路径（V9 新版实测为 kbcx/xskbcx_cxXsgrkb.html，注意必须带 .html 后缀） */
    val schedulePath: String = "kbcx/xskbcx_cxXsgrkb.html",
    /** 课表接口请求方式：V9 新版为 GET（xnm/xqm 拼在 URL）；旧版为 POST 表单 */
    val scheduleMethod: ZfMethod = ZfMethod.GET,
    /** 考试数据接口相对路径（V9 新版实测为 kwgl/kscx_cxXsksxxIndex.html?doType=query） */
    val examPath: String = "kwgl/kscx_cxXsksxxIndex.html?doType=query",
    /** 考试接口请求方式 */
    val examMethod: ZfMethod = ZfMethod.POST,
) {

    /** 登录页 URL（WebView 打开） */
    val loginUrl: String get() = "$baseUrl$contextPath/xtgl/login_slogin.html"

    /** 课表接口完整 URL：GET 时 xnm/xqm 拼入查询串，POST 时放请求体 */
    fun scheduleUrl(xnm: String, xqm: String): String {
        val base = "$baseUrl$contextPath/$schedulePath?gnmkdm=N2151"
        return if (scheduleMethod == ZfMethod.GET) "$base&xnm=$xnm&xqm=$xqm" else base
    }

    /** 考试接口完整 URL */
    fun examUrl(xnm: String, xqm: String): String {
        val base = "$baseUrl$contextPath/$examPath"
        val sep = if (base.contains('?')) '&' else '?'
        return if (examMethod == ZfMethod.GET) "$base${sep}gnmkdm=N358105&xnm=$xnm&xqm=$xqm"
        else "$base${sep}gnmkdm=N358105"
    }

    /** 课表页（AJAX Referer 校验目标） */
    val scheduleReferer: String
        get() = "$baseUrl$contextPath/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

    /** 考试页（AJAX Referer 校验目标） */
    val examReferer: String
        get() = "$baseUrl$contextPath/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default"

    companion object {
        /**
         * 从用户填写的正方登录页 URL 解析配置。
         * 支持填写完整登录页（https://host/jwglxt/xtgl/login_slogin.html 或
         * https://host/xtgl/login_slogin.html）；无法识别为正方登录页返回 null。
         */
        fun fromLoginUrl(raw: String): ZfJwglxtConfig? {
            val trimmed = raw.trim().trimEnd('/')
            val m = Regex("^(https?)://([^/]+)(/.*)?$", RegexOption.IGNORE_CASE).find(trimmed)
                ?: return null
            val scheme = m.groupValues[1].lowercase()
            val host = m.groupValues[2].trim()
            if (host.isBlank()) return null
            val path = m.groupValues[3].takeIf { it.isNotBlank() } ?: "/"
            // 前缀 = 登录路径 "/xtgl/..." 之前的部分（通常为空或 /jwglxt）
            val xtglIdx = path.indexOf("/xtgl/")
            val contextPath = if (xtglIdx >= 0) path.substring(0, xtglIdx)
            else if (path.contains("/jwglxt")) "/jwglxt"
            else ""
            return ZfJwglxtConfig("$scheme://$host", contextPath)
        }
    }
}

/** 正方教务数据接口的请求方式 */
enum class ZfMethod { GET, POST }

/**
 * 支持导入的学校（教务系统）清单。
 *
 * 各字段用于：
 *  - [loginUrl]：WebView 登录页入口（[com.schedule.njfu.ui.import.CasLoginActivity] 的 START_URL）
 *  - [successHostPrefixes]：登录成功判定——WebView 当前 URL 落在该前缀下才认为是登录目标站点
 *  - [successCookieMarker]：登录成功后该站点下发的会话 Cookie 标记（只有带此 Cookie 才算登录完成）
 *  - [successUrlBlacklist]：即使域名与 Cookie 都命中也**不算**成功的 URL 片段——
 *    用于正方系统这类登录页自身就会下发 JSESSIONID 的情况（必须等离开登录页才算登录成功）
 *  - [userAgent]：WebView 登录页使用的 User-Agent；null 表示用系统默认（移动 UA，页面自适应手机布局）。
 *    南林 CAS 必须桌面 UA（移动 UA 会拿到精简版登录页缺字段）；正方 jwglxt 则要移动 UA 才出手机版页面。
 *  - [zfJwglxt]：正方 jwglxt 新版教务连接配置；null 表示非正方（南林走独立导入逻辑）。
 *    非 null 的学校共用通用正方导入流程（GxuAdapter + GxuParser），新增学校只需加一个枚举项。
 */
enum class School(
    val label: String,
    val loginUrl: String,
    val successHostPrefixes: List<String>,
    val successCookieMarker: String,
    val successUrlBlacklist: List<String> = emptyList(),
    val userAgent: String? = null,
    val zfJwglxt: ZfJwglxtConfig? = null,
) {
    NJFU(
        "南京林业大学",
        "https://uia.njfu.edu.cn/authserver/login?service=" +
            URLEncoder.encode("http://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do", "UTF-8"),
        listOf("https://jwxt.njfu.edu.cn", "http://jwxt.njfu.edu.cn"),
        "bzb_jsxsd",
        userAgent = CasLoginClient.BROWSER_UA,
        zfJwglxt = null,
    ),
    GXU(
        "广西大学",
        "https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_slogin.html",
        listOf("https://jwxt2018.gxu.edu.cn"),
        "JSESSIONID",
        listOf("login_slogin"),
        userAgent = null, // 系统默认移动 UA：jwglxt 登录页按手机版自适应渲染
        zfJwglxt = ZfJwglxtConfig("https://jwxt2018.gxu.edu.cn", "/jwglxt"),
    ),
    GDOU(
        "广东海洋大学",
        "https://jw.gdou.edu.cn/xtgl/login_slogin.html",
        listOf("https://jw.gdou.edu.cn"),
        "JSESSIONID",
        listOf("login_slogin"),
        userAgent = null, // 移动 UA：正方新版登录页自适应
        // 正方系统部署在根路径（无 /jwglxt 前缀），接口/登录页均为根路径，2026-08 实测确认
        zfJwglxt = ZfJwglxtConfig("https://jw.gdou.edu.cn", ""),
    ),
}