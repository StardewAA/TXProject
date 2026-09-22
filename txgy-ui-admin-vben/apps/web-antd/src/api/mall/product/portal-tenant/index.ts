import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MallPortalTenantApi {
  export interface PortalTenant {
    id: number;
    tenantId: number;
    tenantSlug: string;
    tenantName: string;
    tenantLogo?: string;
    mainCategories?: string;
    /** 0 开放，1 关闭 */
    openStatus: number;
    sort: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface PageParams extends PageParam {
    tenantId?: number;
    tenantName?: string;
    tenantSlug?: string;
    openStatus?: number;
  }

  export type UpdateParams = Pick<
    PortalTenant,
    | 'id'
    | 'mainCategories'
    | 'openStatus'
    | 'sort'
    | 'tenantLogo'
    | 'tenantSlug'
  >;
}

export function getPortalTenantPage(
  params: MallPortalTenantApi.PageParams,
) {
  return requestClient.get<PageResult<MallPortalTenantApi.PortalTenant>>(
    '/product/portal-tenant/page',
    { params },
  );
}

export function getPortalTenant(id: number) {
  return requestClient.get<MallPortalTenantApi.PortalTenant>(
    `/product/portal-tenant/get?id=${id}`,
  );
}

export function updatePortalTenant(data: MallPortalTenantApi.UpdateParams) {
  return requestClient.put('/product/portal-tenant/update', data);
}
