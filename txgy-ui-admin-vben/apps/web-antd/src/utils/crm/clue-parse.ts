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
  customerId?: number; // 按识别名称在 CRM 中匹配到的客户编号（自动回填所属客户）
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

重要约束（必须遵守）：
- 严禁臆造或默认填充任何字段：资料中未出现的内容一律输出 null，不要猜测。
- name 必须是资料中真实出现的客户 / 公司 / 联系人名称。
- 若图片是软件界面或系统截图，请忽略界面上的系统名称、平台名、菜单、按钮、自有公司名称等无关文字，只提取客户联系信息。
- 无法确定的字段输出 null。

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

/**
 * 将各种日期写法规范化为 YYYY-MM-DD。兼容：
 * 2026-10-20 | 2026-10-2 | 2026.2.3 | 2026/2/3 | 2026年10月2日 | 2026年10月2号 | 2026 10 20
 */
export function normalizeDateStr(value?: string): string | undefined {
  if (!value) return undefined;
  let m = String(value).trim();
  if (!m) return undefined;
  // 先去掉可能的时间部分（如 "2026-10-20 00:00:00"）
  m = m.split(/\s+/)[0] ?? m;
  // 中文字符：年月转分隔符，日/号去掉
  m = m.replace(/[年月]/g, '-').replace(/[日号]/g, '');
  // 各种分隔符统一为 -
  m = m.replace(/[./\\\u3001\uFF0C\u3002]+/g, '-');
  // 只保留数字块（丢弃中文等残余字符，防 "10月2" 残留）
  const parts = m
    .split('-')
    .map((x) => x.match(/\d{1,4}/)?.[0] ?? '')
    .filter(Boolean);
  if (parts.length < 3) return undefined;
  let year = parts[0];
  let mon = parts[1];
  let day = parts[2];
  // 支持省略年份（仅月日，如 10.2/10-2）——无年份不识别，必须带完整年月日
  if (!/^\d{4}$/.test(year)) {
    if (/^\d{2}$/.test(year)) year = `20${year}`;
    else return undefined;
  }
  mon = mon.padStart(2, '0');
  day = day.padStart(2, '0');
  if (!/^\d{2}$/.test(mon) || !/^\d{2}$/.test(day)) return undefined;
  const date = new Date(`${year}-${mon}-${day}T00:00:00`);
  if (Number.isNaN(date.getTime())) return undefined;
  // 用本地年月日拼接，避免 toISOString 的 UTC 偏移导致差一天
  const y = date.getFullYear();
  const mo = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${y}-${mo}-${dd}`;
}

/** 将规范化日期字符串转为毫秒时间戳（DatePicker valueFormat:'x' 用） */
export function dateToTs(value?: string): number | undefined {
  const s = normalizeDateStr(value);
  if (!s) return undefined;
  const ts = new Date(`${s}T00:00:00`).getTime();
  return Number.isNaN(ts) ? undefined : ts;
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


/* ----------------------------- 合同专属识别 ----------------------------- */

export interface ParsedContractProduct {
  name?: string;
  no?: string;
  unit?: string;
  price?: number;
  count?: number;
  totalPrice?: number;
}

export interface ParsedContract {
  name?: string;
  customerName?: string;
  customerId?: number;
  businessName?: string;
  businessId?: number;
  ownerUserName?: string;
  ownerUserId?: number;
  orderDate?: string;
  startTime?: string;
  endTime?: string;
  signUserName?: string;
  signUserId?: number;
  signContactName?: string;
  discountPercent?: number;
  totalPrice?: number;
  products?: ParsedContractProduct[];
  remark?: string;
}

function buildContractPrompt(text: string, hasImage: boolean): string {
  const imageHint = hasImage
    ? '注意：输入可能包含图片，请先 OCR 识别图中文字，再结合文本提取字段。'
    : '';
  return `你是 CRM 合同信息提取助手。${imageHint}请从下面的合同资料中提取结构化字段，只输出 JSON（不要任何解释，也不要使用 markdown 代码块）。

重要约束：
- 严禁臆造：资料中未出现的内容一律输出 null。
- name 必须是【合同名称】（形如“……施工合同 / 采购合同 / 供货合同”），不要把客户公司名称当作合同名。
- customerName 是客户（对方公司）名称；businessName 是关联商机 / 项目名称。
- ownerUserName 是合同负责人（我方销售）姓名，资料中出现“负责人”“销售”“业务员”等并附姓名时提取。
- 日期统一输出 yyyy-MM-dd 格式。
- discountPercent 是【优惠掉的百分比】：出现“折扣 95%”“95 折”表示优惠 5%，输出 5；没有折扣输出 0。
- totalPrice 是折后应付总金额（数字）；products 中 totalPrice 是某产品小计（单价 × 数量）。
- 若资料未直接给出数量 count，可用总价 ÷ 单价推算。
- remark 放补充说明（如施工内容、工艺要求等）。

输出 JSON 结构示例：
{"name":"延安产业园外墙防腐保温施工合同","customerName":"某某公司","businessName":"某某项目","ownerUserName":"刘志强","orderDate":"2026-10-01","startTime":"2026-10-05","endTime":"2026-12-25","signUserName":"朱琳","signContactName":"王经理","discountPercent":5,"totalPrice":74100,"products":[{"name":"聚氨酯保温板","no":"WB001","unit":"㎡","price":78,"count":1000,"totalPrice":78000}],"remark":"项目含外墙除锈、防腐底漆、保温板铺设、防水面层"}

合同资料如下：
"""
${text || '（无文本，请从图片中识别）'}
"""`;
}

export async function parseContract(
  text: string,
  images: string[],
): Promise<{ fields: Partial<ParsedContract> | null; error?: string }> {
  const { enabled, baseUrl, apiKey, model } = getAiClueConfig();
  if (!enabled || !baseUrl || !apiKey || !model) {
    return { fields: null };
  }
  try {
    const content: any[] = [
      { type: 'text', text: buildContractPrompt(text, images.length > 0) },
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
        // 忽略
      }
      return { fields: null, error: detail };
    }
    const data: any = await resp.json();
    const contentText: string = data?.choices?.[0]?.message?.content ?? '';
    const json = extractJson(contentText);
    if (!json) {
      return { fields: null, error: 'AI 返回内容无法解析为 JSON' };
    }
    const out: Partial<ParsedContract> = {};
    const setStr = (k: keyof ParsedContract, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') {
        (out as any)[k] = `${v}`;
      }
    };
    const setNum = (k: keyof ParsedContract, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') {
        const n = Number(v);
        if (!Number.isNaN(n)) {
          (out as any)[k] = n;
        }
      }
    };
    setStr('name', json.name);
    setStr('customerName', json.customerName);
    setStr('businessName', json.businessName);
    setStr('ownerUserName', json.ownerUserName);
    setStr('orderDate', json.orderDate);
    setStr('startTime', json.startTime);
    setStr('endTime', json.endTime);
    setStr('signUserName', json.signUserName);
    setStr('signContactName', json.signContactName);
    setNum('discountPercent', json.discountPercent);
    setNum('totalPrice', json.totalPrice);
    setStr('remark', json.remark);
    if (Array.isArray(json.products)) {
      out.products = json.products
        .map((p: any) => ({
          name: p?.name ? String(p.name) : undefined,
          no: p?.no ? String(p.no) : undefined,
          unit: p?.unit ? String(p.unit) : undefined,
          price: p?.price != null ? Number(p.price) : undefined,
          count: p?.count != null ? Number(p.count) : undefined,
          totalPrice: p?.totalPrice != null ? Number(p.totalPrice) : undefined,
        }))
        .filter((p: any) => p.name || p.no);
    }
    return { fields: out };
  } catch (e: any) {
    return { fields: null, error: e?.message ?? '调用失败' };
  }
}


/* ----------------------------- 商机专属识别 ----------------------------- */

export interface ParsedBusiness {
  name?: string;
  customerName?: string;
  customerId?: number;
  ownerUserName?: string;
  ownerUserId?: number;
  businessStatusTypeName?: string;
  statusTypeId?: number;
  endDate?: string;
  remark?: string;
}

function buildBusinessPrompt(text: string, hasImage: boolean): string {
  const imageHint = hasImage
    ? '注意：输入可能包含图片，请先 OCR 识别图中文字，再结合文本提取字段。'
    : '';
  return `你是 CRM 商机信息提取助手。${imageHint}请从下面的商机资料中提取结构化字段，只输出 JSON（不要任何解释，也不要使用 markdown 代码块）。

重要约束：
- 严禁臆造，资料中未出现的内容一律输出 null。
- name 必须是【商机名称】（一般是项目/工程名称）。
- customerName 是客户公司名称；ownerUserName 是负责人姓名。
- businessStatusTypeName 是商机阶段/状态组名称（如"初步接洽"、"需求确认"、"报价及方案确认"、"商务谈判"、"赢单"）。
- endDate 是预计成交日期，输出 yyyy-MM-dd。
- remark 放补充说明。

输出 JSON 结构示例：
{"name":"延安产业园外墙防腐保温项目","customerName":"固邦防腐材料制造有限公司","ownerUserName":"刘志强","businessStatusTypeName":"报价及方案确认","endDate":"2026-10-20","remark":"客户预算约8万，需尽快报价"}

商机资料如下：
"""
${text || '（无文本，请从图片中识别）'}
"""`;
}

export async function parseBusiness(
  text: string,
  images: string[],
): Promise<{ fields: Partial<ParsedBusiness> | null; error?: string }> {
  const { enabled, baseUrl, apiKey, model } = getAiClueConfig();
  if (!enabled || !baseUrl || !apiKey || !model) return { fields: null };
  try {
    const content: any[] = [
      { type: 'text', text: buildBusinessPrompt(text, images.length > 0) },
    ];
    for (const img of images) content.push({ type: 'image_url', image_url: { url: img } });
    const resp = await fetch(`${baseUrl.replace(/\/$/, '')}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({ model, messages: [{ role: 'user', content }], temperature: 0 }),
    });
    if (!resp.ok) {
      let detail = `HTTP ${resp.status}`;
      try { const j: any = await resp.json(); if (j?.error?.message) detail = j.error.message; } catch {}
      return { fields: null, error: detail };
    }
    const data: any = await resp.json();
    const contentText: string = data?.choices?.[0]?.message?.content ?? '';
    const json = extractJson(contentText);
    if (!json) return { fields: null, error: 'AI 返回无法解析为 JSON' };
    const out: Partial<ParsedBusiness> = {};
    const setStr = (k: keyof ParsedBusiness, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') (out as any)[k] = `${v}`;
    };
    setStr('name', json.name);
    setStr('customerName', json.customerName);
    setStr('ownerUserName', json.ownerUserName);
    setStr('businessStatusTypeName', json.businessStatusTypeName);
    setStr('endDate', json.endDate);
    setStr('remark', json.remark);
    return { fields: out };
  } catch (e: any) {
    return { fields: null, error: e?.message ?? '调用失败' };
  }
}

