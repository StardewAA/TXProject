/**
 * CRM 线索「粘贴 / 图片」一键识别工具
 * - 规则解析（parseClueText）：纯前端正则 + 关键词匹配，离线可用、稳定。
 * - AI 增强（aiParseClue）：调用 OpenAI 兼容的视觉大模型，对【文本 + 图片】做
 *   OCR 与结构化提取；任何异常（未配置 / 网络错误 / CORS / 非 JSON）都会回退到规则解析结果。
 */

import { getAiClueConfig } from './ai-config';

export interface ParsedClue {
  name?: string;
  mobile?: string;
  telephone?: string;
  email?: string;
  wechat?: string;
  qq?: string;
  industryId?: number;
  level?: number;
  source?: number;
  detailAddress?: string;
  remark?: string;
  customerCategory?: number; // 1 下游客户 / 2 上游供应商（前端级联用）
}

// 行业关键词 → 字典数值（下游 101-110，上游 201-206）
const INDUSTRY_KEYWORDS: { value: number; keywords: string[] }[] = [
  { value: 101, keywords: ['石油化工', '石化', '石油'] },
  { value: 102, keywords: ['电力热力', '电力', '热力', '热电'] },
  { value: 103, keywords: ['冶金钢铁', '冶金', '钢铁', '钢厂'] },
  { value: 104, keywords: ['新能源', '风电', '光伏', '储能'] },
  { value: 105, keywords: ['建筑工程总包', '建筑工程', '工程总包', '总包'] },
  { value: 106, keywords: ['海工船舶', '海工', '船舶', '船厂'] },
  { value: 107, keywords: ['轨道交通', '轨道', '铁路'] },
  { value: 108, keywords: ['医药食品', '医药', '食品'] },
  { value: 109, keywords: ['渠道经销商', '经销商', '渠道'] },
  { value: 110, keywords: ['工程施工单位', '工程施工', '施工单位', '施工'] },
  { value: 201, keywords: ['化工原料厂', '化工原料'] },
  { value: 202, keywords: ['保温材料厂', '保温材料', '隔热'] },
  { value: 203, keywords: ['金属材料厂商', '金属材料', '金属'] },
  { value: 204, keywords: ['橡塑塑胶厂商', '橡塑', '塑胶', '橡胶'] },
  { value: 205, keywords: ['填料辅料厂商', '填料辅料', '辅料'] },
  { value: 206, keywords: ['包装材料供应商', '包装材料', '包装'] },
];

// 客户来源关键词 → 字典数值（1-10）
const SOURCE_KEYWORDS: { value: number; keywords: string[] }[] = [
  { value: 1, keywords: ['促销'] },
  { value: 2, keywords: ['搜索引擎', '百度', '搜索'] },
  { value: 3, keywords: ['广告'] },
  { value: 4, keywords: ['转介绍', '朋友介绍', '客户介绍', '老客户介绍'] },
  { value: 5, keywords: ['线上注册', '注册'] },
  { value: 6, keywords: ['线上咨询', '网咨', '在线咨询', '官网咨询', '网站咨询'] },
  { value: 7, keywords: ['预约上门', '上门'] },
  { value: 8, keywords: ['陌拜', '扫楼', '陌生拜访'] },
  { value: 9, keywords: ['电话咨询', '来电'] },
  { value: 10, keywords: ['邮件咨询', '邮件', 'e-mail', 'email'] },
];

function firstMatch(text: string, re: RegExp): string | undefined {
  const m = text.match(re);
  return m ? m[0] : undefined;
}

