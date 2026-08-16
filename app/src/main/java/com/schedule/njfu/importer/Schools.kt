package com.schedule.njfu.importer

import com.schedule.njfu.importer.njfu.CasLoginClient
import java.net.URLEncoder

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
 */
enum class School(
    val label: String,
    val loginUrl: String,
    val successHostPrefixes: List<String>,
    val successCookieMarker: String,
    val successUrlBlacklist: List<String> = emptyList(),
    val userAgent: String? = null,
) {
    NJFU(
        "南京林业大学",
        "https://uia.njfu.edu.cn/authserver/login?service=" +
            URLEncoder.encode("http://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do", "UTF-8"),
        listOf("https://jwxt.njfu.edu.cn", "http://jwxt.njfu.edu.cn"),
        "bzb_jsxsd",
        userAgent = CasLoginClient.BROWSER_UA,
    ),
    GXU(
        "广西大学",
        "https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_slogin.html",
        listOf("https://jwxt2018.gxu.edu.cn"),
        "JSESSIONID",
        listOf("login_slogin"),
        userAgent = null, // 系统默认移动 UA：jwglxt 登录页按手机版自适应渲染
    ),
}
