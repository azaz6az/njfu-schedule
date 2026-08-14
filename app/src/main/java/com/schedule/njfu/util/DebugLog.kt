package com.schedule.njfu.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 轻量调试日志：写入应用私有目录，供设置页「导出调试日志」导出。
 * 用于排查真机上的小部件渲染/崩溃问题（无需 adb 或开发者选项）。
 */
object DebugLog {

    private const val FILE_NAME = "debug_log.txt"
    private const val MAX_SIZE = 512L * 1024

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 追加一条日志（带时间戳与进程号）；文件超限时保留后半部分 */
    fun write(context: Context, tag: String, throwable: Throwable? = null) {
        runCatching {
            val sw = StringWriter()
            throwable?.printStackTrace(PrintWriter(sw))
            val entry = buildString {
                append(java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date()))
                append(" [pid=").append(android.os.Process.myPid())
                append("] ").append(tag)
                if (throwable != null) append(" ").append(throwable.message ?: throwable.javaClass.simpleName)
                append('\n').append(sw).append("----\n")
            }
            val f = file(context)
            if (!f.exists()) f.createNewFile()
            f.appendText(entry, Charsets.UTF_8)
            if (f.length() > MAX_SIZE) {
                val keep = f.readText(Charsets.UTF_8).takeLast(MAX_SIZE.toInt() / 2)
                f.writeText(keep, Charsets.UTF_8)
            }
        }
    }

    /** 读取全部日志；无日志返回空串 */
    fun read(context: Context): String =
        runCatching { file(context).takeIf { it.exists() }?.readText(Charsets.UTF_8) }.getOrNull()
            ?: ""

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
