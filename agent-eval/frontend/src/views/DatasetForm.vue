<template>
  <div style="max-width:800px">
    <h2 style="margin-bottom:24px">{{ isEdit ? '编辑数据集' : '新建数据集' }}</h2>
    <el-form :model="form" label-width="100px" v-loading="loading">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="如：编程问题测试集 v1" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" rows="2" />
      </el-form-item>

      <el-form-item label="测试用例">
        <div style="width:100%">
          <el-table :data="form.items" border style="margin-bottom:8px">
            <el-table-column label="#" width="50" type="index" />
            <el-table-column label="输入问题 *" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.input" type="textarea" rows="2" placeholder="用户问题" />
              </template>
            </el-table-column>
            <el-table-column label="预期答案（可选）" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.expected_output" type="textarea" rows="2" placeholder="用于对比评估" />
              </template>
            </el-table-column>
            <el-table-column label="预期工具（可选）" width="160">
              <template #default="{ row }">
                <el-input v-model="row.expected_tools_str" placeholder="工具名,逗号分隔" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button size="small" type="danger" @click="removeItem($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button @click="addItem" style="width:100%">+ 添加用例</el-button>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="submit">{{ isEdit ? '保存' : '创建' }}</el-button>
        <el-button @click="$router.push('/datasets')">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDataset, createDataset, updateDataset } from '../api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)

const form = ref({ name: '', description: '', items: [] })

function addItem() {
  form.value.items.push({ input: '', expected_output: '', expected_tools_str: '' })
}

function removeItem(idx) {
  form.value.items.splice(idx, 1)
}

async function load() {
  if (!isEdit.value) { addItem(); return }
  loading.value = true
  const res = await getDataset(route.params.id)
  const d = res.data
  form.value = {
    name: d.name,
    description: d.description || '',
    items: d.items.map(item => ({
      ...item,
      expected_tools_str: (item.expected_tools || []).join(','),
    })),
  }
  loading.value = false
}

async function submit() {
  if (!form.value.name) { ElMessage.warning('请填写名称'); return }
  if (!form.value.items.some(i => i.input)) { ElMessage.warning('至少添加一条测试用例'); return }

  const payload = {
    name: form.value.name,
    description: form.value.description,
    items: form.value.items
      .filter(i => i.input)
      .map(({ input, expected_output, expected_tools_str }) => ({
        input,
        expected_output: expected_output || null,
        expected_tools: expected_tools_str
          ? expected_tools_str.split(',').map(s => s.trim()).filter(Boolean)
          : null,
      })),
  }

  if (isEdit.value) {
    await updateDataset(route.params.id, payload)
    ElMessage.success('已更新')
  } else {
    await createDataset(payload)
    ElMessage.success('已创建')
  }
  router.push('/datasets')
}

onMounted(load)
</script>
