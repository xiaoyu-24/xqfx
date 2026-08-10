// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const { apiGet, apiPost } = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

vi.mock('../api', () => ({
  api: {
    get: apiGet,
    post: apiPost,
  },
}))

vi.mock('../composables/useAuth', async () => {
  const { ref } = await import('vue')
  return {
    useAuth: () => ({
      currentUser: ref({
        id: 1,
        username: 'reader',
        displayName: '普通用户',
        departmentId: 1,
        department: '产品部',
        role: 'USER',
      }),
      isHandler: ref(false),
    }),
  }
})

vi.mock('../composables/usePageRefresh', async () => {
  const { ref } = await import('vue')
  return {
    usePageRefresh: (key: string, load: () => Promise<void>) => {
      const loaded = ref(true)
      if (key === 'requirementEditor') {
        loaded.value = false
        void load().then(() => { loaded.value = true })
      }
      return { loaded, refresh: load, refreshing: ref(false), lastUpdatedAt: ref(null) }
    },
  }
})

vi.mock('vue-router', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
    useRoute: () => ({ name: 'requirement-list', query: {}, fullPath: '/requirements' }),
    RouterLink: defineComponent({
      props: ['to'],
      setup: (_props, { slots }) => () => h('a', slots.default?.()),
    }),
  }
})

import RequirementForm from './RequirementForm.vue'
import RequirementList from './RequirementList.vue'
import RequirementEditor from './RequirementEditor.vue'

describe('RequirementForm AI import', () => {
  beforeEach(() => {
    apiGet.mockResolvedValue({ data: [] })
    apiPost.mockResolvedValue({ data: {} })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('provides a text area for AI-assisted requirement import', () => {
    const wrapper = shallowMount(RequirementForm)

    expect(wrapper.find('[data-test="ai-import-text"]').exists()).toBe(true)
  })

  it('shows the existing system selector to an ordinary user', () => {
    const wrapper = shallowMount(RequirementForm, {
      global: { renderStubDefaultSlot: true },
    })

    expect(wrapper.find('[data-test="system-select"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('新系统')
    expect(wrapper.text()).not.toContain('暂无系统')
  })

  it('does not ask the requester to choose a target version', () => {
    const wrapper = shallowMount(RequirementForm, {
      global: { renderStubDefaultSlot: true },
    })

    expect(wrapper.find('[data-test="version-select"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('目标版本')
  })

  it('allows an ordinary user with a department to choose another department', () => {
    const wrapper = shallowMount(RequirementForm, {
      global: { renderStubDefaultSlot: true },
    })

    const select = wrapper.find('[data-test="department-select"]')
    expect(select.exists()).toBe(true)
    expect(select.attributes('disabled')).toBeUndefined()
  })
})

describe('RequirementList ordinary-user filters', () => {
  it('shows system and version filters to an ordinary user', async () => {
    const wrapper = shallowMount(RequirementList, {
      global: {
        renderStubDefaultSlot: true,
        stubs: {
          AButton: defineComponent({
            emits: ['click'],
            setup: (_props, { attrs, emit, slots }) => () => h('button', {
              ...attrs,
              onClick: () => emit('click'),
            }, slots.default?.()),
          }),
        },
      },
    })

    await wrapper.findComponent('.filter-toggle').trigger('click')

    expect(wrapper.find('[data-test="system-filter"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('目标版本')
  })

  it('shows responsible people in the same list columns', () => {
    const wrapper = shallowMount(RequirementList, {
      global: {
        renderStubDefaultSlot: true,
        stubs: {
          ATable: defineComponent({
            props: { columns: { type: Array, default: () => [] } },
            setup: (props, { slots }) => () => h('div', [
              ...(props.columns as Array<{ title?: string }>).map((column) => column.title),
              slots.bodyCell?.({
                column: { key: 'responsible' },
                record: { assigneeName: null, systemOwnerName: '陈明' },
              }),
            ]),
          }),
        },
      },
    })

    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('陈明')
    expect(wrapper.text()).not.toContain('未指派')
  })
})

describe('RequirementEditor ordinary-user system selection', () => {
  it('allows an ordinary user to change the system on an owned requirement', async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === '/requirements/1') {
        return Promise.resolve({ data: {
          id: 1,
          requesterUserId: 1,
          requesterName: '普通用户',
          departmentId: 1,
          department: '产品部',
          title: '待评估需求',
          typeId: 1,
          type: '功能',
          content: '内容',
          status: 'PENDING_EVALUATION',
          saveType: 'SUBMITTED',
          systemId: null,
          targetVersionId: null,
          recordVersion: 1,
        } })
      }
      return Promise.resolve({ data: [] })
    })
    const wrapper = shallowMount(RequirementEditor, {
      props: { requirementId: 1 },
      global: { renderStubDefaultSlot: true },
    })
    await flushPromises()
    const select = wrapper.find('[data-test="edit-system-select"]')

    expect(select.exists()).toBe(true)
    expect(select.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('暂无系统')
  })

  it('blocks an ordinary user from editing a completed requirement', async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === '/requirements/1') {
        return Promise.resolve({ data: {
          id: 1,
          requesterUserId: 1,
          requesterName: '普通用户',
          departmentId: 1,
          department: '产品部',
          title: '已完成需求',
          typeId: 1,
          type: '功能',
          content: '内容',
          status: 'COMPLETED',
          saveType: 'SUBMITTED',
          systemId: null,
          targetVersionId: null,
          recordVersion: 1,
        } })
      }
      return Promise.resolve({ data: [] })
    })

    const wrapper = shallowMount(RequirementEditor, {
      props: { requirementId: 1 },
      global: { renderStubDefaultSlot: true },
    })
    await flushPromises()

    expect(wrapper.find('[data-test="edit-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-error"]').attributes('title')).toContain('当前状态不允许编辑')
  })

  it('shows an existing version as read-only instead of an editable selector', async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === '/requirements/1') {
        return Promise.resolve({ data: {
          id: 1,
          requesterUserId: 1,
          requesterName: '普通用户',
          departmentId: 1,
          department: '产品部',
          title: '已有版本需求',
          typeId: 1,
          type: '功能',
          content: '内容',
          status: 'CONFIRMED',
          saveType: 'SUBMITTED',
          systemId: 1,
          targetVersionId: 9,
          targetVersionName: '1.0',
          recordVersion: 1,
        } })
      }
      if (url === '/systems') return Promise.resolve({ data: [{ id: 1, name: '客服系统', status: 'ACTIVE' }] })
      return Promise.resolve({ data: [] })
    })
    const wrapper = shallowMount(RequirementEditor, {
      props: { requirementId: 1 },
      global: { renderStubDefaultSlot: true },
    })
    await flushPromises()

    expect(wrapper.find('[data-test="current-version-display"]').text()).toContain('1.0')
    expect(wrapper.find('[data-test="edit-version-select"]').exists()).toBe(false)
  })
})
