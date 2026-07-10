<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

type SystemItem = { id: number; name: string; status: string }
type VersionItem = { id: number; name: string; status: string }
type Item = {
  id: number; title: string; type: string | null; requesterName: string | null; department: string | null; status: string | null
  submittedAt: string | null; systemId: number | null; targetVersionId: number | null; periodStartDate: string | null; periodEndDate: string | null
}

const filters = reactive({
  keyword: '', systemId: '', targetVersionId: '', department: '', requesterName: '', type: '', status: '', saveType: '',
  submittedFrom: '', submittedTo: '', periodOverlapStart: '', periodOverlapEnd: '',
})
const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const items = ref<Item[]>([])
const total = ref(0)
const totalPages = ref(0)
const currentPage = ref(0)
const pageSize = 20
const loading = ref(false)

const statusLabel: Record<string, string> = { PENDING_EVALUATION: '待评估', CONFIRMED: '已确认', IN_DEVELOPMENT: '开发中', PAUSED: '暂停', COMPLETED: '已完成', REJECTED: '已拒绝', CLOSED: '已关闭' }
const typeLabel: Record<string, string> = { BUG: 'BUG', REQUIREMENT: '需求' }
const saveTypeLabel: Record<string, string> = { SUBMITTED: '正式需求', DRAFT: '草稿' }
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (id: number | null) => id === null ? '—' : versions.value.find((version) => version.id === id)?.name ?? `版本 #${id}`

const loadSystems = async () => {
  try {
    const { data } = await api.get('/systems')
    systems.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.warning('系统筛选项加载失败')
  }
}

const loadVersions = async () => {
  filters.targetVersionId = ''
  versions.value = []
  if (!filters.systemId || filters.systemId === 'none') return
  try {
    const { data } = await api.get(`/systems/${filters.systemId}/versions`)
    versions.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.warning('版本筛选项加载失败')
  }
}

watch(() => filters.systemId, loadVersions)

const queryParams = (page: number) => {
  const params: Record<string, string | number | boolean> = { page, size: pageSize }
  if (filters.systemId === 'none') params.unassignedSystem = true
  else if (filters.systemId) params.systemId = Number(filters.systemId)
  if (filters.targetVersionId) params.targetVersionId = Number(filters.targetVersionId)
  for (const key of ['keyword', 'department', 'requesterName', 'type', 'status', 'saveType', 'submittedFrom', 'submittedTo', 'periodOverlapStart', 'periodOverlapEnd'] as const) {
    if (filters[key]) params[key] = filters[key]
  }
  return params
}

const query = async (page = 0) => {
  loading.value = true
  try {
    const { data } = await api.get('/requirements/page', { params: queryParams(page) })
    items.value = data.content
    total.value = data.totalElements
    totalPages.value = data.totalPages
    currentPage.value = page
  } catch {
    ElMessage.error('查询失败，请检查筛选条件后重试')
  } finally {
    loading.value = false
  }
}

const reset = () => {
  Object.assign(filters, { keyword: '', systemId: '', targetVersionId: '', department: '', requesterName: '', type: '', status: '', saveType: '', submittedFrom: '', submittedTo: '', periodOverlapStart: '', periodOverlapEnd: '' })
  versions.value = []
  query()
}

onMounted(loadSystems)
</script>

<template>
  <section class="list-page">
    <div class="filter-grid">
      <label>关键词 <input v-model="filters.keyword" placeholder="标题或需求内容"></label>
      <label>所属系统 <select v-model="filters.systemId" data-test="system-filter"><option value="">全部系统</option><option value="none">暂无系统</option><option v-for="system in systems" :key="system.id" :value="String(system.id)">{{ system.name }}</option></select></label>
      <label>目标版本 <select v-model="filters.targetVersionId" :disabled="!filters.systemId || filters.systemId === 'none'"><option value="">全部版本</option><option v-for="version in versions" :key="version.id" :value="String(version.id)">{{ version.name }}</option></select></label>
      <label>部门 <input v-model="filters.department" placeholder="请输入部门"></label>
      <label>填写人 <input v-model="filters.requesterName" placeholder="请输入填写人"></label>
      <label>填写时间起 <input v-model="filters.submittedFrom" type="date"></label>
      <label>填写时间止 <input v-model="filters.submittedTo" type="date"></label>
      <label>需求状态 <select v-model="filters.status"><option value="">全部状态</option><option value="PENDING_EVALUATION">待评估</option><option value="CONFIRMED">已确认</option><option value="IN_DEVELOPMENT">开发中</option><option value="PAUSED">暂停</option><option value="COMPLETED">已完成</option><option value="REJECTED">已拒绝</option><option value="CLOSED">已关闭</option></select></label>
      <label>类型 <select v-model="filters.type"><option value="">全部类型</option><option value="BUG">BUG</option><option value="REQUIREMENT">需求</option></select></label>
      <label>保存类型 <select v-model="filters.saveType"><option value="">全部</option><option value="SUBMITTED">正式需求</option><option value="DRAFT">草稿</option></select></label>
      <label>周期起 <input v-model="filters.periodOverlapStart" type="date"></label>
      <label>周期止 <input v-model="filters.periodOverlapEnd" type="date"></label>
    </div>
    <div class="filter-actions"><button class="primary" type="button" data-test="query" :disabled="loading" @click="query()">查询</button><button class="secondary" type="button" @click="reset">重置</button></div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>类型</th><th>需求标题</th><th>所属系统 / 版本</th><th>填写人 / 部门</th><th>周期</th><th>状态</th><th>填写时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in items" :key="item.id"><td>{{ item.type ? typeLabel[item.type] : '—' }}</td><td>{{ item.title || '未命名草稿' }}</td><td>{{ systemName(item.systemId) }} / {{ versionName(item.targetVersionId) }}</td><td>{{ item.requesterName || '—' }} / {{ item.department || '—' }}</td><td>{{ item.periodStartDate && item.periodEndDate ? `${item.periodStartDate} 至 ${item.periodEndDate}` : '—' }}</td><td>{{ item.status ? statusLabel[item.status] : saveTypeLabel[item.status ?? 'DRAFT'] }}</td><td>{{ item.submittedAt || '—' }}</td><td>查看详情</td></tr>
          <tr v-if="!loading && items.length === 0"><td colspan="8" class="empty">请设置筛选条件并查询需求列表</td></tr>
        </tbody>
      </table>
    </div>
    <div class="pagination-placeholder">共 {{ total }} 条　<button type="button" :disabled="loading || currentPage === 0" @click="query(currentPage - 1)">上一页</button>　第 {{ currentPage + 1 }} / {{ Math.max(totalPages, 1) }} 页　<button type="button" :disabled="loading || currentPage + 1 >= totalPages" @click="query(currentPage + 1)">下一页</button></div>
  </section>
</template>
