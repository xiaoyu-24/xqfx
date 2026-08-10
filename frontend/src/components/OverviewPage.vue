<script setup lang="ts">
import { computed, ref } from 'vue'
import { Empty } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'
import { requirementStatusMeta } from '../constants/statusConfig'

type SystemCount = { systemId: number | null; systemName: string; count: number }
type OverviewData = {
  total: number
  draftCount: number
  unfinishedCount: number
  pendingEvaluationCount: number
  inProgressCount: number
  completedCount: number
  completionRate: number
  highUrgencyPendingCount: number
  systemCounts: SystemCount[]
  statusCounts: Record<string, number>
  urgencyCounts: Record<string, number>
}
type ChartSegment = { key: string; label: string; count: number; color: string; systemId?: number | null }

const circumference = 2 * Math.PI * 70
const systemColors = ['#1677ff', '#69b1ff', '#36cfc9', '#597ef7', '#73d13d', '#ffc53d', '#ff85c0', '#d9d9d9']
const statusColors: Record<string, string> = {
  PENDING_EVALUATION: '#faad14',
  CONFIRMED: '#1677ff',
  IN_DEVELOPMENT: '#9254de',
  PAUSED: '#fa8c16',
  COMPLETED: '#52c41a',
  REJECTED: '#ff4d4f',
  CLOSED: '#bfbfbf',
}
const urgencyMeta: Record<string, { label: string; color: string; gradient: string }> = {
  HIGH: { label: '高', color: '#ff4d4f', gradient: 'linear-gradient(90deg, #ff7875, #ff4d4f)' },
  MEDIUM: { label: '中', color: '#fa8c16', gradient: 'linear-gradient(90deg, #ffc53d, #fa8c16)' },
  LOW: { label: '低', color: '#69b1ff', gradient: 'linear-gradient(90deg, #91caff, #69b1ff)' },
}

const router = useRouter()
const overview = ref<OverviewData>({
  total: 0,
  draftCount: 0,
  unfinishedCount: 0,
  pendingEvaluationCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  completionRate: 0,
  highUrgencyPendingCount: 0,
  systemCounts: [],
  statusCounts: {},
  urgencyCounts: {},
})

const createDonutSegments = (segments: ChartSegment[], total: number) => {
  let offset = 0
  return segments.filter((segment) => segment.count > 0).map((segment) => {
    const length = total > 0 ? segment.count / total * circumference : 0
    const result = {
      ...segment,
      dashArray: `${Math.max(length - 2, 0)} ${circumference}`,
      dashOffset: -offset,
      percent: total > 0 ? Math.round(segment.count / total * 100) : 0,
    }
    offset += length
    return result
  })
}

const systemSegments = computed(() => createDonutSegments(
  overview.value.systemCounts.map((item, index) => ({
    key: String(item.systemId ?? 'unassigned'),
    label: item.systemName,
    count: item.count,
    color: systemColors[index % systemColors.length],
    systemId: item.systemId,
  })),
  overview.value.total,
))

const submittedTotal = computed(() => Object.values(overview.value.statusCounts)
  .reduce((total, count) => total + count, 0))
const statusSegments = computed(() => createDonutSegments(
  Object.entries(statusColors).map(([key, color]) => ({
    key,
    label: requirementStatusMeta(key).label,
    count: overview.value.statusCounts[key] ?? 0,
    color,
  })),
  submittedTotal.value,
))

const urgencySegments = computed(() => {
  const max = Math.max(...Object.values(overview.value.urgencyCounts), 0)
  return ['HIGH', 'MEDIUM', 'LOW'].map((key) => {
    const count = overview.value.urgencyCounts[key] ?? 0
    return {
      key,
      count,
      ...urgencyMeta[key],
      percent: overview.value.total > 0 ? Math.round(count / overview.value.total * 100) : 0,
      width: max > 0 ? Math.round(count / max * 100) : 0,
    }
  })
})

const loadOverview = async () => {
  const { data } = await api.get<OverviewData>('/dashboard/overview')
  overview.value = data
}

const { loaded } = usePageRefresh('overview', loadOverview)

const openSystemRequirements = (segment: { systemId?: number | null }) => {
  const query = segment.systemId == null
    ? { systemId: 'none', scope: 'all' }
    : { systemId: String(segment.systemId), scope: 'all' }
  void router.push({ name: 'requirement-list', query })
}
</script>

