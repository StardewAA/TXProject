import { requestClient } from '#/api/request';

export namespace ErpAssistantApi {
  /** 助手回答里的可跳转入口 */
  export interface AssistantAction {
    label: string; // 按钮文案
    path: string; // 前端路由
    hint?: string; // 补充说明
  }

  /** 助手回答 */
  export interface ChatRespVO {
    answer: string; // 回答内容
    intent?: string; // 命中的意图
    actions?: AssistantAction[]; // 可跳转入口
  }
}

/**
 * 与 ERP 智能助手对话
 *
 * 后端基于真实业务数据回答（找单据 / 产品 / 客商、查库存、查经营统计、看待办）
 */
export function chatWithAssistant(message: string) {
  return requestClient.post<ErpAssistantApi.ChatRespVO>('/erp/assistant/chat', {
    message,
  });
}
