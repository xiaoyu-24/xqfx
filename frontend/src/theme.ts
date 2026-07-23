import { theme as antdTheme } from 'ant-design-vue'
import type { ConfigProviderProps } from 'ant-design-vue'

/**
 * 全局主题配置（Ant Design Vue 4.x ConfigProvider token）。
 *
 * 设计目标：
 * - 统一主色 #1677ff（Ant Design 默认现代蓝）
 * - 内容区浅灰背景 #f5f7fa
 * - 现代圆角 8px（Card 10px）
 * - 深色侧栏 #001529 保留（经典中后台配色）
 * - 组件级 token 覆盖：Layout / Menu / Card / Table / Form
 *
 * 接入方式：在 App.vue 顶层包裹
 *   <a-config-provider :theme="themeConfig" :locale="zhCN">
 */
export const themeConfig: NonNullable<ConfigProviderProps['theme']> = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#1677ff',
    colorInfo: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorBgLayout: '#f5f7fa',
    borderRadius: 8,
    fontFamily:
      'Inter, "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontSize: 14,
    wireframe: false,
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 64,
      siderBg: '#001529',
      bodyBg: '#f5f7fa',
    } as any,
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17',
    } as any,
    Card: {
      borderRadiusLG: 10,
      boxShadowTertiary:
        '0 1px 2px -2px rgba(15, 23, 42, 0.08), 0 3px 6px 0 rgba(15, 23, 42, 0.06)',
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: 'rgba(0, 0, 0, 0.88)',
      rowHoverBg: '#f0f7ff',
    } as any,
    Form: {
      itemMarginBottom: 16,
    } as any,
  },
}
