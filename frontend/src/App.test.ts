import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import App from './App.vue'

vi.mock('./components/RequirementForm.vue', () => ({
  default: defineComponent({
    emits: ['back', 'dirty-change', 'submitted'],
    template: `
      <div>
        <button type="button" data-test="make-dirty" @click="$emit('dirty-change', true)">make dirty</button>
        <button type="button" data-test="back" @click="$emit('back')">back</button>
        <button type="button" data-test="submitted" @click="$emit('submitted')">submitted</button>
        <p>RequirementForm Stub</p>
      </div>
    `,
  }),
}))

vi.mock('./components/RequirementList.vue', () => ({
  default: defineComponent({
    template: '<p>RequirementList Stub</p>',
  }),
}))

vi.mock('./components/SystemManagement.vue', () => ({
  default: defineComponent({
    template: '<p>SystemManagement Stub</p>',
  }),
}))

describe('App', () => {
  it('shows the three primary navigation entries', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('填写需求')
    expect(wrapper.text()).toContain('需求列表')
    expect(wrapper.text()).toContain('系统管理')
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

  it('returns to the list page after the create page emits back', async () => {
    const wrapper = mount(App)

    await wrapper.get('[data-test="back"]').trigger('click')

    expect(wrapper.text()).toContain('RequirementList Stub')
  })
})
