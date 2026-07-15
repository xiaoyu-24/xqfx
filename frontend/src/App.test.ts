import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import App from './App.vue'

vi.mock('./components/RequirementForm.vue', () => ({
  default: defineComponent({
    emits: ['dirty-change', 'submitted'],
    template: `
      <div>
        <button type="button" data-test="make-dirty" @click="$emit('dirty-change', true)">make dirty</button>
        <button type="button" data-test="submitted" @click="$emit('submitted')">submitted</button>
        <p>RequirementForm Stub</p>
      </div>
    `,
  }),
}))

vi.mock('./components/RequirementList.vue', () => ({
  default: defineComponent({
    props: {
      presetSystemId: { type: Number, default: null },
      presetRequestKey: { type: Number, default: 0 },
    },
    template: '<p>RequirementList Stub {{ presetSystemId }} {{ presetRequestKey }}</p>',
  }),
}))

vi.mock('./components/RequirementManagement.vue', () => ({
  default: defineComponent({
    template: '<p>RequirementManagement Stub</p>',
  }),
}))

vi.mock('./components/SystemManagement.vue', () => ({
  default: defineComponent({
    emits: ['view-requirements'],
    template: `
      <div>
        <button type="button" data-test="view-system-requirements" @click="$emit('view-requirements', 12)">view requirements</button>
        <p>SystemManagement Stub</p>
      </div>
    `,
  }),
}))

describe('App', () => {
  it('shows the four primary navigation entries in the planned order', () => {
    const wrapper = mount(App)

    expect(wrapper.findAll('aside nav button').map((button) => button.text())).toEqual([
      '填写需求',
      '需求列表',
      '管理需求',
      '系统管理',
    ])
  })

  it('opens requirement management as an independent page', async () => {
    const wrapper = mount(App)

    await wrapper.findAll('aside nav button')[2].trigger('click')

    expect(wrapper.text()).toContain('RequirementManagement Stub')
    expect(wrapper.text()).not.toContain('RequirementList Stub')
  })

  it('asks for confirmation before leaving the create page from the main menu', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mount(App)
    await wrapper.get('[data-test="make-dirty"]').trigger('click')

    const navButtons = wrapper.findAll('aside nav button')
    await navButtons[1].trigger('click')

    expect(confirm).toHaveBeenCalledWith('当前内容尚未保存，确定离开填写页吗？')
    expect(wrapper.text()).toContain('RequirementForm Stub')
    expect(wrapper.text()).not.toContain('RequirementList Stub')
  })

  it('opens the requirement list with a system preset from system management', async () => {
    const wrapper = mount(App)
    const navButtons = wrapper.findAll('aside nav button')

    await navButtons[3].trigger('click')
    await wrapper.get('[data-test="view-system-requirements"]').trigger('click')

    expect(wrapper.text()).toContain('RequirementList Stub 12 1')
  })
})
