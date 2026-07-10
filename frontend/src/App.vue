<script setup lang="ts">
import { computed, ref } from 'vue'
import RequirementForm from './components/RequirementForm.vue'
import RequirementList from './components/RequirementList.vue'

type PageKey = 'create' | 'list' | 'systems'

const pages: Array<{ key: PageKey; label: string; description: string }> = [
  { key: 'create', label: '填写需求', description: '填写、暂存或正式保存系统需求。' },
  { key: 'list', label: '需求列表', description: '查看、筛选和管理已提交的需求。' },
  { key: 'systems', label: '系统管理', description: '维护系统、负责人、协助人和版本。' },
]

const activePage = ref<PageKey>('create')
const active = computed(() => pages.find((page) => page.key === activePage.value)!)
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
          @click="activePage = page.key"
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
        <RequirementForm v-if="activePage === 'create'" />
        <RequirementList v-else-if="activePage === 'list'" />
        <p v-else>{{ active.description }}</p>
      </section>
    </main>
  </div>
</template>
