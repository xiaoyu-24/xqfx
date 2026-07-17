import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RequirementManagement from './RequirementManagement.vue'

const { get, patch } = vi.hoisted(() => ({ get: vi.fn(), patch: vi.fn() }))
vi.mock('../api', () => ({ api: { get, patch } }))

describe('RequirementManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    patch.mockResolvedValue({ data: {} })
  })

  it('shows loading state instead of empty flash before first query settles', () => {
    get.mockImplementation(() => new Promise(() => {}))

    const wrapper = mount(RequirementManagement)

    expect(wrapper.text()).toContain('加载中')
    expect(wrapper.text()).not.toContain('暂无待处理需求')
  })

  it('loads pending requirements and drafts on its own page', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/management') return Promise.resolve({ data: { content: [{ id: 22, title: '待处理需求', type: 'REQUIREMENT', requesterName: '林琳', department: '研发部', status: 'IN_DEVELOPMENT', submittedAt: '2026-07-14T08:00:00', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 4 }], totalElements: 1, totalPages: 1 } })
      return Promise.resolve({ data: [] })
    })

    const wrapper = mount(RequirementManagement)
    await flushPromises()

    expect(get).toHaveBeenCalledWith('/requirements/management', { params: { page: 0, size: 20 } })
    expect(wrapper.text()).toContain('待处理需求')
    expect(wrapper.text()).toContain('2026-07-14 08:00:00')
  })

  it('updates requirement processing details from the management page', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/management') return Promise.resolve({ data: { content: [{ id: 22, title: '待处理需求', type: 'REQUIREMENT', requesterName: '林琳', department: '研发部', status: 'IN_DEVELOPMENT', submittedAt: '2026-07-14T08:00:00', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 4 }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/22') return Promise.resolve({ data: { id: 22, title: '待处理需求', type: 'REQUIREMENT', status: 'IN_DEVELOPMENT', submittedAt: '2026-07-14T08:00:00', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 4 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementManagement)
    await flushPromises()
    await wrapper.get('[data-test="manage-22"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="processing-status"]').setValue('COMPLETED')
    await wrapper.get('[data-test="processing-completed-at"]').setValue('2026-07-14T09:30')
    await wrapper.get('[data-test="processing-handler"]').setValue('李明')
    await wrapper.get('[data-test="processing-description"]').setValue('已完成开发并验证')
    await wrapper.get('[data-test="processing-form"]').trigger('submit.prevent')
    await flushPromises()

    expect(patch).toHaveBeenCalledWith('/requirements/22/processing', {
      status: 'COMPLETED', completedAt: '2026-07-14T09:30:00', handledBy: '李明', completionDescription: '已完成开发并验证', recordVersion: 4,
    })
  })
})
