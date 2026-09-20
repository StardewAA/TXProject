import { requestClient } from '#/api/request';

export namespace HrmContractTemplateApi {
  /** 合同模板 */
  export interface ContractTemplate {
    id?: number; // 编号
    name?: string; // 模板名称
    category?: number; // 模板分类：1 劳动合同，2 个人电子档案，3 培训协议
    categoryName?: string; // 模板分类名称
    contractType?: number; // 合同类型
    employType?: string; // 适用用工形式
    employerId?: number; // 适用用工主体编号
    employerName?: string; // 适用用工主体名称
    version?: number; // 版本号
    status?: number; // 状态：0 启用，1 停用
    defaultStatus?: boolean; // 是否默认模板
    fileUrl?: string; // 模板文件地址（docx）
    thumbnailUrl?: string; // 缩略图地址（PNG）
    previewUrl?: string; // 预览文件地址（PDF）
    fileHash?: string; // 模板文件 SHA-256
    sort?: number; // 显示顺序
    sourceDir?: string; // 来源目录
    variables?: string; // 模板变量清单
    changeNote?: string; // 本次变更说明
    changeUserId?: number; // 变更人编号
    changeTime?: Date; // 变更时间
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }
}

/** 查询合同模板列表（可按分类筛选） */
export function getContractTemplateList(category?: number) {
  return requestClient.get<HrmContractTemplateApi.ContractTemplate[]>(
    '/hrm/contract/template/list',
    { params: category === undefined ? {} : { category } },
  );
}

/** 查询合同模板详情 */
export function getContractTemplate(id: number) {
  return requestClient.get<HrmContractTemplateApi.ContractTemplate>(
    '/hrm/contract/template/get',
    { params: { id } },
  );
}

/** 保存合同模板（新增） */
export function saveContractTemplate(
  data: HrmContractTemplateApi.ContractTemplate,
) {
  return requestClient.post<number>('/hrm/contract/template/save', data);
}

/** 更新合同模板（模板文件变更时后端自动升版本） */
export function updateContractTemplate(
  data: HrmContractTemplateApi.ContractTemplate,
) {
  return requestClient.put<boolean>('/hrm/contract/template/update', data);
}

/** 删除合同模板 */
export function deleteContractTemplate(id: number) {
  return requestClient.delete<boolean>('/hrm/contract/template/delete', {
    params: { id },
  });
}

/** 获得模板文件下载地址 */
export function getContractTemplateDownloadUrl(id: number) {
  return requestClient.get<string>('/hrm/contract/template/download-url', {
    params: { id },
  });
}
