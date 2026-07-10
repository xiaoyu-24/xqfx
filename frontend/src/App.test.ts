import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import App from './App.vue'

describe('App', () => {
  it('shows the three primary navigation entries', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('填写需求')
    expect(wrapper.text()).toContain('需求列表')
    expect(wrapper.text()).toContain('系统管理')
  })
})
