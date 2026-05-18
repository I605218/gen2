<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h2>评测任务</h2>
      <el-button type="primary" @click="$router.push('/tasks/create')">
        <el-icon><Plus /></el-icon> 新建任务
      </el-button>
    </div>

    <el-table :data="tasks" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="任务名称" min-width="150" />
      <el-table-column prop="agent_version" label="Agent版本" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评测指标" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="m in row.metrics" :key="m" size="small" style="margin:2px">{{ m }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" width="170">
        <template #default="{ row }">{{ formatDate(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" :disabled="row.status === 'running'" @click="runTask(row.id)">
            运行
          </el-button>
          <el-button size="small" type="primary" @click="$router.push(`/tasks/${row.id}/result`)">
            结果
          </el-button>
          <el-button size="small" @click="$router.push(`/tasks/${row.id}/edit`)">编辑</el-button>
          <el-button size="small" type="danger" @click="delTask(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTasks, runTask as apiRunTask, deleteTask } from '../api'

const tasks = ref([])
const loading = ref(false)

async function loadTasks() {
  loading.value = true
  const res = await getTasks()
  tasks.value = res.data
  loading.value = false
}

async function runTask(id) {
  await apiRunTask(id)
  ElMessage.success('任务已启动，请稍后刷新查看结果')
  loadTasks()
}

async function delTask(id) {
  await ElMessageBox.confirm('确认删除该任务及其所有评测记录？', '警告', { type: 'warning' })
  await deleteTask(id)
  ElMessage.success('已删除')
  loadTasks()
}

const statusType = (s) => ({ pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }[s] || '')
const statusLabel = (s) => ({ pending: '待执行', running: '运行中', completed: '已完成', failed: '失败' }[s] || s)
const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : '-'

onMounted(loadTasks)
</script>
