<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Button, DatePicker, FormItem, Input, Modal, Result, Select, Space, Spin, message } from 'ant-design-vue'
import { InboxOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { useFormErrors } from '../composables/useFormErrors'
import { useDictionaryOptions, type DictionaryItem } from '../composables/useDictionaryOptions'
import { urgencyOptions } from '../constants/urgencyConfig'

type SystemItem = { id: number; name: string; status: string }
type VersionItem = { id: number; name: string; status: string }
type Attachment = {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  previewStatus?: string
  previewAvailable?: boolean
  previewContentType?: string | null
  previewErrorMessage?: string | null
}
type EditableRequirement = {
  id: number
  title: string | null
  typeId: number | null
  type: string | null
  requesterName: string | null
  departmentId: number | null
  department: string | null
  status: string | null
  systemId: number | null
  targetVersionId: number | null
  periodStartDate: string | null
  periodEndDate: string | null
  content: string | null
  urgency?: string | null
  saveType?: string
  recordVersion?: number
}

const props = defineProps<{ requirementId: number }>()
const emit = defineEmits<{
  cancel: []
  saved: []
}>()

const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const attachments = ref<Attachment[]>([])
const selectedFiles = ref<File[]>([])
const attachmentInput = ref<HTMLInputElement | null>(null)
const previewAttachment = ref<Attachment | null>(null)
const requirement = ref<EditableRequirement | null>(null)
const loading = ref(true)
const loadError = ref('')
const uploading = ref(false)
const isDragging = ref(false)
const saveType = ref('SUBMITTED')
const form = reactive({
  requesterName: '', departmentId: '', title: '', typeId: '', content: '', systemId: '', targetVersionId: '',
  periodStartDate: '', periodEndDate: '', urgency: 'MEDIUM', status: '', recordVersion: 0,
})
const allowedAttachmentExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx'])
const { departments, requirementTypes, loadDictionaryOptions } = useDictionaryOptions()
const { handleError } = useApiError()
const { errors, clearErrors, setError, applyServerErrors } = useFormErrors()
let previewRefreshTimer: ReturnType<typeof setTimeout> | undefined

const isDraft = computed(() => saveType.value === 'DRAFT')
const previewOpen = computed({
  get: () => previewAttachment.value !== null,
  set: (open: boolean) => { if (!open) previewAttachment.value = null },
})
const optionsWithCurrent = (items: DictionaryItem[], currentId: string, currentName: string | null | undefined) => {
  const options = items.map((item) => ({ label: item.name, value: String(item.id) }))
  if (currentId && !options.some((option) => option.value === currentId)) {
    options.push({ label: `${currentName || `#${currentId}`}（已停用）`, value: currentId })
  }
  return options
}
const departmentOptions = computed(() => optionsWithCurrent(departments.value, form.departmentId, requirement.value?.department))
const typeOptions = computed(() => optionsWithCurrent(requirementTypes.value, form.typeId, requirement.value?.type))
const statusOptions = [
  { label: '待评估', value: 'PENDING_EVALUATION' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '开发中', value: 'IN_DEVELOPMENT' },
  { label: '暂停', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' },
]
const systemOptions = computed(() => [
  { label: '暂无系统', value: '' },
  ...systems.value.map((system) => ({
    label: `${system.name}${system.status === 'ACTIVE' ? '' : '（已停用）'}`,
    value: String(system.id),
    disabled: system.status !== 'ACTIVE' && String(system.id) !== form.systemId,
  })),
])
const versionOptions = computed(() => [
  { label: '请选择版本（可选）', value: '' },
  ...versions.value.map((version) => ({
    label: `${version.name}${version.status === 'ACTIVE' ? '' : '（已停用）'}`,
    value: String(version.id),
    disabled: version.status !== 'ACTIVE' && String(version.id) !== form.targetVersionId,
  })),
])

const clearPreviewRefresh = () => {
  if (previewRefreshTimer !== undefined) clearTimeout(previewRefreshTimer)
  previewRefreshTimer = undefined
}
const schedulePreviewRefresh = () => {
  clearPreviewRefresh()
  if (!attachments.value.some((item) => item.previewStatus === 'PENDING' || item.previewStatus === 'CONVERTING')) return
  previewRefreshTimer = setTimeout(refreshAttachments, 2000)
}
const refreshAttachments = async () => {
  try {
    const { data } = await api.get(`/requirements/${props.requirementId}/attachments`)
    attachments.value = Array.isArray(data) ? data : []
  } finally {
    schedulePreviewRefresh()
  }
}
const loadVersions = async () => {
  versions.value = []
  if (!form.systemId) return
  try {
    const { data } = await api.get(`/systems/${form.systemId}/versions`)
    versions.value = Array.isArray(data) ? data : []
  } catch {
    message.warning('编辑时加载版本失败')
  }
}
const loadEditor = async () => {
  if (!Number.isSafeInteger(props.requirementId) || props.requirementId <= 0) {
    loadError.value = '需求编号无效'
    loading.value = false
    return
  }

  loading.value = true
  loadError.value = ''
  clearPreviewRefresh()
  try {
    const [detailResponse, attachmentResponse, systemsResponse] = await Promise.all([
      api.get(`/requirements/${props.requirementId}`),
      api.get(`/requirements/${props.requirementId}/attachments`),
      api.get('/systems'),
      loadDictionaryOptions(),
    ])
    const detail = detailResponse.data as EditableRequirement
    requirement.value = detail
    systems.value = Array.isArray(systemsResponse.data) ? systemsResponse.data : []
    attachments.value = Array.isArray(attachmentResponse.data) ? attachmentResponse.data : []
    saveType.value = detail.saveType ?? 'SUBMITTED'
    Object.assign(form, {
      requesterName: detail.requesterName ?? '',
      departmentId: detail.departmentId == null ? '' : String(detail.departmentId),
      title: detail.title ?? '',
      typeId: detail.typeId == null ? '' : String(detail.typeId),
      content: detail.content ?? '',
      urgency: detail.urgency ?? 'MEDIUM',
      systemId: detail.systemId === null ? '' : String(detail.systemId),
      targetVersionId: detail.targetVersionId === null ? '' : String(detail.targetVersionId),
      periodStartDate: detail.periodStartDate ?? '',
      periodEndDate: detail.periodEndDate ?? '',
      status: detail.status ?? '',
      recordVersion: detail.recordVersion ?? 0,
    })
    selectedFiles.value = []
    await loadVersions()
    schedulePreviewRefresh()
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    loadError.value = status === 404 ? '该需求不存在或已被删除' : '加载编辑数据失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const addFiles = (files: File[]) => {
  const accepted = files.filter((file) => {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    return allowedAttachmentExtensions.has(extension) && file.size <= 100 * 1024 * 1024
  })
  selectedFiles.value = [
    ...selectedFiles.value,
    ...accepted.filter((file) => !selectedFiles.value.some((item) => item.name === file.name && item.size === file.size && item.lastModified === file.lastModified)),
  ]
}
const selectFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files ?? []))
  input.value = ''
}
const dropFiles = (event: DragEvent) => {
  isDragging.value = false
  addFiles(Array.from(event.dataTransfer?.files ?? []))
}
const openAttachmentPicker = () => attachmentInput.value?.click()
const uploadAttachments = async () => {
  if (selectedFiles.value.length === 0) return
  uploading.value = true
  try {
    for (const file of selectedFiles.value) {
      const body = new FormData()
      body.append('file', file)
      const { data } = await api.post(`/requirements/${props.requirementId}/attachments`, body)
      attachments.value.push(data)
    }
    selectedFiles.value = []
    schedulePreviewRefresh()
    message.success('附件上传成功')
    markChanged(['requirements', 'dashboard'])
  } catch {
    message.error('附件上传失败，未完成的文件可重新选择后上传')
  } finally {
    uploading.value = false
  }
}
const deleteAttachment = (attachment: Attachment) => {
  Modal.confirm({
    title: '删除附件',
    content: `确定删除附件“${attachment.originalName}”吗？`,
    okText: '删除',
    cancelText: '取消',
    onOk: async () => {
  try {
    await api.delete(`/attachments/${attachment.id}`)
    attachments.value = attachments.value.filter((item) => item.id !== attachment.id)
    message.success('附件已删除')
    markChanged(['requirements', 'dashboard'])
  } catch (error: unknown) {
    handleError(error, '删除附件失败，请稍后重试')
  }
    },
  })
}
const changeSystem = async () => {
  form.targetVersionId = ''
  await loadVersions()
}
const canPreview = (attachment: Attachment) => attachment.previewAvailable
  ?? (attachment.contentType === 'application/pdf' || attachment.contentType.startsWith('image/'))
const previewStateLabel = (attachment: Attachment) => attachment.previewStatus === 'FAILED' && attachment.previewErrorMessage
  ? `预览生成失败：${attachment.previewErrorMessage}`
  : ({ PENDING: '等待生成预览', CONVERTING: '正在生成预览', FAILED: '预览生成失败', UNAVAILABLE: '预览不可用' }[attachment.previewStatus ?? ''] ?? '')
const openAttachmentPreview = (attachment: Attachment) => {
  if (canPreview(attachment)) previewAttachment.value = attachment
}
const retryAttachmentPreview = async (attachment: Attachment) => {
  try {
    const { data } = await api.post(`/attachments/${attachment.id}/preview/retry`)
    const index = attachments.value.findIndex((item) => item.id === attachment.id)
    if (index >= 0) attachments.value[index] = data
    schedulePreviewRefresh()
    message.success('已提交预览生成任务')
  } catch {
    message.error('重新生成预览失败')
  }
}
const save = async (submitDraft = false) => {
  clearErrors()
  if (!isDraft.value || submitDraft) {
    const requiredFields: Array<[string, string, string]> = [
      ['requesterName', form.requesterName, '请输入姓名'],
      ['departmentId', form.departmentId, '请选择部门'],
      ['title', form.title, '请输入需求标题'],
      ['typeId', form.typeId, '请选择需求类型'],
      ['urgency', form.urgency, '请选择紧急程度'],
      ['content', form.content, '请输入需求内容'],
    ]
    requiredFields.forEach(([field, value, error]) => { if (!String(value).trim()) setError(field, error) })
    if ((form.periodStartDate && !form.periodEndDate) || (!form.periodStartDate && form.periodEndDate)) {
      setError('period', '开始日期和结束日期必须同时填写')
    } else if (form.periodStartDate && form.periodEndDate && form.periodEndDate < form.periodStartDate) {
      setError('period', '结束日期不能早于开始日期')
    }
    if (Object.keys(errors).length > 0) {
      message.warning('请先补充标记的必填项')
      return
    }
  }
  const body = {
    requesterName: form.requesterName,
    departmentId: form.departmentId ? Number(form.departmentId) : null,
    title: form.title,
    typeId: form.typeId ? Number(form.typeId) : null,
    urgency: form.urgency,
    content: form.content,
    systemId: form.systemId ? Number(form.systemId) : null,
    targetVersionId: form.targetVersionId ? Number(form.targetVersionId) : null,
    periodStartDate: form.periodStartDate || null,
    periodEndDate: form.periodEndDate || null,
    recordVersion: form.recordVersion,
  }
  try {
    const isDraftSave = isDraft.value && !submitDraft
    const path = isDraftSave ? `/requirements/${props.requirementId}/draft` : `/requirements/${props.requirementId}`
    await api.put(path, body)
    message.success(isDraftSave ? '草稿已更新' : submitDraft ? '草稿已正式提交' : '需求已更新')
    markChanged(['requirements', 'dashboard'])
    emit('saved')
  } catch (error: unknown) {
    const details = applyServerErrors(error)
    if (Object.keys(details.fieldErrors).length > 0) {
      message.error(details.message || '请检查标记的字段后重试')
    } else {
      handleError(error, '保存需求失败，请检查必填项和系统版本')
    }
  }
}

watch(() => props.requirementId, () => { void loadEditor() }, { immediate: true })
onBeforeUnmount(clearPreviewRefresh)
</script>

<template>
  <section class="requirement-editor" data-test="requirement-editor">
    <Result v-if="loadError" status="error" :title="loadError">
      <template #extra>
        <Space>
          <Button type="primary" @click="loadEditor">重新加载</Button>
          <Button @click="emit('cancel')">取消</Button>
        </Space>
      </template>
    </Result>

    <Spin v-else :spinning="loading" tip="正在加载编辑数据…">
      <form v-if="!loading" class="requirement-editor-form" data-test="edit-form" @submit.prevent="save()">
        <div class="editor-surface">
          <div class="editor-notice" data-test="edit-field-legend">
            <span class="editor-notice-icon">ⓘ</span>
            <span>带 <span class="field-required">*</span> 的项目为必填项；草稿可暂存未完成内容，正式提交时必须填写完整。</span>
          </div>
          <div class="editor-grid">
            <FormItem label="姓名" :required="!isDraft" :validate-status="errors.requesterName ? 'error' : undefined" :help="errors.requesterName"><Input v-model:value="form.requesterName" placeholder="请输入姓名" /></FormItem>
            <FormItem label="部门" :required="!isDraft" :validate-status="errors.departmentId ? 'error' : undefined" :help="errors.departmentId"><Select v-model:value="form.departmentId" data-test="edit-department-select" :options="departmentOptions" placeholder="请选择部门" /></FormItem>
            <FormItem class="full-width" data-test="edit-title-field" label="需求标题" :required="!isDraft" :validate-status="errors.title ? 'error' : undefined" :help="errors.title"><Input v-model:value="form.title" data-test="edit-title" placeholder="一句话概括需求，建议 30 字以内" /></FormItem>
            <FormItem label="类型" :required="!isDraft" :validate-status="errors.typeId ? 'error' : undefined" :help="errors.typeId"><Select v-model:value="form.typeId" :options="typeOptions" placeholder="请选择需求类型" /></FormItem>
            <FormItem label="紧急程度" :required="!isDraft" :validate-status="errors.urgency ? 'error' : undefined" :help="errors.urgency"><Select v-model:value="form.urgency" :options="urgencyOptions" placeholder="请选择紧急程度" /></FormItem>
            <FormItem class="disabled-field" label="当前状态"><Select v-model:value="form.status" data-test="edit-status" :options="statusOptions" disabled placeholder="提交后默认为待评估" /><template #extra>状态由流程流转自动更新，编辑时不可修改</template></FormItem>
            <FormItem label="所属系统"><Select v-model:value="form.systemId" :options="systemOptions" placeholder="请选择所属系统" @change="changeSystem" /></FormItem>
            <FormItem label="目标版本"><Select v-model:value="form.targetVersionId" :disabled="!form.systemId" :options="versionOptions" placeholder="请选择版本（可选）" /></FormItem>
            <FormItem data-test="edit-period-field" label="需求时间周期" :validate-status="errors.period ? 'error' : undefined" :help="errors.period"><div class="date-range"><DatePicker v-model:value="form.periodStartDate" value-format="YYYY-MM-DD" placeholder="开始日期" /><span>至</span><DatePicker v-model:value="form.periodEndDate" value-format="YYYY-MM-DD" placeholder="结束日期" /></div></FormItem>
            <FormItem class="full-width" label="需求内容" :required="!isDraft" :validate-status="errors.content ? 'error' : undefined" :help="errors.content"><Input.TextArea v-model:value="form.content" :rows="9" placeholder="请详细描述需求背景、目标与验收标准…" /></FormItem>
            <FormItem class="full-width attachment-form-item" label="附件">
              <div class="edit-attachments">
                <div class="attachment-upload">
                  <div class="attachment-dropzone" :class="{ 'is-dragging': isDragging }" data-test="edit-attachment-dropzone" role="button" tabindex="0" @click="openAttachmentPicker" @keydown.enter.prevent="openAttachmentPicker" @dragenter.prevent="isDragging = true" @dragover.prevent="isDragging = true" @dragleave.prevent="isDragging = false" @drop.prevent="dropFiles">
                    <p class="ant-upload-drag-icon"><InboxOutlined /></p>
                    <p class="ant-upload-text">拖拽附件到此处，或 <b>点击选择文件</b></p>
                    <p class="attachment-hint">支持图片、PDF、Word、Excel，单个文件最大 100MB。</p>
                    <input ref="attachmentInput" data-test="edit-attachment-input" type="file" multiple accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="selectFiles">
                  </div>
                  <Button type="primary" html-type="button" data-test="edit-attachment-upload" :loading="uploading" :disabled="selectedFiles.length === 0" @click="uploadAttachments">上传附件</Button>
                </div>
                <ul v-if="selectedFiles.length" class="attachment-list pending-attachments"><li v-for="file in selectedFiles" :key="`${file.name}-${file.lastModified}`">待上传：{{ file.name }}</li></ul>
                <ul v-if="attachments.length" class="attachment-list">
                  <li v-for="attachment in attachments" :key="attachment.id">
                    <div class="attachment-row">
                      <span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span>
                      <span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span>
                      <Space wrap>
                        <Button v-if="canPreview(attachment)" type="link" html-type="button" :data-test="`edit-attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</Button>
                        <Button v-else-if="attachment.previewStatus === 'FAILED'" type="link" html-type="button" @click="retryAttachmentPreview(attachment)">重新生成</Button>
                        <a :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a>
                        <Button danger type="link" html-type="button" :data-test="`edit-attachment-delete-${attachment.id}`" @click="deleteAttachment(attachment)">删除</Button>
                      </Space>
                    </div>
                    <img v-if="attachment.contentType.startsWith('image/')" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`">
                  </li>
                </ul>
                <p v-else class="edit-no-attachment">暂无附件</p>
              </div>
            </FormItem>
          </div>
        </div>
        <div class="editor-footer">
          <span class="editor-save-state">{{ isDraft ? '当前为草稿，可暂存未完成内容' : '修改内容保存后即时生效' }}</span>
          <span class="editor-footer-spacer"></span>
          <Button html-type="button" @click="emit('cancel')">取消</Button>
          <Button v-if="isDraft" html-type="button" :data-test="`save-draft-${requirementId}`" @click="save()">存为草稿</Button>
          <Button type="primary" :html-type="isDraft ? 'button' : 'submit'" :data-test="isDraft ? `submit-draft-${requirementId}` : undefined" @click="isDraft && save(true)">{{ isDraft ? '保存并提交' : '保存修改' }}</Button>
        </div>
      </form>
    </Spin>

    <Modal v-model:open="previewOpen" :title="previewAttachment?.originalName" :footer="null" destroy-on-close :get-container="false" width="1100px" wrap-class-name="attachment-preview-modal">
      <section v-if="previewAttachment" class="attachment-preview-dialog" data-test="attachment-preview-dialog">
        <p v-if="!previewAttachment.contentType.startsWith('image/')" class="preview-disclaimer">在线预览由系统生成，版式可能与原文件略有差异，请以下载的原文件为准。</p>
        <img v-if="previewAttachment.previewContentType?.startsWith('image/') || previewAttachment.contentType.startsWith('image/')" :src="`/api/attachments/${previewAttachment.id}/preview`" :alt="`${previewAttachment.originalName} 在线预览`">
        <iframe v-else data-test="attachment-preview-frame" :src="`/api/attachments/${previewAttachment.id}/preview`" :title="`${previewAttachment.originalName} 在线预览`"></iframe>
        <div class="editor-actions"><a data-test="attachment-preview-download" :href="`/api/attachments/${previewAttachment.id}`" :download="previewAttachment.originalName">下载原文件</a></div>
      </section>
    </Modal>
  </section>
</template>

<style scoped>
.requirement-editor {
  min-width: 0;
}

.requirement-editor-form {
  padding-bottom: 72px;
}

.editor-surface {
  padding: 24px 32px 32px;
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%);
}

.editor-notice {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 26px;
  padding: 10px 16px;
  border: 1px solid #91caff;
  border-radius: 8px;
  background: #e6f4ff;
  color: #0958d9;
  font-size: 13px;
}

.editor-notice-icon {
  flex: 0 0 auto;
  font-size: 14px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 40px;
  max-width: 980px;
}

.editor-grid .full-width {
  grid-column: 1 / -1;
}

.editor-grid :deep(.ant-form-item) {
  min-width: 0;
  margin-bottom: 18px;
}

.editor-grid :deep(.ant-form-item-label) {
  padding-bottom: 6px;
}

.editor-grid :deep(.ant-form-item-label > label) {
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  font-weight: 500;
}

.editor-grid :deep(.ant-input),
.editor-grid :deep(.ant-select-selector),
.editor-grid :deep(.ant-picker) {
  border-radius: 8px;
}

.editor-grid :deep(.ant-input),
.editor-grid :deep(.ant-select-single .ant-select-selector),
.editor-grid :deep(.ant-picker) {
  min-height: 36px;
}

.editor-grid :deep(.ant-input-textarea .ant-input) {
  min-height: 180px;
  padding-top: 10px;
  line-height: 1.8;
}

.disabled-field :deep(.ant-form-item-extra) {
  margin-top: 6px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.date-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.date-range :deep(.ant-picker) {
  width: 100%;
}

.attachment-upload {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.attachment-dropzone {
  min-height: 140px;
  padding: 20px;
  border: 1.5px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafbfc;
}

.attachment-dropzone :deep(.ant-upload-drag-icon) {
  margin: 0 0 8px;
  color: #1677ff;
  font-size: 28px;
}

.attachment-dropzone :deep(.ant-upload-text) {
  margin: 0;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.attachment-dropzone :deep(.ant-upload-text b) {
  color: #1677ff;
}

.attachment-hint {
  margin: 6px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.editor-footer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 200px;
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  min-height: 64px;
  padding: 14px 32px;
  border-top: 1px solid #f0f0f0;
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 -4px 16px rgb(0 0 0 / 4%);
  backdrop-filter: blur(8px);
  z-index: 8;
}

.editor-save-state {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.editor-footer-spacer {
  flex: 1;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 768px) {
  .editor-surface {
    padding: 20px 22px 26px;
  }

  .editor-grid {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .editor-grid .full-width {
    grid-column: auto;
  }

  .editor-footer { padding: 12px 16px; }
}

@media (max-width: 640px) {
  .requirement-editor-form {
    padding-bottom: 118px;
  }

  .editor-surface {
    padding: 16px;
  }

  .editor-notice {
    align-items: flex-start;
  }

  .attachment-upload {
    grid-template-columns: 1fr;
  }

  .date-range {
    grid-template-columns: 1fr;
  }

  .date-range > span {
    display: none;
  }

  .editor-save-state {
    display: none;
  }

  .editor-footer {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
