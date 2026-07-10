<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

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

const submitting = ref(false)
const systems = ref<Array<{ id: number; name: string; status: string }>>([])
onMounted(async () => { try { systems.value = (await api.get('/systems')).data.filter((system: { status: string }) => system.status === 'ACTIVE') } catch { ElMessage.warning('系统列表加载失败，可选择新系统或稍后重试') } })
const requestBody = () => ({
  requesterName: form.requesterName, department: form.department, title: form.title, type: form.type,
  content: form.content, periodStartDate: form.periodStartDate || null, periodEndDate: form.periodEndDate || null,
  systemId: systemMode.value === 'existing' && form.systemId ? Number(form.systemId) : null,
  targetVersionId: form.targetVersionId ? Number(form.targetVersionId) : null,
  newSystem: systemMode.value === 'new' ? { name: form.newSystemName, ownerName: form.newSystemOwnerName, collaborators: form.newSystemCollaborators.split(',').map((item) => item.trim()).filter(Boolean) } : null,
})
const submit = async (draft: boolean) => {
  submitting.value = true
  try {
    const body = requestBody()
    await api.post(draft ? '/requirements/drafts' : '/requirements', body)
    ElMessage.success(draft ? '暂存成功' : '保存成功')
  } catch {
    ElMessage.error('保存失败，请检查填写内容后重试')
  } finally { submitting.value = false }
}
</script>

<template>
  <form class="requirement-form" @submit.prevent="submit(false)">
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
        <label>已有系统 <select v-model="form.systemId"><option value="">请选择系统</option><option v-for="system in systems" :key="system.id" :value="system.id">{{ system.name }}</option></select></label>
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
      <button class="secondary" type="button" :disabled="submitting" @click="submit(true)">暂存</button>
      <button class="primary" type="submit" :disabled="submitting">保存需求</button>
    </div>
  </form>
</template>
