import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RequirementList from './RequirementList.vue'

const { get, post, put, remove } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), remove: vi.fn() }))
vi.mock('../api', () => ({ api: { get, post, put, delete: remove } }))

describe('RequirementList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } })
    post.mockResolvedValue({ data: { id: 100, originalName: '补传.pdf', contentType: 'application/pdf', sizeBytes: 3 } })
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

  it('shows loading state instead of empty flash before first query settles', () => {
    get.mockImplementation(() => new Promise(() => {}))

    const wrapper = mount(RequirementList)

    expect(wrapper.text()).toContain('加载中')
    expect(wrapper.text()).not.toContain('暂无需求')
  })

  it('loads only the full requirement list when mounted', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()

    expect(get).toHaveBeenCalledWith('/requirements/page', { params: { page: 0, size: 20 } })
    expect(get).not.toHaveBeenCalledWith('/requirements/management', expect.anything())
    expect(wrapper.find('[data-test="management-section"]').exists()).toBe(false)
  })

  it('formats requirement filling time in Shanghai display format', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 21, title: '上海时间需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: '2026-07-14T09:05:06.123456', systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()

    expect(wrapper.text()).toContain('2026-07-14 09:05:06')
  })

  it('queries unassigned requirements through the paged endpoint', async () => {
    const wrapper = mount(RequirementList)
    await flushPromises()

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
    await flushPromises()

    await wrapper.get('[data-test="view-7"]').trigger('click')
    await flushPromises()

    expect(get).toHaveBeenCalledWith('/requirements/7')
    expect(wrapper.text()).toContain('这是完整的需求说明')
    expect(wrapper.get('[data-test="attachment-download-11"]').attributes('href')).toBe('/api/attachments/11')
    expect(wrapper.get('[data-test="attachment-preview-12"]').attributes('src')).toBe('/api/attachments/12')
  })

  it('uploads a follow-up attachment from requirement detail', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 7, title: '补传附件需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/7') return Promise.resolve({ data: { id: 7, title: '补传附件需求', content: '内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', saveType: 'SUBMITTED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null } })
      if (url === '/requirements/7/attachments') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()
    await wrapper.get('[data-test="view-7"]').trigger('click')
    await flushPromises()

    const file = new File(['pdf'], '补传.pdf', { type: 'application/pdf' })
    const input = wrapper.get('[data-test="detail-attachment-input"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.get('[data-test="detail-attachment-upload"]').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/requirements/7/attachments', expect.any(FormData), expect.objectContaining({ onUploadProgress: expect.any(Function) }))
  })

  it('displays the target version name returned with a requirement', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [{ id: 3, name: '系统A', status: 'ACTIVE' }] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 9, title: '带版本需求', type: 'REQUIREMENT', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: 3, targetVersionId: 8, targetVersionName: 'v2.1', periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()

    expect(wrapper.text()).toContain('系统A / v2.1')
  })

  it('soft deletes a requirement after confirmation', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 7, title: '待删除需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      return Promise.resolve({ data: [] })
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(RequirementList)
    await flushPromises()
    await wrapper.get('[data-test="delete-7"]').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalled()
    expect(remove).toHaveBeenCalledWith('/requirements/7')
  })

  it('edits a requirement from the list', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [{ id: 3, name: '系统A', status: 'ACTIVE' }] })
      if (url === '/systems/3/versions') return Promise.resolve({ data: [{ id: 8, name: 'v2.1', status: 'ACTIVE' }] })
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 7, title: '编辑需求', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', submittedAt: null, systemId: 3, targetVersionId: 8, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/7') return Promise.resolve({ data: { id: 7, title: '编辑需求', content: '原始内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'PENDING_EVALUATION', saveType: 'SUBMITTED', submittedAt: null, systemId: 3, targetVersionId: 8, periodStartDate: null, periodEndDate: null, recordVersion: 1 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()
    await wrapper.get('[data-test="edit-7"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="edit-title"]').setValue('已修改标题')
    await wrapper.get('[data-test="edit-form"]').trigger('submit.prevent')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/requirements/7', expect.objectContaining({ title: '已修改标题', recordVersion: 1 }))
  })

  it('submits a completed draft as a formal requirement', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 14, title: '待提交草稿', type: 'BUG', requesterName: '林琳', department: '研发部', status: null, submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, saveType: 'DRAFT' }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/14') return Promise.resolve({ data: { id: 14, title: '待提交草稿', content: '完整内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: null, saveType: 'DRAFT', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 2 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()
    await wrapper.get('[data-test="edit-14"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="submit-draft-14"]').trigger('click')
    await flushPromises()

    expect(put).toHaveBeenCalledWith('/requirements/14', expect.objectContaining({ recordVersion: 2 }))
  })

  it('sends pending evaluation when resetting an edited requirement status', async () => {
    get.mockImplementation((url: string) => {
      if (url === '/requirements/page') return Promise.resolve({ data: { content: [{ id: 15, title: '状态回退', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'CONFIRMED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null }], totalElements: 1, totalPages: 1 } })
      if (url === '/requirements/15') return Promise.resolve({ data: { id: 15, title: '状态回退', content: '内容', type: 'BUG', requesterName: '林琳', department: '研发部', status: 'CONFIRMED', saveType: 'SUBMITTED', submittedAt: null, systemId: null, targetVersionId: null, periodStartDate: null, periodEndDate: null, recordVersion: 1 } })
      return Promise.resolve({ data: [] })
    })
    const wrapper = mount(RequirementList)
    await flushPromises()
    await wrapper.get('[data-test="edit-15"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="edit-status"]').setValue('PENDING_EVALUATION')
    await wrapper.get('[data-test="edit-form"]').trigger('submit.prevent')
    await flushPromises()

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
