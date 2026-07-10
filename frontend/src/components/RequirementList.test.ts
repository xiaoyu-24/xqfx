import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RequirementList from './RequirementList.vue'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api', () => ({ api: { get } }))

describe('RequirementList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } })
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
})
