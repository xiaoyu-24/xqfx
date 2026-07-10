import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import App from './App.vue'

const { get } = vi.hoisted(() => ({ get: vi.fn().mockResolvedValue({ data: [] }) }))
vi.mock('./api', () => ({ api: { get } }))

describe('App', () => {
  it('shows the three primary navigation entries', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('填写需求')
    expect(wrapper.text()).toContain('需求列表')
    expect(wrapper.text()).toContain('系统管理')
  })
})
