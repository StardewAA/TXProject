/** Coalesce refreshes without overlapping requests or letting stale responses repaint a new route/filter. */
export function createRefreshQueue(task: (isCurrent: () => boolean) => Promise<void>) {
  let version = 0
  let disposed = false
  let running: Promise<void> | undefined
  return {
    refresh(): Promise<void> {
      if (disposed) return Promise.resolve()
      version += 1
      if (running) return running
      running = (async () => {
        let processed: number
        do {
          processed = version
          await task(() => !disposed && processed === version)
        } while (!disposed && processed !== version)
      })().finally(() => { running = undefined })
      return running
    },
    dispose() { disposed = true; version += 1 },
  }
}
