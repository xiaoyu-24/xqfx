<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

type SystemItem = { id: number; name: string; ownerName: string; collaborators: string[]; status: string }
const systems = ref<SystemItem[]>([])
const loading = ref(false)
const loadSystems = async () => { loading.value = true; try { systems.value = (await api.get('/systems')).data } catch { ElMessage.error('加载系统列表失败') } finally { loading.value = false } }
onMounted(loadSystems)
</script>

<template>
  <section class="system-page">
    <div class="system-toolbar">
      <input placeholder="按系统名称搜索">
      <button class="primary" type="button">新增系统</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>系统名称</th><th>负责人</th><th>协助人</th><th>状态</th><th>关联需求</th><th>操作</th></tr></thead>
        <tbody><tr v-for="system in systems" :key="system.id"><td>{{ system.name }}</td><td>{{ system.ownerName }}</td><td>{{ system.collaborators.join('、') || '—' }}</td><td>{{ system.status === 'ACTIVE' ? '启用' : '停用' }}</td><td>—</td><td>编辑　版本管理　{{ system.status === 'ACTIVE' ? '停用' : '启用' }}　删除</td></tr><tr v-if="!loading && systems.length === 0"><td colspan="6" class="empty">暂无系统数据，请新增系统</td></tr></tbody>
      </table>
    </div>
    <aside class="version-panel">
      <h2>版本管理</h2>
      <p>选择系统后，可新增、编辑、启用、停用或删除版本。</p>
      <button class="secondary" type="button">新增版本</button>
    </aside>
  </section>
</template>
