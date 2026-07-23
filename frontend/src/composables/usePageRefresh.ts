import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'
import { subscribeToRefresh, type RefreshTarget } from './refreshBus'

type RefreshOptions = {
  isPaused?: () => boolean
}

export const usePageRefresh = (
  target: RefreshTarget,
  load: () => void | Promise<void>,
  options: RefreshOptions = {},
) => {
  let active = true
  let mounted = false
  let loading = false

  const refresh = () => {
    if (!active || options.isPaused?.() || loading) return
    loading = true
    Promise.resolve(load()).finally(() => { loading = false })
  }

  const stopListening = subscribeToRefresh(target, refresh)

  onMounted(() => {
    active = true
    mounted = true
    refresh()
  })
  onActivated(() => {
    active = true
    if (mounted) refresh()
  })
  onDeactivated(() => {
    active = false
  })
  onBeforeUnmount(() => {
    active = false
    stopListening()
  })

  return { refresh }
}
