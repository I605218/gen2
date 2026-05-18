<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h2>数据集管理</h2>
      <el-button type="primary" @click="$router.push('/datasets/create')">
        <el-icon><Plus /></el-icon> 新建数据集
      </el-button>
    </div>

    <el-table :data="datasets" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="用例数" width="90">
        <template #default="{ row }">{{ row.items?.length ?? 0 }}</template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" width="170">
        <template #default="{ row }">{{ formatDate(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/datasets/${row.id}/edit`)">编辑</el-button>
          <el-button size="small" type="danger" @click="del(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDatasets, deleteDataset } from '../api'

const datasets = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  const res = await getDatasets()
  datasets.value = res.data
  loading.value = false
}

async function del(id) {
  await ElMessageBox.confirm('确认删除该数据集？', '警告', { type: 'warning' })
  await deleteDataset(id)
  ElMessage.success('已删除')
  load()
}

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : '-'
onMounted(load)
</script>
