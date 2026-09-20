import { requestClient } from '#/api/request';

export namespace HrmEmployeeFileApi {
  /** 员工附件 */
  export interface EmployeeFile {
    id?: number; // 附件编号
    employeeId?: number; // 员工编号
    type?: number; // 附件类型
    url?: string; // 附件地址
    createTime?: Date; // 创建时间
  }
  /** 保存请求 */
  export interface SaveReq {
    employeeId: number; // 员工编号
    type: number; // 附件类型
    fileUrls: string[]; // 附件地址数组
  }
}

/** 查询员工附件列表 */
export function getEmployeeFileList(employeeId: number) {
  return requestClient.get<HrmEmployeeFileApi.EmployeeFile[]>(
    '/hrm/employee/file/list',
    { params: { employeeId } },
  );
}

/** 保存员工附件 */
export function saveEmployeeFiles(data: HrmEmployeeFileApi.SaveReq) {
  return requestClient.put<boolean>('/hrm/employee/file/save', data);
}

/** 按模板生成员工入职档案与培训协议文档（返回本次新增归档的文档地址） */
export function generateOnboardDocs(employeeId: number) {
  return requestClient.post<string[]>('/hrm/employee/onboard-doc/generate', null, {
    params: { employeeId },
  });
}
