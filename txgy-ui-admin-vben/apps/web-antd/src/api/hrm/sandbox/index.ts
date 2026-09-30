import { requestClient } from '#/api/request';

export namespace HrmSandboxApi {
  /** 沙箱数据登记记录（I-025） */
  export interface SandboxRecord {
    id?: number; // 编号
    bizType?: string; // 业务类型：post / leave
    bizId?: number; // 业务主键
    bizName?: string; // 业务名称
    description?: string; // 说明
    createTime?: Date; // 登记时间
  }
}

/** 获得沙箱数据登记列表 */
export function getSandboxRecordList() {
  return requestClient.get<HrmSandboxApi.SandboxRecord[]>(
    '/hrm/sandbox/list',
  );
}

/** 生成演示数据剧本（草稿职位 + 演示请假单） */
export function generateSandboxDemo() {
  return requestClient.post<number>('/hrm/sandbox/generate');
}

/** 一键清场：清理沙箱清单里的演示数据 */
export function clearSandbox() {
  return requestClient.delete<number>('/hrm/sandbox/clear');
}
