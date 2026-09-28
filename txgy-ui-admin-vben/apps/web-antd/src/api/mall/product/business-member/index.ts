import { requestClient } from '#/api/request';

export namespace MallBusinessMemberApi {
  export interface Shop {
    enterpriseId: number;
    id: number;
    logoUrl?: string;
    name: string;
    status: number;
  }

  export interface Member {
    adminUserId: number;
    enterpriseId: number;
    id: number;
    mobile?: string;
    nickname: string;
    /** 1 企业全部店铺；2 指定店铺。 */
    scopeType: number;
    shopIds: number[];
    /** 0 启用；1 停用。 */
    status: number;
  }

  export interface SaveMember {
    adminUserId: number;
    enterpriseId: number;
    scopeType: 1 | 2;
    shopIds: number[];
    status: 0 | 1;
  }
}

export function getBusinessScopeShops() {
  return requestClient.get<MallBusinessMemberApi.Shop[]>(
    '/product/admin-business-scope/shops',
  );
}

export function getBusinessMemberList(enterpriseId: number) {
  return requestClient.get<MallBusinessMemberApi.Member[]>(
    '/product/admin-business-member/list',
    { params: { enterpriseId } },
  );
}

export function saveBusinessMember(data: MallBusinessMemberApi.SaveMember) {
  return requestClient.put<boolean>('/product/admin-business-member/save', data);
}
