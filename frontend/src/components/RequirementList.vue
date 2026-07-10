<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const props = withDefaults(defineProps<{
  presetSystemId?: number | null
  presetRequestKey?: number
}>(), {
  presetSystemId: null,
  presetRequestKey: 0,
})

type SystemItem = { id: number; name: string; status: string }
type VersionItem = { id: number; name: string; status: string }
type Item = {
  id: number; title: string; type: string | null; requesterName: string | null; department: string | null; status: string | null
  submittedAt: string | null; systemId: number | null; targetVersionId: number | null; targetVersionName?: string | null; periodStartDate: string | null; periodEndDate: string | null
  content?: string | null; saveType?: string; updatedAt?: string | null; statusUpdatedAt?: string | null; recordVersion?: number
}
type Attachment = { id: number; originalName: string; contentType: string; sizeBytes: number }

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
const selectedRequirement = ref<Item | null>(null)
const detailAttachments = ref<Attachment[]>([])
const editingId = ref<number | null>(null)
const editingSaveType = ref('SUBMITTED')
const editVersions = ref<VersionItem[]>([])
const editForm = reactive({ requesterName: '', department: '', title: '', type: '', content: '', systemId: '', targetVersionId: '', periodStartDate: '', periodEndDate: '', status: '', recordVersion: 0 })

const statusLabel: Record<string, string> = { PENDING_EVALUATION: '待评估', CONFIRMED: '已确认', IN_DEVELOPMENT: '开发中', PAUSED: '暂停', COMPLETED: '已完成', REJECTED: '已拒绝', CLOSED: '已关闭' }
const typeLabel: Record<string, string> = { BUG: 'BUG', REQUIREMENT: '需求' }
const saveTypeLabel: Record<string, string> = { SUBMITTED: '正式需求', DRAFT: '草稿' }
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (id: number | null, name?: string | null) => id === null ? '—' : name ?? versions.value.find((version) => version.id === id)?.name ?? `版本 #${id}`

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

const applyPresetSystemFilter = async (systemId: number | null) => {
  if (systemId === null) return
  filters.systemId = String(systemId)
  await query()
}

watch(
  () => [props.presetSystemId, props.presetRequestKey] as const,
  async ([systemId]) => {
    await applyPresetSystemFilter(systemId)
  },
  { immediate: true }
)

const reset = () => {
  Object.assign(filters, { keyword: '', systemId: '', targetVersionId: '', department: '', requesterName: '', type: '', status: '', saveType: '', submittedFrom: '', submittedTo: '', periodOverlapStart: '', periodOverlapEnd: '' })
  versions.value = []
  query()
}

const viewDetails = async (id: number) => {
  try {
    const [detail, attachments] = await Promise.all([api.get(`/requirements/${id}`), api.get(`/requirements/${id}/attachments`)])
    selectedRequirement.value = detail.data
    detailAttachments.value = attachments.data
  } catch {
    ElMessage.error('加载需求详情失败')
  }
}

const deleteAttachment = async (attachment: Attachment) => {
  if (!window.confirm(`确定删除附件“${attachment.originalName}”吗？`)) return
  try {
    await api.delete(`/attachments/${attachment.id}`)
    detailAttachments.value = detailAttachments.value.filter((item) => item.id !== attachment.id)
    ElMessage.success('附件已删除')
  } catch {
    ElMessage.error('删除附件失败')
  }
}

const deleteRequirement = async (item: Item) => {
  if (!window.confirm(`确定删除需求“${item.title || '未命名草稿'}”吗？`)) return
  try {
    await api.delete(`/requirements/${item.id}`)
    if (selectedRequirement.value?.id === item.id) selectedRequirement.value = null
    ElMessage.success('需求已删除')
    await query(currentPage.value)
  } catch {
    ElMessage.error('删除需求失败')
  }
}

const loadEditVersions = async () => {
  editVersions.value = []
  if (!editForm.systemId) return
  try {
    const { data } = await api.get(`/systems/${editForm.systemId}/versions`)
    editVersions.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.warning('编辑时加载版本失败')
  }
}

