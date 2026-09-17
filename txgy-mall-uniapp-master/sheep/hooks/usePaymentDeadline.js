import { onBeforeUnmount, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { monotonicNow } from '@/sheep/helper/payment-deadline.mjs';

/** One clock / one refresh in flight per page. Resuming always rechecks authoritative state. */
export function usePaymentDeadline(getStates, reload) {
  const now = ref(monotonicNow());
  const active = ref(false);
  const syncing = ref(false);
  const confirmed = ref(false);
  let timer;
  let inFlight;
  let disposed = false;
  let shown = false;
  let generation = 0;
  let requestGeneration = 0;
  let lastAttempt = -Infinity;
  const expiredKeys = new Set();

  function refresh() {
    if (disposed) return Promise.resolve(false);
    if (inFlight) {
      // A response requested before hiding is not sufficient to re-enable payment on resume.
      return requestGeneration === generation ? inFlight : inFlight.then(() => refresh());
    }
    syncing.value = true;
    confirmed.value = false;
    lastAttempt = monotonicNow();
    requestGeneration = generation;
    const currentGeneration = generation;
    inFlight = Promise.resolve().then(reload).then((ok) => {
      if (!disposed && currentGeneration === generation) confirmed.value = ok !== false;
      return ok !== false;
    }).catch(() => false).finally(() => {
      inFlight = undefined;
      syncing.value = false;
      now.value = monotonicNow();
    });
    return inFlight;
  }

  function tick() {
    now.value = monotonicNow();
    const states = getStates(now.value).filter((state) => state.pending);
    const newlyExpired = states.some((state) => state.expired && !expiredKeys.has(state.key));
    states.filter((state) => state.expired).forEach((state) => expiredKeys.add(state.key));
    if (states.length && (newlyExpired || now.value - lastAttempt >= 15_000)) void refresh();
  }

  function pause() {
    generation++;
    active.value = false;
    confirmed.value = false;
    clearInterval(timer);
    timer = undefined;
  }

  function resume() {
    if (disposed || !shown || (typeof document !== 'undefined' && document.hidden)) return;
    if (active.value) return;
    active.value = true;
    now.value = monotonicNow();
    void refresh();
    timer = setInterval(tick, 1000);
  }

  const visibilityChanged = () => {
    if (typeof document !== 'undefined' && document.hidden) pause();
    else resume();
  };
  function dispose() {
    disposed = true;
    pause();
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', visibilityChanged);
  }
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', visibilityChanged);
  onShow(() => { shown = true; resume(); });
  onHide(() => { shown = false; pause(); });
  onUnload(dispose);
  onBeforeUnmount(dispose);
  return { now, active, syncing, confirmed, refresh };
}
