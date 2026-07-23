export const DEPARTMENTS = [
  'IT部',
  'FAE部',
  '总经办',
  '供应链部',
  '财务部',
  '产品部',
  '市场运营部',
  '业务部',
  '品质部',
  '人力资源部',
] as const

export type Department = (typeof DEPARTMENTS)[number]

export const isDepartment = (value: string): value is Department =>
  (DEPARTMENTS as readonly string[]).includes(value)