/* ----------------------------- 回款专属识别 ----------------------------- */

export interface ParsedReceivable {
  customerName?: string;
  customerId?: number;
  contractNo?: string;
  contractName?: string;
  contractId?: number;
  ownerUserName?: string;
  ownerUserId?: number;
  period?: number;
  returnTime?: string;
  returnTypeName?: string;
  returnType?: number;
  price?: number;
  remark?: string;
}

function buildReceivablePrompt(text: string, hasImage: boolean): string {
  const imageHint = hasImage
    ? '注意：输入可能包含图片，请先 OCR 识别图中文字，再结合文本提取字段。'
    : '';
  return `你是 CRM 回款信息提取助手。${imageHint}请从下面的回款资料中提取结构化字段，只输出 JSON（不要任何解释，也不要使用 markdown 代码块）。

重要约束：
- 严禁臆造，资料中未出现的内容一律输出 null。
- customerName 是客户公司名称；contractNo 是合同编号；contractName 是合同名称。
- period 是回款期数（第几期），输出纯数字（如 1、2、3）。
- returnTime 是回款日期，输出 yyyy-MM-dd。
- returnTypeName 是回款方式名称，如"现金"、"银行转账"、"电汇"、"承兑汇票"等。
- price 是回款金额（数字）。
- ownerUserName 是负责人姓名。
- remark 放补充说明。

输出 JSON 结构示例：
{"customerName":"固邦防腐材料制造有限公司","contractNo":"TXGY-HT-2026-0012","period":1,"returnTime":"2026-10-15","returnTypeName":"电汇","price":74100,"ownerUserName":"刘志强","remark":"项目第一期回款"}

回款资料如下：
"""
${text || '（无文本，请从图片中识别）'}
"""`;
}