/** 纯规则解析：从文本中提取线索字段 */
export function parseClueText(text: string): ParsedClue {
  const result: ParsedClue = {};
  if (!text || !text.trim()) {
    return result;
  }

  // 手机号
  const mobile = firstMatch(text, /1[3-9]\d{9}/);
  if (mobile) {
    result.mobile = mobile;
  }

  // 座机 / 400 电话
  const telephone = firstMatch(
    text,
    /(?:0\d{2,3}-?\d{7,8})|(?:400-?\d{3}-?\d{4})/,
  );
  if (telephone) {
    result.telephone = telephone;
  }

  // 邮箱
  const email = firstMatch(
    text,
    /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/,
  );
  if (email) {
    result.email = email;
  }

  // QQ（带标签）
  const qqM = text.match(/(?:QQ|qq|Qq)[:：]?\s*(\d{5,12})/);
  if (qqM) {
    result.qq = qqM[1];
  }

  // 微信
  const wxM = text.match(
    /(?:微信|微信号|wx|WeChat)[:：]?\s*([A-Za-z0-9_-]{5,20})/i,
  );
  if (wxM) {
    result.wechat = wxM[1];
  }

  // 线索名称：优先公司/机构名，否则取首行
  const compM = text.match(
    /[一-龥A-Za-z0-9（）()]{2,30}?(?:公司|厂|集团|股份|有限公司|企业|研究院|研究所|中心|部|局|所)/,
  );
  if (compM) {
    result.name = compM[0].trim();
  } else {
    const line = text
      .split(/[\n\r]/)
      .map((s) => s.trim())
      .find((s) => s.length > 0 && s.length <= 40);
    if (line) {
      result.name = line;
    }
  }

  // 行业 + 客户类别
  for (const it of INDUSTRY_KEYWORDS) {
    if (it.keywords.some((k) => text.includes(k))) {
      result.industryId = it.value;
      result.customerCategory = it.value >= 200 ? 2 : 1;
      break;
    }
  }

  // 客户等级
  if (/重点|A级|核心|重要客户|关键客户/.test(text)) {
    result.level = 1;
  } else if (/普通|B级/.test(text)) {
    result.level = 2;
  } else if (/非优先|C级|一般|低优先级/.test(text)) {
    result.level = 3;
  }

  // 客户来源
  for (const s of SOURCE_KEYWORDS) {
    if (s.keywords.some((k) => text.includes(k))) {
      result.source = s.value;
      break;
    }
  }

  // 详细地址
  const addr = firstMatch(
    text,
    /[一-龥]{2,10}(?:省|市|区|县|镇|乡|路|街道?|号|栋|楼|室|大厦|广场|工业园)[^。\n]{0,40}/,
  );
  if (addr) {
    result.detailAddress = addr.trim();
  }

  return result;
}

function buildPrompt(text: string, hasImage: boolean): string {
  const imageHint = hasImage
    ? '注意：本次输入可能包含【图片】（如名片、聊天截图、客户资料照片）。请先对图片做 OCR 识别其中文字，再结合文字内容一起提取字段。'
    : '';
  return `你是一个 CRM 线索信息提取助手。${imageHint}请从下面的一段客户资料文本（及可能的图片）中，提取结构化字段，并以 JSON 格式只输出结果（不要包含任何解释，也不要使用 markdown 代码块标记）。

可提取字段及取值约束：
- name: 线索名称 / 客户或公司名称（字符串）
- mobile: 手机号（11 位数字字符串）
- telephone: 电话 / 座机（字符串）
- email: 邮箱（字符串）
- wechat: 微信号（字符串）
- qq: QQ 号（字符串）
- industryId: 客户行业编号，只能从以下集合选一：101 石油化工, 102 电力热力, 103 冶金钢铁, 104 新能源, 105 建筑工程总包, 106 海工船舶, 107 轨道交通, 108 医药食品, 109 渠道经销商, 110 工程施工单位, 201 化工原料厂, 202 保温材料厂, 203 金属材料厂商, 204 橡塑塑胶厂商, 205 填料辅料厂商, 206 包装材料供应商。无法判断则为 null。
- customerCategory: 客户类别，1=下游客户（向我方采购），2=上游供应商（向我方供货），与 industryId 对应。
- level: 客户等级编号，1=A重点客户, 2=B普通客户, 3=C非优先客户。无法判断则为 null。
- source: 客户来源编号，1=促销, 2=搜索引擎, 3=广告, 4=转介绍, 5=线上注册, 6=线上咨询, 7=预约上门, 8=陌拜, 9=电话咨询, 10=邮件咨询。无法判断则为 null。
- detailAddress: 详细地址（字符串），无法判断则为 null。
- remark: 其他补充说明（字符串），可为 null。

只输出 JSON，例如：{"name":"某某公司","mobile":"13800138000","industryId":101,"customerCategory":1,"level":1,"source":4}

客户资料文本如下：
"""
${text || '（无文本，请从图片中识别）'}
"""`;
}

function extractJson(content: string): Record<string, any> | null {
  if (!content) {
    return null;
  }
  const fenced = content.match(/```(?:json)?\s*([\s\S]*?)```/);
  const raw = fenced ? fenced[1] : content;
  try {
    return JSON.parse(raw.trim());
  } catch {
    const s = raw.indexOf('{');
    const e = raw.lastIndexOf('}');
    if (s >= 0 && e > s) {
      try {
        return JSON.parse(raw.slice(s, e + 1));
      } catch {
        return null;
      }
    }
    return null;
  }
}

