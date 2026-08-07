<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Card, Col, Empty, Row, Statistic, Tag } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { subscribeToRefresh } from '../composables/refreshBus'
import { requirementStatusMeta } from '../constants/statusConfig'

interface DashboardSummary {
  total: number
  draftCount: number
  statusCounts: Record<string, number>
}

interface WorkbenchItem {
  id: number
  title: string
  requesterName: string
  systemName: string
  status: string
  updatedAt: string
}

interface WorkbenchData {
  owned: WorkbenchItem[]
  assisting: WorkbenchItem[]
}

const router = useRouter()
const loading = ref(false)
const summary = ref<DashboardSummary>({ total: 0, draftCount: 0, statusCounts: {} })
const workbench = ref<WorkbenchData>({ owned: [], assisting: [] })
const statusEntries = computed(() => Object.entries(summary.value.statusCounts)
  .map(([key, count]) => ({ key, count })))

const loadDashboard = async () => {
  loading.value = true
  try {
    const [summaryResponse, workbenchResponse] = await Promise.all([
      api.get<DashboardSummary>('/dashboard/summary'),
      api.get<WorkbenchData>('/dashboard/workbench'),
    ])
    summary.value = summaryResponse.data
    workbench.value = workbenchResponse.data
  } catch {
    // 保留上一次成功加载的数据，避免刷新失败时页面闪空。
  } finally {
    loading.value = false
  }
}

const unsubscribe = subscribeToRefresh('dashboard', loadDashboard)

onMounted(loadDashboard)
onBeforeUnmount(unsubscribe)

const openRequirement = (id: number) => {
  void router.push({ name: 'requirement-detail', params: { id } })
}

const navigateToRequirements = (filter: { saveType?: string; status?: string }) => {
  void router.push({ name: 'requirement-list', query: filter })
}

const formatUpdatedAt = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}).format(new Date(value))
</script>

<template>
  <section class="dashboard-shell">
    <div class="workbench-grid">
      <Card class="workbench-card" :loading="loading" :bordered="false">
        <template #title>我负责的系统需求</template>
        <div v-if="workbench.owned.length" class="workbench-list">
          <button v-for="item in workbench.owned" :key="item.id" class="workbench-row" type="button" @click="openRequirement(item.id)">
            <span class="workbench-main">
              <span class="workbench-title">{{ item.title }}</span>
              <span class="workbench-meta">{{ item.systemName }} · 提出人：{{ item.requesterName }} · 更新于 {{ formatUpdatedAt(item.updatedAt) }}</span>
            </span>
            <Tag :color="requirementStatusMeta(item.status).color">{{ requirementStatusMeta(item.status).label }}</Tag>
          </button>
        </div>
        <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无待处理需求" />
      </Card>

      <Card class="workbench-card" :loading="loading" :bordered="false">
        <template #title>我协助处理的系统需求</template>
        <div v-if="workbench.assisting.length" class="workbench-list">
          <button v-for="item in workbench.assisting" :key="item.id" class="workbench-row" type="button" @click="openRequirement(item.id)">
            <span class="workbench-main">
              <span class="workbench-title">{{ item.title }}</span>
              <span class="workbench-meta">{{ item.systemName }} · 提出人：{{ item.requesterName }} · 更新于 {{ formatUpdatedAt(item.updatedAt) }}</span>
            </span>
            <Tag :color="requirementStatusMeta(item.status).color">{{ requirementStatusMeta(item.status).label }}</Tag>
          </button>
        </div>
        <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无协助处理需求" />
      </Card>
    </div>

    <h3 class="section-title">需求概览</h3>
    <Row :gutter="[16, 16]">
      <Col :xs="12" :sm="8" :md="6">
        <Card class="stat-card" hoverable :loading="loading" @click="navigateToRequirements({ saveType: 'SUBMITTED' })">
          <Statistic title="已提交需求" :value="summary.total" />
        </Card>
      </Col>
      <Col :xs="12" :sm="8" :md="6">
        <Card class="stat-card" hoverable :loading="loading" @click="navigateToRequirements({ saveType: 'DRAFT' })">
          <Statistic title="草稿" :value="summary.draftCount" />
        </Card>
      </Col>
      <Col v-for="entry in statusEntries" :key="entry.key" :xs="12" :sm="8" :md="6">
        <Card class="stat-card" hoverable :loading="loading" @click="navigateToRequirements({ status: entry.key })">
          <Statistic :value="entry.count">
            <template #title>
              <Tag :color="requirementStatusMeta(entry.key).color">{{ requirementStatusMeta(entry.key).label }}</Tag>
            </template>
          </Statistic>
        </Card>
      </Col>
    </Row>
  </section>
</template>

<style scoped>
.dashboard-shell {
  width: 100%;
}

.workbench-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.workbench-card {
  min-height: 260px;
}

.workbench-list {
  display: grid;
  max-height: 384px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
}

.workbench-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 64px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid rgb(241 245 249);
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.workbench-row:last-child {
  border-bottom: 0;
}

.workbench-row:hover .workbench-title {
  color: rgb(22 119 255);
}

.workbench-main {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.workbench-title,
.workbench-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-title {
  color: rgb(15 23 42);
  font-weight: 600;
}

.workbench-meta {
  color: rgb(100 116 139);
  font-size: 12px;
}

.section-title {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.stat-card {
  cursor: pointer;
  transition: box-shadow 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 4px 16px rgb(15 23 42 / 12%);
}

@media (max-width: 900px) {
  .workbench-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
