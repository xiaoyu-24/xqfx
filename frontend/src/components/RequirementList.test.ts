import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RequirementList from './RequirementList.vue'

describe('RequirementList', () => {
  it('shows the primary list filters', () => {
    const wrapper = mount(RequirementList)

    expect(wrapper.text()).toContain('关键词')
    expect(wrapper.text()).toContain('所属系统')
    expect(wrapper.text()).toContain('需求状态')
    expect(wrapper.text()).toContain('查询')
  })
})
