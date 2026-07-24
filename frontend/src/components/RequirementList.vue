<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { Button, Card, Col, DatePicker, Descriptions, DescriptionsItem, Form, FormItem, Input, Modal, Pagination, Row, Select, Space, Table, Tag, message } from 'ant-design-vue'
import { InboxOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { DEPARTMENTS } from '../constants/departments'
import { requirementStatusMeta, requirementTypeMeta, saveTypeMeta } from '../constants/statusConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { usePageRefresh } from '../composables/usePageRefresh'

const props = withDefaults(defineProps<{
  presetSystemId?: number | null
  presetRequestKey?: number
  presetFilter?: { saveType?: string; status?: string } | null
}>(), {
  presetSystemId: null,
  presetRequestKey: 0,
  presetFilter: null,
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
const editIsDragging = ref(false)
const editAttachmentInput = ref<HTMLInputElement | null>(null)
const editForm = reactive({ requesterName: '', department: '', title: '', type: '', content: '', systemId: '', targetVersionId: '', periodStartDate: '', periodEndDate: '', status: '', recordVersion: 0 })
let previewRefreshTimer: ReturnType<typeof setTimeout> | undefined
const hasModalOpen = computed(() => editingId.value !== null || selectedRequirement.value !== null || previewAttachment.value !== null)
const allowedAttachmentExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx'])
const { handleError } = useApiError()

const systemFilterOptions = computed(() => [
  { label: '全部系统', value: '' },
  { label: '暂无系统', value: 'none' },
  ...systems.value.map((system) => ({ label: system.name, value: String(system.id) })),
])
const versionFilterOptions = computed(() => [
  { label: '全部版本', value: '' },
  ...versions.value.map((version) => ({ label: version.name, value: String(version.id) })),
])
const typeOptions = [
  { label: '全部类型', value: '' },
  { label: 'BUG', value: 'BUG' },
  { label: '需求', value: 'REQUIREMENT' },
]
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待评估', value: 'PENDING_EVALUATION' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '开发中', value: 'IN_DEVELOPMENT' },
  { label: '暂停', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' },
]
const saveTypeOptions = [
  { label: '全部', value: '' },
  { label: '正式需求', value: 'SUBMITTED' },
  { label: '草稿', value: 'DRAFT' },
]
const requirementColumns = [
  { title: '类型', key: 'type', width: 100 },
  { title: '需求标题', key: 'title', width: 220 },
  { title: '所属系统 / 版本', key: 'systemVersion', width: 190 },
  { title: '填写人 / 部门', key: 'requester', width: 150 },
  { title: '周期', key: 'period', width: 210 },
  { title: '状态', key: 'status', width: 120 },
  { title: '填写时间', key: 'submittedAt', width: 175 },
  { title: '操作', key: 'actions', fixed: 'right' as const, width: 200 },
] satisfies TableColumnsType<Item>
const tableRecord = (record: Record<string, unknown>) => record as Item
const editingOpen = computed({
  get: () => editingId.value !== null,
  set: (open: boolean) => { if (!open) editingId.value = null },
})
const detailOpen = computed({
  get: () => selectedRequirement.value !== null,
  set: (open: boolean) => {
    if (!open) {
      selectedRequirement.value = null
      detailAttachments.value = []
      clearPreviewRefresh()
    }
  },
})
const previewOpen = computed({
  get: () => previewAttachment.value !== null,
  set: (open: boolean) => { if (!open) previewAttachment.value = null },
})
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (id: number | null, name?: string | null) => id === null ? '—' : name ?? versions.value.find((version) => version.id === id)?.name ?? `版本 #${id}`
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const match = value.match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] ?? value.slice(0, 10)
}
const loadSystems = async () => {
  try {
    const { data } = await api.get('/systems')
    systems.value = Array.isArray(data) ? data : []
  } catch {
    message.warning('系统筛选项加载失败')
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
    message.warning('版本筛选项加载失败')
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
    message.error('查询失败，请检查筛选条件后重试')
  } finally {
    loading.value = false
  }
}

const applyPresetSystemFilter = async (systemId: number | null) => {
  if (systemId === null) return
  filters.systemId = String(systemId)
  await query()
}

const applyPresetFilter = async (filter: { saveType?: string; status?: string } | null) => {
  if (!filter) return
  if (filter.saveType) filters.saveType = filter.saveType
  if (filter.status) filters.status = filter.status
  await query()
}

watch(
  () => [props.presetSystemId, props.presetRequestKey] as const,
  async ([systemId]) => {
    if (props.presetFilter) {
      await applyPresetFilter(props.presetFilter)
    } else {
      await applyPresetSystemFilter(systemId)
    }
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
    message.error('加载需求详情失败')
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
    message.success('已提交预览生成任务')
  } catch {
    message.error('重新生成预览失败')
  }
}

const deleteRequirement = async (item: Item) => {
  if (!window.confirm(`确定删除需求“${item.title || '未命名草稿'}”吗？`)) return
  try {
    await api.delete(`/requirements/${item.id}`)
    if (selectedRequirement.value?.id === item.id) selectedRequirement.value = null
    message.success('需求已删除')
    await query(currentPage.value)
    markChanged(['requirements', 'management', 'dashboard'])
  } catch {
    message.error('删除需求失败')
  }
}

const loadEditVersions = async () => {
  editVersions.value = []
  if (!editForm.systemId) return
  try {
    const { data } = await api.get(`/systems/${editForm.systemId}/versions`)
    editVersions.value = Array.isArray(data) ? data : []
  } catch {
    message.warning('编辑时加载版本失败')
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
    message.error('加载编辑数据失败')
  }
}

const selectEditFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  addEditFiles(Array.from(input.files ?? []))
  input.value = ''
}

