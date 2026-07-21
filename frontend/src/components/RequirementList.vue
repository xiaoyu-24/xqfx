<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
  completedAt?: string | null; handledBy?: string | null; completionDescription?: string | null
  content?: string | null; saveType?: string; updatedAt?: string | null; statusUpdatedAt?: string | null; recordVersion?: number
}
type Attachment = { id: number; originalName: string; contentType: string; sizeBytes: number; previewStatus?: string; previewAvailable?: boolean; previewContentType?: string | null; previewErrorMessage?: string | null }

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
const loading = ref(true)
const selectedRequirement = ref<Item | null>(null)
const detailAttachments = ref<Attachment[]>([])
const previewAttachment = ref<Attachment | null>(null)
const editingId = ref<number | null>(null)
const editingSaveType = ref('SUBMITTED')
const editVersions = ref<VersionItem[]>([])
const editAttachments = ref<Attachment[]>([])
const editSelectedFiles = ref<File[]>([])
const editUploading = ref(false)
const editForm = reactive({ requesterName: '', department: '', title: '', type: '', content: '', systemId: '', targetVersionId: '', periodStartDate: '', periodEndDate: '', status: '', recordVersion: 0 })
let previewRefreshTimer: ReturnType<typeof setTimeout> | undefined

const statusLabel: Record<string, string> = { PENDING_EVALUATION: '待评估', CONFIRMED: '已确认', IN_DEVELOPMENT: '开发中', PAUSED: '暂停', COMPLETED: '已完成', REJECTED: '已拒绝', CLOSED: '已关闭' }
const typeLabel: Record<string, string> = { BUG: 'BUG', REQUIREMENT: '需求' }
const saveTypeLabel: Record<string, string> = { SUBMITTED: '正式需求', DRAFT: '草稿' }
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (id: number | null, name?: string | null) => id === null ? '—' : name ?? versions.value.find((version) => version.id === id)?.name ?? `版本 #${id}`
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const localValue = value.replace('T', ' ')
  const match = localValue.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/)
  return match?.[1] ?? localValue
}
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
    schedulePreviewRefresh()
  } catch {
    ElMessage.error('加载需求详情失败')
  }
}

const clearPreviewRefresh = () => {
  if (previewRefreshTimer !== undefined) clearTimeout(previewRefreshTimer)
  previewRefreshTimer = undefined
}
const schedulePreviewRefresh = () => {
  clearPreviewRefresh()
  const pending = (list: Attachment[]) => list.some((item) => item.previewStatus === 'PENDING' || item.previewStatus === 'CONVERTING')
  if ((!selectedRequirement.value || !pending(detailAttachments.value)) && (editingId.value === null || !pending(editAttachments.value))) return
  previewRefreshTimer = setTimeout(refreshPreviewAttachments, 2000)
}
const refreshPreviewAttachments = async () => {
  try {
    if (selectedRequirement.value) {
      const { data } = await api.get(`/requirements/${selectedRequirement.value.id}/attachments`)
      detailAttachments.value = data
    }
    if (editingId.value !== null) {
      const { data } = await api.get(`/requirements/${editingId.value}/attachments`)
      editAttachments.value = data
    }
  } finally {
    schedulePreviewRefresh()
  }
}

const canPreview = (attachment: Attachment) => attachment.previewAvailable ?? (attachment.contentType === 'application/pdf' || attachment.contentType.startsWith('image/'))
const previewStateLabel = (attachment: Attachment) => attachment.previewStatus === 'FAILED' && attachment.previewErrorMessage
  ? `预览生成失败：${attachment.previewErrorMessage}`
  : ({ PENDING: '等待生成预览', CONVERTING: '正在生成预览', FAILED: '预览生成失败', UNAVAILABLE: '预览不可用' }[attachment.previewStatus ?? ''] ?? '')
const openAttachmentPreview = (attachment: Attachment) => {
  if (canPreview(attachment)) previewAttachment.value = attachment
}
const retryAttachmentPreview = async (attachment: Attachment) => {
  try {
    const { data } = await api.post(`/attachments/${attachment.id}/preview/retry`)
    for (const list of [detailAttachments, editAttachments]) {
      const index = list.value.findIndex((item) => item.id === attachment.id)
      if (index >= 0) list.value[index] = data
    }
    schedulePreviewRefresh()
    ElMessage.success('已提交预览生成任务')
  } catch {
    ElMessage.error('重新生成预览失败')
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
    editSelectedFiles.value = []
    try {
      const { data } = await api.get(`/requirements/${id}/attachments`)
      editAttachments.value = Array.isArray(data) ? data : []
    } catch {
      editAttachments.value = []
    }
    await loadEditVersions()
  } catch {
    ElMessage.error('加载编辑数据失败')
  }
}

const selectEditFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  editSelectedFiles.value = Array.from(input.files ?? [])
}

const uploadEditAttachments = async () => {
  if (editingId.value === null || editSelectedFiles.value.length === 0) return
  editUploading.value = true
  try {
    for (const file of editSelectedFiles.value) {
      const body = new FormData()
      body.append('file', file)
      const { data } = await api.post(`/requirements/${editingId.value}/attachments`, body)
      editAttachments.value.push(data)
    }
    editSelectedFiles.value = []
    schedulePreviewRefresh()
    ElMessage.success('附件上传成功')
  } catch {
    ElMessage.error('附件上传失败，未完成的文件可重新选择后上传')
  } finally {
    editUploading.value = false
  }
}

const deleteEditAttachment = async (attachment: Attachment) => {
  if (!window.confirm(`确定删除附件“${attachment.originalName}”吗？`)) return
  try {
    await api.delete(`/attachments/${attachment.id}`)
    editAttachments.value = editAttachments.value.filter((item) => item.id !== attachment.id)
    ElMessage.success('附件已删除')
  } catch {
    ElMessage.error('删除附件失败')
  }
}

const changeEditSystem = async () => {
  editForm.targetVersionId = ''
  await loadEditVersions()
}

const saveEdit = async (submitDraft = false) => {
  if (editingId.value === null) return
  const body = {
    requesterName: editForm.requesterName, department: editForm.department, title: editForm.title, type: editForm.type || null, content: editForm.content,
    systemId: editForm.systemId ? Number(editForm.systemId) : null, targetVersionId: editForm.targetVersionId ? Number(editForm.targetVersionId) : null,
    periodStartDate: editForm.periodStartDate || null, periodEndDate: editForm.periodEndDate || null, status: editForm.status || null, recordVersion: editForm.recordVersion,
  }
  try {
    const isDraftSave = editingSaveType.value === 'DRAFT' && !submitDraft
    const path = isDraftSave ? `/requirements/${editingId.value}/draft` : `/requirements/${editingId.value}`
    const { data } = await api.put(path, body)
    if (data?.id) selectedRequirement.value = data
    editingId.value = null
    ElMessage.success(isDraftSave ? '草稿已更新' : submitDraft ? '草稿已正式提交' : '需求已更新')
    await query(currentPage.value)
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) ElMessage.error('需求已被其他人修改，请刷新后重试')
    else ElMessage.error('保存需求失败，请检查必填项和系统版本')
  }
}

onMounted(async () => {
  await loadSystems()
  if (!props.presetSystemId) await query()
})
onBeforeUnmount(clearPreviewRefresh)
</script>

