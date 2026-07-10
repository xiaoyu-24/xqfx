import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SystemManagement from './SystemManagement.vue'

const { get, post, put, patch, remove } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  remove: vi.fn(),
}))

vi.mock('../api', () => ({ api: { get, post, put, patch, delete: remove } }))

describe('SystemManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: [] })
    post.mockResolvedValue({ data: { id: 1, name: '客户系统', ownerName: '李明', collaborators: [], status: 'ACTIVE' } })
  })

  it('shows system and version management controls', () => {
    const wrapper = mount(SystemManagement)

    expect(wrapper.text()).toContain('新增系统')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('协助人')
    expect(wrapper.text()).toContain('版本管理')
  })

  it('creates a system with its owner and collaborators', async () => {
    const wrapper = mount(SystemManagement)

    await wrapper.get('button').trigger('click')
    await wrapper.get('[data-test="system-name"]').setValue('客户系统')
    await wrapper.get('[data-test="system-owner"]').setValue('李明')
    await wrapper.get('[data-test="system-collaborators"]').setValue('王芳, 赵敏')
    await wrapper.get('form').trigger('submit.prevent')

    expect(post).toHaveBeenCalledWith('/systems', {
      name: '客户系统',
      ownerName: '李明',
      collaborators: ['王芳', '赵敏'],
    })
  })
})
