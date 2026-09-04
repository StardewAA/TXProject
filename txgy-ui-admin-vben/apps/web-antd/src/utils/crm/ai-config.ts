/**
 * CRM 线索「智能识别」AI 配置（OpenAI 兼容接口 / 多模态视觉模型）
 *
 * 作用：把粘贴的「文本 + 图片」一起发给视觉大模型，自动 OCR 图中文字并提取线索字段。
 * 不依赖后端 ai-server，因此不会出现「没有 ai-serve」的问题。
 *
 * 配置方式（推荐，无需改代码）：在线索表单的「智能识别」面板里点“AI 配置”，
 * 直接粘贴 baseUrl / model（以及可选 key），会保存在浏览器本地（localStorage）。
 * 也可直接改本文件的 DEFAULT 默认值。
 *
 * - baseUrl：接口基地址，通常以 /v1 结尾
 * - model：支持视觉（看图）的模型名
 * - apiKey：你的密钥
 *
 * 说明：本地开发可把 key 放在这里；生产环境建议改为从后端代理转发，避免 key 暴露在前端。
 * 若 enabled=false 或 baseUrl/model 为空，将自动回退到前端规则解析（离线可用）。
 */
export interface AiClueConfig {
  enabled: boolean;
  baseUrl: string;
  apiKey: string;
  model: string;
}

const STORAGE_KEY = 'txgy.crm.aiClueConfig';

const DEFAULT: AiClueConfig = {
  enabled: true,
  // 阿里云 MaaS 兼容模式接口（已提供）
  baseUrl:
    'https://ws-v32bkus5jl02u7wh.cn-beijing.maas.aliyuncs.com/compatible-mode/v1',
  // API Key（已提供）
  apiKey:
    'sk-ws-H.PMEIMYH.5MTu.MEUCID3-c6mXLM8kvjZptKTDMR0cVyRQpNx-1F0DRZOoIgIIAiEAqOHps7mmyWOMNDKgKXDVwAapwuXcY5UKklRKvqwmBpk',
  // 支持视觉（看图）的模型；如有差异，在界面“AI 配置”里改成你的实际模型名即可
  model: 'qwen-vl-max',
};

/** 读取实际生效的配置：localStorage 覆盖默认；空字符串不覆盖默认 */
export function getAiClueConfig(): AiClueConfig {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const stored = JSON.parse(raw);
      const merged: AiClueConfig = { ...DEFAULT };
      (Object.keys(merged) as (keyof AiClueConfig)[]).forEach((k) => {
        const v = stored[k];
        if (v !== undefined && v !== null && `${v}`.trim() !== '') {
          (merged as any)[k] = v;
        }
      });
      return merged;
    }
  } catch {
    // 解析失败则用默认
  }
  return { ...DEFAULT };
}

/** 保存配置（与已有配置合并，空值不覆盖） */
export function saveAiClueConfig(patch: Partial<AiClueConfig>): AiClueConfig {
  const cur = getAiClueConfig();
  const next: AiClueConfig = { ...cur };
  (Object.keys(patch) as (keyof AiClueConfig)[]).forEach((k) => {
    const v = patch[k];
    if (v !== undefined && `${v}`.trim() !== '') {
      (next as any)[k] = v;
    }
  });
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // 忽略存储异常
  }
  return next;
}
