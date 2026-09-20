import type { PageResult } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const accessStore = useAccessStore();

export namespace AiChatMessageApi {
  export interface ChatMessage {
    id: number; // 编号
    conversationId: number; // 对话编号
    type: string; // 消息类型
    userId: string; // 用户编号
    roleId: string; // 角色编号
    model: number; // 模型标志
    modelId: number; // 模型编号
    content: string; // 聊天内容
    reasoningContent?: string; // 推理内容（深度思考）
    tokens: number; // 消耗 Token 数量
    segmentIds?: number[]; // 段落编号
    segments?: {
      content: string; // 段落内容
      documentId: number; // 文档编号
      documentName: string; // 文档名称
      id: number; // 段落编号
    }[];
    webSearchPages?: WebSearchPage[]; // 联网搜索结果
    attachmentUrls?: string[]; // 附件 URL 数组
    createTime: Date; // 创建时间
    roleAvatar: string; // 角色头像
    userAvatar: string; // 用户头像
  }

  /** 联网搜索页面接口 */
  export interface WebSearchPage {
    name: string; // 网站名称
    icon: string; // 网站图标 URL
    title: string; // 页面标题
    url: string; // 页面 URL
    snippet: string; // 简短描述
    summary: string; // 内容摘要
  }
}

/** 消息列表 */
export function getChatMessageListByConversationId(
  conversationId: null | number,
) {
  return requestClient.get<AiChatMessageApi.ChatMessage[]>(
    `/ai/chat/message/list-by-conversation-id?conversationId=${conversationId}`,
  );
}

/**
 * [SSE 修复] 基于原生 fetch 的 SSE 读取器，替代 @microsoft/fetch-event-source。
 *
 * 背景：原库收到 Content-Type 非 text/event-stream 的响应（例如后端全局异常处理器把
 * 流式链路的错误包装成 application/json 返回）时，会直接抛英文 TypeError
 * "Expected content-type to be text/event-stream, Actual: application/json;charset=UTF-8"，
 * 且默认按 retryInterval 无限重试，用户侧只能看到原始英文报错、看不到具体原因。
 *
 * 本实现：
 *  1. 先显式校验 Content-Type：非 SSE 时尽力解析 JSON body 的业务 msg 并转中文提示，
 *     一次请求即结束、不自动重试、不泄漏英文底层错误；
 *  2. 按 SSE 协议手工切帧（以空行分隔、逐行解析 data:），回调签名与 fetchEventSource
 *     的 onmessage({ data })/onerror/onclose 保持一致，对既有调用方完全透明。
 */
async function postSseStream(
  url: string,
  bodyJson: string,
  headers: Record<string, string>,
  signal: AbortSignal | undefined,
  onMessage: (e: { data: string }) => void,
  onError: (err: unknown) => void,
  onClose: () => void,
): Promise<void> {
  // 安全包装：调用方回调内抛错（如“禁止重试”的 throw）不得产生 unhandled rejection
  const safeCall = (fn: ((arg: unknown) => void) | undefined, arg: unknown) => {
    try {
      fn?.(arg);
    } catch {
      /* 忽略回调自身异常 */
    }
  };

  let resp: Response;
  try {
    resp = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...headers,
      },
      body: bodyJson,
      signal,
    });
  } catch (err) {
    // 网络错误 / 主动 abort（AbortController.abort 触发重新开始会话）
    safeCall(onError, err);
    return;
  }

  // ===== [SSE 修复] Content-Type 显式校验：非 SSE 一律转友好中文提示 =====
  const contentType = (resp.headers.get('content-type') || '').toLowerCase();
  if (!contentType.includes('text/event-stream')) {
    let bizMsg = '';
    try {
      bizMsg = ((await resp.json()) as any)?.msg || '';
    } catch {
      // 响应体可能不是合法 JSON，忽略
    }
    const err: any = new Error(bizMsg || '数据查询接口异常，请稍后重试');
    err.name = 'SseContentTypeError';
    safeCall(onError, err);
    return;
  }

  const reader = resp.body?.getReader();
  if (!reader) {
    safeCall(onError, new Error('读取响应失败，请稍后重试'));
    return;
  }
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    // 读取响应字节流，按 SSE 帧（\n\n 空行分隔）提取 data: 行
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let sep: number;
      while ((sep = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, sep);
        buffer = buffer.slice(sep + 2);
        const dataLines = frame.split('\n').filter((line) => line.startsWith('data:'));
        if (dataLines.length > 0) {
          safeCall(onMessage, {
            data: dataLines.map((line) => line.slice(5).replace(/^\s/, '')).join('\n'),
          });
        }
      }
    }
    safeCall(onClose); // 服务端流正常结束
  } catch (err) {
    // 流中途断开 / 中途 abort
    safeCall(onError, err);
  }
}

/** 发送 Stream 消息 */
export function sendChatMessageStream(
  conversationId: number,
  content: string,
  ctrl: any,
  enableContext: boolean,
  enableWebSearch: boolean,
  onMessage: any,
  onError: any,
  onClose: any,
  attachmentUrls?: string[],
): Promise<void> {
  const token = accessStore.accessToken;
  // 重要：SSE 走原生 fetch，不经过 requestClient 的 axios 请求拦截器，
  // 必须手动补齐租户头，否则多租户安全过滤链（TenantSecurityWebFilter）会拒绝该请求，
  // 表现为「消息无法发送 / 流式连接建立失败」。
  // 租户编号逻辑与 src/api/request.ts 保持一致：优先登录解析的租户，回退默认租户。
  const tenantId =
    accessStore.tenantId ??
    Number(import.meta.env.VITE_APP_DEFAULT_TENANT_ID || 1);
  return postSseStream(
    `${apiURL}/ai/chat/message/send-stream`,
    JSON.stringify({
      conversationId,
      content,
      useContext: enableContext,
      useSearch: enableWebSearch,
      attachmentUrls: attachmentUrls || [],
    }),
    {
      Authorization: `Bearer ${token}`,
      'tenant-id': String(tenantId),
      'visit-tenant-id': String(tenantId),
    },
    ctrl?.signal,
    onMessage,
    onError,
    onClose,
  );
}

/** 删除消息 */
export function deleteChatMessage(id: number) {
  return requestClient.delete(`/ai/chat/message/delete?id=${id}`);
}

/** 删除指定对话的消息 */
export function deleteByConversationId(conversationId: number) {
  return requestClient.delete(
    `/ai/chat/message/delete-by-conversation-id?conversationId=${conversationId}`,
  );
}

/** 获得消息分页 */
export function getChatMessagePage(params: any) {
  return requestClient.get<PageResult<AiChatMessageApi.ChatMessage>>(
    '/ai/chat/message/page',
    { params },
  );
}

/** 管理员删除消息 */
export function deleteChatMessageByAdmin(id: number) {
  return requestClient.delete(`/ai/chat/message/delete-by-admin?id=${id}`);
}
