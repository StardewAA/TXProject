/**
 * WMS AI 历史对话本地持久化
 *
 * 设计说明：
 * WMS AI 后端（/wms/ai/recognize）的多轮上下文存在服务端内存 Map 里，
 * 以 sessionId 为 key、10 分钟过期，且没有对外提供会话查询接口；
 * 平台自带的 ai_chat_conversation 表属于另一套（/ai/chat）子系统，
 * 与 WMS AI 的 sessionId 体系不互通。
 *
 * 因此历史对话采用「前端本地持久化」方案：
 * - 每个会话保存自己的 sessionId（切回会话时继续用同一个 sessionId，
 *   后端内存上下文若还在则多轮仍可延续）；
 * - 同时保存完整消息记录，切回时会话内容立即可见。
 * 这样既不改动任何后端接口，也不影响主菜单与其他功能。
 */

import type { WmsAiApi } from '#/api/wms/ai';

const STORAGE_KEY = 'wmsAiConversations';
const MAX_CONVERSATIONS = 100;

export interface AiAttachment {
  name: string;
  size?: number;
  url: string;
}

export interface AiChatMessage {
  id: string;
  role: 'ai' | 'user';
  text: string;
  images?: AiAttachment[];
  docs?: AiAttachment[];
  action?: string;
  /** 单据子类型：100 生产入库/101 采购入库/102 退货入库/103 归还入库；200 退货出库/201 销售出库/202 生产出库 */
  type?: number;
  /** 识别到的仓库名称 */
  warehouse?: string;
  /** 目标仓库 id（待确认卡片里可修改） */
  warehouseId?: number;
  /** 识别到的供应商/客户名称 */
  merchant?: string;
  /** 供应商/客户 id（待确认卡片里可修改） */
  merchantId?: number;
  /** 备注（建单时可填写） */
  remark?: string;
  /** 待确认明细（结构化，可在卡片中编辑数量/单价/规格后建单） */
  items?: WmsAiApi.ItemVO[];
  buildable?: boolean;
  orderNo?: string;
  orderId?: number;
  /** 是否为欢迎语（不参与持久化） */
  welcome?: boolean;
  /** 是否为权限拒绝提示（用于特殊样式展示） */
  permission?: boolean;
}

export interface AiConversation {
  id: string;
  /** 后端多轮上下文标识 */
  sessionId: string;
  title: string;
  pinned?: boolean;
  createTime: number;
  updateTime: number;
  messages: AiChatMessage[];
}

function safeParse(raw: null | string): AiConversation[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is AiConversation =>
        !!item && typeof item.id === 'string' && Array.isArray(item.messages),
    );
  } catch {
    return [];
  }
}

export function loadConversations(): AiConversation[] {
  return safeParse(localStorage.getItem(STORAGE_KEY));
}

export function saveConversations(list: AiConversation[]) {
  // 只保留必要字段，避免 localStorage 超限（图片 base64 体积大，裁剪掉历史附件）
  const slim = (n: number) =>
    list.slice(0, n).map((c) => ({
      ...c,
      messages: c.messages
        .filter((m) => !m.welcome)
        .map((m) => ({
          ...m,
          // 附件 url 可能是 base64，体积大，持久化时只留文件名
          docs: m.docs?.map((d) => ({ name: d.name, size: d.size, url: '' })),
          images: undefined,
        })),
    }));

  // 写入失败（典型为 QuotaExceededError 超配额）时，按「砍半会话数」逐级降级重试，
  // 优先保住最新的会话，而不是整体放弃导致最新记录静默丢失
  let keep = Math.min(list.length, MAX_CONVERSATIONS);
  while (keep >= 0) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(slim(keep)));
      return;
    } catch (error) {
      console.warn(`[wms-ai] 保存历史对话失败（本次尝试保留 ${keep} 条）`, error);
      if (keep <= 1) break;
      keep = Math.floor(keep / 2);
    }
  }
}

export function newSessionId() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export function createConversation(): AiConversation {
  const now = Date.now();
  return {
    id: `c_${now}_${Math.random().toString(36).slice(2, 8)}`,
    sessionId: newSessionId(),
    title: '新对话',
    createTime: now,
    updateTime: now,
    messages: [],
  };
}

/** 用该会话第一条用户消息生成标题 */
export function deriveTitle(text: string) {
  const t = (text || '').trim().replaceAll(/\s+/g, ' ');
  if (!t) return '新对话';
  return t.length > 18 ? `${t.slice(0, 18)}…` : t;
}

/** 按时间分组，供侧边栏展示 */
export function groupByTime(list: AiConversation[]) {
  const groups: Record<string, AiConversation[]> = {
    置顶: [],
    今天: [],
    一天前: [],
    三天前: [],
    七天前: [],
    三十天前: [],
  };
  const now = Date.now();
  const oneDay = 24 * 60 * 60 * 1000;
  const sorted = [...list].sort((a, b) => b.updateTime - a.updateTime);
  for (const c of sorted) {
    if (c.pinned) {
      groups['置顶']!.push(c);
      continue;
    }
    const diff = now - c.updateTime;
    if (diff < oneDay) groups['今天']!.push(c);
    else if (diff < oneDay * 3) groups['一天前']!.push(c);
    else if (diff < oneDay * 7) groups['三天前']!.push(c);
    else if (diff < oneDay * 30) groups['七天前']!.push(c);
    else groups['三十天前']!.push(c);
  }
  return groups;
}
