/**
 * AI 对话助手 —— 千问意图理解 + 真实 CRM 数据查询
 *
 * 说明：所有结果均来自 CRM 系统真实接口，绝不返回任何编造/模拟数据。
 * 千问负责理解用户自然语言意图，前端再用真实接口拉取数据以表格展示；
 * 若千问不可用，则回退到本地关键词识别，但仍调用真实接口。
 */

import { getCustomerPage } from '#/api/crm/customer';
import { getCluePage } from '#/api/crm/clue';
import { getBusinessPage } from '#/api/crm/business';
import { getContactPage } from '#/api/crm/contact';
import { getContractPage } from '#/api/crm/contract';
import { getReceivablePage } from '#/api/crm/receivable';
import { getReceivablePlanPage } from '#/api/crm/receivable/plan';
import { getProductPage } from '#/api/crm/product';
import { getSimpleDictDataList } from '#/api/system/dict/data';

import {
  getTodayContactCustomerCount,
  getPutPoolRemindCustomerCount,
  getFollowCustomerCount,
} from '#/api/crm/customer';
import { getReceivablePlanRemindCount } from '#/api/crm/receivable/plan';
import { getAuditContractCount, getRemindContractCount } from '#/api/crm/contract';
import { getAuditReceivableCount } from '#/api/crm/receivable';

export interface TableColumn {
  title: string;
  dataIndex: string;
  key: string;
}

export interface TableResult {
  title: string;
  columns: TableColumn[];
  rows: Record<string, any>[];
  /** 数据来源提示 */
  source?: string;
}

export interface AiReply {
  text: string;
  table?: TableResult;
}

function nowMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function isCurrentMonth(dateStr: string | null | undefined): boolean {
  if (!dateStr) return false;
  return String(dateStr).startsWith(nowMonth());
}

function fmtMoney(n: number | null | undefined): string {
  if (n === null || n === undefined) return '-';
  return (
    '¥' +
    Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
  );
}

function fmtDate(v: any): string {
  if (!v) return '-';
  return String(v).slice(0, 10);
}

/* ----------------------------- 字典加载 ----------------------------- */

const DICT_TYPES = {
  INDUSTRY: 'crm_customer_industry',
  LEVEL: 'crm_customer_level',
  SOURCE: 'crm_customer_source',
  AUDIT_STATUS: 'crm_audit_status',
  PRODUCT_STATUS: 'crm_product_status',
  PRODUCT_UNIT: 'crm_product_unit',
  RETURN_TYPE: 'crm_receivable_return_type',
};

let dictPromise: Promise<Record<string, Record<string, string>>> | null = null;

async function loadDictMaps(): Promise<Record<string, Record<string, string>>> {
  if (dictPromise) return dictPromise;
  dictPromise = getSimpleDictDataList().then((list) => {
    const maps: Record<string, Record<string, string>> = {};
    for (const item of list) {
      if (!maps[item.dictType]) maps[item.dictType] = {};
      maps[item.dictType][item.value] = item.label;
    }
    return maps;
  });
  return dictPromise;
}

function labelOf(
  maps: Record<string, Record<string, string>>,
  type: string,
  value: string | number | undefined,
  fallback?: string,
): string {
  if (value === undefined || value === null || value === '') {
    return fallback ?? '-';
  }
  return maps[type]?.[String(value)] ?? fallback ?? String(value);
}

function extractName(q: string): string | null {
  const patterns = [
    /叫([一-龥A-Za-z0-9_]+)/,
    /名为([一-龥A-Za-z0-9_]+)/,
    /名字[叫为]([一-龥A-Za-z0-9_]+)/,
    /“([^”]+)”/,
    /"([^"]+)"/,
    /『([^』]+)』/,
    /【([^】]+)】/,
  ];
  for (const p of patterns) {
    const m = q.match(p);
    if (m && m[1]) return m[1].trim();
  }
  return null;
}

/* ----------------------------- 各意图处理（真实接口） ----------------------------- */

