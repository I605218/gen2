<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" style="background:#1e293b">
      <div style="padding:20px 16px;color:#fff;font-size:18px;font-weight:bold;border-bottom:1px solid #334155">
        Agent 评估平台
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#1e293b"
        text-color="#94a3b8"
        active-text-color="#38bdf8"
      >
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon>评测任务
        </el-menu-item>
        <el-menu-item index="/compare">
          <el-icon><DataAnalysis /></el-icon>对比分析
        </el-menu-item>
        <el-menu-item index="/datasets">
          <el-icon><FolderOpened /></el-icon>数据集管理
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main style="background:#f1f5f9;padding:24px">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; }
</style>
