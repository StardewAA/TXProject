import { requestClient } from '#/api/request';

export namespace CrmGlobalSearchApi {
  /** 检索结果项 */
  export interface Item {
    id: number; // 编号
    name: string; // 名称
    bizType: string; // 业务类型
    bizTypeName: string; // 业务类型名称
    mobile?: string; // 手机
    telephone?: string; // 电话
    extra?: string; // 补充信息
    ownerUserId?: number; // 负责人编号
    ownerUserName?: string; // 负责人名字
  }

  /** 检索结果 */
  export interface Result {
    customers: Item[]; // 客户
    clues: Item[]; // 线索
    contacts: Item[]; // 联系人
    businesses: Item[]; // 商机
    contracts: Item[]; // 合同
    total: number; // 总数
  }
}

/** 全局检索：按公司名 / 手机号 / 电话 / 编号跨模块搜索 */
export function getGlobalSearch(keyword: string, limit = 5) {
  return requestClient.get<CrmGlobalSearchApi.Result>(
    '/crm/global-search/all',
    { params: { keyword, limit } },
  );
}
