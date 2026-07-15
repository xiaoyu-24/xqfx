<script setup lang="ts">
import { computed, ref } from 'vue'
import RequirementForm from './components/RequirementForm.vue'
import RequirementList from './components/RequirementList.vue'
import RequirementManagement from './components/RequirementManagement.vue'
import SystemManagement from './components/SystemManagement.vue'

type PageKey = 'create' | 'list' | 'management' | 'systems'

const pages: Array<{ key: PageKey; label: string; description: string }> = [
  { key: 'create', label: '填写需求', description: '填写、暂存或正式保存系统需求。' },
  { key: 'list', label: '需求列表', description: '查看、筛选、编辑和删除全部需求。' },
  { key: 'management', label: '管理需求', description: '跟进待处理需求和暂存草稿。' },
  { key: 'systems', label: '系统管理', description: '维护系统、负责人、协助人和版本。' },
]

const activePage = ref<PageKey>('create')
const lastNonCreatePage = ref<PageKey>('list')
const createFormDirty = ref(false)
const listPresetSystemId = ref<number | null>(null)
const listPresetRequestKey = ref(0)
const active = computed(() => pages.find((page) => page.key === activePage.value)!)

const clearListPreset = () => {
  listPresetSystemId.value = null
}

const navigateTo = (page: PageKey, skipGuard = false, preserveListPreset = false) => {
  if (page === activePage.value) return
  if (!skipGuard && activePage.value === 'create' && createFormDirty.value && page !== 'create') {
    if (!window.confirm('当前内容尚未保存，确定离开填写页吗？')) return
  }
  if (!preserveListPreset) clearListPreset()
  if (page !== 'create') lastNonCreatePage.value = page
  activePage.value = page
}

const handleCreateBack = () => {
  createFormDirty.value = false
  navigateTo(lastNonCreatePage.value, true)
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
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" aria-label="主菜单">
      <div class="brand">需求收集平台</div>
      <nav>
        <button
          v-for="page in pages"
          :key="page.key"
          class="nav-item"
          :class="{ active: activePage === page.key }"
          type="button"
          @click="navigateTo(page.key)"
        >
          {{ page.label }}
        </button>
      </nav>
    </aside>
    <main class="main-content">
      <header class="page-header">
        <h1>{{ active.label }}</h1>
      </header>
      <section class="page-placeholder">
        <RequirementForm
          v-if="activePage === 'create'"
          @back="handleCreateBack"
          @dirty-change="createFormDirty = $event"
          @submitted="handleCreateSubmitted"
        />
        <RequirementList
          v-else-if="activePage === 'list'"
          :preset-system-id="listPresetSystemId"
          :preset-request-key="listPresetRequestKey"
        />
        <RequirementManagement v-else-if="activePage === 'management'" />
        <SystemManagement v-else @view-requirements="handleViewSystemRequirements" />
      </section>
    </main>
  </div>
</template>
