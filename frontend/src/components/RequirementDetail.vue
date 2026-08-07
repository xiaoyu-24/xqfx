<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button, Card, Descriptions, DescriptionsItem, Form, FormItem, Input, Modal, Result, Select, Space, Spin, Tag, Timeline, TimelineItem, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta, requirementStatusOptions, saveTypeMeta } from '../constants/statusConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import RequirementEditor from './RequirementEditor.vue'

type SystemItem = { id: number; name: string }
type UserItem = { id: number; displayName: string }
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
type Requirement = {
  id: number
  title: string | null
  type: string | null
  requesterName: string | null
  requesterUserId?: number | null
  department: string | null
  status: string | null
  submittedAt: string | null
  updatedAt?: string | null
  systemId: number | null
  targetVersionId: number | null
  targetVersionName?: string | null
  periodStartDate: string | null
  periodEndDate: string | null
  completedAt?: string | null
  handledBy?: string | null
  completionDescription?: string | null
  assigneeId?: number | null
  assigneeName?: string | null
  content?: string | null
  saveType?: string
  recordVersion: number
}
type Progress = {
  id: number
  content: string
  authorId: number
  authorName: string
  status: string | null
  createdAt: string
}

const route = useRoute()
const router = useRouter()
const requirement = ref<Requirement | null>(null)
const systems = ref<SystemItem[]>([])
const users = ref<UserItem[]>([])
const attachments = ref<Attachment[]>([])
const progresses = ref<Progress[]>([])
const previewAttachment = ref<Attachment | null>(null)
const loading = ref(true)
const loadError = ref('')
const editing = ref(false)
const progressModalOpen = ref(false)
const progressLoading = ref(false)
const progressForm = ref({ content: '', status: '' })
const assigneeModalOpen = ref(false)
const assigneeLoading = ref(false)
const assigneeUserId = ref<number | undefined>()
const { handleError } = useApiError()
let previewRefreshTimer: ReturnType<typeof setTimeout> | undefined

const requirementId = computed(() => Number(route.params.id))
const previewOpen = computed({
  get: () => previewAttachment.value !== null,
  set: (open: boolean) => { if (!open) previewAttachment.value = null },
})
const systemName = computed(() => {
  if (!requirement.value || requirement.value.systemId === null) return '暂无系统'
  return systems.value.find((system) => system.id === requirement.value?.systemId)?.name ?? `系统 #${requirement.value.systemId}`
})
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const match = value.match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] ?? value.slice(0, 10)
}
const formatShanghaiDateTime = (value: string | null | undefined) => {
  if (!value) return '—'
  return value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 16)
}
const periodText = computed(() => requirement.value?.periodStartDate && requirement.value.periodEndDate
  ? `${requirement.value.periodStartDate} 至 ${requirement.value.periodEndDate}`
  : '—')
const statusLabel = computed(() => requirement.value?.status
  ? requirementStatusMeta(requirement.value.status).label
  : saveTypeMeta(requirement.value?.saveType ?? 'DRAFT').label)
const statusColor = computed(() => requirement.value?.status
  ? requirementStatusMeta(requirement.value.status).color
  : saveTypeMeta(requirement.value?.saveType ?? 'DRAFT').color)

const clearPreviewRefresh = () => {
  if (previewRefreshTimer !== undefined) clearTimeout(previewRefreshTimer)
  previewRefreshTimer = undefined
}
const schedulePreviewRefresh = () => {
  clearPreviewRefresh()
  if (!requirement.value || !attachments.value.some((item) => item.previewStatus === 'PENDING' || item.previewStatus === 'CONVERTING')) return
  previewRefreshTimer = setTimeout(refreshAttachments, 2000)
}
const refreshAttachments = async () => {
  if (!requirement.value) return
  try {
    const { data } = await api.get(`/requirements/${requirement.value.id}/attachments`)
    attachments.value = Array.isArray(data) ? data : []
  } finally {
    schedulePreviewRefresh()
  }
}

