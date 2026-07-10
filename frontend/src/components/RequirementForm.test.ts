import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RequirementForm from './RequirementForm.vue'

describe('RequirementForm', () => {
  it('shows new-system fields when the user selects a new system', async () => {
    const wrapper = mount(RequirementForm)

    await wrapper.get('[data-test="system-mode"]').setValue('new')

    expect(wrapper.text()).toContain('新系统名称')
    expect(wrapper.text()).toContain('新系统负责人')
    expect(wrapper.text()).toContain('新系统协助人')
  })
})
