import { requestClient } from '#/api/request';

export namespace MallShopApi {
  export interface Shop {
    id?: number;
    enterpriseId?: number;
    enterpriseName?: string;
    tenantId?: number;
    name: string;
    logoUrl?: string;
    contactName?: string;
    contactMobile?: string;
    managerUserId?: number;
    introduction?: string;
    status?: number;
  }
}

export function createShop(data: MallShopApi.Shop) {
  return requestClient.post('/product/shop/create', data);
}

export function updateShop(data: MallShopApi.Shop) {
  return requestClient.put('/product/shop/update', data);
}

export function deleteShop(id: number) {
  return requestClient.delete(`/product/shop/delete?id=${id}`);
}

export function getShop(id: number) {
  return requestClient.get<MallShopApi.Shop>(`/product/shop/get?id=${id}`);
}

export function getShopList() {
  return requestClient.get<MallShopApi.Shop[]>('/product/shop/list');
}

/**
 * 获得当前后台账号可经营或监管的店铺范围。
 *
 * 该接口由服务端结合租户、企业、店铺授权和业务权限过滤，商品经营页
 * 不应使用租户全量店铺列表代替它。
 */
export function getBusinessScopeShopList() {
  return requestClient.get<MallShopApi.Shop[]>(
    '/product/admin-business-scope/shops',
  );
}
