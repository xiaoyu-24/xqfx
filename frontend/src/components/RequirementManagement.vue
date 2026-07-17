<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

type SystemItem = { id: number; name: string }
type Item = {
  id: number
  title: string
  type: string | null
  requesterName: string | null
  department: string | null
  status: string | null
  submittedAt: string | null
  updatedAt?: string | null
  systemId: number | null
  targetVersionId: number | null
  targetVersionName?: string | null
  completedAt?: string | null
  handledBy?: string | null
  completionDescription?: string | null
  recordVersion?: number
}

const systems = ref<SystemItem[]>([])
const items = ref<Item[]>([])
const total = ref(0)
const loading = ref(true)
const processingId = ref<number | null>(null)
const processingLoading = ref(false)
const processingForm = reactive({ status: 'PENDING_EVALUATION', completedAt: '', handledBy: '', completionDescription: '', recordVersion: 0 })
const pageSize = 20

const statusLabel: Record<string, string> = { PENDING_EVALUATION: '待评估', CONFIRMED: '已确认', IN_DEVELOPMENT: '开发中', PAUSED: '暂停', COMPLETED: '已完成', REJECTED: '已拒绝', CLOSED: '已关闭' }
const typeLabel: Record<string, string> = { BUG: 'BUG', REQUIREMENT: '需求' }
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (item: Item) => item.targetVersionId === null ? '—' : item.targetVersionName ?? `版本 #${item.targetVersionId}`
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const localValue = value.replace('T', ' ')
  const match = localValue.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/)
  return match?.[1] ?? localValue
}
const toDateTimeLocal = (value: string | null | undefined) => {
  const formatted = formatShanghai(value)
  return formatted === '—' ? '' : formatted.replace(' ', 'T').slice(0, 16)
}

const loadSystems = async () => {
  try {
    const { data } = await api.get('/systems')
    systems.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.warning('系统信息加载失败')
  }
}

const query = async () => {
  loading.value = true
  try {
    const { data } = await api.get('/requirements/management', { params: { page: 0, size: pageSize } })
    items.value = data.content ?? []
    total.value = data.totalElements ?? 0
  } catch {
    ElMessage.error('加载管理需求失败')
  } finally {
    loading.value = false
  }
}

const openProcessing = async (item: Item) => {
  try {
    const { data } = await api.get(`/requirements/${item.id}`)
    processingId.value = item.id
    Object.assign(processingForm, {
      status: data.status ?? 'PENDING_EVALUATION',
      completedAt: toDateTimeLocal(data.completedAt),
      handledBy: data.handledBy ?? '',
      completionDescription: data.completionDescription ?? '',
      recordVersion: data.recordVersion ?? item.recordVersion ?? 0,
    })
  } catch {
    ElMessage.error('加载处理信息失败')
  }
}

const saveProcessing = async () => {
  if (processingId.value === null) return
  processingLoading.value = true
  try {
    await api.patch(`/requirements/${processingId.value}/processing`, {
      status: processingForm.status,
      completedAt: processingForm.completedAt ? `${processingForm.completedAt}:00` : null,
      handledBy: processingForm.handledBy.trim() || null,
      completionDescription: processingForm.completionDescription,
      recordVersion: processingForm.recordVersion,
    })
    processingId.value = null
    ElMessage.success('处理信息已保存')
    await query()
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) ElMessage.error('需求已被其他人修改，请刷新后重试')
    else ElMessage.error('保存处理信息失败')
  } finally {
    processingLoading.value = false
  }
}

onMounted(async () => {
  await loadSystems()
  await query()
})
</script>

<template>
  <section class="management-section" data-test="management-page">
    <div class="detail-header"><h2>待处理需求</h2><span class="muted">共 {{ total }} 条</span></div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>类型</th><th>需求标题</th><th>所属系统 / 版本</th><th>填写人 / 部门</th><th>当前状态</th><th>填写时间</th><th>最后修改</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in items" :key="item.id"><td>{{ item.type ? typeLabel[item.type] : '—' }}</td><td>{{ item.title || '未命名草稿' }}</td><td>{{ systemName(item.systemId) }} / {{ versionName(item) }}</td><td>{{ item.requesterName || '—' }} / {{ item.department || '—' }}</td><td>{{ item.status ? statusLabel[item.status] : '暂存草稿' }}</td><td>{{ formatShanghai(item.submittedAt || item.updatedAt) }}</td><td>{{ formatShanghai(item.updatedAt) }}</td><td><button type="button" :data-test="`manage-${item.id}`" @click="openProcessing(item)">填写处理情况</button></td></tr>
          <tr v-if="loading"><td colspan="8" class="empty">加载中…</td></tr><tr v-else-if="items.length === 0"><td colspan="8" class="empty">暂无待处理需求</td></tr>
        </tbody>
      </table>
    </div>
    <div v-if="processingId !== null" class="processing-form-wrap">
      <form class="requirement-edit" data-test="processing-form" @submit.prevent="saveProcessing">
        <div class="detail-header"><h3>填写需求完成情况</h3><button class="secondary" type="button" @click="processingId = null">返回管理需求</button></div>
        <div class="form-grid">
          <label>需求状态 <select v-model="processingForm.status" data-test="processing-status"><option value="PENDING_EVALUATION">待评估</option><option value="CONFIRMED">已确认</option><option value="IN_DEVELOPMENT">开发中</option><option value="PAUSED">暂停</option><option value="COMPLETED">已完成</option><option value="REJECTED">已拒绝</option><option value="CLOSED">已关闭</option></select></label>
          <label>完成时间 <input v-model="processingForm.completedAt" data-test="processing-completed-at" type="datetime-local"></label>
          <label>处理人 <input v-model="processingForm.handledBy" data-test="processing-handler" placeholder="请输入处理人"></label>
          <label class="full-width">完成情况 <textarea v-model="processingForm.completionDescription" data-test="processing-description" rows="6" placeholder="请输入处理结果、验证情况等"></textarea></label>
        </div>
        <div class="form-actions"><button class="primary" type="submit" data-test="processing-save" :disabled="processingLoading">{{ processingLoading ? '保存中…' : '保存处理情况' }}</button></div>
      </form>
    </div>
  </section>
</template>