<template>
  <section class="overview-page">
    <ContentSkeleton v-if="!loaded" preset="cards" :rows="8" />
    <template v-else>
      <div class="kpi-grid">
        <section class="kpi-card total-kpi">
          <span class="kpi-label">需求总数</span>
          <strong>{{ overview.total }}</strong>
          <span class="kpi-foot">含草稿 {{ overview.draftCount }} 条</span>
        </section>
        <section class="kpi-card unfinished-kpi">
          <span class="kpi-label">未完结</span>
          <strong>{{ overview.unfinishedCount }}</strong>
          <span class="kpi-foot">待评估 {{ overview.pendingEvaluationCount }} · 处理中 {{ overview.inProgressCount }}</span>
        </section>
        <section class="kpi-card completed-kpi">
          <span class="kpi-label">已完成</span>
          <strong>{{ overview.completedCount }}</strong>
          <span class="kpi-foot">完结率 {{ overview.completionRate }}%</span>
        </section>
        <section class="kpi-card urgent-kpi">
          <span class="kpi-label">高紧急度待处理</span>
          <strong>{{ overview.highUrgencyPendingCount }}</strong>
          <span class="kpi-foot">需优先跟进</span>
        </section>
      </div>

      <div class="chart-grid">
        <section class="chart-card">
          <header class="chart-header">
            <h2>按系统分布</h2>
            <span>点击图例可跳转该系统需求列表</span>
          </header>
          <div v-if="systemSegments.length" class="donut-layout">
            <div class="donut-chart" aria-label="按系统分布环形图">
              <svg viewBox="0 0 190 190" role="img">
                <g transform="rotate(-90 95 95)">
                  <circle cx="95" cy="95" r="70" class="donut-track" />
                  <circle v-for="segment in systemSegments" :key="segment.key" cx="95" cy="95" r="70" class="donut-segment" :stroke="segment.color" :stroke-dasharray="segment.dashArray" :stroke-dashoffset="segment.dashOffset" />
                </g>
              </svg>
              <div class="donut-center"><strong>{{ overview.total }}</strong><span>全部需求</span></div>
            </div>
            <div class="chart-legend">
              <button v-for="segment in systemSegments" :key="segment.key" type="button" class="legend-row clickable" @click="openSystemRequirements(segment)">
                <i :style="{ backgroundColor: segment.color }"></i><span>{{ segment.label }}</span><b>{{ segment.count }}</b><em>{{ segment.percent }}%</em>
              </button>
            </div>
          </div>
          <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无需求数据" />
        </section>

        <section class="chart-card">
          <header class="chart-header"><h2>按状态分布</h2><span>含全部正式需求</span></header>
          <div v-if="statusSegments.length" class="donut-layout">
            <div class="donut-chart" aria-label="按状态分布环形图">
              <svg viewBox="0 0 190 190" role="img">
                <g transform="rotate(-90 95 95)">
                  <circle cx="95" cy="95" r="70" class="donut-track" />
                  <circle v-for="segment in statusSegments" :key="segment.key" cx="95" cy="95" r="70" class="donut-segment" :stroke="segment.color" :stroke-dasharray="segment.dashArray" :stroke-dashoffset="segment.dashOffset" />
                </g>
              </svg>
              <div class="donut-center"><strong>{{ submittedTotal }}</strong><span>正式需求</span></div>
            </div>
            <div class="chart-legend">
              <div v-for="segment in statusSegments" :key="segment.key" class="legend-row"><i :style="{ backgroundColor: segment.color }"></i><span>{{ segment.label }}</span><b>{{ segment.count }}</b><em>{{ segment.percent }}%</em></div>
            </div>
          </div>
          <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无正式需求" />
        </section>

        <section class="chart-card urgency-chart-card">
          <header class="chart-header"><h2>按紧急程度分布</h2><span>高紧急度需求建议优先处理</span></header>
          <div class="urgency-bars">
            <div v-for="segment in urgencySegments" :key="segment.key" class="urgency-row">
              <span class="urgency-label">{{ segment.label }}</span>
              <div class="urgency-track"><div class="urgency-fill" :style="{ width: `${segment.width}%`, background: segment.gradient }"><span v-if="segment.count">{{ segment.count }}</span></div></div>
              <span class="urgency-count"><b>{{ segment.count }}</b> 条 · {{ segment.percent }}%</span>
            </div>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.overview-page { width: 100%; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.kpi-card { position: relative; display: grid; gap: 6px; overflow: hidden; min-height: 130px; padding: 18px 20px; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.kpi-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--accent); content: ''; }
