<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { Alert, Button, Card, Col, Form, FormItem, Input, Modal, Pagination, Row, Select, Space, Table, Tag, message } from 'ant-design-vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta, saveTypeMeta } from '../constants/statusConfig'
import { urgencyMeta } from '../constants/urgencyConfig'
import { markChanged } from '../composables/refreshBus'
import { useDictionaryOptions } from '../composables/useDictionaryOptions'
import { usePageRefresh } from '../composables/usePageRefresh'

type SystemItem = { id: number; name: string; status: string }
type VersionItem = { id: number; name: string; status: string }
type Item = {
  id: number; title: string; typeId: number | null; type: string | null; requesterName: string | null; departmentId: number | null; department: string | null; status: string | null
  submittedAt: string | null; systemId: number | null; targetVersionId: number | null; targetVersionName?: string | null; periodStartDate: string | null; periodEndDate: string | null
  completedAt?: string | null; handledBy?: string | null; completionDescription?: string | null
  content?: string | null; urgency?: string | null; saveType?: string; updatedAt?: string | null; statusUpdatedAt?: string | null; recordVersion?: number
}

const filters = reactive({
  keyword: '', systemId: '', targetVersionId: '', departmentId: '', requesterName: '', typeId: '', status: '', saveType: '',
  scope: 'unfinished',
})
const routeFilterKeys = ['keyword', 'systemId', 'targetVersionId', 'departmentId', 'requesterName', 'typeId', 'status', 'saveType', 'scope'] as const
const router = useRouter()
const route = useRoute()
let syncingRouteFilters = false
const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const items = ref<Item[]>([])
const total = ref(0)
const totalPages = ref(0)
const currentPage = ref(0)
const pageSize = 20
const loading = ref(true)
const sortBy = ref('')
const sortDirection = ref<'asc' | 'desc'>('asc')
const { departments, requirementTypes, loadDictionaryOptions } = useDictionaryOptions()

const systemFilterOptions = computed(() => [
  { label: '全部系统', value: '' },
  { label: '暂无系统', value: 'none' },
  ...systems.value.map((system) => ({ label: system.name, value: String(system.id) })),
])
const versionFilterOptions = computed(() => [
  { label: '全部版本', value: '' },
  ...versions.value.map((version) => ({ label: version.name, value: String(version.id) })),
])
const departmentFilterOptions = computed(() => [
  { label: '全部部门', value: '' },
  ...departments.value.map((department) => ({ label: department.name, value: String(department.id) })),
])
const typeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...requirementTypes.value.map((type) => ({ label: type.name, value: String(type.id) })),
])
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
const requirementColumns = computed(() => [
  { title: '类型', key: 'type', width: 100 },
  { title: '需求标题', key: 'title', width: 260 },
  { title: '所属系统 / 版本', key: 'systemVersion', width: 190 },
  { title: '填写人 / 部门', key: 'requester', width: 150 },
  { title: '周期', key: 'period', width: 210 },
  { title: '紧急程度', key: 'urgency', width: 120, sorter: true, sortOrder: sortBy.value === 'urgency' ? (sortDirection.value === 'asc' ? 'ascend' : 'descend') : null },
  { title: '状态', key: 'status', width: 120 },
  { title: '填写时间', key: 'submittedAt', width: 175 },
  { title: '操作', key: 'actions', fixed: 'right' as const, width: 200 },
] satisfies TableColumnsType<Item>)
const tableRecord = (record: Record<string, unknown>) => record as Item
const systemName = (id: number | null) => id === null ? '暂无系统' : systems.value.find((system) => system.id === id)?.name ?? `系统 #${id}`
const versionName = (id: number | null, name?: string | null) => id === null ? '—' : name ?? versions.value.find((version) => version.id === id)?.name ?? `版本 #${id}`
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const match = value.match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] ?? value.slice(0, 10)
}
const contentSummary = (content: string | null | undefined) => {
  const normalized = content?.replace(/\s+/g, ' ').trim() ?? ''
  return normalized.length > 50 ? `${normalized.slice(0, 50)}...` : normalized
}
const loadSystems = async () => {
  try {
    const { data } = await api.get('/systems')
    systems.value = Array.isArray(data) ? data : []
  } catch {
    message.warning('系统筛选项加载失败')
  }
}

const loadVersions = async (clearSelectedVersion = true) => {
  if (clearSelectedVersion) filters.targetVersionId = ''
  versions.value = []
  if (!filters.systemId || filters.systemId === 'none') return
  try {
    const { data } = await api.get(`/systems/${filters.systemId}/versions`)
    versions.value = Array.isArray(data) ? data : []
  } catch {
    message.warning('版本筛选项加载失败')
  }
}

watch(() => filters.systemId, () => {
  if (!syncingRouteFilters) void loadVersions()
})

