import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import AiConfigPage from '../components/AiConfigPage.vue'
import Dashboard from '../components/Dashboard.vue'
import DictionaryManagement from '../components/DictionaryManagement.vue'
import LoginPage from '../components/LoginPage.vue'
import NotificationCenter from '../components/NotificationCenter.vue'
import RequirementForm from '../components/RequirementForm.vue'
import RequirementDetail from '../components/RequirementDetail.vue'
import RequirementList from '../components/RequirementList.vue'
import SystemManagement from '../components/SystemManagement.vue'
import UserManagement from '../components/UserManagement.vue'
import VersionManagement from '../components/VersionManagement.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    guestOnly?: boolean
    pageKey?: string
    title?: string
    description?: string
  }
}

const protectedPage = (pageKey: string, title: string, description: string, requiresAdmin = false) => ({
  requiresAuth: true,
  requiresAdmin,
  pageKey,
  title,
  description,
})

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
    meta: { guestOnly: true },
  },
  {
    path: '/',
    name: 'dashboard',
    component: Dashboard,
    meta: protectedPage('dashboard', '待办工作台', '查看当前账号负责和协助处理的系统需求。'),
  },
  {
    path: '/requirements/new',
    name: 'requirement-create',
    component: RequirementForm,
    meta: protectedPage('create', '填写需求', '填写、暂存或正式保存系统需求。'),
  },
  {
    path: '/requirements',
    name: 'requirement-list',
    component: RequirementList,
    meta: protectedPage('list', '需求列表', '查看、筛选、编辑和删除全部需求。'),
  },
  {
    path: '/requirements/:id(\\d+)',
    name: 'requirement-detail',
    component: RequirementDetail,
    meta: protectedPage('list', '需求详情', '查看需求详情、附件，并可直接编辑。'),
  },
  {
    path: '/notifications',
    name: 'notification-center',
    component: NotificationCenter,
    meta: protectedPage('notifications', '站内消息', '查看需求动态与待处理提醒。'),
  },
  {
    path: '/systems',
    name: 'system-management',
    component: SystemManagement,
    meta: protectedPage('systems', '系统管理', '维护系统、负责人和协助人。'),
  },
  {
    path: '/versions',
    name: 'version-management',
    component: VersionManagement,
    meta: protectedPage('versions', '版本管理', '维护各系统的版本信息。'),
  },
  {
    path: '/admin/users',
    name: 'user-management',
    component: UserManagement,
    meta: protectedPage('users', '人员管理', '维护账号、重置密码和启停人员。', true),
  },
  {
    path: '/admin/dictionaries',
    name: 'dictionary-management',
    component: DictionaryManagement,
    meta: protectedPage('dictionaries', '字典管理', '维护部门和需求类型，停用项保留历史记录。', true),
  },
  {
    path: '/admin/ai-config',
    name: 'ai-config',
    component: AiConfigPage,
    meta: protectedPage('ai-config', 'AI 配置', '配置 AI 智能分析服务连接信息。', true),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: { name: 'dashboard' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const { initialize, isAdmin, isLoggedIn } = useAuth()
  await initialize()

  if (to.meta.guestOnly && isLoggedIn.value) return { name: 'dashboard' }

  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  if (to.meta.requiresAdmin && !isAdmin.value) return { name: 'dashboard' }

  return true
})

export default router
