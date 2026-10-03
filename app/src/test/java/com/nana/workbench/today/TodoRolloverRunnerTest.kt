package com.nana.workbench.today

import com.nana.workbench.data.db.TodoDao
import com.nana.workbench.data.db.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3/M7 阶段的测试：顺延结算这一步"读出来 → 算出来 → 写回去"。
 *
 * 用一个内存里的假数据库（实现 TodoDao 接口）来测，
 * 这样不需要手机、也不需要模拟器，就能验证"到底改了哪几条、改成什么"。
 */
class TodoRolloverRunnerTest {

    private class FakeTodoDao(initial: List<TodoEntity>) : TodoDao {
        val items = initial.toMutableList()
        var updateCount = 0

        override suspend fun listUnfinishedBefore(date: String): List<TodoEntity> =
            items.filter { !it.done && it.dueDate < date }

        override suspend fun update(item: TodoEntity) {
            updateCount++
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) items[index] = item
        }

        // 下面这些本测试用不到，保持最小实现
        override fun watchForDate(date: String): Flow<List<TodoEntity>> = flowOf(emptyList())
        override fun watchDoneForDate(date: String): Flow<List<TodoEntity>> = flowOf(emptyList())
        override suspend fun listForDate(date: String): List<TodoEntity> = emptyList()
        override fun watchOpenCount(date: String): Flow<Int> = flowOf(0)
        override suspend fun listAllOpen(): List<TodoEntity> = emptyList()
        override suspend fun listAll(): List<TodoEntity> = items.toList()
        override suspend fun insert(item: TodoEntity): Long = 0L
        override suspend fun insertAll(items: List<TodoEntity>) = Unit
        override suspend fun delete(item: TodoEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteDoneOn(date: String) = Unit
        override suspend fun deleteAll() = Unit
    }

    private fun todo(
        id: Long,
        dueDate: String,
        done: Boolean = false,
        postponed: Int = 0,
        title: String = "事$id",
    ) = TodoEntity(
        id = id,
        title = title,
        done = done,
        originalDate = dueDate,
        dueDate = dueDate,
        postponedCount = postponed,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `昨天没做完的会被挪到今天_并累加一天`() = runBlocking {
        val dao = FakeTodoDao(listOf(todo(1, "2026-10-08")))
        val moved = TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals(1, moved)
        assertEquals("2026-10-09", dao.items[0].dueDate)
        assertEquals(1, dao.items[0].postponedCount)
    }

    @Test
    fun `今天和以后的一条都不动`() = runBlocking {
        val dao = FakeTodoDao(
            listOf(
                todo(1, "2026-10-09"),
                todo(2, "2026-10-20"),
            ),
        )
        val moved = TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals(0, moved)
        assertEquals(0, dao.updateCount)
        assertEquals("2026-10-09", dao.items[0].dueDate)
        assertEquals("2026-10-20", dao.items[1].dueDate)
    }

    @Test
    fun `已完成的不动_哪怕日期是昨天`() = runBlocking {
        val dao = FakeTodoDao(listOf(todo(1, "2026-10-08", done = true)))
        val moved = TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals(0, moved)
        assertEquals("2026-10-08", dao.items[0].dueDate)
    }

    @Test
    fun `隔了三天没打开_一次累加三天`() = runBlocking {
        val dao = FakeTodoDao(listOf(todo(1, "2026-10-06", postponed = 2)))
        TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals("2026-10-09", dao.items[0].dueDate)
        assertEquals(5, dao.items[0].postponedCount)
    }

    @Test
    fun `混合清单只动该动的那些`() = runBlocking {
        val dao = FakeTodoDao(
            listOf(
                todo(1, "2026-10-07"),
                todo(2, "2026-10-09"),
                todo(3, "2026-10-08", postponed = 1),
                todo(4, "2026-11-01"),
                todo(5, "2026-10-01", done = true),
            ),
        )
        val moved = TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals("只应挪动 1 和 3", 2, moved)
        assertEquals(2, dao.updateCount)
        assertEquals("2026-10-09", dao.items[0].dueDate)
        assertEquals(2, dao.items[2].postponedCount)
        // 不该动的保持不变
        assertEquals("2026-10-09", dao.items[1].dueDate)
        assertEquals("2026-11-01", dao.items[3].dueDate)
        assertEquals("2026-10-01", dao.items[4].dueDate)
    }

    @Test
    fun `空清单不会碰数据库`() = runBlocking {
        val dao = FakeTodoDao(emptyList())
        assertEquals(0, TodoRolloverRunner.run(dao, "2026-10-09"))
        assertEquals(0, dao.updateCount)
    }

    @Test
    fun `跑两遍不会重复累加`() = runBlocking {
        val dao = FakeTodoDao(listOf(todo(1, "2026-10-08")))
        TodoRolloverRunner.run(dao, "2026-10-09")
        val secondRun = TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals("第二次应该没事可做", 0, secondRun)
        assertEquals(1, dao.items[0].postponedCount)
    }

    @Test
    fun `只改日期与顺延次数_标题等内容不变`() = runBlocking {
        val dao = FakeTodoDao(listOf(todo(1, "2026-10-08", title = "买牛奶")))
        TodoRolloverRunner.run(dao, "2026-10-09")

        assertEquals("买牛奶", dao.items[0].title)
        assertEquals("2026-10-08", dao.items[0].originalDate)
        assertTrue(dao.items[0].updatedAt > 0L)
    }
}