/**
 * AI 多模态提取（best-effort）。
 * @param text   文本
 * @param images 图片的 data URL（base64，形如 data:image/png;base64,xxxx）
 * @returns { fields: 提取到的字段（未配置/无结果时为 null）；error: 调用失败原因（仅真正出错时填充）}
 */
async function aiParseClue(
  text: string,
  images: string[],
): Promise<{ fields: Partial<ParsedClue> | null; error?: string }> {
  const { enabled, baseUrl, apiKey, model } = getAiClueConfig();
  if (!enabled || !baseUrl || !apiKey || !model) {
    return { fields: null };
  }
  try {
    // 构造多模态消息：文本 prompt + 图片（如有）
    const content: any[] = [
      { type: 'text', text: buildPrompt(text, images.length > 0) },
    ];
    for (const img of images) {
      content.push({ type: 'image_url', image_url: { url: img } });
    }

    const resp = await fetch(`${baseUrl.replace(/\/$/, '')}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content }],
        temperature: 0,
      }),
    });
    if (!resp.ok) {
      let detail = `HTTP ${resp.status}`;
      try {
        const j: any = await resp.json();
        if (j?.error?.message) {
          detail = j.error.message;
        }
      } catch {
        // 忽略解析失败
      }
      return { fields: null, error: detail };
    }
    const data: any = await resp.json();
    const contentText: string =
      data?.choices?.[0]?.message?.content ?? '';
    const json = extractJson(contentText);
    if (!json) {
      return { fields: null, error: 'AI 返回内容无法解析为 JSON' };
    }
    const out: Partial<ParsedClue> = {};
    const setStr = (k: keyof ParsedClue, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') {
        (out as any)[k] = `${v}`;
      }
    };
    const setNum = (k: keyof ParsedClue, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') {
        const n = Number(v);
        if (!Number.isNaN(n)) {
          (out as any)[k] = n;
        }
      }
    };
    setStr('name', json.name);
    setStr('mobile', json.mobile);
    setStr('telephone', json.telephone);
    setStr('email', json.email);
    setStr('wechat', json.wechat);
    setStr('qq', json.qq);
    setNum('industryId', json.industryId);
    setNum('level', json.level);
    setNum('source', json.source);
    setStr('detailAddress', json.detailAddress);
    setStr('remark', json.remark);
    if (json.customerCategory != null && `${json.customerCategory}`.trim() !== '') {
      const c = Number(json.customerCategory);
      if (!Number.isNaN(c)) {
        out.customerCategory = c;
      }
    }
    if (out.industryId != null && out.customerCategory == null) {
      out.customerCategory = out.industryId >= 200 ? 2 : 1;
    }
    return { fields: out };
  } catch (e) {
    // 网络 / CORS / 解析等任何异常，回退规则解析，但把原因带回去提示用户
    return {
      fields: null,
      error: e instanceof Error ? e.message : '网络或 CORS 错误',
    };
  }
}

export interface ParseResult {
  fields: ParsedClue;
  /** AI 调用失败原因；未配置或成功时为空 */
  aiError?: string;
}

/**
 * 一键识别主入口：优先用 AI 大模型提取（文本 + 图片），无 AI / 失败则回退到规则解析。
 * @param text   文本
 * @param images 图片 data URL 数组
 * @returns { fields: 合并后的线索字段; aiError?: AI 调用失败原因 }
 */
export async function parseClue(
  text: string,
  images: string[] = [],
): Promise<ParseResult> {
  const rb = parseClueText(text);
  let aiError: string | undefined;
  try {
    const ai = await aiParseClue(text, images);
    aiError = ai.error;
    if (ai.fields && Object.keys(ai.fields).length) {
      const merged: ParsedClue = { ...rb };
      for (const [k, v] of Object.entries(ai.fields)) {
        if (v !== undefined && v !== null && v !== '') {
          (merged as any)[k] = v;
        }
      }
      // 以行业编号为准，统一客户类别，避免前后端不一致
      if (merged.industryId != null) {
        merged.customerCategory = merged.industryId >= 200 ? 2 : 1;
      }
      return { fields: merged, aiError };
    }
  } catch {
    // 忽略，使用规则解析结果
  }
  return { fields: rb, aiError };
}
