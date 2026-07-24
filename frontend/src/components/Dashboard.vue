<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Card, Col, Row, Statistic, Tag } from 'ant-design-vue'
import { api } from '../api'
import { subscribeToRefresh } from '../composables/refreshBus'
import { requirementStatusMeta } from '../constants/statusConfig'

const emit = defineEmits<{
  navigate: [filter: { saveType?: string; status?: string }]
}>()

interface DashboardSummary {
  total: number
  draftCount: number
  statusCounts: Record<string, number>
}

const loading = ref(false)
const summary = ref<DashboardSummary>({ total: 0, draftCount: 0, statusCounts: {} })

const loadSummary = async () => {
  loading.value = true
  try {
    const { data } = await api.get('/dashboard/summary')
    summary.value = data
  } catch {
    // silently ignore
  } finally {
    loading.value = false
  }
}

const unsubscribe = subscribeToRefresh('dashboard', loadSummary)

onMounted(loadSummary)
onBeforeUnmount(unsubscribe)

const statusEntries = ref<Array<{ key: string; count: number }>>([])

const refreshStatusEntries = () => {
  statusEntries.value = Object.entries(summary.value.statusCounts).map(([key, count]) => ({ key, count }))
}

// Watch summary changes
import { watch } from 'vue'
watch(summary, refreshStatusEntries, { immediate: true, deep: true })
</script>

<template>
  <section class="dashboard-shell">
    <Row :gutter="[16, 16]">
      <Col :xs="12" :sm="8" :md="6">
        <Card class="stat-card" hoverable :loading="loading" @click="emit('navigate', { saveType: 'SUBMITTED' })">
          <Statistic title="已提交需求" :value="summary.total" />
        </Card>
      </Col>
      <Col :xs="12" :sm="8" :md="6">
        <Card class="stat-card" hoverable :loading="loading" @click="emit('navigate', { saveType: 'DRAFT' })">
          <Statistic title="草稿" :value="summary.draftCount" />
        </Card>
      </Col>
    </Row>

    <h3 class="section-title">处理状态</h3>
    <Row :gutter="[16, 16]">
      <Col v-for="entry in statusEntries" :key="entry.key" :xs="12" :sm="8" :md="6">
        <Card class="stat-card" hoverable :loading="loading" @click="emit('navigate', { status: entry.key })">
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

.stat-card {
  cursor: pointer;
  transition: box-shadow 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 4px 16px rgb(15 23 42 / 12%);
}

.section-title {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}
</style>
