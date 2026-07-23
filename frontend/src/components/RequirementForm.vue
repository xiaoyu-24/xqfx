<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Button, Card, DatePicker, Form, Input, Radio, Select, message } from 'ant-design-vue'
import { api } from '../api'
import { DEPARTMENTS } from '../constants/departments'
import { markChanged } from '../composables/refreshBus'

type SystemMode = 'existing' | 'new' | 'none'

const emit = defineEmits<{
  'dirty-change': [value: boolean]
  submitted: []
}>()

const systemSelect = ref('')
const systemMode = computed<SystemMode>(() => {
  if (systemSelect.value === 'new') return 'new'
  if (!systemSelect.value || systemSelect.value === 'none') return 'none'
  return 'existing'
})
const form = reactive({
  requesterName: '',
  department: '',
  title: '',
  type: 'REQUIREMENT',
  periodStartDate: '',
  periodEndDate: '',
  systemId: '',
  targetVersionId: '',
  newSystemName: '',
  newSystemOwnerName: '',
  newSystemCollaborators: '',
  content: '',
})

const submitting = ref(false)
const systems = ref<Array<{ id: number; name: string; status: string }>>([])
const versions = ref<Array<{ id: number; name: string; status: string }>>([])
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

const beforeUnloadHandler = (event: BeforeUnloadEvent) => {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(isDirty, (value) => emit('dirty-change', value), { immediate: true })

onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnloadHandler)
  try {
    systems.value = (await api.get('/systems')).data.filter((system: { status: string }) => system.status === 'ACTIVE')
  } catch {
    message.warning('系统列表加载失败，可选择新系统或稍后重试')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  emit('dirty-change', false)
})

const loadVersions = async () => {
  form.targetVersionId = ''
  versions.value = []
  if (!form.systemId) return
  try {
    versions.value = (await api.get(`/systems/${form.systemId}/versions`)).data.filter((version: { status: string }) => version.status === 'ACTIVE')
  } catch {
    message.warning('版本列表加载失败')
  }
}
const onSystemSelectChange = async () => {
  if (systemMode.value === 'existing') {
    form.systemId = systemSelect.value
    await loadVersions()
  } else {
    form.systemId = ''
    form.targetVersionId = ''
    versions.value = []
  }
}
const requestBody = () => ({
  requesterName: form.requesterName, department: form.department, title: form.title, type: form.type,
  content: form.content, periodStartDate: form.periodStartDate || null, periodEndDate: form.periodEndDate || null,
  systemId: systemMode.value === 'existing' && form.systemId ? Number(form.systemId) : null,
  targetVersionId: systemMode.value === 'existing' && form.targetVersionId ? Number(form.targetVersionId) : null,
  newSystem: systemMode.value === 'new' ? { name: form.newSystemName, ownerName: form.newSystemOwnerName, collaborators: form.newSystemCollaborators.split(',').map((item) => item.trim()).filter(Boolean) } : null,
})
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
  if (periodError.value) {
    message.warning(periodError.value)
    return
  }
  submitting.value = true
  try {
    const body = requestBody()
    const shouldRefreshSystemPages = systemMode.value === 'new'
    const { data } = await api.post(draft ? '/requirements/drafts' : '/requirements', body)
    if (selectedFiles.value.length > 0 && data.id) {
      try { await uploadFiles(data.id) } catch { message.warning('需求已保存，但部分附件上传失败，可稍后在详情页重试') }
    }
    lastSavedSnapshot.value = createSnapshot()
    message.success(draft ? '暂存成功' : '保存成功')
    markChanged(shouldRefreshSystemPages
      ? ['requirements', 'management', 'systems', 'versions']
      : ['requirements', 'management'])
    if (!draft) emit('submitted')
  } catch {
    message.error('保存失败，请检查填写内容后重试')
  } finally { submitting.value = false }
}

</script>

<template>
  <section class="requirement-form-shell" aria-labelledby="requirement-form-title">
    <Card class="requirement-form-card" data-test="requirement-form-card" :bordered="false">
      <p class="field-requirement-legend" data-test="field-requirement-legend">带 <span class="field-required">*</span> 的项目为必填项，选填项目可根据实际情况填写。</p>

      <Form :model="form" layout="vertical" class="requirement-form" @submit.prevent="submit(false)">
        <div class="form-grid">
          <Form.Item data-test="requester-name-field" label="姓名" required>
            <Input v-model:value="form.requesterName" required placeholder="请输入姓名" />
          </Form.Item>

          <Form.Item label="部门" required>
            <Select v-model:value="form.department" data-test="department-select" placeholder="请选择部门" class="department-select">
              <Select.Option v-for="department in DEPARTMENTS" :key="department" :value="department">{{ department }}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item class="full-width" label="需求标题" required>
            <Input v-model:value="form.title" required placeholder="请简要概括需求" />
          </Form.Item>

          <Form.Item label="类型" required>
            <Radio.Group v-model:value="form.type">
              <Radio value="BUG">BUG</Radio>
              <Radio value="REQUIREMENT">需求</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item class="period-form-item" label="需求时间周期（上海时间）" data-test="period-field" :validate-status="periodError ? 'error' : undefined" :help="periodError || undefined">
            <div class="date-range">
              <DatePicker v-model:value="form.periodStartDate" value-format="YYYY-MM-DD" placeholder="开始日期" aria-label="开始日期" :status="periodError ? 'error' : undefined" />
              <span class="range-separator">至</span>
              <DatePicker v-model:value="form.periodEndDate" value-format="YYYY-MM-DD" placeholder="结束日期" aria-label="结束日期" :status="periodError ? 'error' : undefined" />
            </div>
          </Form.Item>

          <Form.Item class="full-width" label="所属系统" required>
            <Select v-model:value="systemSelect" data-test="system-select" placeholder="请选择系统" @change="onSystemSelectChange">
              <Select.Option v-for="system in systems" :key="system.id" :value="String(system.id)">{{ system.name }}</Select.Option>
              <Select.Option value="new">新系统</Select.Option>
              <Select.Option value="none">暂无系统</Select.Option>
            </Select>
          </Form.Item>

          <template v-if="systemMode === 'existing'">
            <Form.Item label="目标版本">
              <Select v-model:value="form.targetVersionId" data-test="version-select" placeholder="请选择版本（可选）">
                <Select.Option v-for="version in versions" :key="version.id" :value="String(version.id)">{{ version.name }}</Select.Option>
              </Select>
            </Form.Item>
          </template>

          <template v-else-if="systemMode === 'new'">
            <Form.Item data-test="new-system-name-field" label="新系统名称" required>
              <Input v-model:value="form.newSystemName" :required="systemMode === 'new'" placeholder="请输入系统名称" />
            </Form.Item>
            <Form.Item label="新系统负责人" required>
              <Input v-model:value="form.newSystemOwnerName" :required="systemMode === 'new'" placeholder="请输入负责人姓名" />
            </Form.Item>
            <Form.Item class="full-width" data-test="new-system-collaborators-field" label="新系统协助人">
              <Input v-model:value="form.newSystemCollaborators" placeholder="多人请用逗号分隔" />
            </Form.Item>
          </template>

          <Form.Item class="full-width" label="需求内容" required>
            <Input.TextArea v-model:value="form.content" required :rows="10" placeholder="请描述背景、问题、期望结果和验收标准" />
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
