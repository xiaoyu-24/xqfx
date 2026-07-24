// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const { apiGet, apiPost } = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

vi.mock('../api', () => ({
  api: {
    get: apiGet,
    post: apiPost,
  },
}))

import RequirementForm from './RequirementForm.vue'

describe('RequirementForm AI import', () => {
  beforeEach(() => {
    apiGet.mockResolvedValue({ data: [] })
    apiPost.mockResolvedValue({ data: {} })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('provides a text area for AI-assisted requirement import', () => {
    const wrapper = shallowMount(RequirementForm)

    expect(wrapper.find('[data-test="ai-import-text"]').exists()).toBe(true)
  })
})