const queryParams = (page: number) => {
  const params: Record<string, string | number | boolean> = { page, size: pageSize }
  if (filters.systemId === 'none') params.unassignedSystem = true
  else if (filters.systemId) params.systemId = Number(filters.systemId)
  if (filters.targetVersionId) params.targetVersionId = Number(filters.targetVersionId)
  if (filters.departmentId) params.departmentId = Number(filters.departmentId)
  if (filters.typeId) params.typeId = Number(filters.typeId)
  if (filters.scope === 'unfinished') params.unfinishedOnly = true
  if (sortBy.value === 'urgency') {
    params.sortBy = sortBy.value
    params.sortDirection = sortDirection.value
  }
  for (const key of ['keyword', 'requesterName', 'status', 'saveType'] as const) {
    if (filters[key]) params[key] = filters[key]
  }
  return params
}

const queryRequirements = async (page = 0) => {
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

const routeQueryForFilters = (page: number) => {
  const query: Record<string, string> = {}
  for (const key of routeFilterKeys) {
    if (filters[key]) query[key] = filters[key]
  }
  if (sortBy.value === 'urgency') {
    query.sortBy = sortBy.value
    query.sortDirection = sortDirection.value
  }
  if (page > 0) query.page = String(page + 1)
  return query
}

const querySignature = (query: Record<string, unknown>) => JSON.stringify(
  Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => [key, Array.isArray(value) ? value.join(',') : String(value)])
    .sort(([left], [right]) => left.localeCompare(right)),
)

const applyFilters = async (page = 0) => {
  const query = routeQueryForFilters(page)
  if (route.name === 'requirement-list' && querySignature(route.query) === querySignature(query)) {
    await queryRequirements(page)
    return
  }
  await router.push({ name: 'requirement-list', query })
}

const reset = () => {
  Object.assign(filters, { keyword: '', systemId: '', targetVersionId: '', departmentId: '', requesterName: '', typeId: '', status: '', saveType: '', scope: 'unfinished' })
  sortBy.value = ''
  versions.value = []
  void applyFilters()
}

const setScope = (scope: 'unfinished' | 'all') => {
  filters.scope = scope
  if (scope === 'unfinished' && ['COMPLETED', 'REJECTED', 'CLOSED'].includes(filters.status)) {
    filters.status = ''
  }
  void applyFilters()
}

const onStatusChange = (status: unknown) => {
  if (typeof status === 'string' && ['COMPLETED', 'REJECTED', 'CLOSED'].includes(status)) filters.scope = 'all'
}

const handleTableChange = (_pagination: unknown, _filters: unknown, sorter: { field?: unknown; order?: unknown } | Array<{ field?: unknown; order?: unknown }>) => {
  const currentSorter = Array.isArray(sorter) ? sorter[0] : sorter
  if (currentSorter?.field !== 'urgency') return
  if (currentSorter.order === 'ascend') {
    sortBy.value = 'urgency'
    sortDirection.value = 'asc'
  } else if (currentSorter.order === 'descend') {
    sortBy.value = 'urgency'
    sortDirection.value = 'desc'
  } else {
    sortBy.value = ''
  }
  void applyFilters()
}

const viewDetails = (id: number) => {
  void router.push({ name: 'requirement-detail', params: { id } })
}

const deleteRequirement = (item: Item) => {
  Modal.confirm({
    title: '删除需求',
    content: `确定删除需求“${item.title || '未命名草稿'}”吗？`,
    okText: '删除',
    cancelText: '取消',
    onOk: async () => {
      try {
    await api.delete(`/requirements/${item.id}`)
    message.success('需求已删除')
    await queryRequirements(currentPage.value)
    markChanged(['requirements', 'dashboard'])
      } catch (error: unknown) {
        message.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除需求失败，请稍后重试')
      }
    },
  })
}

const editDetails = (id: number) => {
  void router.push({ name: 'requirement-detail', params: { id }, query: { edit: '1' } })
}

const routeQueryValue = (value: unknown) => Array.isArray(value) ? value[0] ?? '' : typeof value === 'string' ? value : ''
const defaultScopeForRoute = () => {
  const status = routeQueryValue(route.query.status)
  const saveType = routeQueryValue(route.query.saveType)
  return ['COMPLETED', 'REJECTED', 'CLOSED'].includes(status) || saveType === 'DRAFT' ? 'all' : 'unfinished'
}
const routePage = () => {
  const page = Number(routeQueryValue(route.query.page))
  return Number.isSafeInteger(page) && page > 0 ? page - 1 : 0
}

const syncFiltersFromRoute = async () => {
  if (route.name !== 'requirement-list') return
  if (!routeQueryValue(route.query.scope)) {
    await router.replace({ name: 'requirement-list', query: { ...route.query, scope: defaultScopeForRoute() } })
    return
  }
  syncingRouteFilters = true
  for (const key of routeFilterKeys) filters[key] = routeQueryValue(route.query[key])
  filters.scope = filters.scope === 'all' ? 'all' : 'unfinished'
  sortBy.value = routeQueryValue(route.query.sortBy) === 'urgency' ? 'urgency' : ''
  sortDirection.value = routeQueryValue(route.query.sortDirection) === 'desc' ? 'desc' : 'asc'
  await loadVersions(false)
  await nextTick()
  syncingRouteFilters = false
  await queryRequirements(routePage())

}

watch(() => route.fullPath, () => {
  if (route.name === 'requirement-list') void syncFiltersFromRoute()
})

