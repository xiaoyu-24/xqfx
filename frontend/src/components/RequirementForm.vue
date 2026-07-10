<script setup lang="ts">
import { reactive, ref } from 'vue'

type SystemMode = 'existing' | 'new' | 'none'

const systemMode = ref<SystemMode>('existing')
const form = reactive({
  requesterName: '',
  department: '',
  title: '',
  type: 'REQUIREMENT',
  periodStartDate: '',
  periodEndDate: '',
  systemId: '',
  targetVersionId: '',
  newSystemName: '',
  newSystemOwnerName: '',
  newSystemCollaborators: '',
  content: '',
})
</script>

<template>
  <form class="requirement-form" @submit.prevent>
    <div class="form-grid">
      <label>姓名 <input v-model="form.requesterName" required placeholder="请输入姓名"></label>
      <label>部门 <input v-model="form.department" required placeholder="请输入部门"></label>
      <label class="full-width">需求标题 <input v-model="form.title" required placeholder="请简要概括需求"></label>
      <fieldset>
        <legend>类型</legend>
        <label><input v-model="form.type" type="radio" value="BUG"> BUG</label>
        <label><input v-model="form.type" type="radio" value="REQUIREMENT"> 需求</label>
      </fieldset>
      <div>
        <span class="field-label">需求时间周期（上海时间）</span>
        <div class="date-range">
          <input v-model="form.periodStartDate" type="date" aria-label="开始日期">
          <span>至</span>
          <input v-model="form.periodEndDate" type="date" aria-label="结束日期">
        </div>
      </div>
      <label class="full-width">所属系统
        <select v-model="systemMode" data-test="system-mode">
          <option value="existing">选择已有系统</option>
          <option value="new">新系统</option>
          <option value="none">暂无系统</option>
        </select>
      </label>
      <template v-if="systemMode === 'existing'">
        <label>已有系统 <select v-model="form.systemId"><option value="">请选择系统</option></select></label>
        <label>目标版本 <select v-model="form.targetVersionId"><option value="">请选择版本（可选）</option></select></label>
      </template>
      <template v-else-if="systemMode === 'new'">
        <label>新系统名称 <input v-model="form.newSystemName" placeholder="请输入系统名称"></label>
        <label>新系统负责人 <input v-model="form.newSystemOwnerName" placeholder="请输入负责人姓名"></label>
        <label class="full-width">新系统协助人 <input v-model="form.newSystemCollaborators" placeholder="多人请用逗号分隔"></label>
      </template>
      <label class="full-width">需求内容
        <textarea v-model="form.content" required rows="10" placeholder="请描述背景、问题、期望结果和验收标准"></textarea>
      </label>
      <div class="full-width attachment-note">附件：支持图片、PDF、Word、Excel；上传功能将在服务端附件接口完成后启用。</div>
    </div>
    <div class="form-actions">
      <button class="secondary" type="button">暂存</button>
      <button class="primary" type="submit">保存需求</button>
    </div>
  </form>
</template>
