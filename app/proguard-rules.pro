# ============================================================
# Apaches POI (HSSF) —— 仅用于读取老式 .xls (BIFF)
# POI 内部大量依赖反射 / 服务加载 / 类枚举，R8 无从静态分析全部入口，
# 直接整包 keep（safe），并抑制其可选日志/commons 依赖的告警。
# ============================================================
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.commons.**
-dontwarn com.zaxxer.**

# ============================================================
# kotlinx-serialization —— 序列化器为编译期生成、经反射查找，标准 keep 规则
# ============================================================
# kotlinx-serialization 库自带 consumer rules（serializer/module 保留），
# 但 @Serializable 数据类的 "serializer companion" 类还需显式保留。
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.schedule.njfu.**$$serializer { *; }
-keepclassmembers class com.schedule.njfu.** {
    *** Companion;
}
-keepclasseswithmembers class com.schedule.njfu.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.schedule.njfu.**
-keepclassmembers class <1> {
    static <1>$serializer INSTANCE;
}

# ============================================================
# Room —— 自带 consumer rules；下列 keep 兜底（DAO 实现 / 实体元信息）
# ============================================================
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class ** { *; }
-keep @androidx.room.Dao class ** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# ============================================================
# okhttp —— 库自带 consumer rules；仅抑制可选告警
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# 通用
# ============================================================
# fastexcel-reader（org.dhatim）读取 .xlsx 时引用 Java SE 的 javax.xml.stream（StAX），
# Android 平台不提供该 API（JDK 专属）；R8 报 missing class 时用 dontwarn 抑制。
# 运行时若走到 StAX 路径需自行处理（当前 .xlsx 导入走 POI/自定义解析，StAX 路径实际未触发）。
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.**
-dontwarn javax.annotation.**
