import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace AiChatConversationApi {
  export interface ChatConversation {
    id: number; // ID 编号
    userId: number; // 用户编号
    title: string; // 对话标题
    pinned: boolean; // 是否置顶
    roleId: number; // 角色编号
    modelId: number; // 模型编号
    model: string; // 模型标志
    temperature: number; // 温度参数
    maxTokens: number; // 单条回复的最大 Token 数量
    maxContexts: number; // 上下文的最大 Message 数量
    createTime: Date; // 创建时间
    systemMessage?: string; // 角色设定
    modelName?: string; // 模型名字
    roleAvatar?: string; // 角色头像
    modelMaxTokens?: string; // 模型的单条回复的最大 Token 数量
    modelMaxContexts?: string; // 模型的上下文的最大 Message 数量
  }
}

/**
 * 获得【我的】聊天对话
 *
 * 注意：对话 id 是 19 位雪花号，后端按字符串下发，**必须原样透传**。
 * 一旦用 Number() 转换会因 JS 双精度丢末位（…938 → …900），后端即查不到该对话。
 */
export function getChatConversationMy(id: number | string) {
  return requestClient.get<AiChatConversationApi.ChatConversation>(
    `/ai/chat/conversation/get-my?id=${id}`,
  );
}

/** 新增【我的】聊天对话 */
export function createChatConversationMy(
  data: AiChatConversationApi.ChatConversation,
) {
  return requestClient.post('/ai/chat/conversation/create-my', data);
}

/** 更新【我的】聊天对话 */
export function updateChatConversationMy(
  data: AiChatConversationApi.ChatConversation,
) {
  return requestClient.put(`/ai/chat/conversation/update-my`, data);
}

/** 删除【我的】聊天对话（id 是 19 位雪花号，务必原样透传，切勿 Number() 转换） */
export function deleteChatConversationMy(id: number | string) {
  return requestClient.delete(`/ai/chat/conversation/delete-my?id=${id}`);
}

/** 删除【我的】所有对话，置顶除外 */
export function deleteChatConversationMyByUnpinned() {
  return requestClient.delete(`/ai/chat/conversation/delete-by-unpinned`);
}

/** 获得【我的】聊天对话列表 */
export function getChatConversationMyList() {
  return requestClient.get<AiChatConversationApi.ChatConversation[]>(
    `/ai/chat/conversation/my-list`,
  );
}

/** 获得【我的】聊天对话列表 */
export function getChatConversationPage(params: any) {
  return requestClient.get<
    PageResult<AiChatConversationApi.ChatConversation[]>
  >(`/ai/chat/conversation/page`, { params });
}

/** 管理员删除消息（id 是 19 位雪花号，同样需要原样透传） */
export function deleteChatConversationByAdmin(id: number | string) {
  return requestClient.delete(`/ai/chat/conversation/delete-by-admin?id=${id}`);
}