const openEdit = async (id: number) => {
  try {
    const detail = (await api.get(`/requirements/${id}`)).data as Item
    editingId.value = id
    editingSaveType.value = detail.saveType ?? 'SUBMITTED'
    Object.assign(editForm, {
      requesterName: detail.requesterName ?? '', department: detail.department ?? '', title: detail.title ?? '', type: detail.type ?? '', content: detail.content ?? '',
      systemId: detail.systemId === null ? '' : String(detail.systemId), targetVersionId: detail.targetVersionId === null ? '' : String(detail.targetVersionId),
      periodStartDate: detail.periodStartDate ?? '', periodEndDate: detail.periodEndDate ?? '', status: detail.status ?? '', recordVersion: detail.recordVersion ?? 0,
    })
    await loadEditVersions()
  } catch {
    ElMessage.error('加载编辑数据失败')
  }
}

const changeEditSystem = async () => {
  editForm.targetVersionId = ''
  await loadEditVersions()
}

const saveEdit = async () => {
  if (editingId.value === null) return
  const body = {
    requesterName: editForm.requesterName, department: editForm.department, title: editForm.title, type: editForm.type || null, content: editForm.content,
    systemId: editForm.systemId ? Number(editForm.systemId) : null, targetVersionId: editForm.targetVersionId ? Number(editForm.targetVersionId) : null,
    periodStartDate: editForm.periodStartDate || null, periodEndDate: editForm.periodEndDate || null, status: editForm.status || null, recordVersion: editForm.recordVersion,
  }
  try {
    const path = editingSaveType.value === 'DRAFT' ? `/requirements/${editingId.value}/draft` : `/requirements/${editingId.value}`
    const { data } = await api.put(path, body)
    if (data?.id) selectedRequirement.value = data
    editingId.value = null
    ElMessage.success(editingSaveType.value === 'DRAFT' ? '草稿已更新' : '需求已更新')
    await query(currentPage.value)
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) ElMessage.error('需求已被其他人修改，请刷新后重试')
    else ElMessage.error('保存需求失败，请检查必填项和系统版本')
  }
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
          <tr v-for="item in items" :key="item.id"><td>{{ item.type ? typeLabel[item.type] : '—' }}</td><td>{{ item.title || '未命名草稿' }}</td><td>{{ systemName(item.systemId) }} / {{ versionName(item.targetVersionId, item.targetVersionName) }}</td><td>{{ item.requesterName || '—' }} / {{ item.department || '—' }}</td><td>{{ item.periodStartDate && item.periodEndDate ? `${item.periodStartDate} 至 ${item.periodEndDate}` : '—' }}</td><td>{{ item.status ? statusLabel[item.status] : saveTypeLabel[item.saveType ?? 'DRAFT'] }}</td><td>{{ item.submittedAt || '—' }}</td><td class="row-actions"><button type="button" :data-test="`view-${item.id}`" @click="viewDetails(item.id)">查看详情</button><button type="button" :data-test="`edit-${item.id}`" @click="openEdit(item.id)">编辑</button><button class="danger" type="button" :data-test="`delete-${item.id}`" @click="deleteRequirement(item)">删除</button></td></tr>
          <tr v-if="!loading && items.length === 0"><td colspan="8" class="empty">请设置筛选条件并查询需求列表</td></tr>
        </tbody>
      </table>
    </div>
    <form v-if="editingId !== null" class="requirement-edit" data-test="edit-form" @submit.prevent="saveEdit">
      <div class="detail-header"><h2>{{ editingSaveType === 'DRAFT' ? '编辑草稿' : '编辑需求' }}</h2><button class="secondary" type="button" @click="editingId = null">取消</button></div>
      <div class="form-grid">
        <label>姓名 <input v-model="editForm.requesterName" :required="editingSaveType !== 'DRAFT'"></label><label>部门 <input v-model="editForm.department" :required="editingSaveType !== 'DRAFT'"></label>
        <label class="full-width">需求标题 <input v-model="editForm.title" data-test="edit-title" :required="editingSaveType !== 'DRAFT'"></label>
        <label>类型 <select v-model="editForm.type" :required="editingSaveType !== 'DRAFT'"><option value="">请选择类型</option><option value="BUG">BUG</option><option value="REQUIREMENT">需求</option></select></label>
        <label>需求状态 <select v-model="editForm.status" :disabled="editingSaveType === 'DRAFT'"><option value="">待评估</option><option value="PENDING_EVALUATION">待评估</option><option value="CONFIRMED">已确认</option><option value="IN_DEVELOPMENT">开发中</option><option value="PAUSED">暂停</option><option value="COMPLETED">已完成</option><option value="REJECTED">已拒绝</option><option value="CLOSED">已关闭</option></select></label>
        <label>所属系统 <select v-model="editForm.systemId" @change="changeEditSystem"><option value="">暂无系统</option><option v-for="system in systems" :key="system.id" :value="String(system.id)" :disabled="system.status !== 'ACTIVE' && String(system.id) !== editForm.systemId">{{ system.name }}{{ system.status === 'ACTIVE' ? '' : '（已停用）' }}</option></select></label>
        <label>目标版本 <select v-model="editForm.targetVersionId" :disabled="!editForm.systemId"><option value="">请选择版本（可选）</option><option v-for="version in editVersions" :key="version.id" :value="String(version.id)" :disabled="version.status !== 'ACTIVE' && String(version.id) !== editForm.targetVersionId">{{ version.name }}{{ version.status === 'ACTIVE' ? '' : '（已停用）' }}</option></select></label>
        <div><span class="field-label">需求时间周期</span><div class="date-range"><input v-model="editForm.periodStartDate" type="date"><span>至</span><input v-model="editForm.periodEndDate" type="date"></div></div>
        <label class="full-width">需求内容 <textarea v-model="editForm.content" rows="8" :required="editingSaveType !== 'DRAFT'"></textarea></label>
      </div>
      <div class="form-actions"><button class="primary" type="submit">保存修改</button></div>
    </form>
    <article v-if="selectedRequirement" class="requirement-detail">
      <div class="detail-header"><h2>{{ selectedRequirement.title || '未命名草稿' }}</h2><button class="secondary" type="button" @click="selectedRequirement = null; detailAttachments = []">关闭详情</button></div>
      <dl><div><dt>类型</dt><dd>{{ selectedRequirement.type ? typeLabel[selectedRequirement.type] : '—' }}</dd></div><div><dt>状态</dt><dd>{{ selectedRequirement.status ? statusLabel[selectedRequirement.status] : '草稿' }}</dd></div><div><dt>所属系统</dt><dd>{{ systemName(selectedRequirement.systemId) }}</dd></div><div><dt>目标版本</dt><dd>{{ versionName(selectedRequirement.targetVersionId, selectedRequirement.targetVersionName) }}</dd></div><div><dt>填写人 / 部门</dt><dd>{{ selectedRequirement.requesterName || '—' }} / {{ selectedRequirement.department || '—' }}</dd></div><div><dt>填写时间</dt><dd>{{ selectedRequirement.submittedAt || '—' }}</dd></div><div><dt>需求周期</dt><dd>{{ selectedRequirement.periodStartDate && selectedRequirement.periodEndDate ? `${selectedRequirement.periodStartDate} 至 ${selectedRequirement.periodEndDate}` : '—' }}</dd></div><div><dt>最后修改</dt><dd>{{ selectedRequirement.updatedAt || '—' }}</dd></div></dl>
      <h3>需求内容</h3><p class="detail-content">{{ selectedRequirement.content || '—' }}</p>
      <h3>附件</h3><ul v-if="detailAttachments.length" class="attachment-list"><li v-for="attachment in detailAttachments" :key="attachment.id"><a :data-test="`attachment-download-${attachment.id}`" :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">{{ attachment.originalName }}</a>（{{ attachment.sizeBytes }} 字节）<button class="danger" type="button" @click="deleteAttachment(attachment)">删除</button></li></ul><p v-else>暂无附件</p>
    </article>
    <div class="pagination-placeholder">共 {{ total }} 条　<button type="button" :disabled="loading || currentPage === 0" @click="query(currentPage - 1)">上一页</button>　第 {{ currentPage + 1 }} / {{ Math.max(totalPages, 1) }} 页　<button type="button" :disabled="loading || currentPage + 1 >= totalPages" @click="query(currentPage + 1)">下一页</button></div>
  </section>
</template>
