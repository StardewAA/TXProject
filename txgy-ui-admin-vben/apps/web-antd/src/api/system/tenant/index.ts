import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace SystemTenantApi {
  export interface Tenant {
    id?: number;
    name: string;
    packageId: number;
    contactName: string;
    contactMobile: string;
    accountCount: number;
    expireTime: Date;
    websites: string[];
    status: number;
  }
}

export function getTenantPage(params: PageParam) {
  return requestClient.get<PageResult<SystemTenantApi.Tenant>>(
    '/system/tenant/page',
    { params },
  );
}

export function getSimpleTenantList() {
  return requestClient.get<SystemTenantApi.Tenant[]>(
    '/system/tenant/simple-list',
  );
}

export function getTenantIdByName(name: string) {
  return requestClient.get<number>('/system/tenant/get-id-by-name', {
    params: { name },
  });
}

export function getTenant(id: number) {
  return requestClient.get<SystemTenantApi.Tenant>(
    `/system/tenant/get?id=${id}`,
  );
}

export function getTenantList() {
  return requestClient.get<SystemTenantApi.Tenant[]>(
    '/system/tenant/simple-list',
  );
}

export function createTenant(data: SystemTenantApi.Tenant) {
  return requestClient.post('/system/tenant/create', data);
}

export function updateTenant(data: SystemTenantApi.Tenant) {
  return requestClient.put('/system/tenant/update', data);
}

export function deleteTenant(id: number) {
  return requestClient.delete(`/system/tenant/delete?id=${id}`);
}

export function deleteTenantList(ids: number[]) {
  return requestClient.delete(
    `/system/tenant/delete-list?ids=${ids.join(',')}`,
  );
}

export function exportTenant(params: any) {
  return requestClient.download('/system/tenant/export-excel', {
    params,
  });
}
