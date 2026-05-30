package cn.chenxuhang.creativeai.core.database

import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.StructuredMemo

interface MemoTaskLocalDataSource {
    fun getAll(): List<MemoTask>
    fun save(task: MemoTask)
}

class InMemoryMemoTaskLocalDataSource : MemoTaskLocalDataSource {
    private val tasks = mutableListOf<MemoTask>()

    override fun getAll(): List<MemoTask> = tasks.toList()

    override fun save(task: MemoTask) {
        tasks.removeAll { it.id == task.id }
        tasks += task
    }

    fun describe(): String = "InMemoryMemoTaskLocalDataSource(${tasks.size} tasks)"
}

interface StructuredMemoLocalDataSource {
    fun getAll(): List<StructuredMemo>
    fun findByTaskId(taskId: String): StructuredMemo?
    fun save(memo: StructuredMemo)
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

    fun describe(): String = "InMemoryStructuredMemoLocalDataSource(${memos.size} memos)"
}
