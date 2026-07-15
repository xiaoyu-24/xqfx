import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(process.cwd(), 'src/style.css'), 'utf8')

const declarationsFor = (selector) => {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const body = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))?.[1] ?? ''
  return body.split(';').map((declaration) => declaration.trim().replace(/\s+/g, ' ')).filter(Boolean)
}

describe('application layout', () => {
  it('keeps the sidebar fixed while the main content scrolls independently', () => {
    expect(declarationsFor('.app-shell')).toContain('height: 100vh')
    expect(declarationsFor('.app-shell')).toContain('overflow: hidden')
    expect(declarationsFor('.sidebar')).toContain('height: 100vh')
    expect(declarationsFor('.main-content')).toContain('overflow-y: auto')
  })
})
