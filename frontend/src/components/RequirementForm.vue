<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

type SystemMode = 'existing' | 'new' | 'none'

const emit = defineEmits<{
  'dirty-change': [value: boolean]
  submitted: []
}>()

const systemMode = ref<SystemMode>('existing')
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

const createSnapshot = () => JSON.stringify({
  systemMode: systemMode.value,
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
    ElMessage.warning('系统列表加载失败，可选择新系统或稍后重试')
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
    ElMessage.warning('版本列表加载失败')
  }
}
watch(systemMode, (mode) => {
  form.targetVersionId = ''
  if (mode !== 'existing') {
    form.systemId = ''
    versions.value = []
  }
})
const requestBody = () => ({
  requesterName: form.requesterName, department: form.department, title: form.title, type: form.type,
  content: form.content, periodStartDate: form.periodStartDate || null, periodEndDate: form.periodEndDate || null,
  systemId: systemMode.value === 'existing' && form.systemId ? Number(form.systemId) : null,
  targetVersionId: systemMode.value === 'existing' && form.targetVersionId ? Number(form.targetVersionId) : null,
  newSystem: systemMode.value === 'new' ? { name: form.newSystemName, ownerName: form.newSystemOwnerName, collaborators: form.newSystemCollaborators.split(',').map((item) => item.trim()).filter(Boolean) } : null,
})
const selectFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  selectedFiles.value = [...selectedFiles.value, ...Array.from(input.files ?? [])]
  input.value = ''
}
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
    ElMessage.warning(periodError.value)
    return
  }
  submitting.value = true
  try {
    const body = requestBody()
    const { data } = await api.post(draft ? '/requirements/drafts' : '/requirements', body)
    if (selectedFiles.value.length > 0 && data.id) {
      try { await uploadFiles(data.id) } catch { ElMessage.warning('需求已保存，但部分附件上传失败，可稍后在详情页重试') }
    }
    lastSavedSnapshot.value = createSnapshot()
    ElMessage.success(draft ? '暂存成功' : '保存成功')
    if (!draft) emit('submitted')
  } catch {
    ElMessage.error('保存失败，请检查填写内容后重试')
  } finally { submitting.value = false }
}

</script>

<template>
  <form class="requirement-form" @submit.prevent="submit(false)">
    <div class="form-grid">
      <label>姓名 <input v-model="form.requesterName" required placeholder="请输入姓名"></label>
      <label>部门 <input v-model="form.department" required placeholder="请输入部门"></label>
      <label class="full-width">需求标题 <input v-model="form.title" required placeholder="请简要概括需求"></label>
      <fieldset>
        <legend>类型</legend>
        <label><input v-model="form.type" type="radio" value="BUG"> BUG</label>
        <label><input v-model="form.type" type="radio" value="REQUIREMENT"> 需求</label>
      </fieldset>
      <div>
        <span class="field-label">需求时间周期（上海时间）</span>
        <div class="date-range">
          <input v-model="form.periodStartDate" type="date" aria-label="开始日期" :aria-invalid="Boolean(periodError)">
          <span>至</span>
          <input v-model="form.periodEndDate" type="date" aria-label="结束日期" :aria-invalid="Boolean(periodError)">
        </div>
        <p v-if="periodError" class="field-error">{{ periodError }}</p>
      </div>
      <label class="full-width">所属系统
        <select v-model="systemMode" data-test="system-mode">
          <option value="existing">选择已有系统</option>
          <option value="new">新系统</option>
          <option value="none">暂无系统</option>
        </select>
      </label>
      <template v-if="systemMode === 'existing'">
        <label>已有系统 <select v-model="form.systemId" data-test="system-select" @change="loadVersions"><option value="">请选择系统</option><option v-for="system in systems" :key="system.id" :value="system.id">{{ system.name }}</option></select></label>
        <label>目标版本 <select v-model="form.targetVersionId" :disabled="!form.systemId"><option value="">请选择版本（可选）</option><option v-for="version in versions" :key="version.id" :value="version.id">{{ version.name }}</option></select></label>
      </template>
      <template v-else-if="systemMode === 'new'">
        <label>新系统名称 <input v-model="form.newSystemName" :required="systemMode === 'new'" placeholder="请输入系统名称"></label>
        <label>新系统负责人 <input v-model="form.newSystemOwnerName" :required="systemMode === 'new'" placeholder="请输入负责人姓名"></label>
        <label class="full-width">新系统协助人 <input v-model="form.newSystemCollaborators" placeholder="多人请用逗号分隔"></label>
      </template>
      <label class="full-width">需求内容
        <textarea v-model="form.content" required rows="10" placeholder="请描述背景、问题、期望结果和验收标准"></textarea>
      </label>
      <div class="full-width attachment-note"><strong>附件</strong>：支持图片、PDF、Word、Excel，业务层不限制单个文件大小。<input data-test="attachment-input" type="file" multiple accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="selectFiles"><span v-if="submitting && selectedFiles.length">上传中 {{ uploadProgress }}%</span><ul v-if="selectedFiles.length"><li v-for="(file, index) in selectedFiles" :key="`${file.name}-${index}`">{{ file.name }} <button type="button" @click="removeSelectedFile(index)">移除</button></li></ul><ul v-if="uploadedAttachments.length"><li v-for="attachment in uploadedAttachments" :key="attachment.id">已上传：{{ attachment.originalName }}</li></ul></div>
    </div>
    <div class="form-actions">
      <button class="secondary" type="button" :disabled="submitting" @click="submit(true)">暂存</button>
      <button class="primary" type="submit" :disabled="submitting">保存需求</button>
    </div>
  </form>
</template>
