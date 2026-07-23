<script setup lang="ts">
/**
 * 统一页面头部容器：标题 + 描述 + 操作槽 + 内容区。
 *
 * 替代各页面"Card 起手"的散乱写法，与 Ant Design Pro 的 PageContainer 对齐。
 * 由 App.vue 在内容区统一包裹，各页面只负责内容（页面 Card 不再写标题）。
 */
import { Spin } from 'ant-design-vue'

defineProps<{
  title?: string
  description?: string
  loading?: boolean
}>()
</script>

<template>
  <div class="page-container">
    <div v-if="title || description || $slots.extra" class="page-header">
      <div class="page-header-left">
        <h1 v-if="title" class="page-title">{{ title }}</h1>
        <p v-if="description" class="page-description">{{ description }}</p>
      </div>
      <div v-if="$slots.extra" class="page-header-extra">
        <slot name="extra" />
      </div>
    </div>
    <Spin :spinning="loading" tip="加载中…">
      <div class="page-body">
        <slot />
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-header-left {
  min-width: 0;
  flex: 1;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  color: rgba(0, 0, 0, 0.88);
}

.page-description {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}

.page-header-extra {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-body {
  min-height: 0;
}

@media (max-width: 768px) {
  .page-title {
    font-size: 18px;
  }
  .page-header {
    gap: 12px;
  }
}
</style>