async function replyCustomers(q: string): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = extractName(q);
  const isDownstream = /下游/.test(q);

  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getCustomerPage(params);
  let rows: any[] = res.list || [];

  // 下游客户按行业 id 101-110 过滤（系统字典约定）
  if (isDownstream) {
    rows = rows.filter((c) => c.industryId >= 101 && c.industryId <= 110);
  }

  let text: string;
  if (rows.length === 0) {
    text = isDownstream
      ? '当前没有查询到下游客户记录。'
      : name
        ? `未找到名为「${name}」的客户。`
        : '当前没有查询到客户记录。';
  } else {
    text = isDownstream
      ? `为您查询到 ${rows.length} 个下游客户：`
      : name
        ? `已为您找到 ${rows.length} 个名为「${name}」的客户：`
        : `为您查询到 ${rows.length} 个客户：`;
  }

  return {
    text,
    table: {
      title: isDownstream ? '下游客户列表' : '客户列表',
      source: 'CRM 客户管理 · 实查数据',
      columns: [
        { title: '客户名称', dataIndex: 'name', key: 'name' },
        { title: '手机', dataIndex: 'mobile', key: 'mobile' },
        { title: '客户级别', dataIndex: 'level', key: 'level' },
        { title: '所属行业', dataIndex: 'industry', key: 'industry' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
        { title: '成交状态', dataIndex: 'deal', key: 'deal' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        mobile: r.mobile || '-',
        level: labelOf(maps, DICT_TYPES.LEVEL, r.level),
        industry: labelOf(maps, DICT_TYPES.INDUSTRY, r.industryId),
        owner: r.ownerUserName || r.ownerUserDeptName || '-',
        deal: r.dealStatus ? '已成交' : '未成交',
      })),
    },
  };
}

async function replyPool(): Promise<AiReply> {
  const maps = await loadDictMaps();
  const res = await getCustomerPage({ pageNo: 1, pageSize: 100, pool: true });
  const rows: any[] = res.list || [];
  return {
    text:
      rows.length === 0
        ? '当前客户公海没有待领取的客户。'
        : `客户公海当前有 ${rows.length} 个未分配客户：`,
    table: {
      title: '客户公海',
      source: 'CRM 客户公海 · 实查数据',
      columns: [
        { title: '客户名称', dataIndex: 'name', key: 'name' },
        { title: '手机', dataIndex: 'mobile', key: 'mobile' },
        { title: '所属行业', dataIndex: 'industry', key: 'industry' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        mobile: r.mobile || '-',
        industry: labelOf(maps, DICT_TYPES.INDUSTRY, r.industryId),
        owner: '—',
      })),
    },
  };
}

async function replyClues(): Promise<AiReply> {
  const maps = await loadDictMaps();
  const res = await getCluePage({ pageNo: 1, pageSize: 100 });
  const rows: any[] = res.list || [];
  return {
    text:
      rows.length === 0
        ? '当前没有查询到线索记录。'
        : `当前共有 ${rows.length} 条线索：`,
    table: {
      title: '线索列表',
      source: 'CRM 线索管理 · 实查数据',
      columns: [
        { title: '线索名称', dataIndex: 'name', key: 'name' },
        { title: '手机', dataIndex: 'mobile', key: 'mobile' },
        { title: '所属行业', dataIndex: 'industry', key: 'industry' },
        { title: '客户来源', dataIndex: 'source', key: 'source' },
        { title: '转化状态', dataIndex: 'transform', key: 'transform' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        mobile: r.mobile || '-',
        industry: labelOf(maps, DICT_TYPES.INDUSTRY, r.industryId),
        source: labelOf(maps, DICT_TYPES.SOURCE, r.source),
        transform: r.transformStatus ? '已转化' : '未转化',
        owner: r.ownerUserName || r.ownerUserDeptName || '-',
      })),
    },
  };
}

