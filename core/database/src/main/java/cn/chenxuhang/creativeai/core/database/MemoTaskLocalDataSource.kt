package cn.chenxuhang.creativeai.core.database

import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.StructuredMemo

interface MemoTaskLocalDataSource {
    fun getAll(): List<MemoTask>
    fun save(task: MemoTask)
    fun delete(taskId: String)
}

class InMemoryMemoTaskLocalDataSource : MemoTaskLocalDataSource {
    private val tasks = mutableListOf<MemoTask>()

    override fun getAll(): List<MemoTask> = tasks.toList()

    override fun save(task: MemoTask) {
        tasks.removeAll { it.id == task.id }
        tasks += task
    }

    override fun delete(taskId: String) {
        tasks.removeAll { it.id == taskId }
    }

    fun describe(): String = "InMemoryMemoTaskLocalDataSource(${tasks.size} tasks)"
}

interface StructuredMemoLocalDataSource {
    fun getAll(): List<StructuredMemo>
    fun findByTaskId(taskId: String): StructuredMemo?
    fun save(memo: StructuredMemo)
    fun delete(taskId: String)
}

class InMemoryStructuredMemoLocalDataSource : StructuredMemoLocalDataSource {
    private val memos = mutableListOf<StructuredMemo>()

    override fun getAll(): List<StructuredMemo> = memos.toList()

    override fun findByTaskId(taskId: String): StructuredMemo? {
        return memos.lastOrNull { it.taskId == taskId }
    }

    override fun save(memo: StructuredMemo) {
        memos.removeAll { it.taskId == memo.taskId }
        memos += memo
    }

    override fun delete(taskId: String) {
        memos.removeAll { it.taskId == taskId }
    }

    fun describe(): String = "InMemoryStructuredMemoLocalDataSource(${memos.size} memos)"
}
