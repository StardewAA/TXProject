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
import { requestClient } from '#/api/request';

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

/** 业务类型：决定点击表格行时跳转到哪个详情页 */
export type BizType =
  | 'business'
  | 'clue'
  | 'contact'
  | 'contract'
  | 'customer'
  | 'product'
  | 'receivable'
  | 'receivablePlan';

export interface TableResult {
  title: string;
  columns: TableColumn[];
  rows: Record<string, any>[];
  /** 数据来源提示 */
  source?: string;
  /** 业务类型，用于点击行时跳转对应详情页 */
  bizType?: BizType;
}

export interface AiReply {
  text: string;
  table?: TableResult;
  /** AI 意图识别不可用、已降级为关键词匹配 */
  degraded?: boolean;
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

/** 去掉名称前后的动词、助词与请求语，得到纯净的实体名 */
function cleanEntityName(raw: string): string {
  let v = String(raw ?? '').trim();
  v = v.replace(
    /^(?:(?:帮我|帮忙|请|麻烦|你|我|想|要|查一下|查一查|看一下|看下|看看|查找|查询|搜索|检索|列出|显示|展示|查看|了解|介绍|关于|查|找|搜|看|下|的|是|那个|这个))+\s*/g,
    '',
  );
  // 反复剥离尾部请求语与助词（如“张三的客户信息”→“张三”），保留“公司/厂”等实体后缀
  for (let i = 0; i < 5; i += 1) {
    const next = v.replace(
      /(?:的|客户|基本信息|基本资料|详细资料|详细|资料|信息|情况|相关数据|相关|详情|名称|名字)$/g,
      '',
    );
    if (next === v) break;
    v = next;
  }
  return v.trim();
}

/** 泛词（所有/本月/列表…）不是实体名称，需拦截，避免被当成查询条件 */
function isGenericName(v: string): boolean {
  const GENERIC_WORDS =
    '所有|全部|全部数据|本月|这个月|当月|最近|近期|今天|今日|昨天|本周|上周|今年|去年|记录|数据|列表|详情|信息|情况|客户|商机|合同|回款|线索|联系人|产品|一下|下|的|是|哪些|什么|看|查|找|搜|了解|介绍|关于';
  return new RegExp(`^(?:${GENERIC_WORDS})+$`).test(v);
}

/** 从自然语言中提取实体名称（客户/公司/联系人/合同等） */
function extractName(q: string): string | null {
  const accept = (raw?: string): string | null => {
    const v = cleanEntityName(String(raw ?? ''));
    return v && !isGenericName(v) ? v : null;
  };

  // 1) 显式写法：叫 XX / 名为 XX / 引号包裹
  const patterns = [
    /叫([一-龥A-Za-z0-9_（）()·]+)/,
    /名为([一-龥A-Za-z0-9_（）()·]+)/,
    /名字[叫为]([一-龥A-Za-z0-9_（）()·]+)/,
    /“([^”]+)”/,
    /"([^"]+)"/,
    /『([^』]+)』/,
    /【([^】]+)】/,
  ];
  for (const p of patterns) {
    const m = q.match(p);
    if (m?.[1]) {
      const v = accept(m[1]);
      if (v) return v;
    }
  }

  // 2) 企业/机构实体（以后缀结尾且后面是边界）——最可靠，优先于口语式
  const m2 = q.match(
    /([一-龥A-Za-z0-9（）()·]{2,40}?(?:有限责任公司|股份有限公司|有限公司|集团|采油厂|炼油厂|化工厂|制造厂|油田|管道局|研究院|设计院))(?=$|[\s，。？！,?、；;：:]|的)/,
  );
  if (m2?.[1]) {
    const v = accept(m2[1]);
    if (v) return v;
  }
  const m3 = q.match(
    /([一-龥A-Za-z0-9（）()·]{2,40}?(?:分公司|公司|厂|中心))(?=$|[\s，。？！,?、；;：:]|的)/,
  );
  if (m3?.[1]) {
    const v = accept(m3[1]);
    if (v) return v;
  }

  // 3) 口语式：“查找/查询 XX 的客户/信息/合同”
  const m1 = q.match(
    /(?:帮我查|帮我找|帮我看看|查找|查询|搜索|检索|查一下|看一下|看看|列出|显示|展示|了解|介绍|关于|查看|查|找|搜|看)\s*([一-龥A-Za-z0-9（）()·\s]{2,40}?)\s*(?:的)?\s*(?:客户|基本信息|基本资料|详细资料|详细|资料|信息|情况|相关数据|相关|详情|合同|商机|回款|线索|联系人)/,
  );
  if (m1?.[1]) {
    const v = accept(m1[1]);
    if (v) return v;
  }
  return null;
}

