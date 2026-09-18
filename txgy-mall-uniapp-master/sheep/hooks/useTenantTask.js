import { onScopeDispose } from 'vue';
import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';

// Per-component ownership. A fresh request must never inherit a replaced page's form.
export function useTenantTask(onExpire = () => {}) {
  const owner = captureTenantOperation();
  let alive = true, generation = 0;
  const current = () => alive && isTenantOperationCurrent(owner);
  const capture = () => { const revision = generation; return () => current() && revision === generation; };
  const invalidate = () => { generation++; };
  const changed = () => { if (!current()) { alive = false; invalidate(); onExpire(); } };
  uni.$on('mall:session-changed', changed);
  onScopeDispose(() => { alive = false; invalidate(); uni.$off('mall:session-changed', changed); });
  return { current, capture, invalidate };
}
