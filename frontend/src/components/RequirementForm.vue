<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Button, Card, Collapse, CollapsePanel, DatePicker, Form, Input, Select, message } from 'ant-design-vue'
import { RobotOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { markChanged } from '../composables/refreshBus'
import { useCreateFormState } from '../composables/useCreateFormState'
import { apiErrorDetails } from '../composables/useApiError'
import { useDictionaryOptions } from '../composables/useDictionaryOptions'
import { useAuth } from '../composables/useAuth'
import { urgencyOptions } from '../constants/urgencyConfig'
import { useFormErrors } from '../composables/useFormErrors'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'

const router = useRouter()
const { setCreateFormDirty } = useCreateFormState()
const { currentUser } = useAuth()
const systemSelect = ref('')
const form = reactive({
  requesterName: '',
  departmentId: '',
  title: '',
  typeId: '',
  urgency: 'MEDIUM',
  periodStartDate: '',
  periodEndDate: '',
  systemId: '',
  content: '',
})

const aiText = ref('')
const aiAnalyzing = ref(false)
const { departments, requirementTypes, loadDictionaryOptions } = useDictionaryOptions()
const { errors, clearErrors, setError, applyServerErrors } = useFormErrors()

const analyzeWithAi = async () => {
  if (!aiText.value.trim()) {
    message.warning('请输入需要分析的需求描述')
    return
  }
  aiAnalyzing.value = true
  try {
    const { data } = await api.post('/ai/analyze', { text: aiText.value })
    let filled = 0
    if (data.requesterName && !form.requesterName) { form.requesterName = data.requesterName; filled++ }
    if (data.departmentId && !form.departmentId) { form.departmentId = String(data.departmentId); filled++ }
    if (data.title && !form.title) { form.title = data.title; filled++ }
    if (data.typeId && !form.typeId) { form.typeId = String(data.typeId); filled++ }
    if (data.content && !form.content) { form.content = data.content; filled++ }
    if (data.periodStartDate && !form.periodStartDate) { form.periodStartDate = data.periodStartDate; filled++ }
    if (data.periodEndDate && !form.periodEndDate) { form.periodEndDate = data.periodEndDate; filled++ }
    if (data.systemId && !form.systemId) {
      systemSelect.value = String(data.systemId)
      form.systemId = String(data.systemId)
      filled++
    }
    if (filled > 0) {
      message.success(`AI 已识别并填充 ${filled} 个字段`)
    } else {
      message.info('AI 未能从文本中识别出有效字段')
    }
  } catch (error: unknown) {
    const msg = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    message.error(msg || 'AI 分析失败，请稍后重试')
  } finally {
    aiAnalyzing.value = false
  }
}

const submitting = ref(false)
const systems = ref<Array<{ id: number; name: string; status: string }>>([])
const selectedFiles = ref<File[]>([])
const uploadedAttachments = ref<Array<{ id: number; originalName: string }>>([])
const uploadProgress = ref(0)
const isDragging = ref(false)
const attachmentInput = ref<HTMLInputElement | null>(null)
const allowedAttachmentExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx'])

const createSnapshot = () => JSON.stringify({
  systemSelect: systemSelect.value,
  form: { ...form },
  selectedFiles: selectedFiles.value.map((file) => ({
    name: file.name,
    size: file.size,
    type: file.type,
    lastModified: file.lastModified,
  })),
})

const lastSavedSnapshot = ref(createSnapshot())
const isDirty = computed(() => createSnapshot() !== lastSavedSnapshot.value)
const periodError = computed(() => {
  if ((form.periodStartDate && !form.periodEndDate) || (!form.periodStartDate && form.periodEndDate)) {
    return '开始日期和结束日期必须同时填写'
  }
  if (form.periodStartDate && form.periodEndDate && form.periodEndDate < form.periodStartDate) {
    return '结束日期不能早于开始日期'
  }
  return ''
})

watch(isDirty, setCreateFormDirty, { immediate: true })

watch(currentUser, (user) => {
  if (!user) return
  if (!form.requesterName.trim()) form.requesterName = user.displayName
  if (user.departmentId !== null) {
    form.departmentId = String(user.departmentId)
  }
  lastSavedSnapshot.value = createSnapshot()
}, { immediate: true })

const loadFormOptions = async () => {
  await loadDictionaryOptions()
  const systemsResponse = await api.get('/systems')
  systems.value = Array.isArray(systemsResponse.data)
    ? systemsResponse.data.filter((system: { status: string }) => system.status === 'ACTIVE')
    : []
}

const { loaded } = usePageRefresh('requirementForm', loadFormOptions)

onBeforeUnmount(() => {
  setCreateFormDirty(false)
})

const onSystemSelectChange = () => {
  form.systemId = systemSelect.value
}
const requestBody = () => ({
  requesterName: form.requesterName,
  departmentId: form.departmentId ? Number(form.departmentId) : null,
  title: form.title,
  typeId: form.typeId ? Number(form.typeId) : null,
  urgency: form.urgency,
  content: form.content, periodStartDate: form.periodStartDate || null, periodEndDate: form.periodEndDate || null,
  systemId: form.systemId ? Number(form.systemId) : null,
})
const validateForm = (draft: boolean) => {
  clearErrors()
  if (draft) return true
  const requiredFields: Array<[string, string, string]> = [
    ['requesterName', form.requesterName, '请输入提出人姓名'],
    ['departmentId', form.departmentId, '请选择部门'],
    ['title', form.title, '请输入需求标题'],
    ['typeId', form.typeId, '请选择需求类型'],
    ['urgency', form.urgency, '请选择紧急程度'],
    ['content', form.content, '请输入需求内容'],
  ]
  requiredFields.push(['systemSelect', systemSelect.value, '请选择所属系统'])
  requiredFields.forEach(([field, value, error]) => { if (!String(value).trim()) setError(field, error) })
  const valid = Object.keys(errors).length === 0
  if (!valid) message.warning('请先补充标记的必填项')
  return valid
}
const addFiles = (files: File[]) => {
  const accepted: File[] = []
  const rejected: string[] = []
  for (const file of files) {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!allowedAttachmentExtensions.has(extension) || file.size > 100 * 1024 * 1024) {
      rejected.push(file.name)
      continue
    }
    const duplicate = [...selectedFiles.value, ...accepted].some((item) =>
      item.name === file.name && item.size === file.size && item.lastModified === file.lastModified)
    if (!duplicate) accepted.push(file)
  }
  selectedFiles.value = [...selectedFiles.value, ...accepted]
  if (rejected.length > 0) message.warning(`以下附件格式不支持或超过 100MB：${rejected.join('、')}`)
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
const removeSelectedFile = (index: number) => { selectedFiles.value.splice(index, 1) }
const uploadFiles = async (requirementId: number) => {
  uploadProgress.value = 0
  const files = [...selectedFiles.value]
  for (let index = 0; index < files.length; index += 1) {
    const body = new FormData()
    body.append('file', files[index])
    const { data } = await api.post(`/requirements/${requirementId}/attachments`, body, {
      onUploadProgress: (event) => { if (event.total) uploadProgress.value = Math.round(((index + event.loaded / event.total) / files.length) * 100) },
    })
    uploadedAttachments.value.push(data)
  }
  selectedFiles.value = []
}
const submit = async (draft: boolean) => {
  if (!validateForm(draft)) return
  if (periodError.value) {
    setError('period', periodError.value)
    message.warning('请修正需求时间周期')
    return
  }
  submitting.value = true
  try {
    const body = requestBody()
    const { data } = await api.post(draft ? '/requirements/drafts' : '/requirements', body)
    if (selectedFiles.value.length > 0 && data.id) {
      try { await uploadFiles(data.id) } catch (error) { const { message: serverMessage } = apiErrorDetails(error); message.warning(serverMessage || '需求已保存，但部分附件上传失败，可稍后在详情页重试') }
    }
    lastSavedSnapshot.value = createSnapshot()
    message.success(draft ? '暂存成功' : '保存成功')
    markChanged(['requirements', 'dashboard', 'overview'])
    if (!draft) await router.push({ name: 'requirement-list' })
  } catch (error: unknown) {
    const details = applyServerErrors(error)
    message.error(details.message || '保存失败，请检查标记的字段后重试')
  } finally { submitting.value = false }
}

</script>

<template>
  <section class="requirement-form-shell" aria-labelledby="requirement-form-title" data-test="ai-import-text">
    <ContentSkeleton v-if="!loaded" preset="form" :rows="8" />
    <Card v-else class="requirement-form-card" data-test="requirement-form-card" :bordered="false">
      <Collapse class="ai-import-collapse" :bordered="false">
        <CollapsePanel key="ai-import" data-test="ai-import-panel">
          <template #header>
            <span class="ai-import-header"><RobotOutlined /> AI 智能导入</span>
          </template>
          <p class="ai-import-hint">粘贴一段需求描述文本，AI 将自动识别并填充空白字段（已有内容不会被覆盖，不会自动提交）。</p>
          <Input.TextArea v-model:value="aiText" data-test="ai-import-input" :rows="4" placeholder="请粘贴需求描述文本…" />
          <Button class="ai-analyze-btn" type="primary" ghost :loading="aiAnalyzing" @click="analyzeWithAi">分析并填充</Button>
        </CollapsePanel>
      </Collapse>

      <p class="field-requirement-legend" data-test="field-requirement-legend">带 <span class="field-required">*</span> 的项目为必填项，选填项目可根据实际情况填写。</p>

      <Form :model="form" layout="vertical" class="requirement-form" @submit.prevent="submit(false)">
        <div class="form-grid">
          <Form.Item data-test="requester-name-field" label="姓名" required :validate-status="errors.requesterName ? 'error' : undefined" :help="errors.requesterName">
            <Input v-model:value="form.requesterName" placeholder="请输入提出人姓名" />
          </Form.Item>

          <Form.Item label="部门" required :validate-status="errors.departmentId ? 'error' : undefined" :help="errors.departmentId">
            <Select v-model:value="form.departmentId" data-test="department-select" placeholder="请选择部门" class="department-select">
              <Select.Option v-for="department in departments" :key="department.id" :value="String(department.id)">{{ department.name }}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item class="full-width" label="需求标题" required :validate-status="errors.title ? 'error' : undefined" :help="errors.title">
            <Input v-model:value="form.title" placeholder="请简要概括需求" />
          </Form.Item>

          <Form.Item label="类型" required :validate-status="errors.typeId ? 'error' : undefined" :help="errors.typeId">
            <Select v-model:value="form.typeId" placeholder="请选择需求类型">
              <Select.Option v-for="type in requirementTypes" :key="type.id" :value="String(type.id)">{{ type.name }}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item label="紧急程度" required :validate-status="errors.urgency ? 'error' : undefined" :help="errors.urgency">
            <Select v-model:value="form.urgency" :options="urgencyOptions" />
          </Form.Item>

          <Form.Item class="period-form-item" label="需求时间周期（上海时间）" data-test="period-field" :validate-status="errors.period ? 'error' : undefined" :help="errors.period || undefined">
            <div class="date-range">
              <DatePicker v-model:value="form.periodStartDate" value-format="YYYY-MM-DD" placeholder="开始日期" aria-label="开始日期" :status="periodError ? 'error' : undefined" />
              <span class="range-separator">至</span>
              <DatePicker v-model:value="form.periodEndDate" value-format="YYYY-MM-DD" placeholder="结束日期" aria-label="结束日期" :status="periodError ? 'error' : undefined" />
            </div>
          </Form.Item>

          <Form.Item class="full-width" label="所属系统" required :validate-status="errors.systemSelect ? 'error' : undefined" :help="errors.systemSelect">
            <Select v-model:value="systemSelect" data-test="system-select" placeholder="请选择系统" @change="onSystemSelectChange">
              <Select.Option v-for="system in systems" :key="system.id" :value="String(system.id)">{{ system.name }}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item class="full-width" label="需求内容" required :validate-status="errors.content ? 'error' : undefined" :help="errors.content">
            <Input.TextArea v-model:value="form.content" :rows="10" placeholder="请描述背景、问题、期望结果和验收标准" />
          </Form.Item>

          <Form.Item class="full-width attachment-form-item" data-test="attachment-field" label="附件">
            <p class="attachment-hint">支持图片、PDF、Word、Excel，单个文件最大 100MB。</p>
            <div class="attachment-dropzone" :class="{ 'is-dragging': isDragging }" data-test="attachment-dropzone" role="button" tabindex="0" @click="openAttachmentPicker" @keydown.enter.prevent="openAttachmentPicker" @dragenter.prevent="isDragging = true" @dragover.prevent="isDragging = true" @dragleave.prevent="isDragging = false" @drop.prevent="dropFiles">
              <Button type="dashed" block>拖拽文件到此处，或点击选择</Button>
              <input ref="attachmentInput" data-test="attachment-input" class="attachment-input" type="file" multiple accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="selectFiles">
            </div>
            <span v-if="submitting && selectedFiles.length" class="upload-progress">上传中 {{ uploadProgress }}%</span>
            <ul v-if="selectedFiles.length" class="attachment-list">
              <li v-for="(file, index) in selectedFiles" :key="`${file.name}-${index}`">
                <span>{{ file.name }}</span>
                <Button type="link" danger size="small" @click.stop="removeSelectedFile(index)">移除</Button>
              </li>
            </ul>
            <ul v-if="uploadedAttachments.length" class="attachment-list uploaded-attachment-list">
              <li v-for="attachment in uploadedAttachments" :key="attachment.id">已上传：{{ attachment.originalName }}</li>
            </ul>
          </Form.Item>
        </div>

        <div class="form-actions">
          <Button type="default" html-type="button" :loading="submitting" @click="submit(true)">暂存</Button>
          <Button type="primary" html-type="submit" :loading="submitting">保存需求</Button>
        </div>
      </Form>
    </Card>
  </section>
</template>

<style scoped>
.requirement-form-shell {
  width: 100%;
}

.requirement-form-card {
  box-shadow: 0 8px 24px rgb(15 23 42 / 8%);
}


.ai-import-collapse {
  margin-bottom: 16px;
  background: rgb(248 250 252);
  border-radius: 8px;
}

.ai-import-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  color: rgb(22 119 255);
}

