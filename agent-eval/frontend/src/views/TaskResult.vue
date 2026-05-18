<template>
  <div v-loading="loading">
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
      <el-button @click="$router.push('/tasks')" :icon="ArrowLeft" circle />
      <h2>{{ task?.name }} — 评测结果</h2>
      <el-tag :type="statusType(task?.status)">{{ statusLabel(task?.status) }}</el-tag>
    </div>

    <!-- 汇总卡片 -->
    <el-row :gutter="16" v-if="task?.result_summary" style="margin-bottom:20px">
      <el-col :span="4" v-for="(val, key) in task.result_summary" :key="key">
        <el-card shadow="hover" style="text-align:center">
          <div style="font-size:24px;font-weight:bold;color:#3b82f6">
            {{ typeof val === 'number' && val <= 1 ? (val * 100).toFixed(1) + '%' : val }}
          </div>
          <div style="font-size:12px;color:#64748b;margin-top:4px">{{ key }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 雷达图 -->
    <el-card style="margin-bottom:20px" v-if="radarReady">
      <v-chart :option="radarOption" style="height:300px" autoresize />
    </el-card>

    <!-- 详细记录 -->
    <el-card>
      <template #header>详细评测记录（共 {{ records.length }} 条）</template>
      <el-table :data="records" border stripe>
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="input" label="输入" min-width="200" show-overflow-tooltip />
        <el-table-column prop="actual_output" label="Agent 回答" min-width="200" show-overflow-tooltip />
        <el-table-column prop="latency_ms" label="耗时(ms)" width="100" />
        <el-table-column prop="token_count" label="Token" width="80" />
        <el-table-column label="指标得分" min-width="200">
          <template #default="{ row }">
            <div v-if="row.metric_scores">
              <el-tag
                v-for="(v, k) in row.metric_scores"
                :key="k"
                size="small"
                style="margin:2px"
                :type="scoreType(v)"
              >
                {{ k }}: {{ typeof v === 'number' && v <= 1 ? (v * 100).toFixed(0) + '%' : v }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="中间步骤" width="80">
          <template #default="{ row }">
            <el-button size="small" @click="showSteps(row)" :disabled="!row.steps">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 步骤详情弹窗 -->
    <el-dialog v-model="stepsVisible" title="中间步骤" width="60%">
      <pre style="white-space:pre-wrap;font-size:13px;max-height:400px;overflow:auto">{{ JSON.stringify(currentSteps, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getTask, getTaskRecords } from '../api'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { RadarComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([RadarChart, RadarComponent, TooltipComponent, CanvasRenderer])

const route = useRoute()
const task = ref(null)
const records = ref([])
const loading = ref(false)
const stepsVisible = ref(false)
const currentSteps = ref(null)

async function load() {
  loading.value = true
  const [tRes, rRes] = await Promise.all([
    getTask(route.params.id),
    getTaskRecords(route.params.id),
  ])
  task.value = tRes.data
  records.value = rRes.data
  loading.value = false
}

const statusType = (s) => ({ pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }[s] || '')
const statusLabel = (s) => ({ pending: '待执行', running: '运行中', completed: '已完成', failed: '失败' }[s] || s)
const scoreType = (v) => {
  if (typeof v !== 'number') return ''
  if (v >= 0.7) return 'success'
  if (v >= 0.4) return 'warning'
  return 'danger'
}

function showSteps(row) {
  currentSteps.value = row.steps
  stepsVisible.value = true
}

const radarReady = computed(() => {
  const s = task.value?.result_summary
  if (!s) return false
  return Object.keys(s).filter(k => k !== 'total_cases').length >= 2
})

const radarOption = computed(() => {
  const s = task.value?.result_summary || {}
  const entries = Object.entries(s).filter(([k]) => k !== 'total_cases' && !k.endsWith('_ms') && k !== 'latency' && k !== 'token_count')
  return {
    tooltip: {},
    radar: {
      indicator: entries.map(([k]) => ({ name: k, max: 1 })),
    },
    series: [{
      type: 'radar',
      data: [{ value: entries.map(([, v]) => v), name: task.value?.name }],
    }],
  }
})

onMounted(load)
</script>
