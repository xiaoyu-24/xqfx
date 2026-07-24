<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { Button, Card, DatePicker, Form, FormItem, Input, Modal, Select, SelectOption, Table, Tag, Textarea, message } from 'ant-design-vue'
import { api } from '../api'
import { requirementStatusMeta, requirementStatusOptions, requirementTypeMeta } from '../constants/statusConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { usePageRefresh } from '../composables/usePageRefresh'

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
const processingModalOpen = ref(false)
const processingLoading = ref(false)
const processingForm = reactive({ status: 'PENDING_EVALUATION', completedAt: '', handledBy: '', completionDescription: '', recordVersion: 0 })
const pageSize = 20
const processingOpen = computed(() => processingModalOpen.value)

const tableColumns = [
  { title: '类型', key: 'type', width: 96 },
  { title: '需求标题', key: 'title', width: 220 },
  { title: '所属系统 / 版本', key: 'systemVersion', width: 190 },
  { title: '填写人 / 部门', key: 'requesterDepartment', width: 170 },
  { title: '当前状态', key: 'status', width: 120 },
  { title: '填写时间', key: 'submittedAt', width: 176 },
  { title: '最后修改', key: 'updatedAt', width: 176 },
  { title: '操作', key: 'actions', fixed: 'right', width: 140 },
] satisfies TableColumnsType<Item>
const tableRecord = (record: Record<string, unknown>) => record as Item
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (item: Item) => item.targetVersionId === null ? '—' : item.targetVersionName ?? `版本 #${item.targetVersionId}`
const displayStatus = (item: Item) => item.status ? requirementStatusMeta(item.status).label : '暂存草稿'
const displayType = (item: Item) => item.type ? requirementTypeMeta(item.type).label : '—'
const { handleError } = useApiError()
const tableLocale = computed(() => ({ emptyText: loading.value ? '加载中…' : '暂无待处理需求' }))
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const match = value.match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] ?? value.slice(0, 10)
}
const toDateTimeLocal = (value: string | null | undefined) => {
  const formatted = formatShanghai(value)
  return formatted === '—' ? '' : formatted
}

const loadSystems = async () => {
  try {
    const { data } = await api.get('/systems')
    systems.value = Array.isArray(data) ? data : []
  } catch {
    message.warning('系统信息加载失败')
  }
}

const query = async () => {
  loading.value = true
  try {
    const { data } = await api.get('/requirements/management', { params: { page: 0, size: pageSize } })
    items.value = data.content ?? []
    total.value = data.totalElements ?? 0
  } catch {
    message.error('加载管理需求失败')
  } finally {
    loading.value = false
  }
}

const openProcessing = async (item: Item) => {
  try {
    const { data } = await api.get(`/requirements/${item.id}`)
    processingId.value = item.id
    processingModalOpen.value = true
    Object.assign(processingForm, {
      status: data.status ?? 'PENDING_EVALUATION',
      completedAt: toDateTimeLocal(data.completedAt),
      handledBy: data.handledBy ?? '',
      completionDescription: data.completionDescription ?? '',
      recordVersion: data.recordVersion ?? item.recordVersion ?? 0,
    })
  } catch {
    message.error('加载处理信息失败')
  }
}

const saveProcessing = async () => {
  if (processingId.value === null) return
  processingLoading.value = true
  try {
    await api.patch(`/requirements/${processingId.value}/processing`, {
      status: processingForm.status,
      completedAt: processingForm.completedAt ? (processingForm.completedAt.includes('T') ? processingForm.completedAt : `${processingForm.completedAt}T00:00:00`) : null,
      handledBy: processingForm.handledBy.trim() || null,
      completionDescription: processingForm.completionDescription,
      recordVersion: processingForm.recordVersion,
    })
    closeProcessing()
    message.success('处理信息已保存')
    markChanged(['management', 'requirements', 'dashboard'])
    await query()
  } catch (error: unknown) {
    handleError(error, '保存处理信息失败')
  } finally {
    processingLoading.value = false
  }
}

const closeProcessing = () => {
  processingModalOpen.value = false
  processingId.value = null
}

usePageRefresh('management', async () => {
  await Promise.all([loadSystems(), query()])
}, { isPaused: () => processingOpen.value })
</script>

