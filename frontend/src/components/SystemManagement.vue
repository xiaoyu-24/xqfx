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
  versionCount: number
  requirementCount: number
}
type VersionItem = { id: number; systemId: number; name: string; status: SystemStatus; requirementCount: number }

const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const loading = ref(false)
const nameFilter = ref('')
const ownerFilter = ref('')
const collaboratorFilter = ref('')
const statusFilter = ref('')
const selectedSystemId = ref<number | null>(null)
const systemFormMode = ref<'create' | 'edit' | null>(null)
const systemForm = reactive({ id: 0, name: '', ownerName: '', collaborators: '' })
const versionFormMode = ref<'create' | 'edit' | null>(null)
const versionForm = reactive({ id: 0, name: '' })
const migrationSourceId = ref<number | null>(null)
const migrationTargetId = ref<number | null>(null)

const selectedSystem = computed(() => systems.value.find((item) => item.id === selectedSystemId.value) ?? null)
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

const loadVersions = async (systemId: number) => {
  selectedSystemId.value = systemId
  versionFormMode.value = null
  try {
    versions.value = (await api.get(`/systems/${systemId}/versions`)).data
  } catch {
    versions.value = []
    ElMessage.error('加载版本列表失败')
  }
}

const showCreateSystem = () => {
  Object.assign(systemForm, { id: 0, name: '', ownerName: '', collaborators: '' })
  systemFormMode.value = 'create'
}

const showEditSystem = (system: SystemItem) => {
  Object.assign(systemForm, { id: system.id, name: system.name, ownerName: system.ownerName, collaborators: system.collaborators.join(', ') })
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
      await api.put(`/systems/${systemForm.id}`, body)
      ElMessage.success('系统信息已更新')
    }
    systemFormMode.value = null
    await loadSystems()
  } catch {
    ElMessage.error('保存系统失败，请检查名称是否重复')
  }
}

const toggleSystem = async (system: SystemItem) => {
  const status: SystemStatus = system.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/systems/${system.id}/status`, { status })
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
    if (selectedSystemId.value === system.id) {
      selectedSystemId.value = null
      versions.value = []
    }
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

const showCreateVersion = () => {
  Object.assign(versionForm, { id: 0, name: '' })
  versionFormMode.value = 'create'
}

const showEditVersion = (version: VersionItem) => {
  Object.assign(versionForm, { id: version.id, name: version.name })
  versionFormMode.value = 'edit'
}

const saveVersion = async () => {
  if (selectedSystemId.value === null || !versionForm.name.trim()) {
    ElMessage.warning('请填写版本名称')
    return
  }
  try {
    if (versionFormMode.value === 'create') {
      await api.post(`/systems/${selectedSystemId.value}/versions`, { name: versionForm.name.trim() })
      ElMessage.success('新增版本成功')
    } else {
      await api.put(`/system-versions/${versionForm.id}`, { name: versionForm.name.trim() })
      ElMessage.success('版本名称已更新')
    }
    versionFormMode.value = null
    await loadVersions(selectedSystemId.value)
  } catch {
    ElMessage.error('保存版本失败')
  }
}

const toggleVersion = async (version: VersionItem) => {
  const status: SystemStatus = version.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/system-versions/${version.id}/status`, { status })
    ElMessage.success(status === 'ACTIVE' ? '版本已启用' : '版本已停用')
    if (selectedSystemId.value !== null) await loadVersions(selectedSystemId.value)
  } catch {
    ElMessage.error('更新版本状态失败')
  }
}

const deleteVersion = async (version: VersionItem) => {
  if (!window.confirm(`确定删除版本“${version.name}”吗？`)) return
  try {
    await api.delete(`/system-versions/${version.id}`)
    ElMessage.success('版本已删除')
    if (selectedSystemId.value !== null) await loadVersions(selectedSystemId.value)
  } catch {
    ElMessage.error('删除版本失败；若已关联需求，请先调整需求版本')
  }
}

