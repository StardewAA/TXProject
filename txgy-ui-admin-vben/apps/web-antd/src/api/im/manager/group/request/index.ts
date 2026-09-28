import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ImManagerGroupRequestApi {
  /** 加群申请 */
  export interface GroupRequest {
    id: number;
    groupId: number;
    groupName?: string;
    userId: number;
    userNickname?: string;
    inviterUserId?: number;
    inviterNickname?: string;
    applyContent?: string;
    addSource?: number;
    handleResult: number;
    handleUserId?: number;
    handleNickname?: string;
    handleContent?: string;
    handleTime?: Date;
    createTime: Date;
  }
}

/** 获得加群申请分页 */
export function getManagerGroupRequestPage(params: PageParam) {
  return requestClient.get<PageResult<ImManagerGroupRequestApi.GroupRequest>>(
    '/im/manager/group-request/page',
    { params },
  );
}

/** 获得加群申请详情 */
export function getManagerGroupRequest(id: number) {
  return requestClient.get<ImManagerGroupRequestApi.GroupRequest>(
    `/im/manager/group-request/get?id=${id}`,
  );
}

/** 删除加群申请记录（治理动作） */
export function deleteManagerGroupRequest(id: number) {
  return requestClient.delete(`/im/manager/group-request/delete?id=${id}`);
}
