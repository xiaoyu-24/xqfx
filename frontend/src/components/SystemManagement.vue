<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

type SystemStatus = 'ACTIVE' | 'INACTIVE'
type SystemItem = {
  id: number
  name: string
  ownerName: string
  collaborators: string[]
  status: SystemStatus
  recordVersion: number
  versionCount: number
  requirementCount: number
}

const emit = defineEmits<{
  'view-requirements': [systemId: number]
}>()

const systems = ref<SystemItem[]>([])
const loading = ref(true)
const nameFilter = ref('')
const ownerFilter = ref('')
const collaboratorFilter = ref('')
const statusFilter = ref('')
const systemFormMode = ref<'create' | 'edit' | null>(null)
const systemForm = reactive({ id: 0, name: '', ownerName: '', collaborators: '', recordVersion: 0 })
const migrationSourceId = ref<number | null>(null)
const migrationTargetId = ref<number | null>(null)

const filteredSystems = computed(() => {
  const normalizedName = nameFilter.value.trim().toLowerCase()
  const normalizedOwner = ownerFilter.value.trim().toLowerCase()
  const normalizedCollaborator = collaboratorFilter.value.trim().toLowerCase()

  return systems.value.filter((system) => {
    const matchesName = !normalizedName || system.name.toLowerCase().includes(normalizedName)
    const matchesOwner = !normalizedOwner || system.ownerName.toLowerCase().includes(normalizedOwner)
    const matchesCollaborator = !normalizedCollaborator || system.collaborators
      .some((value) => value.toLowerCase().includes(normalizedCollaborator))
    const matchesStatus = !statusFilter.value || system.status === statusFilter.value
    return matchesName && matchesOwner && matchesCollaborator && matchesStatus
  })
})
const migrationTargets = computed(() => systems.value.filter((system) => system.id !== migrationSourceId.value && system.status === 'ACTIVE'))

const loadSystems = async () => {
  loading.value = true
  try {
    systems.value = (await api.get('/systems')).data
  } catch {
    ElMessage.error('加载系统列表失败')
  } finally {
    loading.value = false
  }
}

const showCreateSystem = () => {
  Object.assign(systemForm, { id: 0, name: '', ownerName: '', collaborators: '', recordVersion: 0 })
  systemFormMode.value = 'create'
}

const showEditSystem = (system: SystemItem) => {
  Object.assign(systemForm, { id: system.id, name: system.name, ownerName: system.ownerName, collaborators: system.collaborators.join(', '), recordVersion: system.recordVersion })
  systemFormMode.value = 'edit'
}

const collaboratorList = () => systemForm.collaborators.split(',').map((item) => item.trim()).filter(Boolean)

const saveSystem = async () => {
  const collaborators = collaboratorList()
  if (!systemForm.name.trim() || !systemForm.ownerName.trim()) {
    ElMessage.warning('请填写系统名称和负责人')
    return
  }
  if (new Set(collaborators).size !== collaborators.length) {
    ElMessage.warning('同一系统的协助人不能重复')
    return
  }
  const body = { name: systemForm.name.trim(), ownerName: systemForm.ownerName.trim(), collaborators }
  try {
    if (systemFormMode.value === 'create') {
      await api.post('/systems', body)
      ElMessage.success('新增系统成功')
    } else {
      await api.put(`/systems/${systemForm.id}`, { ...body, recordVersion: systemForm.recordVersion })
      ElMessage.success('系统信息已更新')
    }
    systemFormMode.value = null
    await loadSystems()
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) ElMessage.error('系统已被其他人修改，请刷新后重试')
    else ElMessage.error('保存系统失败，请检查名称是否重复')
  }
}

