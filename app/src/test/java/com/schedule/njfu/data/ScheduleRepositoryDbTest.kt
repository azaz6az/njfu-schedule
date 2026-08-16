package com.schedule.njfu.data

import androidx.room.Room
import androidx.room.withTransaction
import com.schedule.njfu.model.Course
import com.schedule.njfu.model.Exam
import com.schedule.njfu.model.WeekUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ScheduleRepository.replaceAll] 的数据库级测试（Robolectric + Room 内存库，无需真机）。
 *
 * 覆盖：
 *  - 成功路径：replaceAll 后课程/考试表内容正确；
 *  - 幂等：重复调用且数据一致，不产生重复行；
 *  - exam 为空时不触碰 exams 表（手动导入不应清空手动录入的考试）；
 *  - 观察者（Flow）在 replaceAll 完成后收到最终态；
 *  - withTransaction 的失败回滚：事务中途抛异常时课程表不出现「半截状态」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScheduleRepositoryDbTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun course(name: String, day: Int, period: Int, weeks: Int = WeekUtils.maskFor(1, 16)) =
        Course(name = name, dayOfWeek = day, startPeriod = period, endPeriod = period,
            weeks = weeks, color = 0)

    private fun exam(name: String, date: String) = Exam(name = name, date = date)

    private fun courseNames(): List<String> = runBlocking {
        db.courseDao().getAll().map { it.name }.sorted()
    }

    private fun examNames(): List<String> = runBlocking {
        db.examDao().getAll().map { it.name }.sorted()
    }

    @Test
    fun `replaceAll writes courses and exams`() = runBlocking {
        val courses = listOf(
            course("高数", 1, 1),
            course("英语", 2, 1),
        )
        val exams = listOf(exam("高数期末", "2026-12-20"))
        repo.replaceAll(courses, exams)

        assertEquals(listOf("英语", "高数"), courseNames()) // 按 Unicode 码点排序："英"(U+82F1) < "高"(U+9AD8)
        assertEquals(listOf("高数期末"), examNames())
        // 周次掩码等字段原样入库
        val saved = db.courseDao().getAll().first { it.name == "高数" }.toModel()
        assertEquals(1, saved.dayOfWeek)
        assertEquals(WeekUtils.maskFor(1, 16), saved.weeks)
    }

    @Test
    fun `replaceAll is idempotent`() = runBlocking {
        val courses = listOf(
            course("高数", 1, 1),
            course("英语", 2, 1),
        )
        val exams = listOf(exam("高数期末", "2026-12-20"))

        repo.replaceAll(courses, exams)
        repo.replaceAll(courses, exams)

        // 重复调用不会累积重复行（upsert 语义 + 先清后插）
        assertEquals(2, db.courseDao().getAll().size)
        assertEquals(1, db.examDao().getAll().size)
        assertEquals(listOf("英语", "高数"), courseNames())
        assertEquals(listOf("高数期末"), examNames())
    }

    @Test
    fun `replaceAll with empty exams preserves exams table`() = runBlocking {
        // 第一次带考试数据整体替换
        repo.replaceAll(listOf(course("高数", 1, 1)), listOf(exam("高数期末", "2026-12-20")))
        assertEquals(listOf("高数期末"), examNames())

        // 第二次只替换课程（exams 默认空）：课程被替换，exams 表【不得被清空】
        repo.replaceAll(listOf(course("大学物理", 3, 1)))
        assertEquals(listOf("大学物理"), courseNames())
        assertEquals(listOf("高数期末"), examNames())
    }

    @Test
    fun `observer receives final state after replaceAll`() = runBlocking {
        repo.replaceAll(
            listOf(course("高数", 1, 1), course("英语", 2, 1)),
            emptyList(),
        )
        // replaceAll 返回后，观察者（小组件等）首次收到的一定是最终态
        // （事务保证 clear 与 upsert 作为整体提交，不会先看到空列表再看到数据）
        val observed = db.courseDao().observeAll().first().map { it.name }.sorted()
        assertEquals(listOf("英语", "高数"), observed)
    }

    @Test
    fun `failed transaction rolls back and does not empty courses`() = runBlocking {
        // 先有一个稳定状态
        repo.replaceAll(listOf(course("高数", 1, 1)), emptyList())
        assertEquals(listOf("高数"), courseNames())

        // 复现 replaceAll 内部事务同一形态：clear + upsertAll 后抛异常。
        // 生产数据（无 NOT NULL 约束、类型均为合法值）无法触发插入失败，
        // 故直接在事务里人为抛出，验证 withTransaction 的回滚机制本身可靠。
        val thrown = runCatching {
            db.withTransaction {
                db.courseDao().clear()
                db.courseDao().upsertAll(
                    listOf(
                        course("新课A", 1, 1),
                        course("新课B", 2, 1),
                    ).map { it.toEntity() },
                )
                throw IllegalStateException("模拟事务中途失败")
            }
        }
        assertTrue("事务应抛出异常", thrown.isFailure)

        // 回滚后：课程表仍是原状态，不被清空、不出现半截的新数据
        assertEquals(listOf("高数"), courseNames())
        assertTrue(db.courseDao().getAll().none { it.name == "新课A" })
        assertTrue(db.courseDao().getAll().none { it.name == "新课B" })
    }
}