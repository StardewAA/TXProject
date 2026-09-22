import { requestClient } from '#/api/request';

export namespace HrmCompanyProfileApi {
  /** 公司信息（用工主体） */
  export interface CompanyProfile {
    id?: number; // 编号
    name?: string; // 公司名称（用工主体）
    creditCode?: string; // 统一社会信用代码
    address?: string; // 住所
    legalPerson?: string; // 法定代表人
    establishDate?: string; // 成立日期
    registeredCapital?: string; // 注册资本
    companyType?: string; // 企业类型
    businessScope?: string; // 经营范围
    insuredCount?: number; // 参保人数
    phone?: string; // 联系电话
    bankName?: string; // 开户银行
    bankAccount?: string; // 银行账号
    commercialInsurance?: string; // 商业保险说明
    defaultStatus?: boolean; // 是否默认主体
    sort?: number; // 显示顺序
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }
}

/** 查询公司信息列表 */
export function getCompanyProfileList() {
  return requestClient.get<HrmCompanyProfileApi.CompanyProfile[]>(
    '/hrm/company/profile/list',
  );
}

/** 查询默认用工主体 */
export function getDefaultCompanyProfile() {
  return requestClient.get<HrmCompanyProfileApi.CompanyProfile>(
    '/hrm/company/profile/get-default',
  );
}

/** 保存公司信息（新增或更新） */
export function saveCompanyProfile(
  data: HrmCompanyProfileApi.CompanyProfile,
) {
  return requestClient.post<number>('/hrm/company/profile/save', data);
}

/** 删除公司信息 */
export function deleteCompanyProfile(id: number) {
  return requestClient.delete<boolean>('/hrm/company/profile/delete', {
    params: { id },
  });
}