usePageRefresh('requirements', async () => {
  await Promise.all([loadSystems(), loadDictionaryOptions()])
  await syncFiltersFromRoute()
})
</script>

<template>
  <section class="list-page">
    <Card class="filter-card" :bordered="false">
      <Alert class="requirement-scope-alert" type="info" show-icon>
        <template #message>{{ filters.scope === 'unfinished' ? '当前只看未完结需求' : '当前显示全部需求' }}</template>
        <template #action><Button type="link" size="small" @click="setScope(filters.scope === 'unfinished' ? 'all' : 'unfinished')">{{ filters.scope === 'unfinished' ? '查看全部' : '只看未完结' }}</Button></template>
      </Alert>
      <p class="filter-optional-hint" data-test="filter-optional-hint">筛选条件均为选填</p>
      <Form layout="vertical" class="filter-form">
        <Row :gutter="[16, 4]">
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="关键词"><Input v-model:value="filters.keyword" placeholder="标题或需求内容" allow-clear /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="所属系统"><Select v-model:value="filters.systemId" data-test="system-filter" :options="systemFilterOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="目标版本"><Select v-model:value="filters.targetVersionId" :options="versionFilterOptions" :disabled="!filters.systemId || filters.systemId === 'none'" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="部门"><Select v-model:value="filters.departmentId" data-test="department-filter" allow-clear placeholder="全部部门" :options="departmentFilterOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="填写人"><Input v-model:value="filters.requesterName" placeholder="请输入填写人" allow-clear /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="需求状态"><Select v-model:value="filters.status" :options="statusOptions" @change="onStatusChange" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="类型"><Select v-model:value="filters.typeId" :options="typeOptions" /></FormItem></Col>
          <Col :xs="24" :sm="12" :lg="6"><FormItem label="保存类型"><Select v-model:value="filters.saveType" :options="saveTypeOptions" /></FormItem></Col>
        </Row>
        <div class="filter-actions"><Space><Button type="primary" data-test="query" @click="applyFilters()">查询</Button><Button @click="reset">重置</Button></Space></div>
      </Form>
    </Card>
    <Card class="list-table-card" :bordered="false">
      <Table class="requirement-table" :columns="requirementColumns" :data-source="items" :loading="loading" :pagination="false" :scroll="{ x: 1495 }" :row-key="(item: Item) => item.id" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'"><Tag>{{ record.type || '—' }}</Tag></template>
          <template v-else-if="column.key === 'title'"><div class="requirement-title-cell"><RouterLink class="requirement-title-link" :to="{ name: 'requirement-detail', params: { id: tableRecord(record).id } }">{{ record.title || '未命名草稿' }}</RouterLink><span v-if="tableRecord(record).content" class="requirement-content-summary" :title="tableRecord(record).content ?? ''">{{ contentSummary(tableRecord(record).content) }}</span></div></template>
          <template v-else-if="column.key === 'systemVersion'">{{ systemName(record.systemId) }} / {{ versionName(record.targetVersionId, record.targetVersionName) }}</template>
          <template v-else-if="column.key === 'requester'">{{ record.requesterName || '—' }} / {{ record.department || '—' }}</template>
          <template v-else-if="column.key === 'period'">{{ record.periodStartDate && record.periodEndDate ? `${record.periodStartDate} 至 ${record.periodEndDate}` : '—' }}</template>
          <template v-else-if="column.key === 'urgency'"><Tag :color="urgencyMeta(record.urgency).color">{{ urgencyMeta(record.urgency).label }}</Tag></template>
          <template v-else-if="column.key === 'status'"><Tag :color="record.status ? requirementStatusMeta(record.status).color : saveTypeMeta(record.saveType ?? 'DRAFT').color">{{ record.status ? requirementStatusMeta(record.status).label : saveTypeMeta(record.saveType ?? 'DRAFT').label }}</Tag></template>
          <template v-else-if="column.key === 'submittedAt'">{{ formatShanghai(record.submittedAt || record.updatedAt) }}</template>
          <template v-else-if="column.key === 'actions'"><Space size="small"><Button type="link" size="small" :data-test="`view-${record.id}`" @click="viewDetails(tableRecord(record).id)">查看详情</Button><Button type="link" size="small" :data-test="`edit-${record.id}`" @click="editDetails(tableRecord(record).id)">编辑</Button><Button danger type="link" size="small" :data-test="`delete-${record.id}`" @click="deleteRequirement(tableRecord(record))">删除</Button></Space></template>
        </template>
        <template #emptyText>{{ loading ? '加载中…' : '暂无需求' }}</template>
      </Table>
      <div class="pagination-bar"><span>共 {{ total }} 条</span><Pagination :current="currentPage + 1" :total="total" :page-size="pageSize" :show-size-changer="false" :disabled="loading" @change="(page) => applyFilters(page - 1)" /></div>
    </Card>
  </section>
</template>

<style scoped>
.requirement-scope-alert {
  margin-bottom: 14px;
}

.requirement-title-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.requirement-content-summary {
  overflow: hidden;
  color: rgba(0, 0, 0, 0.55);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
