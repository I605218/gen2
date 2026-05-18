<template>
  <div>
    <h2 style="margin-bottom:16px">多任务对比分析</h2>
    <el-card style="margin-bottom:16px">
      <div style="display:flex;gap:12px;align-items:center">
        <el-select v-model="selectedIds" multiple placeholder="选择要对比的任务（可多选）" style="flex:1">
          <el-option v-for="t in allTasks" :key="t.id" :label="`#${t.id} ${t.name}`" :value="t.id" />
        </el-select>
        <el-button type="primary" @click="loadCompare" :disabled="selectedIds.length < 2">开始对比</el-button>
      </div>
    </el-card>

    <el-row :gutter="16" v-if="compareData.length">
      <!-- 柱状图对比 -->
      <el-col :span="24" style="margin-bottom:16px">
        <el-card>
          <template #header>指标得分对比（柱状图）</template>
          <v-chart :option="barOption" style="height:320px" autoresize />
        </el-card>
      </el-col>

      <!-- 详情表格 -->
      <el-col :span="24">
        <el-card>
          <template #header>汇总数据</template>
          <el-table :data="compareData" border>
            <el-table-column prop="name" label="任务名称" min-width="150" />
            <el-table-column prop="agent_version" label="Agent版本" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-for="key in metricKeys" :key="key" :label="key" width="130">
              <template #default="{ row }">
                {{ row.result_summary?.[key] != null
                  ? (row.result_summary[key] <= 1
                    ? (row.result_summary[key] * 100).toFixed(1) + '%'
                    : row.result_summary[key])
                  : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getTasks, compareTasks } from '../api'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const allTasks = ref([])
const selectedIds = ref([])
const compareData = ref([])

async function loadTasks() {
  const res = await getTasks()
  allTasks.value = res.data.filter(t => t.status === 'completed')
}

async function loadCompare() {
  const res = await compareTasks(selectedIds.value)
  compareData.value = res.data
}

const metricKeys = computed(() => {
  const keys = new Set()
  compareData.value.forEach(t => {
    Object.keys(t.result_summary || {}).forEach(k => keys.add(k))
  })
  return [...keys]
})

const barOption = computed(() => {
  const scoreKeys = metricKeys.value.filter(k => k !== 'total_cases' && k !== 'latency_ms' && k !== 'token_count')
  return {
    tooltip: { trigger: 'axis' },
    legend: {},
    xAxis: { type: 'category', data: scoreKeys },
    yAxis: { type: 'value', max: 1 },
    series: compareData.value.map(t => ({
      name: t.name,
      type: 'bar',
      data: scoreKeys.map(k => t.result_summary?.[k] ?? 0),
    })),
  }
})

const statusType = (s) => ({ pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }[s] || '')
const statusLabel = (s) => ({ pending: '待执行', running: '运行中', completed: '已完成', failed: '失败' }[s] || s)

onMounted(loadTasks)
</script>
