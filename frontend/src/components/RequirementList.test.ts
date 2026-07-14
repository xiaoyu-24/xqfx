import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RequirementList from './RequirementList.vue'

const { get, post, put, patch, remove } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), remove: vi.fn() }))
vi.mock('../api', () => ({ api: { get, post, put, patch, delete: remove } }))

describe('RequirementList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } })
    post.mockResolvedValue({ data: { id: 100, originalName: '补传.pdf', contentType: 'application/pdf', sizeBytes: 3 } })
    put.mockResolvedValue({ data: {} })
    patch.mockResolvedValue({ data: {} })
    remove.mockResolvedValue({})
  })

  it('shows the primary list filters', () => {
    const wrapper = mount(RequirementList)

    expect(wrapper.text()).toContain('关键词')
    expect(wrapper.text()).toContain('所属系统')
    expect(wrapper.text()).toContain('需求状态')
    expect(wrapper.text()).toContain('查询')
  })

  it('loads the full requirement list and management list when mounted', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
      if (url === '/requirements/management') return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
      return Promise.resolve({ data: [] })
    })
    mount(RequirementList)
    await flushPromises()

    expect(get).toHaveBeenCalledWith('/requirements/page', { params: { page: 0, size: 20 } })
    expect(get).toHaveBeenCalledWith('/requirements/management', { params: { page: 0, size: 20 } })
  })

  it('formats requirement filling time in Shanghai display format', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 21, title: '上海时间需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: '2026-07-14T09:05:06.123456', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/management') return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()

    expect(wrapper.text()).toContain('2026-07-14 09:05:06')
  })

  it('updates management requirement processing details', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
      if (url === '/requirements/management') return Promise.resolve({ data: { content: [{ id: 22, title: '待处理需求', type: 'REQUIREMENT', requesterName: '林琳', department: '研发部', status: 'IN_DEVELOPMENT', submittedAt: '2026-07-14T08:00:00', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 4 }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/22') return Promise.resolve({ data: { id: 22, title: '待处理需求', type: 'REQUIREMENT', status: 'IN_DEVELOPMENT', submittedAt: '2026-07-14T08:00:00', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 4 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()
    await wrapper.get('[data-test="manage-22"]').trigger('click')
    await wrapper.get('[data-test="processing-status"]').setValue('COMPLETED')
    await wrapper.get('[data-test="processing-completed-at"]').setValue('2026-07-14T09:30')
    await wrapper.get('[data-test="processing-handler"]').setValue('李明')
    await wrapper.get('[data-test="processing-description"]').setValue('已完成开发并验证')
    await wrapper.get('[data-test="processing-form"]').trigger('submit.prevent')

    expect(patch).toHaveBeenCalledWith('/requirements/22/processing', {
      status: 'COMPLETED', completedAt: '2026-07-14T09:30:00', handledBy: '李明', completionDescription: '已完成开发并验证', recordVersion: 4,
    })
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
      if (url === '/requirements/7/attachments') return Promise.resolve({ data: [{ id: 11, originalName: '说明.pdf', contentType: 'application/pdf', sizeBytes: 12 }, { id: 12, originalName: '截图.png', contentType: 'image/png', sizeBytes: 24 }] })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="view-7"]').trigger('click')

    expect(get).toHaveBeenCalledWith('/requirements/7')
    expect(wrapper.text()).toContain('这是完整的需求说明')
    expect(wrapper.get('[data-test="attachment-download-11"]').attributes('href')).toBe('/api/attachments/11')
    expect(wrapper.get('[data-test="attachment-preview-12"]').attributes('src')).toBe('/api/attachments/12')
  })

  it('uploads a follow-up attachment from requirement detail', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 7, title: '补传附件需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/7') return Promise.resolve({ data: { id: 7, title: '补传附件需求', content: '需求内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', saveType: 'SUBMITTED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null } })
      if (url === '/requirements/7/attachments') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="view-7"]').trigger('click')
    const input = wrapper.get<HTMLInputElement>('[data-test="detail-attachment-input"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['pdf'], '补传.pdf', { type: 'application/pdf' })] })
    await input.trigger('change')
    await wrapper.get('[data-test="detail-attachment-upload"]').trigger('click')

    expect(post).toHaveBeenCalledWith('/requirements/7/attachments', expect.any(FormData), expect.any(Object))
  })

  it('displays the target version name returned with a requirement', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 10, title: '版本展示', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: 3, targetVersionName: 'V2.0', periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="query"]').trigger('click')

    expect(wrapper.text()).toContain('V2.0')
    expect(wrapper.text()).not.toContain('版本 #3')
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
      if (url === '/requirements/9') return Promise.resolve({ data: { id: 9, title: '原始标题', content: '原始内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', saveType: 'SUBMITTED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 3 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)

    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="edit-9"]').trigger('click')
    await wrapper.get('[data-test="edit-title"]').setValue('修改后的标题')
    await wrapper.get('[data-test="edit-form"]').trigger('submit.prevent')

    expect(put).toHaveBeenCalledWith('/requirements/9', expect.objectContaining({ title: '修改后的标题', systemId: null, targetVersionId: null, recordVersion: 3 }))
  })

  it('submits a completed draft as a formal requirement', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 14, title: '待提交草稿', type: 'BUG', requesterName: '林琳', department: '研发部', status: null, submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, saveType: 'DRAFT' }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/14') return Promise.resolve({ data: { id: 14, title: '待提交草稿', content: '完整内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: null, saveType: 'DRAFT', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 2 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="edit-14"]').trigger('click')
    await wrapper.get('[data-test="submit-draft-14"]').trigger('click')

    expect(put).toHaveBeenCalledWith('/requirements/14', expect.objectContaining({ recordVersion: 2 }))
  })

  it('sends pending evaluation when resetting an edited requirement status', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 15, title: '状态回退', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'CONFIRMED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/15') return Promise.resolve({ data: { id: 15, title: '状态回退', content: '内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'CONFIRMED', saveType: 'SUBMITTED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 1 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await wrapper.get('[data-test="query"]').trigger('click')
    await wrapper.get('[data-test="edit-15"]').trigger('click')
    await wrapper.get('[data-test="edit-status"]').setValue('PENDING_EVALUATION')
    await wrapper.get('[data-test="edit-form"]').trigger('submit.prevent')

    expect(put).toHaveBeenCalledWith('/requirements/15', expect.objectContaining({ status: 'PENDING_EVALUATION' }))
  })

  it('auto-queries requirements for a preset system', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [{ id: 12, name: '客户系统', status: 'ACTIVE' }] })
      if (url === '/systems/12/versions') return Promise.resolve({ data: [] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
      return Promise.resolve({ data: [] })
    })

    const wrapper = mount(RequirementList, {
      props: { presetSystemId: 12, presetRequestKey: 1 },
    })
    await flushPromises()

    expect((wrapper.get('[data-test="system-filter"]').element as HTMLSelectElement).value).toBe('12')
    expect(get).toHaveBeenCalledWith('/requirements/page', {
      params: { page: 0, size: 20, systemId: 12 },
    })
  })
})