/** 优先使用大模型提取的名称，缺失时退回本地正则提取 */
function pickName(
  hintName: string | null | undefined,
  q: string,
): string | null {
  const h = hintName ? String(hintName).trim() : '';
  if (h) return h;
  return extractName(q);
}

/** 字段值与名称的模糊匹配（不区分大小写） */
function hitName(value: unknown, name: string): boolean {
  if (value === undefined || value === null || value === '') return false;
  return String(value).toLowerCase().includes(String(name).toLowerCase());
}

/**
 * 按名称过滤列表：优先精确包含，其次用前 6 个字宽松匹配；
 * 都不命中时返回空数组（宁可提示未找到，也不返回与问题无关的全量数据）。
 */
function filterByName<T>(
  rows: T[],
  name: string | null,
  getters: ((row: T) => unknown)[],
): T[] {
  if (!name) return rows;
  const keys = [name, name.slice(0, Math.min(6, name.length))].filter(
    (k, i, arr) => k.length > 0 && arr.indexOf(k) === i,
  );
  for (const k of keys) {
    const hit = rows.filter((r) => getters.some((g) => hitName(g(r), k)));
    if (hit.length > 0) return hit;
  }
  return [];
}

/* ----------------------------- 各意图处理（真实接口） ----------------------------- */

