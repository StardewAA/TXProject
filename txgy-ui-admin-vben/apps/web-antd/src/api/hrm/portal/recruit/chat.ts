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
    /** AI 应聘匹配分 0-100，该会话尚未分析过时为空 */
    aiMatchScore?: number;
    /** AI 匹配分析的任务编号，用于查看完整结论 */
    aiTaskId?: number;
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

  /** AI 自然语言筛选的结果 */
  export interface AiFilterResult {
    /** AI 对筛选要求的复述，用于确认它理解得对不对 */
    criteria: string;
    matchedConversationIds: number[];
    totalCandidates: number;
    /** 模型无法翻译成条件的部分，原样回显 */
    unsupported?: string[];
    taskId?: number;
    model?: string;
    promptVersion?: string;
  }

  /** 沟通会话中求职者的在线简历 */
  export interface CandidateResume {
    /** SNAPSHOT 投递时固化的快照，ONLINE 求职者当前在线简历 */
    source: 'ONLINE' | 'SNAPSHOT';
    name?: string;
    title?: string;
    /** 在线简历结构化内容 JSON */
    content: string;
    /** 后端 LocalDateTime 序列化为毫秒时间戳 */
    updateTime?: number | string;
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
  limit?: number;
  minId?: number;
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
  limit?: number;
  minId?: number;
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

/** 查看某条沟通会话中求职者的在线简历（优先投递快照，其次当前在线简历） */
export function getAdminRecruitChatCandidateResume(conversationId: number) {
  return requestClient.get<HrmPortalRecruitChatApi.CandidateResume>(
    '/hrm/recruit/chat/conversation/candidate-resume',
    { params: { conversationId } },
  );
}

/** 对沟通会话中的求职者生成 AI 应聘匹配分析，返回任务编号 */
export function analyzeConversationAiMatch(data: {
  conversationId: number;
  idempotencyKey: string;
}) {
  return requestClient.post<number>(
    '/hrm/recruit/chat/conversation/ai-match',
    data,
  );
}

/**
 * 按一句自然语言筛选沟通中的求职者。
 *
 * 服务端只把这句话交给模型翻译成条件，命中判断在服务端用本地数据完成。
 */
export function aiFilterConversations(data: {
  idempotencyKey: string;
  query: string;
}) {
  return requestClient.post<HrmPortalRecruitChatApi.AiFilterResult>(
    '/hrm/recruit/chat/conversation/ai-filter',
    data,
  );
}
