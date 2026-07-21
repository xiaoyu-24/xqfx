import { flushPromises, mount } from '@vue/test-utils'
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

  it('shows system management controls', () => {
    const wrapper = mount(SystemManagement)

    expect(wrapper.text()).toContain('新增系统')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('协助人')
  })

  it('shows loading state instead of the empty state before systems load', () => {
    get.mockReturnValue(new Promise(() => {}))
    const wrapper = mount(SystemManagement)

    expect(wrapper.text()).toContain('加载中…')
    expect(wrapper.text()).not.toContain('暂无系统数据，请新增系统')
  })

  it('creates a system with its owner and collaborators', async () => {
    const wrapper = mount(SystemManagement)

    await wrapper.get('button').trigger('click')
    expect(wrapper.get('[data-test="system-field-legend"]').text()).toContain('带 * 的项目为必填项')
    expect(wrapper.get('[data-test="system-name-field"]').text()).toContain('* 必填')
    expect(wrapper.get('[data-test="system-collaborators-field"]').text()).toContain('选填')
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

  it('sends record version when editing a system', async () => {
    get.mockResolvedValue({
      data: [{ id: 12, name: '客户系统', ownerName: '李明', collaborators: ['王芳'], status: 'ACTIVE', versionCount: 1, requirementCount: 3, recordVersion: 5 }],
    })
    put.mockResolvedValue({
      data: { id: 12, name: '客户系统-新', ownerName: '李明', collaborators: ['王芳'], status: 'ACTIVE', versionCount: 1, requirementCount: 3, recordVersion: 6 },
    })
    const wrapper = mount(SystemManagement)
    await flushPromises()

    await wrapper.get('[data-test="edit-system-12"]').trigger('click')
    await wrapper.get('[data-test="system-name"]').setValue('客户系统-新')
    await wrapper.get('form').trigger('submit.prevent')

    expect(put).toHaveBeenCalledWith('/systems/12', {
      name: '客户系统-新',
      ownerName: '李明',
      collaborators: ['王芳'],
      recordVersion: 5,
    })
  })

  it('sends record version when changing system status', async () => {
    get.mockResolvedValue({
      data: [{ id: 12, name: '客户系统', ownerName: '李明', collaborators: [], status: 'ACTIVE', versionCount: 0, requirementCount: 0, recordVersion: 5 }],
    })
    const wrapper = mount(SystemManagement)
    await flushPromises()
    await wrapper.get('[data-test="toggle-system-12"]').trigger('click')

    expect(patch).toHaveBeenCalledWith('/systems/12/status', { status: 'INACTIVE', recordVersion: 5 })
  })

  it('filters systems by owner and status while showing version and requirement counts', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') {
        return Promise.resolve({
          data: [
            { id: 1, name: '客户系统', ownerName: '李明', collaborators: ['王芳'], status: 'ACTIVE', versionCount: 3, requirementCount: 8 },
            { id: 2, name: '财务系统', ownerName: '张敏', collaborators: ['赵雷'], status: 'INACTIVE', versionCount: 1, requirementCount: 2 },
          ],
        })
      }
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(SystemManagement)
    await flushPromises()

    expect(wrapper.text()).toContain('版本数')
    expect(wrapper.text()).toContain('关联需求数')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('8')

    await wrapper.get('[data-test="owner-filter"]').setValue('张敏')
    await wrapper.get('[data-test="status-filter"]').setValue('INACTIVE')

    const tableBody = wrapper.find('tbody').text()
    expect(tableBody).toContain('财务系统')
    expect(tableBody).not.toContain('客户系统')
  })

  it('emits an event to view requirements for a system', async () => {
    get.mockResolvedValue({
      data: [{ id: 12, name: '客户系统', ownerName: '李明', collaborators: [], status: 'ACTIVE', versionCount: 1, requirementCount: 3 }],
    })
    const wrapper = mount(SystemManagement)
    await flushPromises()

    await wrapper.get('[data-test="view-requirements-12"]').trigger('click')

    expect(wrapper.emitted('view-requirements')).toEqual([[12]])
  })
})
