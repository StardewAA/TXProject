import { requestClient } from '#/api/request';

export namespace WmsAiApi {
  /** 识别请求 */
  export interface RecognizeReqVO {
    text?: string;
    files?: FileDoc[];
    images?: string[];
    sessionId?: string;
  }

  /** 文件 */
  export interface FileDoc {
    name: string;
    url: string;
  }

  /** 识别出的商品明细 */
  export interface ItemVO {
    /** 商品名称 */
    name: string;
    /** 规格 */
    spec?: string;
    /** 数量 */
    quantity: number;
    /** 单位 */
    unit?: string;
    /** 单价 */
    price?: number;
    /** 重量 */
    weight?: number;
    /** 是否已匹配到主数据 */
    resolved?: boolean;
    /** 商品编号 */
    itemId?: number;
    /** 商品 SKU 编号 */
    skuId?: number;
    /** 候选列表（未匹配到时给出） */
    candidates?: ItemVO[];
  }

  /** 识别结果 */
  /** 主数据实体（仓库 / 企业等）：对应后端 AiEntity */
  export interface EntityVO {
    /** 是否已唯一命中 */
    resolved?: boolean;
    /** 主键编号 */
    id?: number;
    /** 编码 */
    code?: string;
    /** 展示名称 */
    name?: string;
  }

  export interface RecognizeRespVO {
    /** 100 正常 */
    code: number;
    /** 回复文案 */
    reply: string;
    /** 单据类型：IN 入库 / OUT 出库 / MOVE 移库 / CHECK 盘库 / null 闲聊 */
    action?: string;
    /** 单据子类型 */
    type?: number;
    /**
     * 仓库实体（入库=目标仓、出库=来源仓、盘库=盘库仓）。
     * 注意后端返回的是 { id, name, resolved } 对象，不是字符串。
     */
    warehouse?: EntityVO;
    /** 移库专用：移出仓库 */
    sourceWarehouse?: EntityVO;
    /** 移库专用：移入仓库 */
    targetWarehouse?: EntityVO;
    /** 供应商/客户实体（同样是 { id, name, resolved } 对象） */
    merchant?: EntityVO;
    /** 商品明细 */
    items?: ItemVO[];
    /** 提示信息 */
    tips?: string[];
    /** 是否可以建单 */
    buildable?: boolean;
    /** 会话编号 */
    sessionId?: string;
  }

  /** 建单请求 */
  export interface CreateOrderReqVO {
    sessionId?: string;
    action: string;
    type?: number;
    /** 仓库：入库=目标仓、出库=来源仓、移库=移出仓、盘库=盘库仓 */
    warehouseId?: number;
    /** 移库目标仓库编号（仅 MOVE 需要） */
    targetWarehouseId?: number;
    merchantId?: number;
    items: ItemVO[];
    remark?: string;
  }

  /** 建单结果 */
  export interface CreateOrderRespVO {
    code: number;
    reply: string;
    /** 单据编号 */
    orderNo?: string;
    /** 单据主键 */
    orderId?: number;
  }

  /** AI 辅助审批建议 —— 请求 */
  export interface ApprovalAdviceReqVO {
    /** 单据编号 */
    orderId: number;
    /** RECEIPT 入库 / SHIPMENT 出库 */
    orderType: 'RECEIPT' | 'SHIPMENT' | string;
  }

  /** AI 辅助审批建议 —— 风险项 */
  export interface ApprovalRisk {
    /** HIGH 高 / MEDIUM 中 / LOW 低 */
    level?: string;
    title?: string;
    detail?: string;
  }

  /** AI 辅助审批建议 —— 响应 */
  export interface ApprovalAdviceRespVO {
    /** APPROVE 建议通过 / REJECT 建议驳回 / MANUAL 需要人工判断 */
    suggestion?: string;
    suggestionLabel?: string;
    summary?: string;
    risks?: ApprovalRisk[];
    reason?: string;
    /** 是否成功拿到建议 */
    ok?: boolean;
    errorMessage?: string;
    detailCount?: number;
  }

  /** 会话 */
  export interface Conversation {
    id: number;
    title: string;
    pinned?: boolean;
    createTime?: string;
  }

  /** 消息 */
  export interface ChatMessage {
    id: number;
    conversationId: number;
    type?: number;
    content: string;
    createTime?: string;
  }
}

/**
 * AI 识别的统一超时时间。
 *
 * request-client 默认超时是 30 秒，但 AI 识别需要调用大模型：
 * 简单问候约 8 秒，带数据查询约 50 秒，识别失败兜底时可能到 140 秒以上。
 * 后端 WmsAiServiceImpl/WmsAiChatServiceImpl 的 HttpClient 超时是 240 秒，
 * 前端取 280 秒（略大于后端，避免请求还在处理就被前端切断），否则前端会先超时报「识别出错了：timeout of 30000ms exceeded」。
 */
const AI_TIMEOUT = 280_000;

/** 语音/文字 + 图片识别 */
export function recognize(data: WmsAiApi.RecognizeReqVO) {
  return requestClient.post<WmsAiApi.RecognizeRespVO>(
    '/wms/ai/recognize',
    data,
    { timeout: AI_TIMEOUT },
  );
}

/** 文件识别 */
export function recognizeFile(data: WmsAiApi.RecognizeReqVO) {
  return requestClient.post<WmsAiApi.RecognizeRespVO>(
    '/wms/ai/recognize-file',
    data,
    { timeout: AI_TIMEOUT },
  );
}

/** 确认建单 */
export function createOrder(data: WmsAiApi.CreateOrderReqVO) {
  return requestClient.post<WmsAiApi.CreateOrderRespVO>(
    '/wms/ai/create-order',
    data,
    { timeout: AI_TIMEOUT },
  );
}

/**
 * AI 辅助审批：给出单据摘要、风险点与审批建议
 *
 * 只读取单据与库存做分析，不改变单据状态、不代替人工审批。
 */
export function approvalAdvice(data: WmsAiApi.ApprovalAdviceReqVO) {
  return requestClient.post<WmsAiApi.ApprovalAdviceRespVO>(
    '/wms/ai/approval-advice',
    data,
    { timeout: AI_TIMEOUT },
  );
}