async function replyCustomers(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const isDownstream = /下游/.test(q);

  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getCustomerPage(params);
  let rows: any[] = res.list || [];

  // 下游客户按行业 id 101-110 过滤（系统字典约定）
  if (isDownstream) {
    rows = rows.filter((c) => c.industryId >= 101 && c.industryId <= 110);
  }
  // 指定了客户名时只保留匹配项，绝不返回与问题无关的全量列表
  rows = filterByName(rows, name, [(c: any) => c.name]);

  let text: string;
  if (rows.length === 0) {
    text = isDownstream
      ? '当前没有查询到下游客户记录。'
      : name
        ? `未找到名为「${name}」的客户，请确认客户名称是否正确。`
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
      title: isDownstream
        ? '下游客户列表'
        : name
          ? `客户：${name}`
          : '客户列表',
      source: 'CRM 客户管理 · 实查数据',
      bizType: 'customer',
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
      bizType: 'customer',
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

async function replyClues(
  q = '',
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  // 注意：有名称时必须交给后端过滤。只取前 100 条再在前端筛，数据一多就会「搜不到」
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getCluePage(params);
  const all: any[] = res.list || [];
  const rows = filterByName(all, name, [
    (r: any) => r.name,
    (r: any) => r.customerName,
  ]);
  return {
    text:
      rows.length === 0
        ? name
          ? `未找到与「${name}」相关的线索。`
          : '当前没有查询到线索记录。'
        : name
          ? `已为您找到 ${rows.length} 条与「${name}」相关的线索：`
          : `当前共有 ${rows.length} 条线索：`,
    table: {
      title: name ? `线索：${name}` : '线索列表',
      source: 'CRM 线索管理 · 实查数据',
      bizType: 'clue',
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

async function replyBusinesses(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getBusinessPage(params);
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((b) => isCurrentMonth(b.dealTime || b.createTime));
  }
  rows = filterByName(rows, name, [
    (b: any) => b.name,
    (b: any) => b.customerName,
  ]);
  return {
    text:
      rows.length === 0
        ? name
          ? `未找到与「${name}」相关的商机。`
          : month
            ? `本月（${nowMonth()}）没有查询到商机记录。`
            : '当前没有查询到商机记录。'
        : name
          ? `已为您找到 ${rows.length} 个与「${name}」相关的商机：`
          : `为您查询到 ${rows.length} 个商机：`,
    table: {
      title: name ? `商机：${name}` : '商机列表',
      source: 'CRM 商机管理 · 实查数据',
      bizType: 'business',
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

async function replyTodos(q = ''): Promise<AiReply> {
  // 用户提到「下属/团队」时按「下属负责的」视角查（sceneType=3），否则用默认视角
  const sceneType = /下属|团队|他们/.test(q) ? 3 : undefined;
  const baseQuery: Record<string, any> = { pageNo: 1, pageSize: 20 };
  if (sceneType) {
    baseQuery.sceneType = sceneType;
  }

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

  // 关键：只给数量用户没法跟进，同时把「具体是哪些客户」也查出来
  const [overduePage, todayPage, followPage] = await Promise.all([
    getCustomerPage({ ...baseQuery, contactStatus: 2 } as any).catch(() => null),
    getCustomerPage({ ...baseQuery, contactStatus: 1 } as any).catch(() => null),
    getCustomerPage({ ...baseQuery, followUpStatus: false } as any).catch(() => null),
  ]);

  const detailRows: Array<Record<string, any>> = [];
  const collect = (
    page: any,
    type: string,
    timeField: string,
    fallbackField: string,
  ) => {
    for (const c of page?.list ?? []) {
      detailRows.push({
        type,
        name: c?.name ?? '-',
        mobile: c?.mobile ?? '-',
        owner: c?.ownerUserName ?? '-',
        time: fmtDate(c?.[timeField] ?? c?.[fallbackField]),
      });
    }
  };
  collect(overduePage, '已逾期未联系', 'contactNextTime', 'contactLastTime');
  collect(todayPage, '今日需联系', 'contactNextTime', 'contactLastTime');
  collect(followPage, '待跟进', 'contactNextTime', 'contactLastTime');

  const summary =
    `待办总览：今日需联系 ${todayContact} 人、待跟进 ${follow} 人、` +
    `即将进入公海 ${poolRemind} 人、待回款提醒 ${planRemind} 笔、` +
    `待审批合同 ${contractAudit} 份、即将到期合同 ${contractRemind} 份、` +
    `待审批回款 ${receivableAudit} 笔。`;

  // 没有需要联系的客户时，退回展示统计表
  if (detailRows.length === 0) {
    return {
      text: `${summary}\n当前没有需要联系或跟进的客户。`,
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

  return {
    text:
      `${summary}\n以下是需要处理的客户明细` +
      `${detailRows.length >= 20 ? '（仅显示前 20 条）' : ''}：`,
    table: {
      title: '待办客户明细',
      source: `CRM 客户 · 实查数据${sceneType === 3 ? ' · 下属负责的' : ''}`,
      bizType: 'customer',
      columns: [
        { title: '待办类型', dataIndex: 'type', key: 'type' },
        { title: '客户名称', dataIndex: 'name', key: 'name' },
        { title: '手机号', dataIndex: 'mobile', key: 'mobile' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
        { title: '下次联系', dataIndex: 'time', key: 'time' },
      ],
      rows: detailRows,
    },
  };
}

async function replyContacts(
  q = '',
  hintName?: string | null,
): Promise<AiReply> {
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getContactPage(params);
  const all: any[] = res.list || [];
  const rows = filterByName(all, name, [
    (r: any) => r.name,
    (r: any) => r.customerName,
  ]);
  return {
    text:
      rows.length === 0
        ? name
          ? `未找到与「${name}」相关的联系人。`
          : '当前没有查询到联系人记录。'
        : name
          ? `已为您找到 ${rows.length} 位与「${name}」相关的联系人：`
          : `共检索到 ${rows.length} 位联系人：`,
    table: {
      title: name ? `联系人：${name}` : '联系人列表',
      source: 'CRM 联系人管理 · 实查数据',
      bizType: 'contact',
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

async function replyContracts(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getContractPage(params);
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((c) => isCurrentMonth(c.createTime || c.startTime));
  }
  rows = filterByName(rows, name, [
    (c: any) => c.no,
    (c: any) => c.name,
    (c: any) => c.customerName,
  ]);
  return {
    text:
      rows.length === 0
        ? name
          ? `未找到与「${name}」相关的合同。`
          : month
            ? `本月（${nowMonth()}）没有查询到合同记录。`
            : '当前没有查询到合同记录。'
        : name
          ? `已为您找到 ${rows.length} 份与「${name}」相关的合同：`
          : `共检索到 ${rows.length} 份合同：`,
    table: {
      title: name ? `合同：${name}` : '合同列表',
      source: 'CRM 合同管理 · 实查数据',
      bizType: 'contract',
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

async function replyReceivables(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getReceivablePage(params);
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((r) => isCurrentMonth(r.returnTime));
  }
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.no,
  ]);
  return {
    text:
      rows.length === 0
        ? name
          ? `未找到与「${name}」相关的回款记录。`
          : month
            ? `本月（${nowMonth()}）没有查询到回款记录。`
            : '当前没有查询到回款记录。'
        : name
          ? `已为您找到 ${rows.length} 条与「${name}」相关的回款记录：`
          : `共检索到 ${rows.length} 条回款记录：`,
    table: {
      title: name ? `回款：${name}` : '回款列表',
      source: 'CRM 回款管理 · 实查数据',
      bizType: 'receivable',
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

async function replyReceivablePlans(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  const res = await getReceivablePlanPage(params);
  let rows: any[] = res.list || [];
  if (month) {
    rows = rows.filter((r) => isCurrentMonth(r.returnTime));
  }
  rows = filterByName(rows, name, [(r: any) => r.customerName]);
  return {
    text:
      rows.length === 0
        ? name
          ? `未找到与「${name}」相关的回款计划。`
          : month
            ? `本月（${nowMonth()}）没有查询到回款计划。`
            : '当前没有查询到回款计划。'
        : name
          ? `已为您找到 ${rows.length} 条与「${name}」相关的回款计划：`
          : `共检索到 ${rows.length} 条回款计划：`,
    table: {
      title: name ? `回款计划：${name}` : '回款计划列表',
      source: 'CRM 回款计划 · 实查数据',
      bizType: 'receivablePlan',
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
      bizType: 'product',
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

// 注意：意图识别提示词与模型密钥都已移到后端（CrmAiIntentClient + txgy.crm.ai 配置），
// 前端只调 /crm/ai/classify-intent，不再保存密钥，也不依赖 Vite 代理。

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
    // 由后端转发大模型：密钥保存在服务端，前端接触不到；
    // 也不再依赖 Vite 开发代理，打包部署后同样可用
    const res: any = await requestClient.post('/crm/ai/classify-intent', {
      query,
    });
    const content: string = res?.content ?? '';
    return parseJsonSafe<QwenClassify>(content);
  } catch {
    return null;
  }
}

async function getLocalTable(
  intent: string,
  q: string,
  hintName?: string | null,
): Promise<AiReply | null> {
  switch (intent) {
    case 'customer':
      return await replyCustomers(q, hintName);
    case 'pool':
      return await replyPool();
    case 'clue':
      return await replyClues(q, hintName);
    case 'business':
      return await replyBusinesses(q, hintName);
    case 'todo':
      return await replyTodos(q);
    case 'contact':
      return await replyContacts(q, hintName);
    case 'contract':
      return await replyContracts(q, hintName);
    case 'receivable':
      return await replyReceivables(q, hintName);
    case 'receivablePlan':
      return await replyReceivablePlans(q, hintName);
    case 'product':
      return await replyProducts();
    default:
      return null;
  }
}

/* ----------------------------- 意图分发 ----------------------------- */

/**
 * 结果条数达到单次查询上限时补一句说明。
 * 否则用户会把「只取回 100 条」误判成「系统里就这么多数据」。
 */
function withLimitHint(reply: AiReply): AiReply {
  const count = reply.table?.rows?.length ?? 0;
  if (count >= 100) {
    return {
      ...reply,
      text: `${reply.text}\n（本次最多展示 100 条，可补充名称等条件缩小范围）`,
    };
  }
  return reply;
}

/**
 * 主入口：优先调用千问大模型做意图理解，再用 CRM 真实接口拉取数据；
 * 若千问不可用（网络/密钥问题），则回退到本地关键词识别，仍调用真实接口。
 */
export async function mockAiReply(query: string): Promise<AiReply> {
  const q = query.trim();

  try {
    const cls = await classifyIntentWithQwen(q);
    if (cls) {
      const local = await getLocalTable(cls.intent, q, cls.name);
      if (local) {
        // 只有确实查到数据时才用千问的口头总结；
        // 未查到时要保留真实提示（否则「未找到」会被总结语盖掉，看着像有数据）
        const hasRows = (local.table?.rows?.length ?? 0) > 0;
        const text = hasRows ? cls.summary?.trim() || local.text : local.text;
        return withLimitHint({ text, table: local.table });
      }
      if (cls.summary) {
        return { text: cls.summary };
      }
    }
  } catch {
    // 落到下方关键词兜底
  }

  // 兜底：纯前端关键词识别（仍调用真实接口）。
  // 标记 degraded，让界面明确告知用户「AI 意图识别未生效」，而不是静默变笨
  return { ...withLimitHint(await keywordMockReply(q)), degraded: true };
}

async function keywordMockReply(query: string): Promise<AiReply> {
  const q = query.trim();

  // 优先级：公海 > 客户 > 线索 > 商机 > 待办 > 联系人 > 合同 > 回款计划 > 回款 > 产品
  if (/公海/.test(q)) return await replyPool();
  // 「待办」必须排在「客户」之前：像「今日待办要联系的客户」同时含两个词，应走待办明细
  if (/待办|todo|事项/.test(q)) return await replyTodos(q);
  if (/客户/.test(q) && !/公海/.test(q)) return await replyCustomers(q);
  if (/线索/.test(q)) return await replyClues(q);
  if (/商机|机会/.test(q)) return await replyBusinesses(q);
  if (/联系人|联系/.test(q)) return await replyContacts(q);
  if (/合同/.test(q)) return await replyContracts(q);
  if (/回款计划/.test(q)) return await replyReceivablePlans(q);
  if (/回款/.test(q)) return await replyReceivables(q);
  if (/产品|商品/.test(q)) return await replyProducts();

  return replyHelp();
}
