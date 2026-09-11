import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ImManagerFriendApi {
  /** 好友关系 */
  export interface Friend {
    id: number;
    userId: number;
    userNickname?: string;
    friendUserId: number;
    friendNickname?: string;
    displayName?: string;
    addSource?: number;
    silent: boolean;
    pinned: boolean;
    blocked: boolean;
    status: number;
    addTime?: Date;
    deleteTime?: Date;
    createTime: Date;
  }
}

/** 获得好友关系分页 */
export function getManagerFriendPage(params: PageParam) {
  return requestClient.get<PageResult<ImManagerFriendApi.Friend>>(
    '/im/manager/friend/page',
    { params },
  );
}

/** 获得好友关系详情 */
export function getManagerFriend(id: number) {
  return requestClient.get<ImManagerFriendApi.Friend>(
    `/im/manager/friend/get?id=${id}`,
  );
}

/** 解除好友关系（双向删除） */
export function deleteManagerFriend(id: number) {
  return requestClient.delete(`/im/manager/friend/delete?id=${id}`);
}
