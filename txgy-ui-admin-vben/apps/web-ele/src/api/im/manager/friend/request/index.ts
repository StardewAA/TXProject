import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ImManagerFriendRequestApi {
  /** 好友申请 */
  export interface FriendRequest {
    id: number;
    fromUserId: number;
    fromNickname?: string;
    toUserId: number;
    toNickname?: string;
    applyContent?: string;
    displayName?: string;
    addSource?: number;
    handleResult: number;
    handleContent?: string;
    handleTime?: Date;
    createTime: Date;
  }
}

/** 获得好友申请分页 */
export function getManagerFriendRequestPage(params: PageParam) {
  return requestClient.get<PageResult<ImManagerFriendRequestApi.FriendRequest>>(
    '/im/manager/friend-request/page',
    { params },
  );
}

/** 获得好友申请详情 */
export function getManagerFriendRequest(id: number) {
  return requestClient.get<ImManagerFriendRequestApi.FriendRequest>(
    `/im/manager/friend-request/get?id=${id}`,
  );
}

/** 删除好友申请记录（治理动作） */
export function deleteManagerFriendRequest(id: number) {
  return requestClient.delete(`/im/manager/friend-request/delete?id=${id}`);
}
