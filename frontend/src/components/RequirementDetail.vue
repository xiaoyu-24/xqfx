<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button, Card, Descriptions, DescriptionsItem, Modal, Result, Space, Spin, Tag, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta, saveTypeMeta } from '../constants/statusConfig'
import RequirementEditor from './RequirementEditor.vue'

type SystemItem = { id: number; name: string }
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
  content?: string | null
  saveType?: string
}

const route = useRoute()
const router = useRouter()
const requirement = ref<Requirement | null>(null)
const systems = ref<SystemItem[]>([])
const attachments = ref<Attachment[]>([])
const previewAttachment = ref<Attachment | null>(null)
const loading = ref(true)
const loadError = ref('')
const editing = ref(false)
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
    const [detail, attachmentList, systemList] = await Promise.all([
      api.get(`/requirements/${requirementId.value}`),
      api.get(`/requirements/${requirementId.value}/attachments`),
      api.get('/systems'),
    ])
    requirement.value = detail.data
    attachments.value = Array.isArray(attachmentList.data) ? attachmentList.data : []
    systems.value = Array.isArray(systemList.data) ? systemList.data : []
    schedulePreviewRefresh()
  } catch (error: unknown) {
    requirement.value = null
    attachments.value = []
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
const editRequirement = () => {
  editing.value = true
}
const finishEditing = () => {
  editing.value = false
  void loadRequirement()
}
const cancelEditing = () => {
  editing.value = false
  void loadRequirement()
}

watch(() => route.params.id, () => {
  editing.value = false
  void loadRequirement()
}, { immediate: true })
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
          <Descriptions bordered size="small" :column="1" class="detail-core-descriptions">
            <DescriptionsItem label="类型">{{ requirement.type || '—' }}</DescriptionsItem>
            <DescriptionsItem label="需求"><p class="detail-content">{{ requirement.content || '—' }}</p></DescriptionsItem>
          </Descriptions>

          <Descriptions bordered size="small" :column="2">
            <DescriptionsItem label="状态"><Tag :color="statusColor">{{ statusLabel }}</Tag></DescriptionsItem>
            <DescriptionsItem label="所属系统">{{ systemName }}</DescriptionsItem>
            <DescriptionsItem label="目标版本">{{ requirement.targetVersionId === null ? '—' : requirement.targetVersionName ?? `版本 #${requirement.targetVersionId}` }}</DescriptionsItem>
            <DescriptionsItem label="填写人 / 部门">{{ requirement.requesterName || '—' }} / {{ requirement.department || '—' }}</DescriptionsItem>
            <DescriptionsItem label="填写时间">{{ formatShanghai(requirement.submittedAt || requirement.updatedAt) }}</DescriptionsItem>
            <DescriptionsItem label="需求周期">{{ periodText }}</DescriptionsItem>
            <DescriptionsItem label="最后修改">{{ formatShanghai(requirement.updatedAt) }}</DescriptionsItem>
            <DescriptionsItem v-if="requirement.completedAt" label="完成时间">{{ formatShanghai(requirement.completedAt) }}</DescriptionsItem>
            <DescriptionsItem v-if="requirement.handledBy" label="处理人">{{ requirement.handledBy }}</DescriptionsItem>
          </Descriptions>

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

.detail-core-descriptions {
  margin-bottom: 16px;
}

.detail-section h2 {
  margin: 0 0 10px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 15px;
  font-weight: 600;
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
}
</style>
