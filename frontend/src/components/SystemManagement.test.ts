import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SystemManagement from './SystemManagement.vue'

describe('SystemManagement', () => {
  it('shows system and version management controls', () => {
    const wrapper = mount(SystemManagement)

    expect(wrapper.text()).toContain('新增系统')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('协助人')
    expect(wrapper.text()).toContain('版本管理')
  })
})
