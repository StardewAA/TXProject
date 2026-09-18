import { requestClient } from '#/api/request';

export namespace HrmPortalRecruitChatApi {
  export interface Conversation {
    id: number;
    postId: number;
    postName?: string;
    areaId?: number;
    areaName?: string;
    minSalary?: number;
    maxSalary?: number;
    salaryUnit?: number;
    hrUserId: number;
    candidateUserId: number;
    candidateId?: number;
    peerUserId?: number;
    peerUserName?: string;
    peerUserAvatar?: string;
    candidateName?: string;
    candidateSex?: number;
    candidateAge?: number;
    candidateWorkTime?: number;
    candidateEducation?: number;
    candidateEducationName?: string;
    candidateGraduateSchool?: string;
    hrSide?: boolean;
    status?: number;
    applyQualified?: boolean;
    applicationStatus?: 'READY_TO_SUBMIT' | 'SUBMITTED';
    applicationId?: number;
    hrReplyTime?: string;
    endTime?: string;
    endReason?: string;
    lastContent?: string;
    lastSenderUserId?: number;
    lastTime?: string;
    unreadCount?: number;
  }

  export interface ChatMessage {
    id: number;
    conversationId: number;
    senderUserId: number;
    contentType: number;
    content: string;
    sendTime?: string;
    mine?: boolean;
  }

  export interface SendReq {
    conversationId: number;
    contentType?: number;
    content: string;
  }
}

export function openRecruitChatConversation(postId: number) {
  return requestClient.post<HrmPortalRecruitChatApi.Conversation>(
    '/hrm/portal/recruit/chat/conversation/open',
    { postId },
    { headers: { 'X-Silent-Error': 'true' } },
  );
}

export function getRecruitChatConversationList() {
  return requestClient.get<HrmPortalRecruitChatApi.Conversation[]>(
    '/hrm/portal/recruit/chat/conversation/list',
  );
}

export function getRecruitChatMessageList(params: {
  conversationId: number;
  minId?: number;
  limit?: number;
}) {
  return requestClient.get<HrmPortalRecruitChatApi.ChatMessage[]>(
    '/hrm/portal/recruit/chat/message/list',
    { params },
  );
}

export function sendRecruitChatMessage(data: HrmPortalRecruitChatApi.SendReq) {
  return requestClient.post<HrmPortalRecruitChatApi.ChatMessage>(
    '/hrm/portal/recruit/chat/message/send',
    data,
  );
}

export function markRecruitChatRead(conversationId: number) {
  return requestClient.put<boolean>(
    '/hrm/portal/recruit/chat/conversation/read',
    undefined,
    { params: { conversationId } },
  );
}

export function endRecruitChatConversation(
  conversationId: number,
  reason: string,
) {
  return requestClient.put<boolean>(
    '/hrm/portal/recruit/chat/conversation/end',
    { conversationId, reason },
  );
}

export function getAdminRecruitChatConversationList(params?: {
  candidateId?: number;
  endTime?: string;
  postId?: number;
  startTime?: string;
  status?: number;
}) {
  return requestClient.get<HrmPortalRecruitChatApi.Conversation[]>(
    '/hrm/recruit/chat/conversation/list',
    { params },
  );
}

export function getAdminRecruitChatMessageList(params: {
  conversationId: number;
  minId?: number;
  limit?: number;
}) {
  return requestClient.get<HrmPortalRecruitChatApi.ChatMessage[]>(
    '/hrm/recruit/chat/message/list',
    { params },
  );
}

export function sendAdminRecruitChatMessage(data: HrmPortalRecruitChatApi.SendReq) {
  return requestClient.post<HrmPortalRecruitChatApi.ChatMessage>(
    '/hrm/recruit/chat/message/send',
    data,
  );
}

export function markAdminRecruitChatRead(conversationId: number) {
  return requestClient.put<boolean>(
    '/hrm/recruit/chat/conversation/read',
    undefined,
    { params: { conversationId } },
  );
}

export function convertAdminRecruitChatConversationToCandidate(conversationId: number) {
  return requestClient.post<number>(
    '/hrm/recruit/chat/conversation/convert-candidate',
    undefined,
    { params: { conversationId } },
  );
}

export function deleteAdminRecruitChatConversation(id: number) {
  return requestClient.delete<boolean>('/hrm/recruit/chat/conversation/delete', {
    params: { id },
  });
}

export function deleteAdminRecruitChatConversationList(ids: number[]) {
  return requestClient.delete<boolean>(
    '/hrm/recruit/chat/conversation/delete-list',
    { params: { ids: ids.join(',') } },
  );
}