async function replyBusinesses(q: string): Promise<AiReply> {
  const month = /本月|这个月|当月/.test(q);
  const res = await getBusinessPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((b) => isCurrentMonth(b.dealTime || b.createTime));
  }
  return {
    text:
      rows.length === 0
        ? month
          ? `本月（${nowMonth()}）没有查询到商机记录。`
          : '当前没有查询到商机记录。'
        : `为您查询到 ${rows.length} 个商机：`,
    table: {
      title: '商机列表',
      source: 'CRM 商机管理 · 实查数据',
      columns: [
        { title: '商机名称', dataIndex: 'name', key: 'name' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '金额', dataIndex: 'amount', key: 'amount' },
        { title: '阶段', dataIndex: 'stage', key: 'stage' },
        { title: '预计成交日期', dataIndex: 'dealTime', key: 'dealTime' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        customer: r.customerName || '-',
        amount: fmtMoney(r.totalPrice),
        stage: r.statusName || r.statusTypeName || '-',
        dealTime: fmtDate(r.dealTime),
        owner: r.ownerUserName || r.ownerUserDeptName || '-',
      })),
    },
  };
}

async function replyTodos(): Promise<AiReply> {
  const [todayContact, poolRemind, follow, planRemind, contractAudit, contractRemind, receivableAudit] =
    await Promise.all([
      getTodayContactCustomerCount(),
      getPutPoolRemindCustomerCount(),
      getFollowCustomerCount(),
      getReceivablePlanRemindCount(),
      getAuditContractCount(),
      getRemindContractCount(),
      getAuditReceivableCount(),
    ]);

  const rows = [
    { item: '今日需联系客户', count: todayContact },
    { item: '待跟进客户', count: follow },
    { item: '即将进入公海客户', count: poolRemind },
    { item: '待回款提醒', count: planRemind },
    { item: '待审批合同', count: contractAudit },
    { item: '即将到期合同', count: contractRemind },
    { item: '待审批回款', count: receivableAudit },
  ];

  return {
    text: `以下是 CRM 系统当前的待办事项总览（实查）：`,
    table: {
      title: '待办事项总览',
      source: 'CRM 统计 · 实查数据',
      columns: [
        { title: '待办类型', dataIndex: 'item', key: 'item' },
        { title: '数量', dataIndex: 'count', key: 'count' },
      ],
      rows,
    },
  };
}

async function replyContacts(): Promise<AiReply> {
  const res = await getContactPage({ pageNo: 1, pageSize: 100 });
  const rows: any[] = res.list || [];
  return {
    text:
      rows.length === 0
        ? '当前没有查询到联系人记录。'
        : `共检索到 ${rows.length} 位联系人：`,
    table: {
      title: '联系人列表',
      source: 'CRM 联系人管理 · 实查数据',
      columns: [
        { title: '姓名', dataIndex: 'name', key: 'name' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '手机', dataIndex: 'mobile', key: 'mobile' },
        { title: '职务', dataIndex: 'post', key: 'post' },
        { title: '是否主联系人', dataIndex: 'master', key: 'master' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        customer: r.customerName || '-',
        mobile: r.mobile || '-',
        post: r.post || '-',
        master: r.master ? '是' : '否',
      })),
    },
  };
}

async function replyContracts(q: string): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const res = await getContractPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((c) => isCurrentMonth(c.createTime || c.startTime));
  }
  return {
    text:
      rows.length === 0
        ? month
          ? `本月（${nowMonth()}）没有查询到合同记录。`
          : '当前没有查询到合同记录。'
        : `共检索到 ${rows.length} 份合同：`,
    table: {
      title: '合同列表',
      source: 'CRM 合同管理 · 实查数据',
      columns: [
        { title: '合同编号', dataIndex: 'no', key: 'no' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '合同金额', dataIndex: 'amount', key: 'amount' },
        { title: '签约日期', dataIndex: 'signDate', key: 'signDate' },
        { title: '审批状态', dataIndex: 'status', key: 'status' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        no: r.no || '-',
        customer: r.customerName || '-',
        amount: fmtMoney(r.totalPrice),
        signDate: fmtDate(r.startTime || r.createTime),
        status: labelOf(maps, DICT_TYPES.AUDIT_STATUS, r.auditStatus),
      })),
    },
  };
}

