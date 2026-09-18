import { reactive } from 'vue';
import ComparisonApi from '@/sheep/api/product/comparison';
import { captureTenantOperation, isTenantOperationCurrent, getControlledTenantId, readTenantToken } from './tenant-runtime';
import { createComparisonController } from './comparison.mjs';

export const comparisonState = reactive({});
const guestKey = scope => `mall-comparison-guest-v1:${scope.tenantId}`;
export function comparisonData(response) {
  if (response?.code !== 0) throw new Error(response?.code === 409
    ? '清单已在另一端更新，请刷新后重新操作，未覆盖另一端的内容'
    : response?.msg || '对比服务暂不可用，请重试');
  return response.data;
}
export const comparison = createComparisonController({
  capture: () => ({ operation: captureTenantOperation(), tenantId: String(getControlledTenantId()), authenticated: !!readTenantToken('token') }),
  current: scope => isTenantOperationCurrent(scope.operation) && scope.tenantId === String(getControlledTenantId()),
  readGuest: scope => uni.getStorageSync(guestKey(scope)),
  writeGuest: (scope, value) => uni.setStorageSync(guestKey(scope), value),
  readList: async () => comparisonData(await ComparisonApi.getList()),
  saveList: async value => comparisonData(await ComparisonApi.saveList(value)),
}, comparisonState);
uni.$on('mall:session-changed', comparison.reset);
if (typeof window !== 'undefined') window.addEventListener('storage', event => {
  if (event.key?.startsWith('mall-comparison-guest-v1:') && !readTenantToken('token')) comparison.reset();
});