const addEditFiles = (files: File[]) => {
  const accepted = files.filter((file) => {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    return allowedAttachmentExtensions.has(extension) && file.size <= 100 * 1024 * 1024
  })
  editSelectedFiles.value = [...editSelectedFiles.value, ...accepted.filter((file) => !editSelectedFiles.value.some((item) => item.name === file.name && item.size === file.size && item.lastModified === file.lastModified))]
}
const dropEditFiles = (event: DragEvent) => {
  editIsDragging.value = false
  addEditFiles(Array.from(event.dataTransfer?.files ?? []))
}
const openEditAttachmentPicker = () => editAttachmentInput.value?.click()

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
    message.success('附件上传成功')
    markChanged(['requirements', 'management', 'dashboard'])
  } catch {
    message.error('附件上传失败，未完成的文件可重新选择后上传')
  } finally {
    editUploading.value = false
  }
}

const deleteEditAttachment = async (attachment: Attachment) => {
  if (!window.confirm(`确定删除附件“${attachment.originalName}”吗？`)) return
  try {
    await api.delete(`/attachments/${attachment.id}`)
    editAttachments.value = editAttachments.value.filter((item) => item.id !== attachment.id)
    message.success('附件已删除')
    markChanged(['requirements', 'management', 'dashboard'])
  } catch {
    message.error('删除附件失败')
  }
}

const changeEditSystem = async () => {
  editForm.targetVersionId = ''
  await loadEditVersions()
}

const saveEdit = async (submitDraft = false) => {
  if (editingId.value === null) return
  if (editForm.department && !(DEPARTMENTS as readonly string[]).includes(editForm.department)) {
    message.error('请从指定部门列表中重新选择部门后再保存')
    return
  }
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
    message.success(isDraftSave ? '草稿已更新' : submitDraft ? '草稿已正式提交' : '需求已更新')
    await query(currentPage.value)
    markChanged(['requirements', 'management', 'dashboard'])
  } catch (error: unknown) {
    handleError(error, '保存需求失败，请检查必填项和系统版本')
  }
}

usePageRefresh('requirements', async () => {
  await loadSystems()
  if (props.presetSystemId !== null) await applyPresetSystemFilter(props.presetSystemId)
  else await query(currentPage.value)
}, { isPaused: () => hasModalOpen.value || editUploading.value })
onBeforeUnmount(clearPreviewRefresh)
</script>