.total-kpi { --accent: #1677ff; }
.unfinished-kpi { --accent: #faad14; }
.completed-kpi { --accent: #52c41a; }
.urgent-kpi { --accent: #ff4d4f; }
.kpi-label, .kpi-foot { color: rgba(0, 0, 0, .45); font-size: 13px; }
.kpi-card strong { color: rgba(0, 0, 0, .88); font-size: 28px; font-variant-numeric: tabular-nums; line-height: 1.1; }
.kpi-foot { font-size: 12px; }

.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.chart-card { overflow: hidden; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.urgency-chart-card { grid-column: 1 / -1; }
.chart-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 52px; padding: 14px 20px; border-bottom: 1px solid #f0f0f0; background: linear-gradient(180deg, #fafcff, #fff); }
.chart-header h2 { display: flex; align-items: center; gap: 8px; margin: 0; color: rgba(0, 0, 0, .88); font-size: 15px; font-weight: 600; }
.chart-header h2::before { width: 3px; height: 14px; border-radius: 2px; background: #1677ff; content: ''; }
.chart-header span { color: rgba(0, 0, 0, .45); font-size: 12px; }

.donut-layout { display: flex; align-items: center; gap: 28px; min-height: 244px; padding: 22px 24px 26px; }
.donut-chart { position: relative; width: 190px; flex: 0 0 190px; }
.donut-chart svg { display: block; width: 190px; height: 190px; }
.donut-track, .donut-segment { fill: none; stroke-width: 26; }
.donut-track { stroke: #f3f5f7; }
.donut-segment { stroke-linecap: butt; }
.donut-center { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; }
.donut-center strong { color: rgba(0, 0, 0, .88); font-size: 26px; font-variant-numeric: tabular-nums; line-height: 1.1; }
.donut-center span { margin-top: 3px; color: rgba(0, 0, 0, .45); font-size: 12px; }

.chart-legend { display: grid; min-width: 0; flex: 1; gap: 9px; }
.legend-row { display: grid; grid-template-columns: 9px minmax(0, 1fr) auto 42px; align-items: center; gap: 8px; width: 100%; padding: 0; border: 0; background: transparent; color: inherit; text-align: left; }
.legend-row i { width: 9px; height: 9px; border-radius: 3px; }
.legend-row span { overflow: hidden; color: rgba(0, 0, 0, .65); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.legend-row b { color: rgba(0, 0, 0, .88); font-size: 13px; font-variant-numeric: tabular-nums; }
.legend-row em { color: rgba(0, 0, 0, .45); font-size: 12px; font-style: normal; text-align: right; }
.legend-row.clickable { cursor: pointer; }
.legend-row.clickable:hover span { color: #1677ff; }

.urgency-bars { display: grid; gap: 18px; padding: 20px 24px 24px; }
.urgency-row { display: flex; align-items: center; gap: 14px; }
.urgency-label { width: 64px; flex: 0 0 64px; color: rgba(0, 0, 0, .65); font-size: 13px; }
.urgency-track { height: 22px; flex: 1; overflow: hidden; border-radius: 6px; background: #f3f5f7; }
.urgency-fill { display: flex; height: 100%; min-width: 0; align-items: center; justify-content: flex-end; border-radius: 6px; color: #fff; font-size: 12px; font-weight: 600; transition: width .3s ease; }
.urgency-fill span { padding-right: 8px; }
.urgency-count { width: 78px; flex: 0 0 78px; color: rgba(0, 0, 0, .45); font-size: 13px; text-align: right; }
.urgency-count b { color: rgba(0, 0, 0, .88); font-size: 14px; font-variant-numeric: tabular-nums; }

@media (max-width: 1200px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .chart-grid { grid-template-columns: minmax(0, 1fr); }
  .urgency-chart-card { grid-column: auto; }
}

@media (max-width: 720px) {
  .kpi-grid { grid-template-columns: minmax(0, 1fr); }
  .donut-layout { align-items: center; flex-direction: column; }
  .chart-header { align-items: flex-start; flex-direction: column; }
}
</style>