<template>
  <section class="management-section" data-test="management-page">
    <Card class="management-card" data-test="management-card" :bordered="false">
      <template #title>
        <div class="management-card-title">
          <span>待处理需求</span>
          <Tag color="blue">共 {{ total }} 条</Tag>
        </div>
      </template>

      <Table
        class="management-table"
        data-test="management-table"
        :columns="tableColumns"
        :data-source="items"
        :loading="loading"
        :locale="tableLocale"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 1280 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <Tag :color="record.type ? requirementTypeMeta(record.type).color : 'default'">{{ displayType(tableRecord(record)) }}</Tag>
          </template>
          <template v-else-if="column.key === 'title'">
            <span class="management-title">{{ record.title || '未命名草稿' }}</span>
          </template>
          <template v-else-if="column.key === 'systemVersion'">
            <div class="management-two-line-cell">
              <span>{{ systemName(record.systemId) }}</span>
              <span class="management-secondary">{{ versionName(tableRecord(record)) }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'requesterDepartment'">
            <div class="management-two-line-cell">
              <span>{{ record.requesterName || '—' }}</span>
              <span class="management-secondary">{{ record.department || '—' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status ? requirementStatusMeta(record.status).color : 'default'" :data-test="`management-status-${record.id}`">{{ displayStatus(tableRecord(record)) }}</Tag>
          </template>
          <template v-else-if="column.key === 'submittedAt'">{{ formatShanghai(record.submittedAt || record.updatedAt) }}</template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatShanghai(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <Button type="link" :data-test="`manage-${record.id}`" @click="openProcessing(tableRecord(record))">填写处理情况</Button>
          </template>
        </template>
      </Table>
    </Card>

    <Modal v-model:open="processingModalOpen" title="填写处理情况" :mask-closable="false" :footer="null" destroy-on-close :get-container="false" @cancel="closeProcessing">
      <div class="processing-modal-content" data-test="processing-modal">
      <Form class="processing-form" data-test="processing-form" layout="vertical" :model="processingForm" @submit.prevent="saveProcessing">
        <div class="processing-form-heading"><h3>填写需求完成情况</h3><Button type="text" html-type="button" @click="closeProcessing">关闭</Button></div>
        <p class="field-requirement-legend" data-test="processing-field-legend">带 <span class="field-required">*</span> 的项目为必填项，选填项目可根据实际情况填写。</p>
        <div class="processing-form-grid">
          <FormItem required data-test="processing-status-field" label="需求状态">
            <Select v-model:value="processingForm.status" data-test="processing-status" placeholder="请选择需求状态">
              <SelectOption v-for="option in requirementStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</SelectOption>
            </Select>
          </FormItem>
          <FormItem label="完成时间">
            <DatePicker v-model:value="processingForm.completedAt" data-test="processing-completed-at" value-format="YYYY-MM-DD" placeholder="选择完成时间" style="width: 100%" />
          </FormItem>
          <FormItem label="处理人">
            <Input v-model:value="processingForm.handledBy" data-test="processing-handler" placeholder="请输入处理人" />
          </FormItem>
          <FormItem class="processing-form-full-width" data-test="processing-description-field">
            <template #label>完成情况</template>
            <Textarea v-model:value="processingForm.completionDescription" data-test="processing-description" :rows="6" placeholder="请输入处理结果、验证情况等" />
          </FormItem>
        </div>
        <div class="processing-form-actions">
          <Button html-type="button" @click="closeProcessing">取消</Button>
          <Button type="primary" html-type="submit" data-test="processing-save" :loading="processingLoading">保存处理情况</Button>
        </div>
      </Form>
      </div>
    </Modal>
  </section>
</template>

<style scoped>
.management-section {
  min-width: 0;
}

.management-card :deep(.ant-card-head-title) {
  padding: 14px 0;
}

.management-card-title,
.processing-form-heading,
.processing-form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.management-card-title {
  font-size: 16px;
  font-weight: 600;
}

.management-card-hint,
.management-secondary {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.management-table :deep(.ant-table-cell) {
  vertical-align: middle;
}

.management-title {
  color: rgba(0, 0, 0, 0.88);
  font-weight: 500;
}

.management-two-line-cell {
  display: grid;
  gap: 3px;
}

.processing-modal-content {
  padding-top: 4px;
}

.processing-form-heading {
  justify-content: space-between;
  margin-bottom: 12px;
}

.processing-form-heading h3 {
  margin: 0;
  font-size: 16px;
}

.processing-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.processing-form-full-width {
  grid-column: 1 / -1;
}

.processing-form-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 720px) {
  .management-card-hint {
    display: none;
  }

  .processing-form-grid {
    grid-template-columns: 1fr;
  }

  .processing-form-full-width {
    grid-column: auto;
  }
}
</style>