<template>
  <section class="list-page">
    <Card class="filter-card" :bordered="false">
      <p class="filter-optional-hint" data-test="filter-optional-hint">筛选条件均为选填</p>
      <Form layout="vertical" class="filter-form">
        <Row :gutter="[16, 4]">
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="关键词"><Input v-model:value="filters.keyword" placeholder="标题或需求内容" allow-clear /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="所属系统"><Select v-model:value="filters.systemId" data-test="system-filter" :options="systemFilterOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="目标版本"><Select v-model:value="filters.targetVersionId" :options="versionFilterOptions" :disabled="!filters.systemId || filters.systemId === 'none'" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="部门"><Select v-model:value="filters.department" data-test="department-filter" allow-clear placeholder="全部部门" :options="DEPARTMENTS.map((department) => ({ label: department, value: department }))" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="填写人"><Input v-model:value="filters.requesterName" placeholder="请输入填写人" allow-clear /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="填写时间起"><DatePicker v-model:value="filters.submittedFrom" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 100%" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="填写时间止"><DatePicker v-model:value="filters.submittedTo" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 100%" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="需求状态"><Select v-model:value="filters.status" :options="statusOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="类型"><Select v-model:value="filters.type" :options="typeOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="保存类型"><Select v-model:value="filters.saveType" :options="saveTypeOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="周期起"><DatePicker v-model:value="filters.periodOverlapStart" value-format="YYYY-MM-DD" placeholder="周期开始" style="width: 100%" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="周期止"><DatePicker v-model:value="filters.periodOverlapEnd" value-format="YYYY-MM-DD" placeholder="周期结束" style="width: 100%" /></FormItem></Col>
        </Row>
        <div class="filter-actions"><Space><Button type="primary" data-test="query" @click="query()">查询</Button><Button @click="reset">重置</Button></Space></div>
      </Form>
    </Card>
    <Card class="list-table-card" :bordered="false">
      <Table class="requirement-table" :columns="requirementColumns" :data-source="items" :loading="loading" :pagination="false" :scroll="{ x: 1365 }" :row-key="(item: Item) => item.id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'"><Tag :color="record.type ? requirementTypeMeta(record.type).color : 'default'">{{ record.type ? requirementTypeMeta(record.type).label : '—' }}</Tag></template>
          <template v-else-if="column.key === 'title'"><strong>{{ record.title || '未命名草稿' }}</strong></template>
          <template v-else-if="column.key === 'systemVersion'">{{ systemName(record.systemId) }} / {{ versionName(record.targetVersionId, record.targetVersionName) }}</template>
          <template v-else-if="column.key === 'requester'">{{ record.requesterName || '—' }} / {{ record.department || '—' }}</template>
          <template v-else-if="column.key === 'period'">{{ record.periodStartDate && record.periodEndDate ? `${record.periodStartDate} 至 ${record.periodEndDate}` : '—' }}</template>
          <template v-else-if="column.key === 'status'"><Tag :color="record.status ? requirementStatusMeta(record.status).color : saveTypeMeta(record.saveType ?? 'DRAFT').color">{{ record.status ? requirementStatusMeta(record.status).label : saveTypeMeta(record.saveType ?? 'DRAFT').label }}</Tag></template>
          <template v-else-if="column.key === 'submittedAt'">{{ formatShanghai(record.submittedAt || record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'"><Space size="small"><Button type="link" size="small" :data-test="`view-${record.id}`" @click="viewDetails(tableRecord(record).id)">查看详情</Button><Button type="link" size="small" :data-test="`edit-${record.id}`" @click="openEdit(tableRecord(record).id)">编辑</Button><Button danger type="link" size="small" :data-test="`delete-${record.id}`" @click="deleteRequirement(tableRecord(record))">删除</Button></Space></template>
        </template>
        <template #emptyText>{{ loading ? '加载中…' : '暂无需求' }}</template>
      </Table>
      <div class="pagination-bar"><span>共 {{ total }} 条</span><Pagination :current="currentPage + 1" :total="total" :page-size="pageSize" :show-size-changer="false" :disabled="loading" @change="(page) => query(page - 1)" /></div>
    </Card>
    <Modal v-model:open="editingOpen" :title="editingSaveType === 'DRAFT' ? '编辑草稿' : '编辑需求'" :footer="null" :mask-closable="false" destroy-on-close :get-container="false" width="900px">
      <form class="ant-modal-form" data-test="edit-form" @submit.prevent="saveEdit()">
        <p class="field-requirement-legend" data-test="edit-field-legend">带 <span class="field-required">*</span> 的项目为必填项；草稿可暂存未完成内容，正式提交时必须填写完整。</p>
        <Row :gutter="[16, 4]">
          <Col :span="12"><FormItem label="姓名" :required="editingSaveType !== 'DRAFT'"><Input v-model:value="editForm.requesterName" :required="editingSaveType !== 'DRAFT'" /></FormItem></Col>
          <Col :span="12"><FormItem label="部门" :required="editingSaveType !== 'DRAFT'"><Select v-model:value="editForm.department" data-test="edit-department-select" :options="DEPARTMENTS.map((department) => ({ label: department, value: department }))" /></FormItem></Col>
          <Col :span="24"><FormItem data-test="edit-title-field" label="需求标题" :required="editingSaveType !== 'DRAFT'"><Input v-model:value="editForm.title" data-test="edit-title" :required="editingSaveType !== 'DRAFT'" /></FormItem></Col>
          <Col :span="12"><FormItem label="类型" :required="editingSaveType !== 'DRAFT'"><Select v-model:value="editForm.type" :options="typeOptions.slice(1)" :required="editingSaveType !== 'DRAFT'" /></FormItem></Col>
          <Col :span="12"><FormItem label="需求状态" :required="editingSaveType !== 'DRAFT'"><Select v-model:value="editForm.status" data-test="edit-status" :options="statusOptions.slice(1)" :disabled="editingSaveType === 'DRAFT'" /></FormItem></Col>
          <Col :span="12"><FormItem label="所属系统"><Select v-model:value="editForm.systemId" :options="[{ label: '暂无系统', value: '' }, ...systems.map((system) => ({ label: `${system.name}${system.status === 'ACTIVE' ? '' : '（已停用）'}`, value: String(system.id), disabled: system.status !== 'ACTIVE' && String(system.id) !== editForm.systemId }))]" @change="changeEditSystem" /></FormItem></Col>
          <Col :span="12"><FormItem label="目标版本"><Select v-model:value="editForm.targetVersionId" :disabled="!editForm.systemId" :options="[{ label: '请选择版本（可选）', value: '' }, ...editVersions.map((version) => ({ label: `${version.name}${version.status === 'ACTIVE' ? '' : '（已停用）'}`, value: String(version.id), disabled: version.status !== 'ACTIVE' && String(version.id) !== editForm.targetVersionId }))]" /></FormItem></Col>
          <Col :span="24" data-test="edit-period-field"><FormItem label="需求时间周期"><Space><DatePicker v-model:value="editForm.periodStartDate" value-format="YYYY-MM-DD" placeholder="开始日期" /><span>至</span><DatePicker v-model:value="editForm.periodEndDate" value-format="YYYY-MM-DD" placeholder="结束日期" /></Space></FormItem></Col>
          <Col :span="24"><FormItem label="需求内容" required><Input.TextArea v-model:value="editForm.content" :rows="8" :required="editingSaveType !== 'DRAFT'" /></FormItem></Col>
          <Col :span="24"><FormItem label="附件"><div class="edit-attachments"><div class="attachment-upload"><div class="attachment-dropzone ant-upload ant-upload-drag" :class="{ 'is-dragging': editIsDragging }" data-test="edit-attachment-dropzone" role="button" tabindex="0" @click="openEditAttachmentPicker" @keydown.enter.prevent="openEditAttachmentPicker" @dragenter.prevent="editIsDragging = true" @dragover.prevent="editIsDragging = true" @dragleave.prevent="editIsDragging = false" @drop.prevent="dropEditFiles"><p class="ant-upload-drag-icon"><InboxOutlined /></p><p class="ant-upload-text">拖拽附件到此处，或点击选择文件</p><input ref="editAttachmentInput" data-test="edit-attachment-input" type="file" multiple accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="selectEditFiles"></div><Button type="primary" data-test="edit-attachment-upload" :loading="editUploading" :disabled="editSelectedFiles.length === 0" @click="uploadEditAttachments">上传附件</Button></div><ul v-if="editSelectedFiles.length" class="attachment-list pending-attachments"><li v-for="file in editSelectedFiles" :key="`${file.name}-${file.lastModified}`">待上传：{{ file.name }}</li></ul><ul v-if="editAttachments.length" class="attachment-list"><li v-for="attachment in editAttachments" :key="attachment.id"><div class="attachment-row"><span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span><span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span><Space><Button v-if="canPreview(attachment)" type="link" :data-test="`edit-attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</Button><Button v-else-if="attachment.previewStatus === 'FAILED'" type="link" @click="retryAttachmentPreview(attachment)">重新生成</Button><a :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a><Button danger type="link" :data-test="`edit-attachment-delete-${attachment.id}`" @click="deleteEditAttachment(attachment)">删除</Button></Space></div><img v-if="attachment.contentType.startsWith('image/')" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`"></li></ul><p v-else class="edit-no-attachment">暂无附件</p></div></FormItem></Col>
        </Row>
        <div class="modal-footer"><Space><Button @click="editingId = null">取消</Button><Button v-if="editingSaveType === 'DRAFT'" :data-test="`save-draft-${editingId}`" @click="saveEdit()">保存草稿</Button><Button type="primary" :html-type="editingSaveType === 'DRAFT' ? 'button' : 'submit'" :data-test="editingSaveType === 'DRAFT' ? `submit-draft-${editingId}` : undefined" @click="editingSaveType === 'DRAFT' && saveEdit(true)">{{ editingSaveType === 'DRAFT' ? '正式提交' : '保存修改' }}</Button></Space></div>
      </form>
    </Modal>
    <Modal v-model:open="detailOpen" title="需求详情" :mask-closable="false" destroy-on-close :get-container="false" width="900px">
      <div v-if="selectedRequirement" class="detail-modal-body" data-test="detail-modal">
        <h4 class="detail-title">{{ selectedRequirement.title || '未命名草稿' }}</h4>
        <Descriptions bordered size="small" :column="2">
          <DescriptionsItem label="类型">{{ selectedRequirement.type ? requirementTypeMeta(selectedRequirement.type).label : '—' }}</DescriptionsItem><DescriptionsItem label="状态">{{ selectedRequirement.status ? requirementStatusMeta(selectedRequirement.status).label : '草稿' }}</DescriptionsItem>
          <DescriptionsItem label="所属系统">{{ systemName(selectedRequirement.systemId) }}</DescriptionsItem><DescriptionsItem label="目标版本">{{ versionName(selectedRequirement.targetVersionId, selectedRequirement.targetVersionName) }}</DescriptionsItem>
          <DescriptionsItem label="填写人 / 部门">{{ selectedRequirement.requesterName || '—' }} / {{ selectedRequirement.department || '—' }}</DescriptionsItem><DescriptionsItem label="填写时间">{{ formatShanghai(selectedRequirement.submittedAt || selectedRequirement.updatedAt) }}</DescriptionsItem>
          <DescriptionsItem label="需求周期">{{ selectedRequirement.periodStartDate && selectedRequirement.periodEndDate ? `${selectedRequirement.periodStartDate} 至 ${selectedRequirement.periodEndDate}` : '—' }}</DescriptionsItem><DescriptionsItem label="最后修改">{{ formatShanghai(selectedRequirement.updatedAt) }}</DescriptionsItem>
          <DescriptionsItem v-if="selectedRequirement.completedAt" label="完成时间">{{ formatShanghai(selectedRequirement.completedAt) }}</DescriptionsItem><DescriptionsItem v-if="selectedRequirement.handledBy" label="处理人">{{ selectedRequirement.handledBy }}</DescriptionsItem>
        </Descriptions>
        <h3>需求内容</h3><p class="detail-content">{{ selectedRequirement.content || '—' }}</p>
        <div v-if="selectedRequirement.completionDescription"><h3>完成情况</h3><p class="detail-content">{{ selectedRequirement.completionDescription }}</p></div>
        <h3>附件</h3><ul v-if="detailAttachments.length" class="attachment-list"><li v-for="attachment in detailAttachments" :key="attachment.id"><div class="attachment-row"><span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span><span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span><Space><Button v-if="canPreview(attachment)" type="link" :data-test="`attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</Button><Button v-else-if="attachment.previewStatus === 'FAILED'" type="link" :data-test="`attachment-retry-preview-${attachment.id}`" @click="retryAttachmentPreview(attachment)">重新生成</Button><a :data-test="`attachment-download-${attachment.id}`" :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a></Space></div><img v-if="attachment.contentType.startsWith('image/')" :data-test="`attachment-preview-${attachment.id}`" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`"></li></ul><p v-else>无</p>
      </div>
      <template #footer><Button @click="detailOpen = false">关闭</Button></template>
    </Modal>
    <Modal v-model:open="previewOpen" :title="previewAttachment?.originalName" :footer="null" destroy-on-close :get-container="false" width="1100px" wrap-class-name="attachment-preview-modal">
      <section v-if="previewAttachment" class="attachment-preview-dialog" data-test="attachment-preview-dialog">
        <p v-if="!previewAttachment.contentType.startsWith('image/')" class="preview-disclaimer">在线预览由系统生成，版式可能与原文件略有差异，请以下载的原文件为准。</p>
        <img v-if="previewAttachment.previewContentType?.startsWith('image/') || previewAttachment.contentType.startsWith('image/')" :src="`/api/attachments/${previewAttachment.id}/preview`" :alt="`${previewAttachment.originalName} 在线预览`">
        <iframe v-else data-test="attachment-preview-frame" :src="`/api/attachments/${previewAttachment.id}/preview`" :title="`${previewAttachment.originalName} 在线预览`"></iframe>
        <div class="form-actions"><a data-test="attachment-preview-download" :href="`/api/attachments/${previewAttachment.id}`" :download="previewAttachment.originalName">下载原文件</a></div>
      </section>
    </Modal>
  </section>
</template>
