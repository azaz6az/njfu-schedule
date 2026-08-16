package com.schedule.njfu.importer

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
 */
enum class School(
    val label: String,
    val loginUrl: String,
    val successHostPrefixes: List<String>,
    val successCookieMarker: String,
    val successUrlBlacklist: List<String> = emptyList(),
) {
    NJFU(
        "南京林业大学",
        "https://uia.njfu.edu.cn/authserver/login?service=" +
            URLEncoder.encode("http://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do", "UTF-8"),
        listOf("https://jwxt.njfu.edu.cn", "http://jwxt.njfu.edu.cn"),
        "bzb_jsxsd",
    ),
    GXU(
        "广西大学",
        "https://jwxt2018.gxu.edu.cn/jwglxt/xtgl/login_slogin.html",
        listOf("https://jwxt2018.gxu.edu.cn"),
        "JSESSIONID",
        listOf("login_slogin"),
    ),
}
