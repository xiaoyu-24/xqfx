<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { Button, Card, Table, Tag, message } from 'ant-design-vue'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta } from '../constants/statusConfig'
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
}

const systems = ref<SystemItem[]>([])
const items = ref<Item[]>([])
const total = ref(0)
const loading = ref(true)
const pageSize = 20
const router = useRouter()

const tableColumns = [
  { title: '类型', key: 'type', width: 96 },
  { title: '需求标题', key: 'title', width: 220 },
  { title: '所属系统 / 版本', key: 'systemVersion', width: 190 },
  { title: '填写人 / 部门', key: 'requesterDepartment', width: 170 },
  { title: '当前状态', key: 'status', width: 120 },
  { title: '填写时间', key: 'submittedAt', width: 176 },
  { title: '最后修改', key: 'updatedAt', width: 176 },
  { title: '操作', key: 'actions', fixed: 'right', width: 120 },
] satisfies TableColumnsType<Item>
const tableRecord = (record: Record<string, unknown>) => record as Item
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (item: Item) => item.targetVersionId === null ? '—' : item.targetVersionName ?? `版本 #${item.targetVersionId}`
const displayStatus = (item: Item) => item.status ? requirementStatusMeta(item.status).label : '暂存草稿'
const displayType = (item: Item) => item.type ?? '—'
const tableLocale = computed(() => ({ emptyText: loading.value ? '加载中…' : '暂无待处理需求' }))
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

const updateProgress = (item: Item) => {
  void router.push({ name: 'requirement-detail', params: { id: item.id } })
}

usePageRefresh('management', async () => {
  await Promise.all([loadSystems(), query()])
})
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
        :scroll="{ x: 1260 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <Tag>{{ displayType(tableRecord(record)) }}</Tag>
          </template>
          <template v-else-if="column.key === 'title'">
            <RouterLink class="management-title requirement-title-link" :to="{ name: 'requirement-detail', params: { id: tableRecord(record).id } }">{{ record.title || '未命名草稿' }}</RouterLink>
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
            <Button type="link" :data-test="`manage-${record.id}`" @click="updateProgress(tableRecord(record))">更新进度</Button>
          </template>
        </template>
      </Table>
    </Card>
  </section>
</template>

<style scoped>
.management-section {
  min-width: 0;
}

.management-card :deep(.ant-card-head-title) {
  padding: 14px 0;
}

.management-card-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
}

.management-secondary {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.management-table :deep(.ant-table-cell) {
  vertical-align: middle;
}

.management-title {
  color: #1677ff;
  font-weight: 500;
}

.management-title:hover {
  color: #4096ff;
  text-decoration: underline;
}

.management-two-line-cell {
  display: grid;
  gap: 3px;
}
</style>
