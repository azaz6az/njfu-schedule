package com.schedule.njfu.data

import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam

/** 导入差异：与现有课表对比后的结果，供导入预览确认 */
data class ImportDiff(
    val added: List<Course>,
    val removed: List<Course>,
    val changed: List<Pair<Course, Course>>, // (旧, 新)
    val unchanged: List<Course>,
    val conflicts: List<Pair<Course, Course>>, // 新数据内部互相冲突的课程对
) {
    val incomingSize: Int get() = added.size + changed.size + unchanged.size
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
}

class ScheduleRepository(
    private val db: AppDatabase,
) {
    val courses = db.courseDao().observeAll()

    suspend fun replaceAll(courses: List<Course>, exams: List<Exam> = emptyList()) {
        db.courseDao().clear()
        db.courseDao().upsertAll(courses.map { it.toEntity() })
        if (exams.isNotEmpty()) {
            db.examDao().clear()
            db.examDao().upsertAll(exams.map { it.toEntity() })
        }
    }

    suspend fun addCourse(course: Course) = db.courseDao().upsert(course.toEntity())
    suspend fun deleteCourse(id: Long) = db.courseDao().deleteById(id)

    companion object {
        /** 自动导入去重：name+day+startPeriod 相同视为重复 */
        fun merge(auto: List<Course>, manual: List<Course>): List<Course> {
            val seen = hashSetOf<Triple<String, Int, Int>>()
            val result = mutableListOf<Course>()
            (manual + auto).forEach { c ->
                val key = Triple(c.name, c.dayOfWeek, c.startPeriod)
                if (seen.add(key)) result += c
            }
            return result
        }

        /** 课程排课键：课名+星期+起止节+周次（决定课程在网格中的位置） */
        private fun keyOf(c: Course) = listOf(
            c.name, c.dayOfWeek, c.startPeriod, c.endPeriod, c.weeks,
        )

        /**
         * 计算导入差异。同键（name+day+start+end+weeks）且其余字段一致 → unchanged；
         * 同键但 teacher/location/color/note 不同 → changed（旧, 新）；
         * 新数据有旧数据没有的键 → added；反之 → removed。
         */
        fun diff(existing: List<Course>, incoming: List<Course>): ImportDiff {
            val oldByKey = existing.groupBy { keyOf(it) }
            val newByKey = incoming.groupBy { keyOf(it) }

            val added = mutableListOf<Course>()
            val removed = mutableListOf<Course>()
            val changed = mutableListOf<Pair<Course, Course>>()
            val unchanged = mutableListOf<Course>()

            newByKey.forEach { (key, newList) ->
                val oldList = oldByKey[key]
                if (oldList == null) {
                    added += newList
                } else {
                    // 同键多门课（重复数据）按顺序配对，剩余按新增/删除处理
                    val paired = minOf(oldList.size, newList.size)
                    for (i in 0 until paired) {
                        val old = oldList[i]
                        val new = newList[i]
                        if (sameCourse(old, new)) unchanged += new else changed += old to new
                    }
                    if (newList.size > paired) added += newList.drop(paired)
                    if (oldList.size > paired) removed += oldList.drop(paired)
                }
            }
            oldByKey.filterKeys { it !in newByKey }.values.forEach { removed += it }

            // 新数据内部冲突：同日、周次有交集、节次区间重叠（两两检测，去重）
            val conflicts = mutableListOf<Pair<Course, Course>>()
            for (i in incoming.indices) {
                for (j in i + 1 until incoming.size) {
                    val a = incoming[i]
                    val b = incoming[j]
                    if (a.dayOfWeek == b.dayOfWeek &&
                        (a.weeks and b.weeks) != 0 &&
                        a.startPeriod <= b.endPeriod && b.startPeriod <= a.endPeriod
                    ) {
                        conflicts += a to b
                    }
                }
            }
            return ImportDiff(added, removed, changed, unchanged, conflicts)
        }

        /** 排课属性一致即视为同一课程（用于预览摘要计数） */
        private fun sameCourse(a: Course, b: Course): Boolean =
            a.name == b.name && a.dayOfWeek == b.dayOfWeek &&
                a.startPeriod == b.startPeriod && a.endPeriod == b.endPeriod &&
                a.weeks == b.weeks && a.teacher == b.teacher &&
                a.location == b.location && a.color == b.color && a.note == b.note
    }
}