// 回款方式字典(crm_receivable_return_type)：1支票 2现金 3邮政汇款 4电汇 5网上转账 6支付宝 7微信支付 8其他
const RETURN_TYPE_NAME_MAP: Record<string, number> = {
  '支票': 1,
  '现金': 2,
  '邮政汇款': 3,
  '电汇': 4,
  '网上转账': 5,
  '银行转账': 5,
  '银行': 5,
  '转账': 5,
  '支付宝': 6,
  '微信': 7,
  '承兑': 8, // 承兑汇票不在字典中，归入其他避免空白
  '其他': 8,
};

export function matchReturnType(name?: string): number | undefined {
  if (!name) return undefined;
  const n = String(name).trim();
  for (const [k, v] of Object.entries(RETURN_TYPE_NAME_MAP)) {
    if (n.includes(k)) return v;
  }
  return undefined;
}

export async function parseReceivable(
  text: string,
  images: string[],
): Promise<{ fields: Partial<ParsedReceivable> | null; error?: string }> {
  const { enabled, baseUrl, apiKey, model } = getAiClueConfig();
  if (!enabled || !baseUrl || !apiKey || !model) return { fields: null };
  try {
    const content: any[] = [
      { type: 'text', text: buildReceivablePrompt(text, images.length > 0) },
    ];
    for (const img of images) content.push({ type: 'image_url', image_url: { url: img } });
    const resp = await fetch(`${baseUrl.replace(/\/$/, '')}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({ model, messages: [{ role: 'user', content }], temperature: 0 }),
    });
    if (!resp.ok) {
      let detail = `HTTP ${resp.status}`;
      try { const j: any = await resp.json(); if (j?.error?.message) detail = j.error.message; } catch {}
      return { fields: null, error: detail };
    }
    const data: any = await resp.json();
    const contentText: string = data?.choices?.[0]?.message?.content ?? '';
    const json = extractJson(contentText);
    if (!json) return { fields: null, error: 'AI 返回无法解析为 JSON' };
    const out: Partial<ParsedReceivable> = {};
    const setStr = (k: keyof ParsedReceivable, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') (out as any)[k] = `${v}`;
    };
    const setNum = (k: keyof ParsedReceivable, v: any) => {
      if (v !== undefined && v !== null && `${v}`.trim() !== '') {
        const n = Number(v);
        if (!Number.isNaN(n)) (out as any)[k] = n;
      }
    };
    setStr('customerName', json.customerName);
    setStr('contractNo', json.contractNo);
    setStr('contractName', json.contractName);
    setStr('ownerUserName', json.ownerUserName);
    setNum('period', json.period);
    setStr('returnTime', json.returnTime);
    setStr('returnTypeName', json.returnTypeName);
    setNum('price', json.price);
    setStr('remark', json.remark);
    if (out.returnTypeName) {
      out.returnType = matchReturnType(out.returnTypeName);
    }
    return { fields: out };
  } catch (e: any) {
    return { fields: null, error: e?.message ?? '调用失败' };
  }
}
