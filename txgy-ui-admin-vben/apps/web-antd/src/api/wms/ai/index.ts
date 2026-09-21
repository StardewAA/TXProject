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
  export interface RecognizeRespVO {
    /** 100 正常 */
    code: number;
    /** 回复文案 */
    reply: string;
    /** 单据类型：IN 入库 / OUT 出库 / MOVE 移库 / null 闲聊 */
    action?: string;
    /** 单据子类型 */
    type?: number;
    /** 仓库 */
    warehouse?: string;
    /** 仓库编号 */
    warehouseId?: number;
    /** 供应商/客户 */
    merchant?: string;
    /** 企业编号 */
    merchantId?: number;
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
    warehouseId?: number;
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

/** 语音/文字 + 图片识别（LLM 解析较慢，超时放宽至 280s） */
export function recognize(data: WmsAiApi.RecognizeReqVO) {
  return requestClient.post<WmsAiApi.RecognizeRespVO>(
    '/wms/ai/recognize',
    data,
    { timeout: 280_000 },
  );
}

/** 文件识别 */
export function recognizeFile(data: WmsAiApi.RecognizeReqVO) {
  return requestClient.post<WmsAiApi.RecognizeRespVO>(
    '/wms/ai/recognize-file',
    data,
    { timeout: 280_000 },
  );
}

/** 确认建单 */
export function createOrder(data: WmsAiApi.CreateOrderReqVO) {
  return requestClient.post<WmsAiApi.CreateOrderRespVO>(
    '/wms/ai/create-order',
    data,
    { timeout: 280_000 },
  );
}
