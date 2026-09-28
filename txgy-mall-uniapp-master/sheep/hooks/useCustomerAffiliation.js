import { reactive, computed, watch, onBeforeUnmount } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import request from '@/sheep/request';
import { captureTenantOperation, getControlledTenantId } from '@/sheep/helper/tenant-runtime';
import { createCustomerAffiliationLoader, customerAffiliationMessage } from '@/sheep/helper/customer-affiliation.mjs';

export function useCustomerAffiliation() {
  const user = sheep.$store('user');
  const state = reactive({ result: null, loading: false, error: false, scope: '' });
  let shown = false, disposed = false;
  const scope = () => {
    if (!shown || disposed || !user.isLogin || !user.userInfo?.id) return '';
    const operation = captureTenantOperation();
    return operation.token ? `${operation.sessionEpoch}:${getControlledTenantId()}:${user.userInfo.id}` : '';
  };
  const loader = createCustomerAffiliationLoader(state, scope, async () => {
    const response = await request({ url: '/member/mall-tenant/customer-affiliation', method: 'GET',
      custom: { auth: true, showLoading: false, showError: false } });
    if (response?.code !== 0) throw new Error('服务企业查询失败');
    return response.data;
  });
  const visible = computed(() => Boolean(scope()) && state.scope === scope() && state.result?.state !== 'DISABLED');
  const message = computed(() => customerAffiliationMessage(state));
  const retryable = computed(() => state.error || ['TEMPORARILY_UNAVAILABLE', 'OWNER_UNAVAILABLE'].includes(state.result?.state));
  const refresh = () => loader.refresh();
  watch(() => [user.isLogin, user.userInfo?.id], refresh, { flush: 'sync' });
  onShow(() => { shown = true; void refresh(); });
  onHide(() => { shown = false; loader.reset(); });
  function dispose() { disposed = true; loader.reset(); }
  onUnload(dispose); onBeforeUnmount(dispose);
  return { state, visible, message, retryable, refresh };
}
