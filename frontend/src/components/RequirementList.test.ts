import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RequirementList from './RequirementList.vue'

const { get, put, remove } = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), remove: vi.fn() }))
vi.mock('../api', () => ({ api: { get, put, delete: remove } }))

describe('RequirementList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } })
    put.mockResolvedValue({ data: {} })
    remove.mockResolvedValue({})
  })

  it('shows the primary list filters', () => {
    const wrapper = mount(RequirementList)

    expect(wrapper.text()).toContain('关键词')
    expect(wrapper.text()).toContain('所属系统')
    expect(wrapper.text()).toContain('需求状态')
    expect(wrapper.text()).toContain('查询')
  })

  it('queries unassigned requirements through the paged endpoint', async () => {
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="system-filter"]').setValue('none')
    await wrapper.get('[data-test="query"]').trigger('click')

    expect(get).toHaveBeenCalledWith('/requirements/page', {
      params: { page: 0, size: 20, unassignedSystem: true },
    })
  })

  it('loads and displays requirement detail from a list row', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 7, title: '详情需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/7') return Promise.resolve({ data: { id: 7, title: '详情需求', content: '这是完整的需求说明', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', saveType: 'SUBMITTED', submittedAt: '2026-07-10T10:00:00', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="view-7"]').trigger('click')

    expect(get).toHaveBeenCalledWith('/requirements/7')
    expect(wrapper.text()).toContain('这是完整的需求说明')
  })

  it('soft deletes a requirement after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    get.mockImplementation((url: string) => url === '/requirements/page'
      ? Promise.resolve({ data: { content: [{ id: 8, title: '待删除需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      : Promise.resolve({ data: [] }))
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="delete-8"]').trigger('click')

    expect(remove).toHaveBeenCalledWith('/requirements/8')
  })

  it('edits a requirement from the list', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 9, title: '原始标题', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/9') return Promise.resolve({ data: { id: 9, title: '原始标题', content: '原始内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', saveType: 'SUBMITTED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="edit-9"]').trigger('click')
    await wrapper.get('[data-test="edit-title"]').setValue('修改后的标题')
    await wrapper.get('[data-test="edit-form"]').trigger('submit.prevent')

    expect(put).toHaveBeenCalledWith('/requirements/9', expect.objectContaining({ title: '修改后的标题', systemId: null, targetVersionId: null }))
  })
})