<template>
  <section class="list-page">
    <p class="filter-optional-hint" data-test="filter-optional-hint">筛选条件均为选填</p>
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
          <tr v-for="item in items" :key="item.id"><td>{{ item.type ? typeLabel[item.type] : '—' }}</td><td>{{ item.title || '未命名草稿' }}</td><td>{{ systemName(item.systemId) }} / {{ versionName(item.targetVersionId, item.targetVersionName) }}</td><td>{{ item.requesterName || '—' }} / {{ item.department || '—' }}</td><td>{{ item.periodStartDate && item.periodEndDate ? `${item.periodStartDate} 至 ${item.periodEndDate}` : '—' }}</td><td>{{ item.status ? statusLabel[item.status] : saveTypeLabel[item.saveType ?? 'DRAFT'] }}</td><td>{{ formatShanghai(item.submittedAt || item.updatedAt) }}</td><td class="row-actions"><button type="button" :data-test="`view-${item.id}`" @click="viewDetails(item.id)">查看详情</button><button type="button" :data-test="`edit-${item.id}`" @click="openEdit(item.id)">编辑</button><button class="danger" type="button" :data-test="`delete-${item.id}`" @click="deleteRequirement(item)">删除</button></td></tr>
          <tr v-if="loading"><td colspan="8" class="empty">加载中…</td></tr><tr v-else-if="items.length === 0"><td colspan="8" class="empty">暂无需求</td></tr>
        </tbody>
      </table>
    </div>
    <div v-if="editingId !== null" class="modal-overlay" @keydown.esc="editingId = null">
      <form class="modal-dialog modal-dialog-lg" data-test="edit-form" @submit.prevent="saveEdit()">
        <div class="modal-scroll">
        <div class="modal-header">
          <h3>{{ editingSaveType === 'DRAFT' ? '编辑草稿' : '编辑需求' }}</h3>
          <button class="modal-close" type="button" @click="editingId = null">&times;</button>
        </div>
        <div class="modal-body">
          <p class="field-requirement-legend" data-test="edit-field-legend">带 <span class="field-required">*</span> 的项目为必填项；草稿可暂存未完成内容，正式提交时必须填写完整。</p>
          <div class="form-grid">
            <label>姓名 <span class="field-required">* 必填</span><input v-model="editForm.requesterName" :required="editingSaveType !== 'DRAFT'"></label><label>部门 <span class="field-required">* 必填</span><input v-model="editForm.department" :required="editingSaveType !== 'DRAFT'"></label>
            <label class="full-width" data-test="edit-title-field">需求标题 <span class="field-required">* 必填</span><input v-model="editForm.title" data-test="edit-title" :required="editingSaveType !== 'DRAFT'"></label>
            <label>类型 <span class="field-required">* 必填</span><select v-model="editForm.type" :required="editingSaveType !== 'DRAFT'"><option value="">请选择类型</option><option value="BUG">BUG</option><option value="REQUIREMENT">需求</option></select></label>
            <label>需求状态 <span :class="editingSaveType === 'DRAFT' ? 'field-optional' : 'field-required'">{{ editingSaveType === 'DRAFT' ? '正式提交时生成' : '* 必填' }}</span><select v-model="editForm.status" data-test="edit-status" :disabled="editingSaveType === 'DRAFT'"><option value="PENDING_EVALUATION">待评估</option><option value="CONFIRMED">已确认</option><option value="IN_DEVELOPMENT">开发中</option><option value="PAUSED">暂停</option><option value="COMPLETED">已完成</option><option value="REJECTED">已拒绝</option><option value="CLOSED">已关闭</option></select></label>
            <label>所属系统 <span class="field-optional">选填</span><select v-model="editForm.systemId" @change="changeEditSystem"><option value="">暂无系统</option><option v-for="system in systems" :key="system.id" :value="String(system.id)" :disabled="system.status !== 'ACTIVE' && String(system.id) !== editForm.systemId">{{ system.name }}{{ system.status === 'ACTIVE' ? '' : '（已停用）' }}</option></select></label>
            <label>目标版本 <span class="field-optional">选填</span><select v-model="editForm.targetVersionId" :disabled="!editForm.systemId"><option value="">请选择版本（可选）</option><option v-for="version in editVersions" :key="version.id" :value="String(version.id)" :disabled="version.status !== 'ACTIVE' && String(version.id) !== editForm.targetVersionId">{{ version.name }}{{ version.status === 'ACTIVE' ? '' : '（已停用）' }}</option></select></label>
            <div data-test="edit-period-field"><span class="field-label">需求时间周期 <span class="field-optional">选填</span></span><div class="date-range"><input v-model="editForm.periodStartDate" type="date"><span>至</span><input v-model="editForm.periodEndDate" type="date"></div></div>
            <label class="full-width">需求内容 <span class="field-required">* 必填</span><textarea v-model="editForm.content" rows="8" :required="editingSaveType !== 'DRAFT'"></textarea></label>
            <div class="full-width edit-attachments">
              <span class="field-label">附件 <span class="field-optional">选填</span></span>
              <div class="attachment-upload"><input data-test="edit-attachment-input" type="file" multiple accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="selectEditFiles"><button class="secondary" type="button" data-test="edit-attachment-upload" :disabled="editUploading || editSelectedFiles.length === 0" @click="uploadEditAttachments">{{ editUploading ? '上传中…' : '上传附件' }}</button></div>
              <ul v-if="editAttachments.length" class="attachment-list"><li v-for="attachment in editAttachments" :key="attachment.id"><div class="attachment-row"><span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span><span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span><span class="row-actions"><button v-if="canPreview(attachment)" type="button" :data-test="`edit-attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</button><button v-else-if="attachment.previewStatus === 'FAILED'" type="button" @click="retryAttachmentPreview(attachment)">重新生成</button><a :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a><button class="danger" type="button" :data-test="`edit-attachment-delete-${attachment.id}`" @click="deleteEditAttachment(attachment)">删除</button></span></div><img v-if="attachment.contentType.startsWith('image/')" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`"></li></ul>
              <p v-else class="edit-no-attachment">暂无附件</p>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" type="button" @click="editingId = null">取消</button>
          <button v-if="editingSaveType === 'DRAFT'" class="btn-cancel" type="button" :data-test="`save-draft-${editingId}`" @click="saveEdit()">保存草稿</button>
          <button class="btn-primary" :type="editingSaveType === 'DRAFT' ? 'button' : 'submit'" :data-test="editingSaveType === 'DRAFT' ? `submit-draft-${editingId}` : undefined" @click="editingSaveType === 'DRAFT' && saveEdit(true)">{{ editingSaveType === 'DRAFT' ? '正式提交' : '保存修改' }}</button>
        </div>
        </div>
      </form>
    </div>
    <div v-if="selectedRequirement" class="modal-overlay" data-test="detail-modal" @keydown.esc="selectedRequirement = null; detailAttachments = []">
      <div class="modal-dialog modal-dialog-lg">
        <div class="modal-scroll">
        <div class="modal-header">
          <h3>需求详情</h3>
          <button class="modal-close" type="button" @click="selectedRequirement = null; detailAttachments = []">&times;</button>
        </div>
        <div class="modal-body detail-modal-body">
          <h4 class="detail-title">{{ selectedRequirement.title || '未命名草稿' }}</h4>
          <dl><div><dt>类型</dt><dd>{{ selectedRequirement.type ? typeLabel[selectedRequirement.type] : '—' }}</dd></div><div><dt>状态</dt><dd>{{ selectedRequirement.status ? statusLabel[selectedRequirement.status] : '草稿' }}</dd></div><div><dt>所属系统</dt><dd>{{ systemName(selectedRequirement.systemId) }}</dd></div><div><dt>目标版本</dt><dd>{{ versionName(selectedRequirement.targetVersionId, selectedRequirement.targetVersionName) }}</dd></div><div><dt>填写人 / 部门</dt><dd>{{ selectedRequirement.requesterName || '—' }} / {{ selectedRequirement.department || '—' }}</dd></div><div><dt>填写时间</dt><dd>{{ formatShanghai(selectedRequirement.submittedAt || selectedRequirement.updatedAt) }}</dd></div><div><dt>需求周期</dt><dd>{{ selectedRequirement.periodStartDate && selectedRequirement.periodEndDate ? `${selectedRequirement.periodStartDate} 至 ${selectedRequirement.periodEndDate}` : '—' }}</dd></div><div><dt>最后修改</dt><dd>{{ formatShanghai(selectedRequirement.updatedAt) }}</dd></div><div v-if="selectedRequirement.completedAt"><dt>完成时间</dt><dd>{{ formatShanghai(selectedRequirement.completedAt) }}</dd></div><div v-if="selectedRequirement.handledBy"><dt>处理人</dt><dd>{{ selectedRequirement.handledBy }}</dd></div></dl>
          <h3>需求内容</h3><p class="detail-content">{{ selectedRequirement.content || '—' }}</p>
          <div v-if="selectedRequirement.completionDescription"><h3>完成情况</h3><p class="detail-content">{{ selectedRequirement.completionDescription }}</p></div>
          <h3>附件</h3><ul v-if="detailAttachments.length" class="attachment-list"><li v-for="attachment in detailAttachments" :key="attachment.id"><div class="attachment-row"><span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span><span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span><span class="row-actions"><button v-if="canPreview(attachment)" type="button" :data-test="`attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</button><button v-else-if="attachment.previewStatus === 'FAILED'" type="button" :data-test="`attachment-retry-preview-${attachment.id}`" @click="retryAttachmentPreview(attachment)">重新生成</button><a :data-test="`attachment-download-${attachment.id}`" :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a></span></div><img v-if="attachment.contentType.startsWith('image/')" :data-test="`attachment-preview-${attachment.id}`" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`"></li></ul><p v-else>无</p>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" type="button" @click="selectedRequirement = null; detailAttachments = []">关闭</button>
        </div>
        </div>
      </div>
    </div>
    <div v-if="previewAttachment" class="attachment-preview-overlay" data-test="attachment-preview-dialog" role="dialog" aria-modal="true">
      <section class="attachment-preview-dialog">
        <div class="detail-header"><h2>{{ previewAttachment.originalName }}</h2><button type="button" @click="previewAttachment = null">关闭</button></div>
        <p v-if="!previewAttachment.contentType.startsWith('image/')" class="preview-disclaimer">在线预览由系统生成，版式可能与原文件略有差异，请以下载的原文件为准。</p>
        <img v-if="previewAttachment.previewContentType?.startsWith('image/') || previewAttachment.contentType.startsWith('image/')" :src="`/api/attachments/${previewAttachment.id}/preview`" :alt="`${previewAttachment.originalName} 在线预览`">
        <iframe v-else data-test="attachment-preview-frame" :src="`/api/attachments/${previewAttachment.id}/preview`" :title="`${previewAttachment.originalName} 在线预览`"></iframe>
        <div class="form-actions"><a data-test="attachment-preview-download" :href="`/api/attachments/${previewAttachment.id}`" :download="previewAttachment.originalName">下载原文件</a></div>
      </section>
    </div>
    <div class="pagination-placeholder">共 {{ total }} 条　<button type="button" :disabled="loading || currentPage === 0" @click="query(currentPage - 1)">上一页</button>　第 {{ currentPage + 1 }} / {{ Math.max(totalPages, 1) }} 页　<button type="button" :disabled="loading || currentPage + 1 >= totalPages" @click="query(currentPage + 1)">下一页</button></div>
  </section>
</template>

