export type RefreshTarget = 'requirements' | 'systems' | 'versions' | 'dashboard'

type Listener = () => void

const listeners = new Map<RefreshTarget, Set<Listener>>()

export const subscribeToRefresh = (target: RefreshTarget, listener: Listener) => {
  const targetListeners = listeners.get(target) ?? new Set<Listener>()
  targetListeners.add(listener)
  listeners.set(target, targetListeners)
  return () => targetListeners.delete(listener)
}

export const markChanged = (targets: RefreshTarget | RefreshTarget[]) => {
  for (const target of Array.isArray(targets) ? targets : [targets]) {
    listeners.get(target)?.forEach((listener) => listener())
  }
}
