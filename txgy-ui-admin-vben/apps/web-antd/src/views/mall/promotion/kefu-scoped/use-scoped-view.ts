import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue';
import { useAccess } from '@vben/access';
import { useAccessStore, useUserStore } from '@vben/stores';
import type { ChatCapability, KefuScope } from '#/api/mall/promotion/kefu-scoped';
import { getChatCapability } from '#/api/mall/promotion/kefu-scoped';
import { createRequestFence, kefuError, positiveId, scopeQuery } from './model';

export function useScopedView(permission: string, reset: () => void) {
  const access = useAccessStore();
  const user = useUserStore();
  const { hasAccessByCodes } = useAccess();
  const scope = ref<KefuScope>('PLATFORM');
  const shopId = ref<number>();
  const active = ref(false);
  const capability = ref<ChatCapability>();
  const capabilityLoading = ref(false);
  const capabilityError = ref('');
  const fences: ReturnType<typeof createRequestFence>[] = [];
  const fence = () => { const value = createRequestFence(); fences.push(value); return value; };
  const capabilityFence = fence();
  const crossTenant = computed(() => access.visitTenantId != null && String(access.visitTenantId) !== String(access.tenantId));
  const authorized = computed(() => !!access.accessToken && positiveId(access.tenantId) && hasAccessByCodes([permission]) && !crossTenant.value);
  const query = computed(() => scopeQuery(scope.value, shopId.value));
  const key = computed(() => [access.accessToken, user.userInfo?.id, access.tenantId, access.visitTenantId, access.accessCodes.join('|'), scope.value, shopId.value].join(':'));
  const ready = computed(() => active.value && authorized.value && capability.value?.enabled === true && !!query.value);
  const label = computed(() => `本租户 ${access.tenantId ?? '—'} · ${scope.value === 'PLATFORM' ? '平台客服' : `店铺 ${shopId.value || '待选择'}`}`);
  const blocked = computed(() => crossTenant.value ? '客服不支持跨租户代管，请切回当前账号所属租户。'
    : !authorized.value ? `当前账号缺少 ${permission} 权限，页面不会读取任何会话或名单。`
      : capabilityError.value || (!capability.value ? '正在核实客服开通状态…'
        : !capability.value.enabled ? '店铺范围客服尚未开通，请等待受控启用。旧客服历史不会自动迁移或混入。' : ''));
  function invalidate() { for (const value of fences) value.invalidate(); reset(); }
  async function refreshCapability() {
    if (!active.value || !authorized.value) return;
    const request = capabilityFence.begin();
    capabilityLoading.value = true;
    capabilityError.value = '';
    try { const result = await getChatCapability(request.signal); if (request.current()) capability.value = result; }
    catch (error) { if (request.current()) { capability.value = undefined; capabilityError.value = kefuError(error); } }
    finally { if (request.current()) capabilityLoading.value = false; }
  }
  watch(key, () => { invalidate(); capability.value = undefined; capabilityLoading.value = false; void refreshCapability(); }, { flush: 'sync' });
  watch(ready, (value, previous) => {
    if (!value && previous) {
      // A scope change already started a fresh capability request. Cancel only business reads here,
      // otherwise this deferred watcher could cancel that new request and leave the page blocked.
      for (const pending of fences) if (pending !== capabilityFence) pending.invalidate();
      reset();
    }
  });
  function start() { if (active.value) return; active.value = true; void refreshCapability(); }
  function stop() { active.value = false; invalidate(); capability.value = undefined; capabilityLoading.value = false; }
  onMounted(start); onActivated(start); onDeactivated(stop); onUnmounted(stop);
  return { scope, shopId, ready, authorized, query, key, label, blocked, active, capability, capabilityLoading, refreshCapability, fence };
}
