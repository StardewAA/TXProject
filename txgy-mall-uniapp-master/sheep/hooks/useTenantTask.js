import { onScopeDispose } from 'vue';
import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';

// Per-component ownership. A fresh request must never inherit a replaced page's form.
export function useTenantTask(onExpire = () => {}) {
  let owner = captureTenantOperation();
  let alive = true, generation = 0;
  const current = () => alive && isTenantOperationCurrent(owner);
  const capture = () => { const revision = generation; return () => current() && revision === generation; };
  const invalidate = () => { generation++; };
  // A page can be created before the initial authenticated-session proof finishes.
  // Rebase is allowed only while the original component is still alive; a real
  // session-change event permanently expires it and cannot be bypassed by rebase.
  const rebase = () => {
    if (!alive) return false;
    owner = captureTenantOperation();
    invalidate();
    return true;
  };
  const changed = () => { if (!current()) { alive = false; invalidate(); onExpire(); } };
  uni.$on('mall:session-changed', changed);
  onScopeDispose(() => { alive = false; invalidate(); uni.$off('mall:session-changed', changed); });
  return { current, capture, invalidate, rebase };
}
