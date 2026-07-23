<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Button, Card, Empty, Form, FormItem, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'ant-design-vue'
import { api } from '../api'
import { entityStatusMeta } from '../constants/statusConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { usePageRefresh } from '../composables/usePageRefresh'

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

const { handleError } = useApiError()

const systems = ref<SystemItem[]>([])
const loading = ref(true)
const nameFilter = ref('')
const ownerFilter = ref('')
const collaboratorFilter = ref('')
const statusFilter = ref('')
const systemFormMode = ref<'create' | 'edit' | null>(null)
const systemForm = reactive({ id: 0, name: '', ownerName: '', collaborators: '', recordVersion: 0 })
const migrationSourceId = ref<number | null>(null)
const unassignedMigrationTarget = '__unassigned__'
const migrationTargetId = ref<number | typeof unassignedMigrationTarget>(unassignedMigrationTarget)
const systemColumns = [
  { title: '系统名称', dataIndex: 'name', key: 'name', minWidth: 160 },
  { title: '负责人', dataIndex: 'ownerName', key: 'ownerName', width: 120 },
  { title: '协助人', key: 'collaborators', minWidth: 160 },
  { title: '状态', key: 'status', width: 100 },
  { title: '版本数', dataIndex: 'versionCount', key: 'versionCount', width: 90 },
  { title: '关联需求数', dataIndex: 'requirementCount', key: 'requirementCount', width: 110 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' as const },
]
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '启用' },
  { value: 'INACTIVE', label: '停用' },
]

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
const systemFormOpen = computed({
  get: () => systemFormMode.value !== null,
  set: (open: boolean) => { if (!open) systemFormMode.value = null },
})
const migrationOpen = computed({
  get: () => migrationSourceId.value !== null,
  set: (open: boolean) => { if (!open) migrationSourceId.value = null },
})

const loadSystems = async () => {
  loading.value = true
  try {
    systems.value = (await api.get('/systems')).data
  } catch {
    message.error('加载系统列表失败')
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
    message.warning('请填写系统名称和负责人')
    return
  }
  if (new Set(collaborators).size !== collaborators.length) {
    message.warning('同一系统的协助人不能重复')
    return
  }
  const body = { name: systemForm.name.trim(), ownerName: systemForm.ownerName.trim(), collaborators }
  try {
    if (systemFormMode.value === 'create') {
      await api.post('/systems', body)
      message.success('新增系统成功')
    } else {
      await api.put(`/systems/${systemForm.id}`, { ...body, recordVersion: systemForm.recordVersion })
      message.success('系统信息已更新')
    }
    systemFormMode.value = null
    await loadSystems()
    markChanged(['requirements', 'versions'])
  } catch (error: unknown) {
    handleError(error, '保存系统失败，请检查名称是否重复')
  }
}

