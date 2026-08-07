<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Button, Card, Input, Modal, Select, SelectOption, Switch, Table, Tag, message } from 'ant-design-vue'
import { LockOutlined, PlusOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { DEPARTMENTS } from '../constants/departments'

interface User {
  id: number
  username: string
  displayName: string
  department: string | null
  admin: boolean
  disabled: boolean
  mustChangePassword: boolean
}

const users = ref<User[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const modalTitle = ref('新增人员')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  username: '',
  displayName: '',
  department: '',
  admin: false,
})

const columns = [
  { title: '账号', dataIndex: 'username', key: 'username', width: 150 },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 150 },
  { title: '部门', dataIndex: 'department', key: 'department', width: 150 },
  { title: '角色', key: 'role', width: 100 },
  { title: '状态', key: 'status', width: 120 },
  { title: '操作', key: 'action', width: 260 },
]

const loadUsers = async () => {
  loading.value = true
  try {
    const { data } = await api.get<User[]>('/users')
    users.value = data
  } catch {
    message.error('加载人员列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)

const openCreateModal = () => {
  modalTitle.value = '新增人员'
  editingId.value = null
  form.username = ''
  form.displayName = ''
  form.department = ''
  form.admin = false
  modalOpen.value = true
}

const handleSave = async () => {
  if (!form.username.trim() || !form.displayName.trim()) {
    message.warning('账号和姓名为必填项')
    return
  }
  saving.value = true
  try {
    await api.post('/users', {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      department: form.department || null,
      admin: form.admin,
    })
    modalOpen.value = false
    Modal.success({
      title: '用户已创建',
      content: `${form.displayName.trim()} 的账号已创建，初始密码为 888888。首次登录时需要修改密码。`,
      okText: '知道了',
    })
    await loadUsers()
  } catch (error: unknown) {
    const data = (error as { response?: { data?: { message?: string } } })?.response?.data
    const msg = data?.message && data.message.trim().length > 0 ? data.message : '创建人员失败'
    message.error(msg)
  } finally {
    saving.value = false
  }
}

const handleResetPassword = async (userId: number, displayName: string) => {
  Modal.confirm({
    title: '重置密码',
    content: `确定要重置 ${displayName} 的密码吗？重置后该用户此前的登录会全部失效。`,
    okText: '确定重置',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.post(`/users/${userId}/reset-password`)
        Modal.success({
          title: '密码已重置',
          content: `${displayName} 的初始密码已重置为 888888。首次登录时需要修改密码。`,
          okText: '知道了',
        })
        await loadUsers()
      } catch {
        message.error('重置密码失败')
      }
    },
  })
}

const handleToggleDisabled = async (user: Pick<User, 'id' | 'disabled'>) => {
  const nextDisabled = !user.disabled
  const actionText = nextDisabled ? '停用' : '启用'
  try {
    await api.patch(`/users/${user.id}/disabled`, { disabled: nextDisabled })
    message.success(`已${actionText}该账号`)
    await loadUsers()
  } catch (error: unknown) {
    const data = (error as { response?: { data?: { message?: string } } })?.response?.data
    message.error(data?.message?.trim() || `${actionText}失败`)
  }
}
</script>

<template>
  <div>
    <Card :bordered="false">
      <template #title>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>人员管理</span>
          <Button type="primary" @click="openCreateModal">
            <template #icon><PlusOutlined /></template>
            新增人员
          </Button>
        </div>
      </template>

      <Table :columns="columns" :data-source="users" :loading="loading" row-key="id" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <Tag v-if="record.admin" color="blue">管理员</Tag>
            <Tag v-else>普通用户</Tag>
          </template>

          <template v-else-if="column.key === 'status'">
            <Tag v-if="record.disabled" color="default">
              <StopOutlined />
              已停用
            </Tag>
            <Tag v-else-if="record.mustChangePassword" color="orange">需改密</Tag>
            <Tag v-else color="success">
              <CheckCircleOutlined />
              正常
            </Tag>
          </template>

          <template v-else-if="column.key === 'action'">
            <Button type="link" size="small" @click="handleResetPassword(record.id, record.displayName)">
              <template #icon><LockOutlined /></template>
              重置密码
            </Button>
            <Button
              type="link"
              size="small"
              :danger="!record.disabled"
              @click="handleToggleDisabled({ id: record.id, disabled: record.disabled })"
            >
              {{ record.disabled ? '启用' : '停用' }}
            </Button>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <div style="display: flex; flex-direction: column; gap: 16px; padding: 16px 0">
        <div>
          <label style="display: block; margin-bottom: 4px; font-size: 14px">账号 *</label>
          <Input
            v-model:value="form.username"
            placeholder="用于登录，创建后不可修改"
            autocomplete="off"
          />
        </div>
        <div>
          <label style="display: block; margin-bottom: 4px; font-size: 14px">姓名 *</label>
          <Input v-model:value="form.displayName" placeholder="真实姓名" />
        </div>
        <div>
          <label style="display: block; margin-bottom: 4px; font-size: 14px">部门</label>
          <Select v-model:value="form.department" placeholder="请选择" allow-clear>
            <SelectOption v-for="dept in DEPARTMENTS" :key="dept" :value="dept">
              {{ dept }}
            </SelectOption>
          </Select>
        </div>
        <div>
          <label style="display: block; margin-bottom: 8px; font-size: 14px">角色</label>
          <Switch v-model:checked="form.admin" checked-children="管理员" un-checked-children="普通用户" />
          <div style="margin-top: 6px; color: rgba(0, 0, 0, 0.45); font-size: 12px">
            管理员可以访问人员管理和 AI 配置页。
          </div>
        </div>
      </div>
    </Modal>
  </div>
</template>
