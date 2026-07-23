<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Button, Card, Empty, Form, FormItem, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'ant-design-vue'
import { api } from '../api'
import { entityStatusMeta } from '../constants/statusConfig'
import { markChanged } from '../composables/refreshBus'
import { usePageRefresh } from '../composables/usePageRefresh'

type SystemStatus = 'ACTIVE' | 'INACTIVE'
type SystemItem = { id: number; name: string; status: SystemStatus }
type VersionItem = { id: number; systemId: number; name: string; status: SystemStatus; requirementCount: number }

const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const selectedSystemId = ref<number | undefined>(undefined)
const versionFormMode = ref<'create' | 'edit' | null>(null)
const versionForm = reactive({ id: 0, name: '' })
const loading = ref(false)
const versionColumns = [
  { title: '版本名称', dataIndex: 'name', key: 'name', minWidth: 220 },
  { title: '状态', key: 'status', width: 120 },
  { title: '关联需求数', key: 'requirementCount', width: 150 },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' as const },
]

const selectedSystem = computed(() => systems.value.find((item) => item.id === selectedSystemId.value) ?? null)
const versionFormOpen = computed({
  get: () => versionFormMode.value !== null,
  set: (open: boolean) => { if (!open) versionFormMode.value = null },
})

const loadSystems = async () => {
  try {
    systems.value = (await api.get('/systems')).data
  } catch {
    message.error('加载系统列表失败')
  }
}

const loadVersions = async (systemId: number) => {
  selectedSystemId.value = systemId
  versionFormMode.value = null
  loading.value = true
  try {
    versions.value = (await api.get(`/systems/${systemId}/versions`)).data
  } catch {
    versions.value = []
    message.error('加载版本列表失败')
  } finally {
    loading.value = false
  }
}

const onSystemChange = (value: unknown) => {
  const systemId = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : Number.NaN
  if (Number.isInteger(systemId)) {
    loadVersions(systemId)
  } else {
    selectedSystemId.value = undefined
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
  if (selectedSystemId.value === undefined || !versionForm.name.trim()) {
    message.warning('请填写版本名称')
    return
  }
  try {
    if (versionFormMode.value === 'create') {
      await api.post(`/systems/${selectedSystemId.value}/versions`, { name: versionForm.name.trim() })
      message.success('新增版本成功')
    } else {
      await api.put(`/system-versions/${versionForm.id}`, { name: versionForm.name.trim() })
      message.success('版本名称已更新')
    }
    versionFormMode.value = null
    await loadVersions(selectedSystemId.value)
    markChanged(['requirements'])
  } catch {
    message.error('保存版本失败')
  }
}

const toggleVersion = async (version: VersionItem) => {
  const status: SystemStatus = version.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/system-versions/${version.id}/status`, { status })
    message.success(status === 'ACTIVE' ? '版本已启用' : '版本已停用')
    if (selectedSystemId.value !== undefined) await loadVersions(selectedSystemId.value)
    markChanged(['requirements'])
  } catch {
    message.error('更新版本状态失败')
  }
}

const deleteVersion = async (version: VersionItem) => {
  try {
    await api.delete(`/system-versions/${version.id}`)
    message.success('版本已删除')
    if (selectedSystemId.value !== undefined) await loadVersions(selectedSystemId.value)
    markChanged(['requirements'])
  } catch {
    message.error('删除版本失败；若已关联需求，请先调整需求版本')
  }
}

usePageRefresh('versions', async () => {
  await loadSystems()
  if (selectedSystemId.value !== undefined) await loadVersions(selectedSystemId.value)
})
</script>

<template>
  <section class="version-management-page">
    <Card title="版本管理" :bordered="false">
      <Form class="version-toolbar" layout="inline">
        <FormItem label="选择系统">
          <Select v-model:value="selectedSystemId" data-test="version-system-select" allow-clear show-search placeholder="请选择系统" :options="systems.map((system) => ({ value: system.id, label: system.name }))" @change="onSystemChange" />
        </FormItem>
        <FormItem v-if="selectedSystem"><Button type="primary" data-test="create-version" @click="showCreateVersion">新增版本</Button></FormItem>
      </Form>

      <Empty v-if="!selectedSystem" description="请选择一个系统后维护其版本。" />

      <Table
        v-else
        class="version-table"
        :columns="versionColumns"
        :data-source="versions"
        :loading="{ spinning: loading, tip: '加载中…' }"
        :pagination="false"
        :scroll="{ x: 720 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'"><Tag :color="entityStatusMeta(record.status).color">{{ entityStatusMeta(record.status).label }}</Tag></template>
          <template v-else-if="column.key === 'requirementCount'">{{ record.requirementCount }} 条需求</template>
          <template v-else-if="column.key === 'actions'">
            <Space :size="0">
              <Button type="link" @click="showEditVersion(record as VersionItem)">编辑</Button>
              <Button type="link" @click="toggleVersion(record as VersionItem)">{{ record.status === 'ACTIVE' ? '停用' : '启用' }}</Button>
              <Popconfirm :title="`确定删除版本“${record.name}”吗？`" ok-text="删除" cancel-text="取消" @confirm="deleteVersion(record as VersionItem)">
                <Button danger type="link">删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText><Empty description="暂无版本，请新增。" /></template>
      </Table>
    </Card>

    <Modal v-model:open="versionFormOpen" :title="versionFormMode === 'create' ? '新增版本' : '编辑版本'" :footer="null" destroy-on-close :get-container="false" @cancel="versionFormMode = null">
      <Form data-test="version-modal" layout="vertical" @submit.prevent="saveVersion">
        <FormItem><template #label>版本名称 <span class="field-required">* 必填</span></template><Input v-model:value="versionForm.name" data-test="version-name" placeholder="请输入版本名称" /></FormItem>
        <Space class="form-actions"><Button html-type="button" @click="versionFormMode = null">取消</Button><Button type="primary" html-type="submit">保存</Button></Space>
      </Form>
    </Modal>
  </section>
</template>