const toggleSystem = async (system: SystemItem) => {
  const status: SystemStatus = system.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/systems/${system.id}/status`, { status, recordVersion: system.recordVersion })
    ElMessage.success(status === 'ACTIVE' ? '系统已启用' : '系统已停用')
    await loadSystems()
  } catch {
    ElMessage.error('更新系统状态失败')
  }
}

const deleteSystem = async (system: SystemItem) => {
  if (!window.confirm(`确定删除系统“${system.name}”吗？`)) return
  try {
    await api.delete(`/systems/${system.id}`)
    ElMessage.success('系统已删除')
    await loadSystems()
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) {
      migrationSourceId.value = system.id
      migrationTargetId.value = null
      ElMessage.warning('该系统存在关联需求，请先迁移需求')
    } else {
      ElMessage.error('删除系统失败')
    }
  }
}

const migrateRequirements = async () => {
  if (migrationSourceId.value === null) return
  try {
    const { data } = await api.post(`/systems/${migrationSourceId.value}/migrate`, { targetSystemId: migrationTargetId.value })
    ElMessage.success(`已迁移 ${data.migratedCount} 条需求`)
    migrationSourceId.value = null
    await loadSystems()
  } catch {
    ElMessage.error('迁移需求失败，请检查目标系统状态')
  }
}

const viewRequirements = (systemId: number) => {
  emit('view-requirements', systemId)
}

onMounted(loadSystems)
</script>

<template>
  <section class="system-page">
    <p class="filter-optional-hint">筛选条件均为选填</p>
    <div class="system-toolbar">
      <input v-model="nameFilter" data-test="name-filter" placeholder="按系统名称筛选">
      <input v-model="ownerFilter" data-test="owner-filter" placeholder="按负责人筛选">
      <input v-model="collaboratorFilter" data-test="collaborator-filter" placeholder="按协助人筛选">
      <select v-model="statusFilter" data-test="status-filter">
        <option value="">全部状态</option>
        <option value="ACTIVE">启用</option>
        <option value="INACTIVE">停用</option>
      </select>
      <button class="primary" type="button" @click="showCreateSystem">新增系统</button>
    </div>

    <div class="table-wrap">
      <table>
        <thead><tr><th>系统名称</th><th>负责人</th><th>协助人</th><th>状态</th><th>版本数</th><th>关联需求数</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="system in filteredSystems" :key="system.id">
            <td>{{ system.name }}</td><td>{{ system.ownerName }}</td><td>{{ system.collaborators.join('、') || '—' }}</td><td>{{ system.status === 'ACTIVE' ? '启用' : '停用' }}</td><td>{{ system.versionCount }}</td><td>{{ system.requirementCount }}</td>
            <td class="row-actions">
              <button type="button" :data-test="`edit-system-${system.id}`" @click="showEditSystem(system)">编辑</button>
              <button type="button" :data-test="`view-requirements-${system.id}`" @click="viewRequirements(system.id)">查看需求</button>
              <button type="button" :data-test="`toggle-system-${system.id}`" @click="toggleSystem(system)">{{ system.status === 'ACTIVE' ? '停用' : '启用' }}</button>
              <button type="button" class="danger" @click="deleteSystem(system)">删除</button>
            </td>
          </tr>
          <tr v-if="loading"><td colspan="7" class="empty">加载中…</td></tr><tr v-else-if="filteredSystems.length === 0"><td colspan="7" class="empty">暂无系统数据，请新增系统</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 新增/编辑系统弹窗 -->
    <div v-if="systemFormMode" class="modal-overlay" @keydown.esc="systemFormMode = null">
      <form class="modal-dialog" data-test="system-modal" @submit.prevent="saveSystem">
        <div class="modal-scroll">
        <div class="modal-header">
          <h3>{{ systemFormMode === 'create' ? '新增系统' : '编辑系统' }}</h3>
          <button class="modal-close" type="button" @click="systemFormMode = null">&times;</button>
        </div>
        <div class="modal-body">
          <p class="field-requirement-legend" data-test="system-field-legend">带 <span class="field-required">*</span> 的项目为必填项，选填项目可根据实际情况填写。</p>
          <label data-test="system-name-field">系统名称 <span class="field-required">* 必填</span><input v-model="systemForm.name" data-test="system-name" required></label>
          <label>负责人 <span class="field-required">* 必填</span><input v-model="systemForm.ownerName" data-test="system-owner" required></label>
          <label data-test="system-collaborators-field">协助人 <span class="field-optional">选填</span><input v-model="systemForm.collaborators" data-test="system-collaborators" placeholder="多人用逗号分隔"></label>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" type="button" @click="systemFormMode = null">取消</button>
          <button class="btn-primary" type="submit">保存</button>
        </div>
        </div>
      </form>
    </div>

    <!-- 迁移关联需求弹窗 -->
    <div v-if="migrationSourceId !== null" class="modal-overlay" @keydown.esc="migrationSourceId = null">
      <form class="modal-dialog" data-test="migration-modal" @submit.prevent="migrateRequirements">
        <div class="modal-scroll">
        <div class="modal-header">
          <h3>迁移关联需求</h3>
          <button class="modal-close" type="button" @click="migrationSourceId = null">&times;</button>
        </div>
        <div class="modal-body">
          <label>迁移目标 <span class="field-required">* 必填</span>
            <select v-model="migrationTargetId"><option :value="null">暂无系统</option><option v-for="system in migrationTargets" :key="system.id" :value="system.id">{{ system.name }}</option></select>
          </label>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" type="button" @click="migrationSourceId = null">取消</button>
          <button class="btn-primary" type="submit">确认迁移</button>
        </div>
        </div>
      </form>
    </div>
  </section>
</template>
