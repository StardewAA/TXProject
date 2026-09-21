import { requestClient } from '#/api/request';

/**
 * WMS AI 客服配置
 *
 * 模型与 API Key 由 AI 模块统一维护（模型配置 / API 密钥菜单），
 * 这里只决定「仓储 AI 助手用哪个模型」。
 */
export namespace WmsAiConfigApi {
  /** 查询结果 */
  export interface Config {
    /** 配置主键 */
    id?: number;
    /** 是否启用界面配置 */
    enabled?: boolean;
    /** 调用超时（秒） */
    timeoutSeconds?: number;
    /** 备注 */
    remark?: string;
    /** 配置的模型编号 */
    modelId?: number;
    /** 模型名称 */
    modelName?: string;
    /** 模型标识 */
    model?: string;
    /** 所属平台 */
    platform?: string;
    /** 模型状态：0 开启 1 关闭 */
    modelStatus?: number;
    /** 生效来源：CONFIG 界面配置 / ENV 环境变量 / NONE 未配置 */
    effectiveSource?: string;
    /** 实际生效的模型名 */
    effectiveModel?: string;
    /** 实际生效的调用地址（不含 Key） */
    effectiveBaseUrl?: string;
  }

  /** 保存入参 */
  export interface SaveReqVO {
    modelId: number;
    enabled?: boolean;
    timeoutSeconds?: number;
    remark?: string;
  }
}

/** 查询 AI 客服配置 */
export function getAiConfig() {
  return requestClient.get<WmsAiConfigApi.Config>('/wms/ai-config/get');
}

/** 保存 AI 客服配置 */
export function saveAiConfig(data: WmsAiConfigApi.SaveReqVO) {
  return requestClient.put<boolean>('/wms/ai-config/update', data);
}

/**
 * 测试模型连通性
 *
 * 用指定模型发一句最短对话，验证「调用地址 + API Key + 模型名」三者可用。
 * 失败时后端返回可直接展示的原因（含 HTTP 状态与响应体）。
 *
 * @param modelId 要测试的模型编号；不传则测试当前生效配置
 * @returns 耗时（毫秒）
 */
export function testAiModel(modelId?: number) {
  return requestClient.post<number>('/wms/ai-config/test', null, {
    params: modelId ? { modelId } : {},
    timeout: 60_000,
  });
}
