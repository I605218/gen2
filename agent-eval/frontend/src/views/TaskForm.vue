<template>
  <div style="max-width:680px">
    <h2 style="margin-bottom:24px">{{ isEdit ? '编辑任务' : '新建评测任务' }}</h2>
    <el-form :model="form" label-width="120px" v-loading="loading">
      <el-form-item label="任务名称" required>
        <el-input v-model="form.name" placeholder="如：代码助手 v1.0 评测" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" rows="2" />
      </el-form-item>
      <el-form-item label="Agent URL" required>
        <el-input v-model="form.agent_url" placeholder="http://localhost:8080/evaluate" />
        <div style="font-size:12px;color:#999;margin-top:4px">Agent 需接受 POST 请求，body: {"input": "..."}</div>
      </el-form-item>
      <el-form-item label="Agent 版本">
        <el-input v-model="form.agent_version" placeholder="如：v1.0" />
      </el-form-item>
      <el-form-item label="数据集" required>
        <el-select v-model="form.dataset_id" placeholder="选择数据集" style="width:100%">
          <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="评测指标" required>
        <el-checkbox-group v-model="form.metrics">
          <el-checkbox v-for="m in availableMetrics" :key="m.key" :label="m.key">
            {{ m.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submit">{{ isEdit ? '保存' : '创建' }}</el-button>
        <el-button @click="$router.push('/tasks')">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDatasets, getMetrics, getTask, createTask, updateTask } from '../api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const datasets = ref([])
const availableMetrics = ref([])

const form = ref({
  name: '',
  description: '',
  agent_url: '',
  agent_version: '',
  dataset_id: null,
  metrics: [],
})

async function loadData() {
  loading.value = true
  const [dsRes, mRes] = await Promise.all([getDatasets(), getMetrics()])
  datasets.value = dsRes.data
  availableMetrics.value = mRes.data
  if (isEdit.value) {
    const taskRes = await getTask(route.params.id)
    const t = taskRes.data
    form.value = {
      name: t.name,
      description: t.description || '',
      agent_url: t.agent_url,
      agent_version: t.agent_version || '',
      dataset_id: t.dataset_id,
      metrics: t.metrics,
    }
  }
  loading.value = false
}

async function submit() {
  if (!form.value.name || !form.value.agent_url || !form.value.dataset_id || !form.value.metrics.length) {
    ElMessage.warning('请填写必填项')
    return
  }
  if (isEdit.value) {
    await updateTask(route.params.id, form.value)
    ElMessage.success('已更新')
  } else {
    await createTask(form.value)
    ElMessage.success('已创建')
  }
  router.push('/tasks')
}

onMounted(loadData)
</script>
