export interface WorkbenchPollResult<T> {
  snapshot: T
  cursor: string
  addedCount: number
  pollIntervalSeconds?: number
  resetRequired?: boolean
}

export interface WorkbenchPollState<T> {
  snapshot?: T
  loading: boolean
  error: string
  updatedAt?: number
  newCount: number
  interval: number
}

/** A scope owns one serial request stream. Resuming establishes a fresh baseline, not a burst of old alerts. */
export function createWorkbenchPoller<T>(
  fetchSnapshot: (cursor?: string) => Promise<WorkbenchPollResult<T>>,
  publish: (state: WorkbenchPollState<T>) => void,
) {
  let state: WorkbenchPollState<T> = { loading: false, error: '', newCount: 0, interval: 30000 }
  let cursor: string | undefined
  let latestCursor: string | undefined
  let generation = 0
  let disposed = false
  let pending = false
  let baseline = true
  let running: Promise<void> | undefined
  const emit = () => publish({ ...state })

  function refresh(resume = false): Promise<void> {
    if (disposed) return Promise.resolve()
    if (resume) { generation += 1; baseline = true; cursor = undefined; latestCursor = undefined; state.newCount = 0 }
    pending = true
    if (running) return running
    running = (async () => {
      while (pending && !disposed) {
        pending = false
        const version = generation
        const wasBaseline = baseline
        state.loading = true
        emit()
        try {
          const result = await fetchSnapshot(cursor)
          if (disposed || version !== generation) continue
          const reset = wasBaseline || result.resetRequired === true
          latestCursor = result.cursor
          if (reset) cursor = result.cursor
          baseline = false
          state = {
            snapshot: result.snapshot,
            loading: false,
            error: '',
            updatedAt: Date.now(),
            newCount: reset ? 0 : Math.max(0, Number(result.addedCount) || 0),
            interval: Math.min(60000, Math.max(5000, (Number(result.pollIntervalSeconds) || 30) * 1000)),
          }
        } catch (error) {
          if (disposed || version !== generation) continue
          // Keep the previous snapshot and timestamp, and make its stale state explicit.
          state.error = error instanceof Error ? error.message : '暂无法更新待办'
          state.loading = false
        }
        if (!disposed && version === generation) emit()
      }
    })().finally(() => { running = undefined })
    return running
  }

  return {
    refresh,
    dismissBanner() { state.newCount = 0; cursor = latestCursor; emit() },
    dispose() { disposed = true; generation += 1; pending = false; cursor = undefined },
  }
}