const toggleSystem = async (system: SystemItem) => {
  const status: SystemStatus = system.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/systems/${system.id}/status`, { status, recordVersion: system.recordVersion })
    message.success(status === 'ACTIVE' ? '系统已启用' : '系统已停用')
    await loadSystems()
    markChanged(['requirements', 'versions'])
  } catch {
    message.error('更新系统状态失败')
  }
}

const deleteSystem = async (system: SystemItem) => {
  try {
    await api.delete(`/systems/${system.id}`)
    message.success('系统已删除')
    await loadSystems()
    markChanged(['requirements', 'versions'])
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) {
      migrationSourceId.value = system.id
      migrationTargetId.value = unassignedMigrationTarget
      message.warning('该系统存在关联需求，请先迁移需求')
    } else {
      message.error('删除系统失败')
    }
  }
}

const migrateRequirements = async () => {
  if (migrationSourceId.value === null) return
  try {
    const targetSystemId = migrationTargetId.value === unassignedMigrationTarget ? null : migrationTargetId.value
    const { data } = await api.post(`/systems/${migrationSourceId.value}/migrate`, { targetSystemId })
    message.success(`已迁移 ${data.migratedCount} 条需求`)
    migrationSourceId.value = null
    await loadSystems()
    markChanged(['requirements', 'versions'])
  } catch {
    message.error('迁移需求失败，请检查目标系统状态')
  }
}

const viewRequirements = (systemId: number) => {
  emit('view-requirements', systemId)
}

usePageRefresh('systems', loadSystems)
</script>

<template>
  <section class="system-page" data-test="system-page">
    <Card :bordered="false">
      <div class="system-toolbar">
        <Space wrap :size="12">
          <Input v-model:value="nameFilter" data-test="name-filter" allow-clear placeholder="按系统名称筛选" style="width: 220px" />
          <Input v-model:value="ownerFilter" data-test="owner-filter" allow-clear placeholder="按负责人筛选" style="width: 200px" />
          <Input v-model:value="collaboratorFilter" data-test="collaborator-filter" allow-clear placeholder="按协助人筛选" style="width: 220px" />
          <Select v-model:value="statusFilter" data-test="status-filter" allow-clear placeholder="全部状态" :options="statusOptions" style="width: 180px" />
          <Button type="primary" @click="showCreateSystem">新增系统</Button>
        </Space>
      </div>

      <Table
        class="system-table"
        :columns="systemColumns"
        :data-source="filteredSystems"
        :loading="{ spinning: loading, tip: '加载中…' }"
        :pagination="false"
        :scroll="{ x: 1000 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'collaborators'">{{ record.collaborators.join('、') || '—' }}</template>
          <template v-else-if="column.key === 'status'"><Tag :color="entityStatusMeta(record.status).color">{{ entityStatusMeta(record.status).label }}</Tag></template>
          <template v-else-if="column.key === 'actions'">
            <Space :size="0" wrap>
              <Button type="link" :data-test="`edit-system-${record.id}`" @click="showEditSystem(record as SystemItem)">编辑</Button>
              <Button type="link" :data-test="`view-requirements-${record.id}`" @click="viewRequirements(record.id)">查看需求</Button>
              <Button type="link" :data-test="`toggle-system-${record.id}`" @click="toggleSystem(record as SystemItem)">{{ record.status === 'ACTIVE' ? '停用' : '启用' }}</Button>
              <Popconfirm :title="`确定删除系统“${record.name}”吗？`" ok-text="删除" cancel-text="取消" @confirm="deleteSystem(record as SystemItem)">
                <Button danger type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText><Empty v-if="!loading" description="暂无系统数据，请新增系统" /></template>
      </Table>
    </Card>

    <Modal v-model:open="systemFormOpen" :title="systemFormMode === 'create' ? '新增系统' : '编辑系统'" :footer="null" destroy-on-close :get-container="false" @cancel="systemFormMode = null">
      <Form data-test="system-modal" layout="vertical" @submit.prevent="saveSystem">
        <p class="field-requirement-legend" data-test="system-field-legend">带 <span class="field-required">*</span> 的项目为必填项，选填项目可根据实际情况填写。</p>
        <FormItem data-test="system-name-field" label="系统名称" required><Input v-model:value="systemForm.name" data-test="system-name" /></FormItem>
        <FormItem label="负责人" required><Input v-model:value="systemForm.ownerName" data-test="system-owner" /></FormItem>
        <FormItem data-test="system-collaborators-field" label="协助人" extra="多人用逗号分隔"><Input v-model:value="systemForm.collaborators" data-test="system-collaborators" placeholder="多人用逗号分隔" /></FormItem>
        <Space class="form-actions"><Button html-type="button" @click="systemFormMode = null">取消</Button><Button type="primary" html-type="submit">保存</Button></Space>
      </Form>
    </Modal>

    <Modal v-model:open="migrationOpen" title="迁移关联需求" :footer="null" destroy-on-close :get-container="false" @cancel="migrationSourceId = null">
      <Form data-test="migration-modal" layout="vertical" @submit.prevent="migrateRequirements">
        <FormItem label="迁移目标" required>
          <Select v-model:value="migrationTargetId" :options="[{ value: unassignedMigrationTarget, label: '暂无系统' }, ...migrationTargets.map((system) => ({ value: system.id, label: system.name }))]" />
        </FormItem>
        <Space class="form-actions"><Button html-type="button" @click="migrationSourceId = null">取消</Button><Button type="primary" html-type="submit">确认迁移</Button></Space>
      </Form>
    </Modal>
  </section>
</template>
