<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { Badge, Button, ConfigProvider, Layout, LayoutContent, LayoutHeader, LayoutSider, Menu, MenuItem, Spin, message } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { useRoute, useRouter } from 'vue-router'

dayjs.locale('zh-cn')
import { AppstoreOutlined, AuditOutlined, BellOutlined, DashboardOutlined, DatabaseOutlined, FileTextOutlined, LogoutOutlined, RobotOutlined, SettingOutlined, TagsOutlined, TeamOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { themeConfig } from './theme'
import { onUnauthorized } from './api'
import { useAuth } from './composables/useAuth'
import { useCreateFormState } from './composables/useCreateFormState'
import { useNotifications } from './composables/useNotifications'
import LoginPage from './components/LoginPage.vue'
import PageContainer from './components/PageContainer.vue'

type PageKey = 'dashboard' | 'create' | 'list' | 'management' | 'systems' | 'versions' | 'ai-config' | 'users' | 'dictionaries'

interface PageDefinition {
  key: PageKey
  routeName: string
  label: string
  description: string
  /** 仅管理员可见。路由守卫和后端接口同样会校验角色。 */
  adminOnly?: boolean
}

const pages: PageDefinition[] = [
  { key: 'dashboard', routeName: 'dashboard', label: '待办工作台', description: '查看当前账号负责和协助处理的系统需求。' },
  { key: 'create', routeName: 'requirement-create', label: '填写需求', description: '填写、暂存或正式保存系统需求。' },
  { key: 'list', routeName: 'requirement-list', label: '需求列表', description: '查看、筛选、编辑和删除全部需求。' },
  { key: 'management', routeName: 'requirement-management', label: '管理需求', description: '跟进待处理需求和暂存草稿。' },
  { key: 'systems', routeName: 'system-management', label: '系统管理', description: '维护系统、负责人和协助人。' },
  { key: 'versions', routeName: 'version-management', label: '版本管理', description: '维护各系统的版本信息。' },
  { key: 'users', routeName: 'user-management', label: '人员管理', description: '维护账号、重置密码和启停人员。', adminOnly: true },
  { key: 'dictionaries', routeName: 'dictionary-management', label: '字典管理', description: '维护部门和需求类型，停用项保留历史记录。', adminOnly: true },
  { key: 'ai-config', routeName: 'ai-config', label: 'AI 配置', description: '配置 AI 智能分析服务连接信息。', adminOnly: true },
]

const router = useRouter()
const route = useRoute()
const { currentUser, initializing, isLoggedIn, isAdmin, logout, clearSession } = useAuth()
const { createFormDirty, setCreateFormDirty } = useCreateFormState()
const { unreadCount, refreshUnreadCount, clearUnreadCount } = useNotifications()

const visiblePages = computed(() => pages.filter((page) => !page.adminOnly || isAdmin.value))
const activePage = computed<PageKey>(() => {
  const pageKey = route.meta.pageKey
  return pages.some((page) => page.key === pageKey) ? pageKey as PageKey : 'dashboard'
})
const active = computed(() => visiblePages.value.find((page) => page.key === activePage.value) ?? visiblePages.value[0])
const pageTitle = computed(() => typeof route.meta.title === 'string' ? route.meta.title : active.value.label)
const pageDescription = computed(() => typeof route.meta.description === 'string' ? route.meta.description : active.value.description)
const cachedViews = ['RequirementList', 'RequirementManagement', 'SystemManagement', 'VersionManagement']

// 浏览器前进/后退和所有菜单跳转共用这一层离开确认，不让未保存内容被路由切换覆盖。
const removeCreateFormGuard = router.beforeEach((to, from) => {
  if (from.name === 'requirement-create' && to.name !== 'requirement-create' && createFormDirty.value) {
    return window.confirm('当前内容尚未保存，确定离开填写页吗？')
  }
  return true
})

let notificationRefreshTimer: ReturnType<typeof window.setInterval> | undefined

const stopNotificationRefresh = () => {
  if (notificationRefreshTimer !== undefined) {
    window.clearInterval(notificationRefreshTimer)
    notificationRefreshTimer = undefined
  }
}

watch(isLoggedIn, (loggedIn) => {
  stopNotificationRefresh()
  if (!loggedIn) {
    clearUnreadCount()
    return
  }
  void refreshUnreadCount()
  notificationRefreshTimer = window.setInterval(() => void refreshUnreadCount(), 60_000)
}, { immediate: true })

onBeforeUnmount(() => {
  removeCreateFormGuard()
  stopNotificationRefresh()
})

// 令牌失效时回到登录页，同时把未保存标记清掉，避免离开确认框阻断安全跳转。
onUnauthorized(() => {
  if (!isLoggedIn.value) return
  const redirect = route.meta.requiresAuth ? route.fullPath : undefined
  clearSession()
  setCreateFormDirty(false)
  message.warning('登录状态已失效，请重新登录')
  void router.replace({ name: 'login', query: redirect ? { redirect } : undefined })
})

const handleLogout = async () => {
  if (createFormDirty.value && !window.confirm('当前内容尚未保存，确定退出登录吗？')) return
  setCreateFormDirty(false)
  await logout()
  await router.replace({ name: 'login' })
}

const handleMenuClick = ({ key }: { key: string | number }) => {
  const page = visiblePages.value.find((item) => item.key === key)
  if (page) void router.push({ name: page.routeName })
}

const openNotificationCenter = () => {
  void router.push({ name: 'notification-center' })
}
</script>

<template>
  <ConfigProvider :theme="themeConfig" :locale="zhCN">
    <div v-if="initializing" class="app-loading">
      <Spin size="large" tip="正在加载…" />
    </div>
    <template v-else>
      <!-- 登录组件保持挂载，登录成功切换到主界面时可保留首次改密所需的初始密码。 -->
      <LoginPage />
      <Layout v-if="isLoggedIn" class="app-shell ant-app-shell" data-test="ant-layout">
        <LayoutHeader class="pro-header ant-layout-header">
        <div class="brand ant-brand">
          <AppstoreOutlined class="brand-icon" />
          <span class="brand-title">需求分析平台</span>
        </div>
        <div class="app-user-box">
          <span class="app-user-name" data-test="current-user">{{ currentUser?.displayName }}</span>
          <span class="app-user-role">{{ isAdmin ? '管理员' : '普通用户' }}</span>
          <Button type="text" class="notification-bell" data-test="notification-bell" title="站内消息" aria-label="站内消息" @click="openNotificationCenter">
            <Badge :count="unreadCount" :overflow-count="99" :show-zero="false">
              <BellOutlined />
            </Badge>
          </Button>
          <Button type="text" data-test="logout" @click="handleLogout">
            <template #icon><LogoutOutlined /></template>
            退出
          </Button>
        </div>
        </LayoutHeader>
        <Layout class="pro-body-layout">
        <LayoutSider class="sidebar ant-sidebar" :width="200" theme="light">
          <nav aria-label="主菜单">
            <Menu theme="light" mode="inline" :selected-keys="[activePage]" @click="handleMenuClick">
              <MenuItem v-for="page in visiblePages" :key="page.key" :data-test="`nav-${page.key}`">
                <template #icon>
                  <DashboardOutlined v-if="page.key === 'dashboard'" />
                  <FileTextOutlined v-else-if="page.key === 'create'" />
                  <UnorderedListOutlined v-else-if="page.key === 'list'" />
                  <AuditOutlined v-else-if="page.key === 'management'" />
                  <SettingOutlined v-else-if="page.key === 'systems'" />
                  <TagsOutlined v-else-if="page.key === 'versions'" />
                  <TeamOutlined v-else-if="page.key === 'users'" />
                  <DatabaseOutlined v-else-if="page.key === 'dictionaries'" />
                  <RobotOutlined v-else />
                </template>
                {{ page.label }}
              </MenuItem>
            </Menu>
          </nav>
        </LayoutSider>
        <LayoutContent class="main-content ant-main-content">
          <PageContainer :title="pageTitle" :description="pageDescription">
            <RouterView v-slot="{ Component }">
              <KeepAlive :include="cachedViews">
                <component :is="Component" />
              </KeepAlive>
            </RouterView>
          </PageContainer>
        </LayoutContent>
        </Layout>
      </Layout>
    </template>
  </ConfigProvider>
</template>
