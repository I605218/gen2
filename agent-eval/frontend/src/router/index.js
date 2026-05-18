import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/tasks' },
  {
    path: '/tasks',
    component: () => import('../views/TaskList.vue'),
    meta: { title: '评测任务' },
  },
  {
    path: '/tasks/create',
    component: () => import('../views/TaskForm.vue'),
    meta: { title: '创建任务' },
  },
  {
    path: '/tasks/:id/edit',
    component: () => import('../views/TaskForm.vue'),
    meta: { title: '编辑任务' },
  },
  {
    path: '/tasks/:id/result',
    component: () => import('../views/TaskResult.vue'),
    meta: { title: '评测结果' },
  },
  {
    path: '/compare',
    component: () => import('../views/Compare.vue'),
    meta: { title: '对比分析' },
  },
  {
    path: '/datasets',
    component: () => import('../views/DatasetList.vue'),
    meta: { title: '数据集管理' },
  },
  {
    path: '/datasets/create',
    component: () => import('../views/DatasetForm.vue'),
    meta: { title: '创建数据集' },
  },
  {
    path: '/datasets/:id/edit',
    component: () => import('../views/DatasetForm.vue'),
    meta: { title: '编辑数据集' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