async function replyReceivables(q: string): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const res = await getReceivablePage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((r) => isCurrentMonth(r.returnTime));
  }
  return {
    text:
      rows.length === 0
        ? month
          ? `本月（${nowMonth()}）没有查询到回款记录。`
          : '当前没有查询到回款记录。'
        : `共检索到 ${rows.length} 条回款记录：`,
    table: {
      title: '回款列表',
      source: 'CRM 回款管理 · 实查数据',
      columns: [
        { title: '回款编号', dataIndex: 'no', key: 'no' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '回款金额', dataIndex: 'amount', key: 'amount' },
        { title: '回款日期', dataIndex: 'date', key: 'date' },
        { title: '回款方式', dataIndex: 'method', key: 'method' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        no: r.no || '-',
        customer: r.customerName || '-',
        amount: fmtMoney(r.price),
        date: fmtDate(r.returnTime),
        method: labelOf(maps, DICT_TYPES.RETURN_TYPE, r.returnType),
      })),
    },
  };
}

async function replyReceivablePlans(q: string): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const res = await getReceivablePlanPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((r) => isCurrentMonth(r.returnTime));
  }
  return {
    text:
      rows.length === 0
        ? month
          ? `本月（${nowMonth()}）没有查询到回款计划。`
          : '当前没有查询到回款计划。'
        : `共检索到 ${rows.length} 条回款计划：`,
    table: {
      title: '回款计划列表',
      source: 'CRM 回款计划 · 实查数据',
      columns: [
        { title: '计划编号', dataIndex: 'id', key: 'id' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '计划金额', dataIndex: 'amount', key: 'amount' },
        { title: '计划回款日期', dataIndex: 'planDate', key: 'planDate' },
        { title: '回款方式', dataIndex: 'method', key: 'method' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        customer: r.customerName || '-',
        amount: fmtMoney(r.price),
        planDate: fmtDate(r.returnTime),
        method: labelOf(maps, DICT_TYPES.RETURN_TYPE, r.returnType),
      })),
    },
  };
}

async function replyProducts(): Promise<AiReply> {
  const maps = await loadDictMaps();
  const res = await getProductPage({ pageNo: 1, pageSize: 100 });
  const rows: any[] = res.list || [];
  return {
    text:
      rows.length === 0
        ? '当前没有查询到产品记录。'
        : `共检索到 ${rows.length} 个产品：`,
    table: {
      title: '产品列表',
      source: 'CRM 产品管理 · 实查数据',
      columns: [
        { title: '产品名称', dataIndex: 'name', key: 'name' },
        { title: '产品编码', dataIndex: 'no', key: 'no' },
        { title: '单位', dataIndex: 'unit', key: 'unit' },
        { title: '标准价格', dataIndex: 'price', key: 'price' },
        { title: '状态', dataIndex: 'status', key: 'status' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        no: r.no || '-',
        unit: labelOf(maps, DICT_TYPES.PRODUCT_UNIT, r.unit),
        price: fmtMoney(r.price),
        status: labelOf(maps, DICT_TYPES.PRODUCT_STATUS, r.status),
      })),
    },
  };
}

function replyHelp(): AiReply {
  return {
    text:
      '我是 CRM 智能助手，所有数据均来自 CRM 系统真实接口。试试这样问我：\n' +
      '· “帮我查找名字叫张三的客户信息”\n' +
      '· “列出本月所有商机”\n' +
      '· “查看待办事项”\n' +
      '· “查询客户公海” / “列出所有线索” / “本月合同” / “回款计划” / “联系人” / “产品”',
  };
}

/* ----------------------------- 千问大模型接入 ----------------------------- */

interface QwenClassify {
  intent:
    | 'customer'
    | 'pool'
    | 'clue'
    | 'business'
    | 'todo'
    | 'contact'
    | 'contract'
    | 'receivable'
    | 'receivablePlan'
    | 'product'
    | 'unknown';
  name?: string | null;
  month?: boolean;
  summary?: string;
}

