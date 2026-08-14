package com.schedule.njfu.importer

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 全局共享的 HTTP 会话：登录与抓课表共用同一个 CookieJar，
 * 保证 CAS 登录成功后 jwxt 的会话 Cookie 能带到后续课表请求。
 *
 * 两个 client 共享同一 CookieJar：
 *  - [client]：自动跟随重定向（常规请求）
 *  - [noRedirectClient]：关闭重定向（CAS 登录流程需手动判定 302）
 */
object HttpSession {

    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
            cookies.compute(url.host) { _, old ->
                val list = old ?: mutableListOf()
                list.removeAll { oldCookie ->
                    newCookies.any { it.name == oldCookie.name && it.domain == oldCookie.domain }
                }
                list.addAll(newCookies)
                list
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies[url.host]?.filter { it.matches(url) } ?: emptyList()
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder().cookieJar(cookieJar).followRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