onMounted(loadSystems)
</script>

<template>
  <section class="system-page">
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

    <form v-if="systemFormMode" class="management-form" @submit.prevent="saveSystem">
      <strong>{{ systemFormMode === 'create' ? '新增系统' : '编辑系统' }}</strong>
      <label>系统名称 <input v-model="systemForm.name" data-test="system-name" required></label>
      <label>负责人 <input v-model="systemForm.ownerName" data-test="system-owner" required></label>
      <label>协助人 <input v-model="systemForm.collaborators" data-test="system-collaborators" placeholder="多人用逗号分隔"></label>
      <div class="management-actions"><button class="primary" type="submit">保存</button><button class="secondary" type="button" @click="systemFormMode = null">取消</button></div>
    </form>

    <div class="table-wrap">
      <table>
        <thead><tr><th>系统名称</th><th>负责人</th><th>协助人</th><th>状态</th><th>版本数</th><th>关联需求数</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="system in filteredSystems" :key="system.id">
            <td>{{ system.name }}</td><td>{{ system.ownerName }}</td><td>{{ system.collaborators.join('、') || '—' }}</td><td>{{ system.status === 'ACTIVE' ? '启用' : '停用' }}</td><td>{{ system.versionCount }}</td><td>{{ system.requirementCount }}</td>
            <td class="row-actions">
              <button type="button" @click="showEditSystem(system)">编辑</button>
              <button type="button" :data-test="`versions-${system.id}`" @click="loadVersions(system.id)">版本管理</button>
              <button type="button" @click="toggleSystem(system)">{{ system.status === 'ACTIVE' ? '停用' : '启用' }}</button>
              <button type="button" class="danger" @click="deleteSystem(system)">删除</button>
            </td>
          </tr>
          <tr v-if="!loading && filteredSystems.length === 0"><td colspan="7" class="empty">暂无系统数据，请新增系统</td></tr>
        </tbody>
      </table>
    </div>

    <form v-if="migrationSourceId !== null" class="management-form migration-form" @submit.prevent="migrateRequirements">
      <strong>迁移关联需求</strong>
      <label>迁移目标
        <select v-model="migrationTargetId"><option :value="null">暂无系统</option><option v-for="system in migrationTargets" :key="system.id" :value="system.id">{{ system.name }}</option></select>
      </label>
      <div class="management-actions"><button class="primary" type="submit">确认迁移</button><button class="secondary" type="button" @click="migrationSourceId = null">取消</button></div>
    </form>

    <aside class="version-panel">
      <h2>版本管理<span v-if="selectedSystem">：{{ selectedSystem.name }}</span></h2>
      <p v-if="!selectedSystem">请选择一个系统后维护其版本。</p>
      <template v-else>
        <button class="secondary" type="button" @click="showCreateVersion">新增版本</button>
        <form v-if="versionFormMode" class="management-form" @submit.prevent="saveVersion">
          <strong>{{ versionFormMode === 'create' ? '新增版本' : '编辑版本' }}</strong>
          <label>版本名称 <input v-model="versionForm.name" required></label>
          <div class="management-actions"><button class="primary" type="submit">保存</button><button class="secondary" type="button" @click="versionFormMode = null">取消</button></div>
        </form>
        <div class="version-list"><div v-for="version in versions" :key="version.id" class="version-row"><span>{{ version.name }}</span><span>{{ version.status === 'ACTIVE' ? '启用' : '停用' }}</span><span>{{ version.requirementCount }} 条需求</span><span class="row-actions"><button type="button" @click="showEditVersion(version)">编辑</button><button type="button" @click="toggleVersion(version)">{{ version.status === 'ACTIVE' ? '停用' : '启用' }}</button><button class="danger" type="button" @click="deleteVersion(version)">删除</button></span></div><p v-if="versions.length === 0">暂无版本，请新增。</p></div>
      </template>
    </aside>
  </section>
</template>