const loadRequirement = async () => {
  if (!Number.isSafeInteger(requirementId.value) || requirementId.value <= 0) {
    loadError.value = '需求编号无效'
    loading.value = false
    return
  }

  loading.value = true
  loadError.value = ''
  clearPreviewRefresh()
  try {
    const [detail, attachmentList, systemList, progressList, activeUsers] = await Promise.all([
      api.get(`/requirements/${requirementId.value}`),
      api.get(`/requirements/${requirementId.value}/attachments`),
      api.get('/systems'),
      api.get(`/requirements/${requirementId.value}/progresses`),
      api.get('/users/active'),
    ])
    requirement.value = detail.data
    attachments.value = Array.isArray(attachmentList.data) ? attachmentList.data : []
    systems.value = Array.isArray(systemList.data) ? systemList.data : []
    progresses.value = Array.isArray(progressList.data) ? progressList.data : []
    users.value = Array.isArray(activeUsers.data) ? activeUsers.data : []
    schedulePreviewRefresh()
  } catch (error: unknown) {
    requirement.value = null
    attachments.value = []
    progresses.value = []
    const status = (error as { response?: { status?: number } }).response?.status
    loadError.value = status === 404 ? '该需求不存在或已被删除' : '加载需求详情失败，请稍后重试'
  } finally {
    loading.value = false
  }
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
const returnToList = () => {
  void router.push({ name: 'requirement-list' })
}
const setEditQuery = (enabled: boolean) => {
  const query = { ...route.query }
  if (enabled) query.edit = '1'
  else delete query.edit
  void router.replace({ name: 'requirement-detail', params: { id: requirementId.value }, query })
}
const editRequirement = () => {
  editing.value = true
  setEditQuery(true)
}
const finishEditing = () => {
  editing.value = false
  setEditQuery(false)
  void loadRequirement()
}
const cancelEditing = () => {
  editing.value = false
  setEditQuery(false)
  void loadRequirement()
}
const openProgressUpdate = () => {
  progressForm.value = { content: '', status: '' }
  progressModalOpen.value = true
}
const saveProgressUpdate = async () => {
  if (!requirement.value) return
  const content = progressForm.value.content.trim()
  if (!content) {
    message.warning('请输入进展内容')
    return
  }

  progressLoading.value = true
  try {
    await api.post(`/requirements/${requirement.value.id}/progresses`, {
      content,
      status: progressForm.value.status || null,
      recordVersion: requirement.value.recordVersion,
    })
    progressModalOpen.value = false
    message.success('进展已更新')
    markChanged(['requirements', 'management', 'dashboard'])
    await loadRequirement()
  } catch (error: unknown) {
    handleError(error, '更新进度失败，请稍后重试')
  } finally {
    progressLoading.value = false
  }
}

const openAssigneeModal = () => {
  assigneeUserId.value = requirement.value?.assigneeId ?? undefined
  assigneeModalOpen.value = true
}

const saveAssignee = async () => {
  if (!requirement.value) return
  assigneeLoading.value = true
  try {
    await api.patch(`/requirements/${requirement.value.id}/assignee`, {
      assigneeUserId: assigneeUserId.value ?? null,
      recordVersion: requirement.value.recordVersion,
    })
    assigneeModalOpen.value = false
    message.success(assigneeUserId.value == null ? '已取消指派处理人' : '已指派处理人')
    markChanged(['requirements', 'management', 'dashboard'])
    await loadRequirement()
  } catch (error: unknown) {
    handleError(error, '指派处理人失败，请稍后重试')
  } finally {
    assigneeLoading.value = false
  }
}

watch(() => route.params.id, () => {
  editing.value = route.query.edit === '1'
  void loadRequirement()
}, { immediate: true })
watch(() => route.query.edit, (value, previousValue) => {
  if (value === previousValue || !requirement.value) return
  editing.value = value === '1'
})
onBeforeUnmount(clearPreviewRefresh)
</script>

<template>
  <section class="requirement-detail-page" data-test="requirement-detail-page">
    <Spin :spinning="loading" tip="正在加载需求详情…">
      <Result v-if="loadError" status="error" :title="loadError">
        <template #extra><Button type="primary" @click="returnToList">返回需求列表</Button></template>
      </Result>

      <Card v-else-if="requirement" :bordered="false" class="requirement-detail-card">
        <template #title>
          <div class="detail-heading">
            <span class="detail-title">{{ editing ? '编辑需求' : requirement.title || '未命名草稿' }}</span>
            <Space wrap>
              <Button @click="returnToList">返回列表</Button>
              <Button v-if="editing" @click="cancelEditing">取消编辑</Button>
              <Button v-else type="primary" data-test="edit-from-detail" @click="editRequirement">编辑需求</Button>
            </Space>
          </div>
        </template>

        <RequirementEditor v-if="editing" :key="requirement.id" :requirement-id="requirement.id" @cancel="cancelEditing" @saved="finishEditing" />

        <template v-else>
          <section class="detail-core-fields" data-test="detail-core-fields">
            <div class="detail-type-field">
              <span class="detail-field-label">类型</span>
              <Tag>{{ requirement.type || '—' }}</Tag>
            </div>
            <div class="detail-content-field">
              <h2>需求</h2>
              <p class="detail-content">{{ requirement.content || '—' }}</p>
            </div>
          </section>

          <Descriptions bordered size="small" :column="2">
            <DescriptionsItem label="状态"><Tag :color="statusColor">{{ statusLabel }}</Tag></DescriptionsItem>
            <DescriptionsItem label="所属系统">{{ systemName }}</DescriptionsItem>
            <DescriptionsItem label="目标版本">{{ requirement.targetVersionId === null ? '—' : requirement.targetVersionName ?? `版本 #${requirement.targetVersionId}` }}</DescriptionsItem>
            <DescriptionsItem label="填写人 / 部门">{{ requirement.requesterName || '—' }} / {{ requirement.department || '—' }}</DescriptionsItem>
            <DescriptionsItem label="填写时间">{{ formatShanghai(requirement.submittedAt || requirement.updatedAt) }}</DescriptionsItem>
            <DescriptionsItem label="需求周期">{{ periodText }}</DescriptionsItem>
            <DescriptionsItem label="最后修改">{{ formatShanghai(requirement.updatedAt) }}</DescriptionsItem>
            <DescriptionsItem v-if="requirement.saveType === 'SUBMITTED'" label="当前处理人">
              <Space wrap>
                <span>{{ requirement.assigneeName || '未指派' }}</span>
                <Button type="link" size="small" data-test="assign-requirement" @click="openAssigneeModal">指派</Button>
              </Space>
            </DescriptionsItem>
            <DescriptionsItem v-if="requirement.completedAt" label="完成时间">{{ formatShanghai(requirement.completedAt) }}</DescriptionsItem>
            <DescriptionsItem v-if="requirement.handledBy" label="处理人">{{ requirement.handledBy }}</DescriptionsItem>
          </Descriptions>

          <section class="detail-section" data-test="progress-timeline-section">
            <div class="detail-section-heading">
              <h2>进展记录</h2>
              <Button type="primary" data-test="open-progress-update" @click="openProgressUpdate">更新进度</Button>
            </div>
            <Timeline v-if="progresses.length" class="progress-timeline">
              <TimelineItem v-for="progress in progresses" :key="progress.id">
                <div class="progress-meta">
                  <strong>{{ progress.authorName }}</strong>
                  <span>{{ formatShanghaiDateTime(progress.createdAt) }}</span>
                </div>
                <p class="progress-content">{{ progress.content }}</p>
                <Tag v-if="progress.status" :color="requirementStatusMeta(progress.status).color">状态更新为 {{ requirementStatusMeta(progress.status).label }}</Tag>
              </TimelineItem>
            </Timeline>
            <p v-else class="detail-empty">暂无进展记录</p>
          </section>

          <section v-if="requirement.completionDescription" class="detail-section">
            <h2>完成情况</h2>
            <p class="detail-content">{{ requirement.completionDescription }}</p>
          </section>
          <section class="detail-section">
            <h2>附件</h2>
            <ul v-if="attachments.length" class="attachment-list">
              <li v-for="attachment in attachments" :key="attachment.id">
                <div class="attachment-row">
                  <span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span>
                  <span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span>
                  <Space wrap>
                    <Button v-if="canPreview(attachment)" type="link" :data-test="`attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</Button>
                    <Button v-else-if="attachment.previewStatus === 'FAILED'" type="link" :data-test="`attachment-retry-preview-${attachment.id}`" @click="retryAttachmentPreview(attachment)">重新生成</Button>
                    <a :data-test="`attachment-download-${attachment.id}`" :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a>
                  </Space>
                </div>
                <img v-if="attachment.contentType.startsWith('image/')" :data-test="`attachment-preview-${attachment.id}`" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`">
              </li>
            </ul>
            <p v-else class="detail-empty">无附件</p>
          </section>
        </template>
      </Card>
    </Spin>

    <Modal v-model:open="previewOpen" :title="previewAttachment?.originalName" :footer="null" destroy-on-close :get-container="false" width="1100px" wrap-class-name="attachment-preview-modal">
      <section v-if="previewAttachment" class="attachment-preview-dialog" data-test="attachment-preview-dialog">
        <p v-if="!previewAttachment.contentType.startsWith('image/')" class="preview-disclaimer">在线预览由系统生成，版式可能与原文件略有差异，请以下载的原文件为准。</p>
        <img v-if="previewAttachment.previewContentType?.startsWith('image/') || previewAttachment.contentType.startsWith('image/')" :src="`/api/attachments/${previewAttachment.id}/preview`" :alt="`${previewAttachment.originalName} 在线预览`">
        <iframe v-else data-test="attachment-preview-frame" :src="`/api/attachments/${previewAttachment.id}/preview`" :title="`${previewAttachment.originalName} 在线预览`"></iframe>
        <div class="form-actions"><a data-test="attachment-preview-download" :href="`/api/attachments/${previewAttachment.id}`" :download="previewAttachment.originalName">下载原文件</a></div>
      </section>
    </Modal>

    <Modal v-model:open="progressModalOpen" title="更新进度" :footer="null" :mask-closable="false" destroy-on-close :get-container="false" @cancel="progressModalOpen = false">
      <Form class="progress-update-form" layout="vertical" :model="progressForm" @submit.prevent="saveProgressUpdate">
        <FormItem label="当前状态"><Tag :color="statusColor">{{ statusLabel }}</Tag></FormItem>
        <FormItem label="更新后状态（可选）">
          <Select v-model:value="progressForm.status" data-test="progress-status" allow-clear placeholder="不修改状态" :options="requirementStatusOptions" />
        </FormItem>
        <FormItem label="进展内容" required>
          <Input.TextArea v-model:value="progressForm.content" data-test="progress-content" :rows="6" required placeholder="请输入当前进展、处理结果或补充说明" />
        </FormItem>
        <div class="progress-form-actions">
          <Space>
            <Button html-type="button" @click="progressModalOpen = false">取消</Button>
            <Button type="primary" html-type="submit" data-test="save-progress" :loading="progressLoading">提交进度</Button>
          </Space>
        </div>
      </Form>
    </Modal>

    <Modal v-model:open="assigneeModalOpen" title="指派处理人" :confirm-loading="assigneeLoading" ok-text="保存" cancel-text="取消" @ok="saveAssignee">
      <Form layout="vertical">
        <FormItem label="当前处理人">
          <Select v-model:value="assigneeUserId" data-test="assignee-select" allow-clear placeholder="暂不指派" :options="users.map((user) => ({ value: user.id, label: user.displayName }))" />
        </FormItem>
      </Form>
    </Modal>
  </section>
</template>

<style scoped>
.requirement-detail-page {
  min-width: 0;
}

.requirement-detail-card :deep(.ant-card-head-title) {
  min-width: 0;
}

.detail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.detail-title {
  overflow: hidden;
  color: rgba(0, 0, 0, 0.88);
  font-size: 17px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-section {
  margin-top: 24px;
}

.detail-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.detail-core-fields {
  display: grid;
  gap: 20px;
  margin-bottom: 20px;
}

.detail-type-field {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.detail-field-label {
  color: rgba(0, 0, 0, 0.88);
  font-weight: 600;
}

.detail-content-field {
  padding: 16px 18px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fafafa;
}

.detail-content-field h2 {
  margin-bottom: 10px;
}

.detail-section h2 {
  margin: 0 0 10px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 15px;
  font-weight: 600;
}

.progress-timeline {
  margin-top: 16px;
}

.progress-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: rgba(0, 0, 0, 0.88);
}

.progress-meta span {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.progress-content {
  margin: 6px 0 8px;
  color: rgba(0, 0, 0, 0.72);
  white-space: pre-wrap;
}

.progress-form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.detail-content,
.detail-empty {
  margin: 0;
  color: rgba(0, 0, 0, 0.72);
  white-space: pre-wrap;
}

@media (max-width: 640px) {
  .detail-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-title {
    max-width: 100%;
  }

  .detail-section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
