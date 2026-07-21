import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RequirementForm from './RequirementForm.vue'

const { get, post } = vi.hoisted(() => ({ get: vi.fn().mockResolvedValue({ data: [] }), post: vi.fn() }))
vi.mock('../api', () => ({ api: { get, post } }))

describe('RequirementForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    post.mockResolvedValue({ data: { id: 1 } })
    get.mockImplementation((url: string) => {
      if (url === '/systems') return Promise.resolve({ data: [{ id: 4, name: '客户系统', status: 'ACTIVE' }] })
      if (url === '/systems/4/versions') return Promise.resolve({ data: [{ id: 8, name: 'V2.0', status: 'ACTIVE' }] })
      return Promise.resolve({ data: [] })
    })
  })

  it('shows new-system fields when the user selects a new system', async () => {
    const wrapper = mount(RequirementForm)

    await wrapper.get('[data-test="system-mode"]').setValue('new')

    expect(wrapper.text()).toContain('新系统名称')
    expect(wrapper.text()).toContain('新系统负责人')
    expect(wrapper.text()).toContain('新系统协助人')
  })

  it('labels required, optional, and conditional fields explicitly', async () => {
    const wrapper = mount(RequirementForm)

    expect(wrapper.get('[data-test="field-requirement-legend"]').text()).toContain('带 * 的项目为必填项')
    expect(wrapper.get('[data-test="requester-name-field"]').text()).toContain('* 必填')
    expect(wrapper.get('[data-test="period-field"]').text()).toContain('选填')
    expect(wrapper.get('[data-test="attachment-field"]').text()).toContain('选填')

    await wrapper.get('[data-test="system-mode"]').setValue('new')

    expect(wrapper.get('[data-test="new-system-name-field"]').text()).toContain('* 必填')
    expect(wrapper.get('[data-test="new-system-collaborators-field"]').text()).toContain('选填')
  })

  it('loads target versions for the selected system', async () => {
    const wrapper = mount(RequirementForm)
    await flushPromises()

    await wrapper.get('[data-test="system-select"]').setValue('4')

    expect(get).toHaveBeenCalledWith('/systems/4/versions')
    expect(wrapper.text()).toContain('V2.0')
  })

  it('clears target version when switching to no system', async () => {
    const wrapper = mount(RequirementForm)
    await flushPromises()
    await wrapper.get('[data-test="system-select"]').setValue('4')
    await flushPromises()
    await wrapper.findAll('select')[2].setValue('8')
    await wrapper.get('[data-test="system-mode"]').setValue('none')
    await wrapper.find('input[placeholder="请输入姓名"]').setValue('林琳')
    await wrapper.find('input[placeholder="请输入部门"]').setValue('研发部')
    await wrapper.find('input[placeholder="请简要概括需求"]').setValue('暂无系统需求')
    await wrapper.find('textarea').setValue('切换到暂无系统后不能保留目标版本')
    await wrapper.find('form').trigger('submit.prevent')

    expect(post).toHaveBeenCalledWith('/requirements', expect.objectContaining({ systemId: null, targetVersionId: null }))
  })

  it('uploads selected files after a requirement is saved', async () => {
    post.mockResolvedValueOnce({ data: { id: 15 } }).mockResolvedValueOnce({ data: { id: 32, originalName: '说明.pdf' } })
    const wrapper = mount(RequirementForm)
    await wrapper.find('input[placeholder="请输入姓名"]').setValue('林琳')
    await wrapper.find('input[placeholder="请输入部门"]').setValue('研发部')
    await wrapper.find('input[placeholder="请简要概括需求"]').setValue('附件上传需求')
    await wrapper.find('textarea').setValue('保存需求后应上传已选择的附件')
    const attachmentInput = wrapper.get('[data-test="attachment-input"]')
    Object.defineProperty(attachmentInput.element, 'files', { value: [new File(['file'], '说明.pdf', { type: 'application/pdf' })] })
    await attachmentInput.trigger('change')
    await wrapper.find('form').trigger('submit.prevent')

    expect(post).toHaveBeenNthCalledWith(2, '/requirements/15/attachments', expect.any(FormData), expect.objectContaining({ onUploadProgress: expect.any(Function) }))
  })

  it('shows an immediate error and blocks submit when only one period date is filled', async () => {
    const wrapper = mount(RequirementForm)
    await wrapper.find('input[placeholder="请输入姓名"]').setValue('林琳')
    await wrapper.find('input[placeholder="请输入部门"]').setValue('研发部')
    await wrapper.find('input[placeholder="请简要概括需求"]').setValue('时间周期校验')
    await wrapper.find('textarea').setValue('开始日期和结束日期必须同时填写')
    await wrapper.find('input[aria-label="开始日期"]').setValue('2026-07-10')

    expect(wrapper.text()).toContain('开始日期和结束日期必须同时填写')

    await wrapper.find('form').trigger('submit.prevent')

    expect(post).not.toHaveBeenCalled()
  })

  it('does not show a return button above the form', () => {
    const wrapper = mount(RequirementForm)

    expect(wrapper.find('[data-test="back"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('返回')
  })
})
