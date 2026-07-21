<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

type SystemStatus = 'ACTIVE' | 'INACTIVE'
type SystemItem = { id: number; name: string; status: SystemStatus }
type VersionItem = { id: number; systemId: number; name: string; status: SystemStatus; requirementCount: number }

const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const selectedSystemId = ref<number | null>(null)
const versionFormMode = ref<'create' | 'edit' | null>(null)
const versionForm = reactive({ id: 0, name: '' })

const selectedSystem = computed(() => systems.value.find((item) => item.id === selectedSystemId.value) ?? null)

const loadSystems = async () => {
  try {
    systems.value = (await api.get('/systems')).data
  } catch {
    ElMessage.error('加载系统列表失败')
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

const onSystemChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  if (value) {
    loadVersions(Number(value))
  } else {
    selectedSystemId.value = null
    versions.value = []
    versionFormMode.value = null
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
  if (!window.confirm(`确定删除版本"${version.name}"吗？`)) return
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
  <section class="version-management-page">
    <div class="system-toolbar version-toolbar">
      <label>选择系统
        <select data-test="version-system-select" @change="onSystemChange">
          <option value="">请选择系统</option>
          <option v-for="system in systems" :key="system.id" :value="system.id">{{ system.name }}</option>
        </select>
      </label>
      <button v-if="selectedSystem" class="primary" type="button" @click="showCreateVersion">新增版本</button>
    </div>

    <div v-if="!selectedSystem" class="version-empty-hint">
      <p>请选择一个系统后维护其版本。</p>
    </div>

    <div v-else class="table-wrap">
      <table>
        <thead><tr><th>版本名称</th><th>状态</th><th>关联需求数</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="version in versions" :key="version.id">
            <td>{{ version.name }}</td>
            <td>{{ version.status === 'ACTIVE' ? '启用' : '停用' }}</td>
            <td>{{ version.requirementCount }} 条需求</td>
            <td class="row-actions">
              <button type="button" @click="showEditVersion(version)">编辑</button>
              <button type="button" @click="toggleVersion(version)">{{ version.status === 'ACTIVE' ? '停用' : '启用' }}</button>
              <button class="danger" type="button" @click="deleteVersion(version)">删除</button>
            </td>
          </tr>
          <tr v-if="versions.length === 0"><td colspan="4" class="empty">暂无版本，请新增。</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 新增/编辑版本弹窗 -->
    <div v-if="versionFormMode" class="modal-overlay" @keydown.esc="versionFormMode = null">
      <form class="modal-dialog" data-test="version-modal" @submit.prevent="saveVersion">
        <div class="modal-scroll">
        <div class="modal-header">
          <h3>{{ versionFormMode === 'create' ? '新增版本' : '编辑版本' }}</h3>
          <button class="modal-close" type="button" @click="versionFormMode = null">&times;</button>
        </div>
        <div class="modal-body">
          <label>版本名称 <span class="field-required">* 必填</span><input v-model="versionForm.name" data-test="version-name" required placeholder="请输入版本名称"></label>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" type="button" @click="versionFormMode = null">取消</button>
          <button class="btn-primary" type="submit">保存</button>
        </div>
        </div>
      </form>
    </div>
  </section>
</template>