.ai-import-hint {
  margin: 0 0 8px;
  color: rgb(100 116 139);
  font-size: 13px;
}

.ai-analyze-btn {
  margin-top: 10px;
}

.form-title,
.form-subtitle {
  margin: 0;
}

.form-title {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}

.form-subtitle {
  margin-top: 4px;
  color: rgb(100 116 139);
  font-size: 13px;
  font-weight: 400;
}

.field-requirement-legend {
  margin: 0 0 20px;
  color: rgb(100 116 139);
  font-size: 13px;
}

.field-required {
  color: rgb(220 38 38);
}

.field-optional {
  color: rgb(100 116 139);
  font-size: 12px;
  font-weight: 400;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.full-width {
  grid-column: 1 / -1;
}

.date-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.range-separator {
  color: rgb(100 116 139);
}

.date-range :deep(.ant-picker) {
  width: 100%;
}

.attachment-hint {
  margin: 0 0 8px;
  color: rgb(100 116 139);
  font-size: 13px;
}

.attachment-dropzone {
  padding: 16px;
  border: 1px dashed rgb(148 163 184);
  border-radius: 8px;
  background: rgb(248 250 252);
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.attachment-dropzone:hover,
.attachment-dropzone.is-dragging {
  border-color: rgb(22 119 255);
  background: rgb(230 244 255);
}

.attachment-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

.upload-progress {
  display: inline-block;
  margin-top: 8px;
  color: rgb(22 119 255);
  font-size: 13px;
}

.attachment-list {
  display: grid;
  gap: 4px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.attachment-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px;
  border-radius: 6px;
  background: rgb(248 250 252);
  color: rgb(51 65 85);
  font-size: 13px;
}

.uploaded-attachment-list li {
  display: block;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid rgb(226 232 240);
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .full-width {
    grid-column: auto;
  }

  .date-range {
    grid-template-columns: minmax(0, 1fr);
  }

  .range-separator {
    display: none;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .form-actions :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
