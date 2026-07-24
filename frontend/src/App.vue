<script setup lang="ts">
import { computed, ref } from 'vue'
import { ConfigProvider, Layout, LayoutContent, LayoutHeader, LayoutSider, Menu, MenuItem } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

dayjs.locale('zh-cn')
import { AppstoreOutlined, AuditOutlined, DashboardOutlined, FileTextOutlined, RobotOutlined, SettingOutlined, TagsOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { themeConfig } from './theme'
import PageContainer from './components/PageContainer.vue'
import Dashboard from './components/Dashboard.vue'
import AiConfigPage from './components/AiConfigPage.vue'
import RequirementForm from './components/RequirementForm.vue'
import RequirementList from './components/RequirementList.vue'
import RequirementManagement from './components/RequirementManagement.vue'
import SystemManagement from './components/SystemManagement.vue'
import VersionManagement from './components/VersionManagement.vue'

type PageKey = 'dashboard' | 'create' | 'list' | 'management' | 'systems' | 'versions' | 'ai-config'

const pages: Array<{ key: PageKey; label: string; description: string }> = [
  { key: 'dashboard', label: '数据看板', description: '总览需求提交与处理状态。' },
  { key: 'create', label: '填写需求', description: '填写、暂存或正式保存系统需求。' },
  { key: 'list', label: '需求列表', description: '查看、筛选、编辑和删除全部需求。' },
  { key: 'management', label: '管理需求', description: '跟进待处理需求和暂存草稿。' },
  { key: 'systems', label: '系统管理', description: '维护系统、负责人和协助人。' },
  { key: 'versions', label: '版本管理', description: '维护各系统的版本信息。' },
  { key: 'ai-config', label: 'AI 配置', description: '配置 AI 智能分析服务连接信息。' },
]

const activePage = ref<PageKey>('dashboard')
const createFormDirty = ref(false)
const listPresetSystemId = ref<number | null>(null)
const listPresetRequestKey = ref(0)
const listPresetFilter = ref<{ saveType?: string; status?: string } | null>(null)
const active = computed(() => pages.find((page) => page.key === activePage.value)!)

const clearListPreset = () => {
  listPresetSystemId.value = null
  listPresetFilter.value = null
}

const navigateTo = (page: PageKey, skipGuard = false, preserveListPreset = false) => {
  if (page === activePage.value) return
  if (!skipGuard && activePage.value === 'create' && createFormDirty.value && page !== 'create') {
    if (!window.confirm('当前内容尚未保存，确定离开填写页吗？')) return
  }
  if (!preserveListPreset) clearListPreset()
  activePage.value = page
}

const handleMenuClick = ({ key }: { key: string | number }) => {
  if (typeof key === 'string') navigateTo(key as PageKey)
}

const handleCreateSubmitted = () => {
  createFormDirty.value = false
  navigateTo('list', true)
}

const handleViewSystemRequirements = (systemId: number) => {
  listPresetSystemId.value = systemId
  listPresetRequestKey.value += 1
  navigateTo('list', false, true)
}

const handleDashboardNavigate = (filter: { saveType?: string; status?: string }) => {
  listPresetFilter.value = filter
  listPresetRequestKey.value += 1
  navigateTo('list', true, true)
}
</script>

<template>
  <ConfigProvider :theme="themeConfig" :locale="zhCN">
    <Layout class="app-shell ant-app-shell" data-test="ant-layout">
      <LayoutHeader class="pro-header ant-layout-header">
        <div class="brand ant-brand">
          <AppstoreOutlined class="brand-icon" />
          <span class="brand-title">需求分析平台</span>
        </div>
      </LayoutHeader>
      <Layout class="pro-body-layout">
        <LayoutSider class="sidebar ant-sidebar" :width="200" theme="light">
          <nav aria-label="主菜单">
            <Menu theme="light" mode="inline" :selected-keys="[activePage]" @click="handleMenuClick">
              <MenuItem v-for="page in pages" :key="page.key" :data-test="`nav-${page.key}`">
                <template #icon>
                  <DashboardOutlined v-if="page.key === 'dashboard'" />
                  <FileTextOutlined v-else-if="page.key === 'create'" />
                  <UnorderedListOutlined v-else-if="page.key === 'list'" />
                  <AuditOutlined v-else-if="page.key === 'management'" />
                  <SettingOutlined v-else-if="page.key === 'systems'" />
                  <TagsOutlined v-else-if="page.key === 'versions'" />
                  <RobotOutlined v-else />
                </template>
                {{ page.label }}
              </MenuItem>
            </Menu>
          </nav>
        </LayoutSider>
        <LayoutContent class="main-content ant-main-content">
          <PageContainer :title="active.label" :description="active.description">
            <Dashboard
              v-if="activePage === 'dashboard'"
              @navigate="handleDashboardNavigate"
            />
            <RequirementForm
              v-else-if="activePage === 'create'"
              @dirty-change="createFormDirty = $event"
              @submitted="handleCreateSubmitted"
            />
            <KeepAlive>
              <RequirementList
                v-if="activePage === 'list'"
                :preset-system-id="listPresetSystemId"
                :preset-request-key="listPresetRequestKey"
                :preset-filter="listPresetFilter"
              />
              <RequirementManagement v-else-if="activePage === 'management'" />
              <SystemManagement
                v-else-if="activePage === 'systems'"
                @view-requirements="handleViewSystemRequirements"
              />
              <VersionManagement v-else-if="activePage === 'versions'" />
            </KeepAlive>
            <AiConfigPage v-if="activePage === 'ai-config'" />
          </PageContainer>
        </LayoutContent>
      </Layout>
    </Layout>
  </ConfigProvider>
</template>