const QWEN_SYSTEM_PROMPT = `你是一个 CRM 智能助手。用户会用中文询问 CRM 业务（客户、线索、商机、待办、联系人、合同、回款、回款计划、产品、客户公海）。
请分析用户意图并提取过滤条件，只返回一个 JSON 对象（不要 markdown、不要多余文字），结构如下：
{
  "intent": "customer" | "pool" | "clue" | "business" | "todo" | "contact" | "contract" | "receivable" | "receivablePlan" | "product" | "unknown",
  "name": "提取到的客户/联系人姓名，没有则为 null",
  "month": true或false（用户是否要求“本月/这个月”）,
  "summary": "用一句中文简要说明你将展示什么（1-2 句）"
}`;

function parseJsonSafe<T>(text: string): T | null {
  if (!text) return null;
  let t = text
    .trim()
    .replace(/^```(?:json)?/i, '')
    .replace(/```$/, '')
    .trim();
  const s = t.indexOf('{');
  const e = t.lastIndexOf('}');
  if (s === -1 || e === -1) return null;
  try {
    return JSON.parse(t.slice(s, e + 1)) as T;
  } catch {
    return null;
  }
}

async function classifyIntentWithQwen(
  query: string,
): Promise<QwenClassify | null> {
  try {
    const res = await fetch('/api/ai/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'qwen-plus',
        temperature: 0.2,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: QWEN_SYSTEM_PROMPT },
          { role: 'user', content: query },
        ],
      }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    const content: string = data?.choices?.[0]?.message?.content ?? '';
    return parseJsonSafe<QwenClassify>(content);
  } catch {
    return null;
  }
}

async function getLocalTable(
  intent: string,
  q: string,
): Promise<AiReply | null> {
  switch (intent) {
    case 'customer':
      return await replyCustomers(q);
    case 'pool':
      return await replyPool();
    case 'clue':
      return await replyClues();
    case 'business':
      return await replyBusinesses(q);
    case 'todo':
      return await replyTodos();
    case 'contact':
      return await replyContacts();
    case 'contract':
      return await replyContracts(q);
    case 'receivable':
      return await replyReceivables(q);
    case 'receivablePlan':
      return await replyReceivablePlans(q);
    case 'product':
      return await replyProducts();
    default:
      return null;
  }
}

/* ----------------------------- 意图分发 ----------------------------- */

/**
 * 主入口：优先调用千问大模型做意图理解，再用 CRM 真实接口拉取数据；
 * 若千问不可用（网络/密钥问题），则回退到本地关键词识别，仍调用真实接口。
 */
export async function mockAiReply(query: string): Promise<AiReply> {
  const q = query.trim();

  try {
    const cls = await classifyIntentWithQwen(q);
    if (cls) {
      const local = await getLocalTable(cls.intent, q);
      if (local) {
        const text = cls.summary?.trim() || local.text;
        return { text, table: local.table };
      }
      if (cls.summary) {
        return { text: cls.summary };
      }
    }
  } catch {
    // 落到下方关键词兜底
  }

  // 兜底：纯前端关键词识别（仍调用真实接口）
  return keywordMockReply(q);
}

async function keywordMockReply(query: string): Promise<AiReply> {
  const q = query.trim();

  // 优先级：公海 > 客户 > 线索 > 商机 > 待办 > 联系人 > 合同 > 回款计划 > 回款 > 产品
  if (/公海/.test(q)) return await replyPool();
  if (/客户/.test(q) && !/公海/.test(q)) return await replyCustomers(q);
  if (/线索/.test(q)) return await replyClues();
  if (/商机|机会/.test(q)) return await replyBusinesses(q);
  if (/待办|todo|事项/.test(q)) return await replyTodos();
  if (/联系人|联系/.test(q)) return await replyContacts();
  if (/合同/.test(q)) return await replyContracts(q);
  if (/回款计划/.test(q)) return await replyReceivablePlans(q);
  if (/回款/.test(q)) return await replyReceivables(q);
  if (/产品|商品/.test(q)) return await replyProducts();

  return replyHelp();
}
