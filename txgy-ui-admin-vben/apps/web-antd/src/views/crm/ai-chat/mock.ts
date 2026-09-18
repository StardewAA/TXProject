/**
 * AI 对话助手 —— 千问意图理解 + 真实 CRM 数据查询
 *
 * 说明：所有结果均来自 CRM 系统真实接口，绝不返回任何编造/模拟数据。
 * 千问负责理解用户自然语言意图，前端再用真实接口拉取数据以表格展示；
 * 若千问不可用，则回退到本地关键词识别，但仍调用真实接口。
 */

import { getCustomerPage } from '#/api/crm/customer';
import { getCustomerPoolConfig } from '#/api/crm/customer/poolConfig';
import { getCluePage } from '#/api/crm/clue';
import { getBusinessPage } from '#/api/crm/business';
import { getContactPage } from '#/api/crm/contact';
import { createContract, getContractPage } from '#/api/crm/contract';
import { getReceivablePage } from '#/api/crm/receivable';
import { getReceivablePlanPage } from '#/api/crm/receivable/plan';
import { getProductPage } from '#/api/crm/product';
import { createQuotation, getQuotationPage } from '#/api/crm/quotation';
import { createAfterSaleVisitPlan } from '#/api/crm/after-sale/plan';
import { createAfterSaleVisitTask } from '#/api/crm/after-sale/task';
import {
  createFollowUpRecord,
  deleteFollowUpRecord,
  getFollowUpRecordPage,
} from '#/api/crm/followup';
import { getAfterSaleTicketPage } from '#/api/crm/after-sale/ticket';
import { getAfterSaleVisitPlanPage } from '#/api/crm/after-sale/plan';
import { getAfterSaleVisitTaskPage } from '#/api/crm/after-sale/task';
import { getAfterSaleVisitRecordPage } from '#/api/crm/after-sale/record';
import { getAfterSaleWarrantyPage } from '#/api/crm/after-sale/warranty';
import { getSimpleDictDataList } from '#/api/system/dict/data';
import { requestClient } from '#/api/request';

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
  | 'quotation'
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
  /** 一句话查多个对象时的多表结果（如「报价单 + 售后工单 + 质保档案」），含 table 本身 */
  tables?: TableResult[];
  /** 写操作：待用户二次确认的操作卡片 */
  action?: PendingAction;
  /** AI 意图识别不可用、已降级为关键词匹配 */
  degraded?: boolean;
}

/** 二次确认的写操作卡片 */
export interface PendingAction {
  type:
    | 'todoCreate'
    | 'todoComplete'
    | 'todoReschedule'
    | 'visitPlanCreate'
    | 'visitTaskCreate'
    | 'quotationCreate'
    | 'contractCreate';
  /** 卡片标题，如「新建待办」 */
  title: string;
  /** 待写入内容回显 */
  fields: { label: string; value: string }[];
  /** 执行所需数据 */
  payload: Record<string, any>;
  /**
   * 需要用户先做选择的场景（如合同类型无法判定时），
   * 点击按钮会把 value 合并进 payload 再执行，避免「一问多解」时猜错
   */
  choices?: { label: string; value: Record<string, any> }[];
}

function nowMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function isCurrentMonth(v: any): boolean {
  if (!v) return false;
  // 兼容毫秒时间戳：后端 LocalDateTime 以时间戳序列化（如 1789454512000），
  // 直接 startsWith('2026-09') 永远匹配不上，会导致「本月」查询返回空
  const n = Number(v);
  if (Number.isFinite(n) && String(v).trim().length >= 12) {
    const d = new Date(n);
    if (!Number.isNaN(d.getTime())) {
      const now = new Date();
      return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
    }
  }
  return String(v).startsWith(nowMonth());
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
  // 兼容毫秒时间戳：后端 LocalDateTime 以毫秒时间戳序列化（如 1788226956000）
  const n = Number(v);
  if (Number.isFinite(n) && String(v).trim().length >= 12) {
    const d = new Date(n);
    if (!Number.isNaN(d.getTime())) {
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }
  }
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
  AFTER_SALE_TICKET_STATUS: 'crm_after_sale_ticket_status',
  AFTER_SALE_TICKET_SOURCE: 'crm_after_sale_ticket_source',
  AFTER_SALE_TICKET_PRIORITY: 'crm_after_sale_ticket_priority',
  AFTER_SALE_VISIT_PLAN_TYPE: 'crm_after_sale_visit_plan_type',
  AFTER_SALE_VISIT_PLAN_STATUS: 'crm_after_sale_visit_plan_status',
  AFTER_SALE_VISIT_CYCLE: 'crm_after_sale_visit_cycle',
  AFTER_SALE_VISIT_TASK_STATUS: 'crm_after_sale_visit_task_status',
  AFTER_SALE_VISIT_TYPE: 'crm_after_sale_visit_type',
  AFTER_SALE_SATISFACTION: 'crm_after_sale_satisfaction',
  AFTER_SALE_WARRANTY_STATUS: 'crm_after_sale_warranty_status',
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
  // 注意：这里不能把单独的「下」当成前缀动词剥离，
  // 否则「我的下属所有客户」会被削成「属所有」，进而被误当成客户名
  v = v.replace(
    /^(?:(?:帮我|帮忙|请|麻烦|你|我|想|要|查一下|查一查|看一下|看下|看看|查找|查询|搜索|检索|列出|显示|展示|查看|了解|介绍|关于|查|找|搜|看|的|是|那个|这个))+\s*/g,
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
  if (new RegExp(`^(?:${GENERIC_WORDS})+$`).test(v)) {
    return true;
  }
  // 范围指代词只用来描述「谁的数据」，不可能是业务对象名称，一律拦截
  return /下属|团队|本组|手下|我们组|本人|当前用户/.test(v);
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

/**
 * 取本次查询要用的实体名称。
 *
 * 三种情况必须区分开，否则会把范围指代词误当成业务对象名：
 * - hintName 有值：用大模型识别出的名称；
 * - hintName 为 null：大模型明确判断问题里没有具体名称（如「我的下属所有客户」），
 *   此时绝不能再退回本地正则去猜，否则会猜出「属所有」这种脏名字；
 * - hintName 为 undefined：压根没走大模型（降级为关键词匹配），才用本地正则兜底。
 */
function pickName(
  hintName: string | null | undefined,
  q: string,
): string | null {
  if (hintName === null) return null;
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

/**
 * 计算有效归属范围（业务规则，必须严格区分，严禁混淆）
 *
 * 1. 「列出所有 XX / 全部 XX」    → 我负责的 + 下属负责的（管理者视角，不分 sceneType）
 * 2. 「列出我的 XX / 我负责的 XX」→ 仅本人负责（sceneType = 1），坚决排除下属数据
 * 3. 未指定归属（默认）           → 同「列出所有」：我负责的 + 下属负责的
 * 4. 「我的下属 / 团队成员」      → 仅下属负责（sceneType = 3）
 *
 * 说明：模型给的 sceneType 不能直接使用 —— 它默认返回 mine，
 * 会把「所有 XX」「未转化的线索」这类问法错误地缩到本人名下，导致下属数据整体消失。
 * 越权访问仍由后端数据权限强制拦截，这里只负责把业务口径翻译成查询条件。
 */
function resolveScope(q: string, _sceneType?: number): number | undefined {
  const isSubordinate =
    /下属|下级|手下|团队成员|下属负责/.test(q);
  const isTeam = /团队|我们组|我们部门|本组/.test(q);
  // ②「我的」：排除同时提到下属/团队的情况（如「我的下属」应走下属口径）
  if (/我的|我负责|我名下|本人|自己/.test(q) && !isSubordinate && !isTeam) {
    return 1;
  }
  // ④「我的下属 / 团队成员」→ 仅下属
  if (isSubordinate) {
    return 3;
  }
  // ①③「所有 / 全部 / 未指定」→ 我负责的 + 下属负责的（不限定归属）
  return undefined;
}

/** 归属范围的中文表述，用于回复与表格，保证文案与数据一致 */
function scopeLabelOf(sceneType?: number): string {
  if (sceneType === 1) return '我负责的';
  if (sceneType === 3) return '下属负责的';
  return '我负责的+下属负责的';
}

async function replyCustomers(
  q: string,
  hintName?: string | null,
  customerType?: null | number,
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  // 客户类别：优先用模型解析出的值（1=下游客户，2=上游供应商）；
  // 模型没给时，再沿用查询串里的「下游」关键词兜底
  const typeFilter = customerType ?? (/下游/.test(q) ? 1 : null);
  const typeLabel =
    typeFilter === 1 ? '下游客户' : typeFilter === 2 ? '上游供应商' : '';

  // 跟进口径：「需要跟进 / 待跟进的客户」= 跟进状态为待跟进（followUpStatus=false）
  const needFollow =
    /待跟进|需要跟进|需跟进|要跟进|需要被跟进|未跟进|没跟进|没有跟进/.test(q);
  // 归属范围：所有/未指定 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const effectiveSceneType = resolveScope(q, sceneType);
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  if (effectiveSceneType) params.sceneType = effectiveSceneType;
  if (needFollow) params.followUpStatus = false;
  const res = await getCustomerPage(params);
  // 已逻辑删除的客户一律不展示
  let rows: any[] = onlyAlive(res.list || []);

  // 按客户类别过滤：新数据直接用 customerType 字段；
  // 字段整体缺失时，回退到「下游客户行业 id 101-110」的旧字典约定
  if (typeFilter) {
    const hasField = rows.some(
      (c) => c.customerType !== null && c.customerType !== undefined,
    );
    if (hasField) {
      rows = rows.filter((c) => Number(c.customerType) === typeFilter);
    } else {
      const isInDownstreamIndustry = (c: any) =>
        c.industryId >= 101 && c.industryId <= 110;
      rows =
        typeFilter === 1
          ? rows.filter(isInDownstreamIndustry)
          : rows.filter((c) => !isInDownstreamIndustry(c));
    }
  }
  // 指定了客户名时只保留匹配项，绝不返回与问题无关的全量列表
  rows = filterByName(rows, name, [(c: any) => c.name]);
  // 按客户 ID 去重：同一客户只展示一次
  rows = dedupeById(rows);
  // 双保险：字段存在时再按同一规则兜一遍，杜绝条件未生效时返回全量
  if (needFollow && rows.some((c: any) => typeof c.followUpStatus === 'boolean')) {
    rows = rows.filter((c: any) => c.followUpStatus === false);
  }

  const scopeLabel = scopeLabelOf(effectiveSceneType);
  // 有结果只出表格，不再输出「为您查询到 N 个…」这类描述；查不到才如实说明
  let text = '';
  if (rows.length === 0) {
    text = needFollow
      ? `系统未查询到该数据：${scopeLabel}暂无待跟进的客户（可指定「我负责的」或「下属负责的」调整范围）。`
      : typeLabel
        ? `系统未查询到该数据：没有${typeLabel}记录。`
        : name
          ? `系统未查询到该数据：没有找到名为「${name}」的客户，请确认名称是否正确。`
          : '系统未查询到该数据。';
  }

  return {
    text,
    table: {
      title: typeLabel
        ? `${typeLabel}列表`
        : name
          ? `客户：${name}`
          : '客户列表',
      source: 'CRM 客户管理 · 实查数据',
      bizType: 'customer',
      columns: [
        { title: '客户名称', dataIndex: 'name', key: 'name' },
        { title: '客户类别', dataIndex: 'customerType', key: 'customerType' },
        { title: '手机', dataIndex: 'mobile', key: 'mobile' },
        { title: '客户级别', dataIndex: 'level', key: 'level' },
        { title: '所属行业', dataIndex: 'industry', key: 'industry' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
        { title: '成交状态', dataIndex: 'deal', key: 'deal' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        customerType:
          r.customerType === null || r.customerType === undefined
            ? '-'
            : Number(r.customerType) === 2
              ? '上游供应商'
              : '下游客户',
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
  // 已删除客户不展示 + 按客户 ID 去重
  const rows: any[] = dedupeById(onlyAlive(res.list || []));
  return {
    // 有结果只出表格；查不到才说明
    text:
      rows.length === 0 ? '系统未查询到该数据：客户公海没有待领取的客户。' : '',
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
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  // 业务规则：「需要被跟进的线索」= 跟进状态为待跟进（未跟进）+ 转化状态为未转化
  const needFollow =
    /待跟进|需要跟进|需跟进|要跟进|需要被跟进|未跟进|没跟进|没有跟进/.test(q);
  const noTransform =
    /未转化|没转化|没有转化|尚未转化|未成交|没成交/.test(q);
  // 归属范围：所有/未指定 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const effectiveSceneType = resolveScope(q, sceneType);

  // 注意：过滤条件必须交给后端，否则只取前 100 条再筛，数据一多就会「查不到 / 查到全量」
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  if (effectiveSceneType) params.sceneType = effectiveSceneType;
  if (needFollow) params.followUpStatus = false; // false = 待跟进（未跟进）
  if (noTransform) params.transformStatus = false; // false = 未转化
  const res = await getCluePage(params);
  // 已逻辑删除的线索一律不展示
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.name,
    (r: any) => r.customerName,
  ]);
  // 双保险：字段存在时再按同一规则兜一遍，杜绝「条件没生效就把全量返回」的情况
  if (needFollow && rows.some((r: any) => typeof r.followUpStatus === 'boolean')) {
    rows = rows.filter((r: any) => r.followUpStatus === false);
  }
  if (
    noTransform &&
    rows.some((r: any) => typeof r.transformStatus === 'boolean')
  ) {
    rows = rows.filter((r: any) => r.transformStatus === false);
  }
  // 按线索 ID 去重：同一条线索只展示一次
  rows = dedupeById(rows);

  const byRule = needFollow && noTransform;
  const scopeLabel = scopeLabelOf(effectiveSceneType);
  const condLabel = [needFollow ? '待跟进' : '', noTransform ? '未转化' : '']
    .filter(Boolean)
    .join('、');
  // 有结果只出表格（同名不同编号的线索已在明细里按「线索编号」区分）；
  // 查不到才如实说明，并保留口径与条件，避免用户误以为系统里没有数据
  let text = '';
  if (rows.length === 0) {
    text = byRule
      ? `系统未查询到该数据：${scopeLabel}暂无待跟进未转化线索。`
      : condLabel
        ? `系统未查询到该数据：${scopeLabel}没有${condLabel}的线索（可指定「我负责的」或「下属负责的」调整范围）。`
        : name
          ? `系统未查询到该数据：没有找到与「${name}」相关的线索。`
          : `系统未查询到该数据：${scopeLabel}暂无线索记录。`;
  }

  return {
    text,
    table: {
      title: name
        ? `线索：${name}`
        : byRule
          ? '待跟进未转化线索'
          : condLabel
            ? `${condLabel}线索`
            : '线索列表',
      source: 'CRM 线索管理 · 实查数据',
      bizType: 'clue',
      columns: [
        { title: '线索名称', dataIndex: 'name', key: 'name' },
        { title: '线索编号', dataIndex: 'no', key: 'no' },
        { title: '手机', dataIndex: 'mobile', key: 'mobile' },
        { title: '客户来源', dataIndex: 'source', key: 'source' },
        { title: '所属行业', dataIndex: 'industry', key: 'industry' },
        { title: '转化状态', dataIndex: 'transform', key: 'transform' },
        { title: '跟进状态', dataIndex: 'follow', key: 'follow' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        no: r.id,
        name: r.name || '-',
        mobile: r.mobile || '-',
        source: labelOf(maps, DICT_TYPES.SOURCE, r.source),
        industry: labelOf(maps, DICT_TYPES.INDUSTRY, r.industryId),
        transform: r.transformStatus ? '已转化' : '未转化',
        follow: r.followUpStatus ? '已跟进' : '待跟进',
        owner: r.ownerUserName || r.ownerUserDeptName || '-',
      })),
    },
  };
}

async function replyBusinesses(
  q: string,
  hintName?: string | null,
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  // 归属范围：所有/默认 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const scope = resolveScope(q, sceneType);
  if (scope) params.sceneType = scope;
  const res = await getBusinessPage(params);
  let rows: any[] = onlyAlive(res.list || []);
  if (month) {
    rows = rows.filter((b) => isCurrentMonth(b.dealTime || b.createTime));
  }
  rows = filterByName(rows, name, [
    (b: any) => b.name,
    (b: any) => b.customerName,
  ]);
  return {
    // 有结果只出表格；查不到才说明
    text:
      rows.length === 0
        ? month
          ? `系统未查询到该数据：本月（${nowMonth()}）没有商机记录。`
          : noDataText(name)
        : '',
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

async function replyTodos(
  q = '',
  scopeSceneType?: number,
  /** 大模型解析出的时间范围（如「今天」「本月」），用于按提问口径收窄 */
  timeRange?: null | string,
): Promise<AiReply> {
  // 大模型识别出的范围优先；降级模式下再从问题关键词判断（sceneType=3=下属负责的）
  const sceneType =
    scopeSceneType ?? (/下属|团队|他们/.test(q) ? 3 : undefined);
  const baseQuery: Record<string, any> = { pageNo: 1, pageSize: 20 };
  if (sceneType) {
    baseQuery.sceneType = sceneType;
  }

  // ---------------------------------------------------------------------------
  // 提问口径（严格遵循）：问什么就给什么，不做「搭售」
  //   · 问「今日/今天」      → 只给「今日需联系」客户
  //   · 问「逾期/超期」      → 只给「已逾期未联系」客户
  //   · 问「待跟进/需要跟进」→ 只给「待跟进」客户
  //   · 未限定口径（如「查看我的待办」「待办事项」）→ 才把三类合并去重展示
  // 这样明细与用户的问题严格一致，不会出现「问今天却给一堆逾期」。
  // ---------------------------------------------------------------------------
  const ask = `${q} ${timeRange ?? ''}`;
  const wantToday = /今天|今日|当天|本日/.test(ask);
  const wantOverdue = /逾期|超期|过期/.test(ask);
  const wantFollow = /待跟进|需要跟进|需跟进|要跟进/.test(ask);
  const scoped = wantToday || wantOverdue || wantFollow;
  const needToday = !scoped || wantToday;
  const needOverdue = !scoped || wantOverdue;
  const needFollow = !scoped || wantFollow;

  // 说明：不再调用各模块的「待办计数」接口 —— 输出里已不展示待办总览，
  // 只按用户问的口径查出客户明细，避免无谓请求与多余统计

  // 关键：只给数量用户没法跟进，同时把「具体是哪些客户」也查出来
  // 按需查询：用户没问的那一类不去查（也不展示），避免答案与提问口径不符
  const [overduePage, todayPage, followPage] = await Promise.all([
    needOverdue
      ? getCustomerPage({ ...baseQuery, contactStatus: 2 } as any).catch(
          () => null,
        )
      : null,
    needToday
      ? getCustomerPage({ ...baseQuery, contactStatus: 1 } as any).catch(
          () => null,
        )
      : null,
    needFollow
      ? getCustomerPage({ ...baseQuery, followUpStatus: false } as any).catch(
          () => null,
        )
      : null,
  ]);
  // 明细数据源（三类待办按需查询，只用于生成下方明细表格）
  const overdueList = onlyAlive(overduePage?.list ?? []);
  const todayList = onlyAlive(todayPage?.list ?? []);
  const followList = onlyAlive(followPage?.list ?? []);

  // 按客户聚合：同一客户可能同时命中多种待办（如既「已逾期未联系」又「待跟进」），
  // 一条业务对象只输出一行，待办类型合并展示，绝不重复输出同一客户
  interface TodoAgg {
    id: any;
    name: string;
    mobile: string;
    owner: string;
    time: string;
    timeRaw: any;
    types: string[];
  }
  const agg = new Map<string, TodoAgg>();
  const collect = (
    list: any[],
    type: string,
    timeField: string,
    fallbackField: string,
  ) => {
    for (const c of list) {
      const key =
        c?.id === null || c?.id === undefined
          ? `#${c?.name ?? ''}`
          : String(c.id);
      const timeRaw = c?.[timeField] ?? c?.[fallbackField];
      const exist = agg.get(key);
      if (exist) {
        // 合并待办类型；下次联系时间取更早的一条（逾期优先）
        if (!exist.types.includes(type)) {
          exist.types.push(type);
        }
        if (
          timeRaw &&
          (!exist.timeRaw || Number(timeRaw) < Number(exist.timeRaw))
        ) {
          exist.timeRaw = timeRaw;
          exist.time = fmtDate(timeRaw);
        }
        if (!exist.mobile || exist.mobile === '-') {
          exist.mobile = c?.mobile ?? exist.mobile;
        }
        if (!exist.owner || exist.owner === '-') {
          exist.owner = c?.ownerUserName ?? exist.owner;
        }
        continue;
      }
      agg.set(key, {
        id: c?.id,
        name: c?.name ?? '-',
        mobile: c?.mobile ?? '-',
        owner: c?.ownerUserName ?? '-',
        // 待跟进类客户可能尚未设置下次联系时间，如实标注为「未设置」而不是「-」
        time: timeRaw ? fmtDate(timeRaw) : '未设置',
        timeRaw,
        types: [type],
      });
    }
  };
  collect(overdueList, '已逾期未联系', 'contactNextTime', 'contactLastTime');
  collect(todayList, '今日需联系', 'contactNextTime', 'contactLastTime');
  collect(followList, '待跟进', 'contactNextTime', 'contactLastTime');

  // 逾期的排前面，其次按下次联系时间从早到晚
  const typeOrder = ['已逾期未联系', '今日需联系', '待跟进'];
  const detailRows: Array<Record<string, any>> = [...agg.values()]
    .sort((a, b) => {
      const oa = typeOrder.findIndex((t) => a.types.includes(t));
      const ob = typeOrder.findIndex((t) => b.types.includes(t));
      if (oa !== ob) return oa - ob;
      return Number(a.timeRaw ?? 0) - Number(b.timeRaw ?? 0);
    })
    .map((v) => ({
      id: v.id,
      type: v.types.join(' / '),
      name: v.name,
      mobile: v.mobile,
      owner: v.owner,
      time: v.time,
    }));
  // 本次实际展示的客户类口径（用于表格标题）
  const shownCats = [
    needToday ? '今日需联系' : '',
    needOverdue ? '已逾期未联系' : '',
    needFollow ? '待跟进' : '',
  ].filter(Boolean);

  // 输出格式：只给明细表格。
  // 不再输出「待办总览 / 口径说明 / 合并说明 / 引导提示」等任何辅助话术。
  if (detailRows.length === 0) {
    return { text: '系统未查询到该数据。' };
  }

  return {
    text: '',
    table: {
      title: scoped ? `${shownCats.join(' / ')}客户明细` : '待办客户明细',
      source: 'CRM 客户 · 实查数据',
      bizType: 'customer',
      columns: [
        { title: '待办类型', dataIndex: 'type', key: 'type' },
        { title: '客户名称', dataIndex: 'name', key: 'name' },
        { title: '下次联系', dataIndex: 'time', key: 'time' },
        { title: '手机号', dataIndex: 'mobile', key: 'mobile' },
        { title: '负责人', dataIndex: 'owner', key: 'owner' },
      ],
      rows: detailRows,
    },
  };
}

async function replyContacts(
  q = '',
  hintName?: string | null,
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  // 归属范围：所有/默认 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const scope = resolveScope(q, sceneType);
  if (scope) params.sceneType = scope;
  const res = await getContactPage(params);
  // 已逻辑删除的联系人不展示 + 按联系人 ID 去重
  const all: any[] = onlyAlive(res.list || []);
  const rows = dedupeById(
    filterByName(all, name, [
      (r: any) => r.name,
      (r: any) => r.customerName,
    ]),
  );
  return {
    // 有结果只出表格，不再输出「已为您找到 N 位…」这类话术；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
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
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  // 归属范围：所有/默认 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const scope = resolveScope(q, sceneType);
  if (scope) params.sceneType = scope;
  const res = await getContractPage(params);
  // 已逻辑删除的合同一律不展示
  let rows: any[] = onlyAlive(res.list || []);
  if (month) {
    rows = rows.filter((c) => isCurrentMonth(c.createTime || c.startTime));
  }
  rows = filterByName(rows, name, [
    (c: any) => c.no,
    (c: any) => c.name,
    (c: any) => c.customerName,
  ]);
  // 按合同 ID 去重：同一份合同只展示一次
  rows = dedupeById(rows);
  return {
    // 有结果只出表格；查不到才说明（本月无数据时点明月份）
    text:
      rows.length === 0
        ? month
          ? `系统未查询到该数据：本月（${nowMonth()}）没有合同记录。`
          : noDataText(name)
        : '',
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
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  // 归属范围：所有/默认 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const scope = resolveScope(q, sceneType);
  if (scope) params.sceneType = scope;
  const res = await getReceivablePage(params);
  let rows: any[] = onlyAlive(res.list || []);
  if (month) {
    rows = rows.filter((r) => isCurrentMonth(r.returnTime));
  }
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.no,
  ]);
  return {
    // 有结果只出表格；查不到才说明
    text:
      rows.length === 0
        ? month
          ? `系统未查询到该数据：本月（${nowMonth()}）没有回款记录。`
          : noDataText(name)
        : '',
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
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const month = /本月|这个月|当月/.test(q);
  const name = pickName(hintName, q);
  // 有名称时交给后端过滤，避免只在前 100 条里找
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  // 归属范围：所有/默认 = 我负责的+下属负责的；我的 = 仅本人；下属 = 仅下属
  const scope = resolveScope(q, sceneType);
  if (scope) params.sceneType = scope;
  const res = await getReceivablePlanPage(params);
  let rows: any[] = onlyAlive(res.list || []);
  if (month) {
    rows = rows.filter((r) => isCurrentMonth(r.returnTime));
  }
  rows = filterByName(rows, name, [(r: any) => r.customerName]);
  return {
    // 有结果只出表格；查不到才说明
    text:
      rows.length === 0
        ? month
          ? `系统未查询到该数据：本月（${nowMonth()}）没有回款计划。`
          : noDataText(name)
        : '',
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
  const rows: any[] = onlyAlive(res.list || []);
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? '系统未查询到该数据。' : '',
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

/* ------------------------- 报价 / 售后（真实接口） ------------------------- */

/** 统一的无数据话术（严格遵循「系统未查询到该数据」约束，绝不编造） */
function noDataText(name?: string | null): string {
  return name
    ? `系统未查询到该数据：没有找到与「${name}」相关的记录。`
    : '系统未查询到该数据。';
}

/* ------------------- 结果清洗：逻辑删除防御 + 按业务 ID 去重 ------------------- */

/**
 * 判断一条记录是否已被逻辑删除
 *
 * 业务口径：只有「未打软删除标记」的记录才算系统当前有效存在的数据；
 * 软删除记录一律视为不存在，不参与检索、不返回结果。
 *
 * 两道防线：
 *   1) 后端：各分页/详情接口由 MyBatis-Plus 逻辑删除自动过滤 deleted=0（主防线）；
 *   2) 前端：本函数 + onlyAlive() 兜底 —— 若响应里带出任何删除标记
 *      （deleted / isDeleted / delFlag，值为 1/'1'/true/'true'/字节数组等），
 *      直接在 AI 层剔除，任何查询场景都不展示。
 */
function isDeletedRow(r: any): boolean {
  if (!r || typeof r !== 'object') {
    return false;
  }
  const d = r.deleted ?? r.isDeleted ?? r.delFlag;
  if (d === null || d === undefined || d === '' || d === false) {
    return false;
  }
  if (d === 0 || d === '0') {
    return false;
  }
  if (d === 1 || d === '1' || d === true || d === 'true') {
    return true;
  }
  // 兼容 Buffer / {type:'Buffer', data:[1]} 这类序列化形式
  if (typeof d === 'object') {
    const bytes = Array.isArray((d as any).data)
      ? (d as any).data
      : Array.isArray(d)
        ? d
        : null;
    if (bytes && bytes.length > 0) {
      return Number(bytes[0]) !== 0;
    }
    return false;
  }
  // 其它形式：先按明确的「未删除」写法排除，再按数字/布尔语义兜底
  const s = String(d).trim().toLowerCase();
  if (['false', 'n', 'no'].includes(s)) {
    return false;
  }
  if (['y', 'yes'].includes(s)) {
    return true;
  }
  const n = Number(s);
  return Number.isFinite(n) ? n !== 0 : false;
}

/** 剔除已逻辑删除的记录（软删除数据一律不展示） */
function onlyAlive<T>(rows: T[]): T[] {
  return (rows || []).filter((r) => !isDeletedRow(r));
}

/**
 * 按业务唯一 ID 去重：同一实体只保留一条
 * 没有 ID 的行无法判定唯一性，原样保留（不误删）
 */
function dedupeById<T extends Record<string, any>>(
  rows: T[],
  idOf: (r: T) => any = (r: any) => r?.id,
): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const r of rows || []) {
    const raw = idOf(r);
    if (raw === null || raw === undefined || raw === '') {
      out.push(r);
      continue;
    }
    const key = String(raw);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(r);
  }
  return out;
}

/** 今天（本地时区）YYYY-MM-DD，用于回访任务的今日/逾期筛选 */
function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** 按字典标签包含关键词过滤（不猜测状态数字，只按标签匹配，匹配不到时如实说明） */
function filterRowsByLabel(
  rows: Record<string, any>[],
  maps: Record<string, Record<string, string>>,
  dictType: string,
  field: string,
  keyword: string,
): Record<string, any>[] {
  const kw = keyword.trim();
  if (!kw) return rows;
  return rows.filter((r) => {
    const label = String(maps[dictType]?.[String(r[field] ?? '')] ?? '');
    return label !== '' && (label.includes(kw) || kw.includes(label));
  });
}

/** 报价单查询（支持按客户名/单号/名称过滤） */
async function replyQuotations(
  q: string,
  hintName?: string | null,
  sceneType?: number,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const params: any = { pageNo: 1, pageSize: 100 };
  if (name) params.name = name;
  if (sceneType) params.sceneType = sceneType;
  const res = await getQuotationPage(params);
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.name,
    (r: any) => r.no,
    (r: any) => r.customerName,
  ]);
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
    table: {
      title: name ? `报价单：${name}` : '报价单列表',
      source: 'CRM 报价管理 · 实查数据',
      bizType: 'quotation',
      columns: [
        { title: '报价单号', dataIndex: 'no', key: 'no' },
        { title: '报价名称', dataIndex: 'name', key: 'name' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '报价金额', dataIndex: 'amount', key: 'amount' },
        { title: '报价日期', dataIndex: 'date', key: 'date' },
        { title: '有效期至', dataIndex: 'validUntil', key: 'validUntil' },
        { title: '审批状态', dataIndex: 'status', key: 'status' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        no: r.no || '-',
        name: r.name || '-',
        customer: r.customerName || '-',
        amount: fmtMoney(r.totalPrice),
        date: fmtDate(r.orderDate || r.createTime),
        validUntil: fmtDate(r.validUntil),
        status: labelOf(maps, DICT_TYPES.AUDIT_STATUS, r.auditStatus),
      })),
    },
  };
}

/** 售后问题工单查询 */
async function replyAfterSaleTickets(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const res = await getAfterSaleTicketPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.no,
    (r: any) => r.title,
  ]);
  // 「待处理 / 处理中」类筛选：按字典标签匹配，不猜测状态数字
  const statusKw = /待处理|处理中|未完成|未关闭/.exec(q)?.[0];
  if (statusKw) {
    rows = filterRowsByLabel(
      rows,
      maps,
      DICT_TYPES.AFTER_SALE_TICKET_STATUS,
      'status',
      statusKw,
    );
  }
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
    table: {
      title: name ? `售后工单：${name}` : '售后问题工单',
      source: 'CRM 售后管理 · 实查数据',
      columns: [
        { title: '工单编号', dataIndex: 'no', key: 'no' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '问题描述', dataIndex: 'title', key: 'title' },
        { title: '优先级', dataIndex: 'priority', key: 'priority' },
        { title: '状态', dataIndex: 'status', key: 'status' },
        { title: '处理人', dataIndex: 'assignee', key: 'assignee' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        no: r.no || '-',
        customer: r.customerName || '-',
        title: r.title || r.problemType || '-',
        priority: labelOf(
          maps,
          DICT_TYPES.AFTER_SALE_TICKET_PRIORITY,
          r.priority,
        ),
        status: labelOf(maps, DICT_TYPES.AFTER_SALE_TICKET_STATUS, r.status),
        assignee: r.assigneeNickname || '-',
      })),
    },
  };
}

/** 售后回访计划查询 */
async function replyVisitPlans(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const res = await getAfterSaleVisitPlanPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.name,
    (r: any) => r.projectName,
    (r: any) => r.warrantyNo,
  ]);
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
    table: {
      title: name ? `回访计划：${name}` : '售后回访计划',
      source: 'CRM 售后管理 · 实查数据',
      columns: [
        { title: '计划名称', dataIndex: 'name', key: 'name' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '工程项目', dataIndex: 'project', key: 'project' },
        { title: '质保编号', dataIndex: 'warrantyNo', key: 'warrantyNo' },
        { title: '回访方式', dataIndex: 'visitType', key: 'visitType' },
        { title: '状态', dataIndex: 'status', key: 'status' },
        { title: '处理人', dataIndex: 'assignee', key: 'assignee' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        name: r.name || '-',
        customer: r.customerName || '-',
        project: r.projectName || '-',
        warrantyNo: r.warrantyNo || '-',
        visitType: labelOf(maps, DICT_TYPES.AFTER_SALE_VISIT_TYPE, r.visitType),
        status: labelOf(
          maps,
          DICT_TYPES.AFTER_SALE_VISIT_PLAN_STATUS,
          r.status,
        ),
        assignee: r.assigneeNickname || '-',
      })),
    },
  };
}

/** 售后回访任务查询（支持 待回访/逾期/今日/本周 等口语筛选） */
async function replyVisitTasks(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const res = await getAfterSaleVisitTaskPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.projectName,
    (r: any) => r.warrantyNo,
    (r: any) => r.contractName,
  ]);
  const today = todayStr();
  // 待回访 / 未完成：以是否已有完成时间判断，不猜测状态数字
  if (/待回访|未完成|未回访/.test(q)) {
    rows = rows.filter((r) => !r.finishTime);
  }
  // 逾期：任务日期早于今天且尚未完成
  if (/逾期|超期/.test(q)) {
    rows = rows.filter(
      (r) => !r.finishTime && r.taskDate && fmtDate(r.taskDate) < today,
    );
  }
  if (/今天|今日/.test(q)) {
    rows = rows.filter((r) => fmtDate(r.taskDate) === today);
  }
  if (/明天|明日/.test(q)) {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const t = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
    rows = rows.filter((r) => fmtDate(r.taskDate) === t);
  }
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
    table: {
      title: name ? `回访任务：${name}` : '售后回访任务',
      source: 'CRM 售后管理 · 实查数据',
      columns: [
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '工程项目', dataIndex: 'project', key: 'project' },
        { title: '质保编号', dataIndex: 'warrantyNo', key: 'warrantyNo' },
        { title: '任务日期', dataIndex: 'taskDate', key: 'taskDate' },
        { title: '状态', dataIndex: 'status', key: 'status' },
        { title: '回访记录', dataIndex: 'record', key: 'record' },
        { title: '处理人', dataIndex: 'assignee', key: 'assignee' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        customer: r.customerName || '-',
        project: r.projectName || '-',
        warrantyNo: r.warrantyNo || '-',
        taskDate: fmtDate(r.taskDate),
        status: labelOf(
          maps,
          DICT_TYPES.AFTER_SALE_VISIT_TASK_STATUS,
          r.status,
        ),
        record: r.hasRecord ? '已登记' : '未登记',
        assignee: r.assigneeNickname || '-',
      })),
    },
  };
}

/** 售后回访记录查询 */
async function replyVisitRecords(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const res = await getAfterSaleVisitRecordPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.projectName,
    (r: any) => r.warrantyNo,
  ]);
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
    table: {
      title: name ? `回访记录：${name}` : '售后回访记录',
      source: 'CRM 售后管理 · 实查数据',
      columns: [
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '工程项目', dataIndex: 'project', key: 'project' },
        { title: '质保编号', dataIndex: 'warrantyNo', key: 'warrantyNo' },
        { title: '回访时间', dataIndex: 'visitTime', key: 'visitTime' },
        { title: '回访方式', dataIndex: 'visitType', key: 'visitType' },
        { title: '满意度', dataIndex: 'satisfaction', key: 'satisfaction' },
        { title: '回访内容', dataIndex: 'content', key: 'content' },
        { title: '记录人', dataIndex: 'creator', key: 'creator' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        customer: r.customerName || '-',
        project: r.projectName || '-',
        warrantyNo: r.warrantyNo || '-',
        visitTime: fmtDate(r.visitTime || r.createTime),
        visitType: labelOf(maps, DICT_TYPES.AFTER_SALE_VISIT_TYPE, r.visitType),
        satisfaction: labelOf(
          maps,
          DICT_TYPES.AFTER_SALE_SATISFACTION,
          r.satisfactionLevel,
        ),
        content: r.content || '-',
        creator: r.creatorNickname || '-',
      })),
    },
  };
}

/** 质保档案查询（支持 即将到期/已到期 筛选） */
async function replyWarranties(
  q: string,
  hintName?: string | null,
): Promise<AiReply> {
  const maps = await loadDictMaps();
  const name = pickName(hintName, q);
  const res = await getAfterSaleWarrantyPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.projectName,
    (r: any) => r.no,
    (r: any) => r.contractNo,
  ]);
  if (/即将到期|快到期|临期|快要到期/.test(q)) {
    rows = rows.filter(
      (r) => r.remainDays != null && r.remainDays >= 0 && r.remainDays <= 30,
    );
  }
  if (/已到期|过期|超期/.test(q)) {
    rows = rows.filter((r) => r.remainDays != null && r.remainDays < 0);
  }
  return {
    // 有结果只出表格；查不到才说明
    text: rows.length === 0 ? noDataText(name) : '',
    table: {
      title: name ? `质保档案：${name}` : '客户质保档案',
      source: 'CRM 售后管理 · 实查数据',
      columns: [
        { title: '质保编号', dataIndex: 'no', key: 'no' },
        { title: '客户', dataIndex: 'customer', key: 'customer' },
        { title: '工程项目', dataIndex: 'project', key: 'project' },
        { title: '合同编号', dataIndex: 'contractNo', key: 'contractNo' },
        { title: '质保年限', dataIndex: 'years', key: 'years' },
        { title: '质保到期', dataIndex: 'endDate', key: 'endDate' },
        { title: '剩余时限', dataIndex: 'remain', key: 'remain' },
        { title: '状态', dataIndex: 'status', key: 'status' },
      ],
      rows: rows.map((r) => ({
        id: r.id,
        no: r.no || '-',
        customer: r.customerName || '-',
        project: r.projectName || '-',
        contractNo: r.contractNo || '-',
        years: r.warrantyYears != null ? `${r.warrantyYears} 年` : '-',
        endDate: fmtDate(r.warrantyEndDate),
        remain:
          r.remainDays == null
            ? '-'
            : r.remainDays < 0
              ? `已过期 ${-r.remainDays} 天`
              : `剩余 ${r.remainDays} 天`,
        status: labelOf(
          maps,
          DICT_TYPES.AFTER_SALE_WARRANTY_STATUS,
          r.status,
        ),
      })),
    },
  };
}

/* ---------------------- 业务规则知识库（问“规则”≠查“数据”） ---------------------- */

/** 规则类问法：用户问的是“制度 / 口径 / 怎么算 / 区别”，不是在要数据列表 */
const RULE_ASK_RE =
  /规则|制度|机制|流程|政策|口径|标准|条件|怎么算|怎么计算|如何计算|怎么统计|如何统计|多久|多长时间|多少天|几天|判定|定义|含义|是什么意思|是什么|什么是|解释一下|说明一下|怎么回事|怎么样|怎么办|怎么回收|如何回收|怎么流转|如何流转|怎么转化|如何转化|怎么操作|如何操作|区别|不同|差异|能做什么|能干什么|有哪些功能|支持哪些|你会什么/;

/** 强规则信号：即使句子里出现「列出/查看/有哪些」也仍按规则问答处理 */
const STRONG_RULE_RE =
  /规则|制度|机制|流程|口径|标准|怎么算|怎么计算|如何计算|多久|多长时间|多少天|什么条件|含义|是什么意思|定义|怎么回收|如何回收|怎么流转|如何流转|怎么转化|如何转化/;

/** 明确的「要数据列表」问法 */
const DATA_ASK_RE =
  /^(列出|列一下|列举|列个|查看|查一下|查询|查下|看一下|看看|展示|显示|给我看看|给我看|找一下|帮我找|搜索|筛选)|有哪些/;

/** CRM 业务域词：避免把无关闲聊判成业务规则问答 */
const CRM_DOMAIN_RE =
  /公海|回收|认领|客户|线索|联系人|商机|阶段|赢单|输单|合同|回款|逾期|账期|待办|提醒|跟进|下次联系|售后|回访|质保|工单|报价|产品|WMS|库存|CRM|能做什么|能干什么|有哪些功能|支持哪些|你会什么/;

/**
 * 是否属于「业务规则 / 制度咨询」
 *
 * 判定依据：
 *   1. 必须落在 CRM 业务域内，且出现规则类问法；
 *   2. 若同时是明确的「列出 / 查看 / 有哪些」数据请求，且没有强规则信号
 *      （规则、制度、流程、怎么算、多久……），则仍按数据查询处理：
 *      例如「不同类型的客户有哪些」= 查数据；「客户公海回收的规则是什么」= 问规则。
 */
function isRuleQuestion(q: string): boolean {
  const s = (q || '').trim();
  if (!s || !CRM_DOMAIN_RE.test(s) || !RULE_ASK_RE.test(s)) {
    return false;
  }
  if (DATA_ASK_RE.test(s) && !STRONG_RULE_RE.test(s)) {
    return false;
  }
  return true;
}

/** 公海回收规则的兜底文案（拿不到配置时使用，绝不编造具体天数） */
const POOL_RULE_FALLBACK =
  '客户公海回收规则：\n' +
  '· 触发条件：客户在「N 天不跟进」或「N 天未成交」达到设定天数后，自动回收进客户公海；\n' +
  '· 计时起点：以「实际跟进时间」为准（本次实际联系客户的时间，不是跟进笔记录入的时间）；从未跟进过的，从领取 / 创建时间起算；\n' +
  '· 回收结果：客户从原负责人名下移出、负责人清空，进入客户公海，公海内所有有权限的成员都可以认领；\n' +
  '· 提前提醒：可配置「提前 N 天提醒即将回收的客户」；\n' +
  '· 具体天数与开关见「CRM → 客户管理 → 客户公海设置」（我也可以直接帮你读当前配置）。';

/** 读取真实公海配置，拼出带具体天数的规则说明（数字全部来自系统配置，不编造） */
async function poolRuleText(): Promise<string> {
  try {
    const cfg: any = await getCustomerPoolConfig();
    if (!cfg) {
      return POOL_RULE_FALLBACK;
    }
    if (cfg.enabled !== true) {
      return (
        '客户公海回收规则（依据本租户「客户公海设置」的当前配置）：\n' +
        '· 当前「客户公海规则设置」为【关闭】：系统不会自动把客户回收进公海；\n' +
        '· 如需自动回收，管理员可在「CRM → 客户管理 → 客户公海设置」中开启并设置天数。'
      );
    }
    const notify =
      cfg.notifyEnabled === true
        ? `已开启，提前 ${cfg.notifyDays ?? '-'} 天提醒`
        : '未开启';
    return (
      '客户公海回收规则（依据本租户「客户公海设置」的当前配置）：\n' +
      '· 客户公海规则设置：已开启\n' +
      `· 回收条件（满足其一即回收）：${cfg.contactExpireDays ?? '-'} 天不跟进，或 ${cfg.dealExpireDays ?? '-'} 天未成交\n` +
      `· 提前提醒设置：${notify}\n` +
      '· 计时起点：以「实际跟进时间」为准（本次实际联系客户的时间，不是跟进笔记录入的时间）；从未跟进过的，从领取 / 创建时间起算\n' +
      '· 回收结果：客户从原负责人名下移出、负责人清空，进入客户公海，公海内所有有权限的成员都可以认领\n' +
      '· 被回收后如需继续跟进，可到「客户公海」把客户认领回自己名下'
    );
  } catch {
    // 无权限或接口异常时退回通用说明，不编造天数
    return POOL_RULE_FALLBACK;
  }
}

/** 业务规则知识库：按主题返回文字说明（只讲规则，不查列表数据） */
const RULE_TOPICS: { title: string; match: RegExp; text: string }[] = [
  {
    title: '客户公海回收规则',
    match: /公海|回收|认领|捞/,
    text: '',
  },
  {
    title: '线索转客户规则',
    match: /线索/,
    text:
      '线索与客户是两套独立实体、独立编号：\n' +
      '· 线索 = 尚未验证的潜在机会；客户 = 已确认的合作对象；\n' +
      '· 线索有两个关键状态：跟进状态（待跟进 / 已跟进）、转化状态（未转化 / 已转化）；\n' +
      '· 线索确认有价值后可「转为客户」，转化后线索标记为已转化；\n' +
      '· 因此问「需要跟进的线索」，系统只返回「未跟进且未转化」的线索，已跟进或已转化的不会出现。',
  },
  {
    title: '商机阶段流转规则',
    match: /商机|阶段|赢单|输单/,
    text:
      '· 商机挂在客户下，用「商机状态组 + 阶段」描述销售进度；\n' +
      '· 推进方式：修改商机当前阶段，系统按所属状态组的配置决定可选阶段；\n' +
      '· 结案：赢单 / 输单会结束商机（进入终态），结案后不再参与阶段推进；\n' +
      '· 阶段名称、顺序与赢输单判定以「CRM → 商机 → 商机状态组」的配置为准。',
  },
  {
    title: '回款与逾期规则',
    match: /回款|逾期|账期/,
    text:
      '· 回款计划 = 计划收款（计划金额 + 计划回款日期）；回款单 = 实际到账登记；\n' +
      '· 逾期判定：计划回款日期已过、且该计划尚未回款或未回完，即视为逾期并进入回款提醒；\n' +
      '· 只有登记了「回款计划」的合同 / 业务才会产生逾期提醒；\n' +
      '· 逾期与提醒口径可在「CRM → 回款管理」列表中用条件筛选核对。',
  },
  {
    title: '跟进记录 / 下次联系时间 / 实际跟进时间的区别',
    match: /跟进记录|下次联系|实际跟进|字段/,
    text:
      '三个时间字段很容易混淆，请注意区分：\n' +
      '· 创建时间 create_time：这条跟进笔记「录入系统」的时间；\n' +
      '· 实际跟进时间 follow_up_time：本次「实际联系客户」的时间 —— 公海回收计时以它为准；\n' +
      '· 下次联系时间 next_time：「下次计划联系」的时间，也是待办、今日需联系、逾期判定的依据；\n' +
      '· 记忆要点：录入笔记 ≠ 实际联系 ≠ 下次计划，三者不能混用。',
  },
  {
    title: '待办与超时提醒规则',
    match: /待办|超时|提醒/,
    text:
      '· 本系统的「待办事项」是聚合口径，不是独立的待办表，由这些业务数据汇总而成：\n' +
      '  今日需联系客户 / 已逾期未联系客户（看客户的「下次联系时间」）、待跟进客户、\n' +
      '  即将进入公海客户、待回款提醒、待审批合同、即将到期合同、待审批回款；\n' +
      '· 超时口径：下次联系时间早于今天 = 已逾期未联系；等于今天 = 今日需联系；\n' +
      '· 同一客户若同时命中多种待办，系统会合并为一条展示，不重复输出；\n' +
      '· 我也可以用对话帮你新建 / 改期 / 标记完成这类跟进提醒待办，执行前会先让你确认。',
  },
  {
    title: '售后回访与质保规则',
    match: /售后|回访|质保|工单/,
    text:
      '· 质保档案：登记客户 / 工程项目的质保期（质保年限、起止日期、关联合同）；\n' +
      '· 回访计划：按周期定义回访规则（每季度 / 每半年 / 每年 / 到期前重点回访），可指定回访方式（电话 / 上门 / 微信 / 邮件）；\n' +
      '· 回访任务：由回访计划生成或手工创建，到执行日期后需要完成回访；\n' +
      '· 回访记录：登记实际回访的时间、方式、满意度与回访内容；\n' +
      '· 问题工单：客户报修 / 投诉形成工单，按状态流转（如待处理 / 处理中 / 已解决）。',
  },
  {
    title: '报价流程规则',
    match: /报价|审批流/,
    text:
      '· 报价类型：购销报价（关联客户 + 商机，审批通过后转「购销合同」）、采购报价（关联上游供应商，审批通过后转「采购合同」）；\n' +
      '· 报价单由产品明细组成：数量 × 单价 = 明细金额，汇总为报价总额；\n' +
      '· 报价单走审批流（草稿 → 审批中 → 审批通过 / 审批不通过）；\n' +
      '· 审批通过后可作为转合同的依据。',
  },
];

/**
 * 回答业务规则 / 制度咨询
 *
 * 注意：这里只返回文字规则说明，绝不因为「查不到数据」而回「暂无数据」；
 * 公海回收规则会读取系统真实配置，避免编造天数。
 */
async function replyRuleAnswer(q: string): Promise<AiReply> {
  const topic = RULE_TOPICS.find((t) => t.match.test(q));
  if (!topic) {
    // 没命中具体主题时，退回能力与规则清单（仍是文字说明，不查列表）
    return replyHelp();
  }
  if (topic.title === '客户公海回收规则') {
    return { text: await poolRuleText() };
  }
  return { text: `【${topic.title}】\n${topic.text}` };
}

function replyHelp(): AiReply {
  return {
    text:
      '我是 CRM 智能助手，数据全部来自 CRM 系统真实接口，查不到会如实告知。可以这样问我：\n' +
      '· 查数据：“帮我查找名字叫张三的客户信息” / “查询客户公海” / “列出本月所有商机”\n' +
      '· 查待办：“查看我的待办” / “今日要联系的客户” / “查询待回访任务”\n' +
      '· 查单据：“查询XX客户的报价单” / “列出待处理的售后工单” / “XX客户的质保档案”\n' +
      '· 图片识别：上传报价单 / 合同图片，自动识别字段与产品明细并回填建单\n\n' +
      '业务规则咨询（问“规则是什么”我讲制度，不会去查列表）：\n' +
      '· 客户公海回收规则（会带系统当前配置的天数）\n' +
      '· 线索转客户规则 / 商机阶段流转规则 / 回款与逾期规则\n' +
      '· 跟进记录、实际跟进时间、下次联系时间的区别\n' +
      '· 待办与超时提醒规则 / 售后回访与质保规则 / 报价流程规则\n' +
      '（各项时限类数值均以系统实际配置为准，不编造。）',
  };
}

/* ----------------- 写操作（先解析生成确认卡，用户二次确认后才执行） ----------------- */

/** 按名称解析客户：0 个 / 多个时返回提示文本，绝不替用户猜 */
async function resolveCustomer(
  hintName?: string | null,
  q = '',
): Promise<{ ok: boolean; customer?: any; text?: string }> {
  const name = pickName(hintName, q);
  if (!name) {
    return { ok: false, text: '请告诉我客户名称，例如「给固邦建材新建待办」。' };
  }
  const res = await getCustomerPage({ name, pageNo: 1, pageSize: 50 } as any);
  const list: any[] = onlyAlive(res.list || []);
  const exact = list.filter((c: any) => String(c.name || '') === name);
  const hit = exact.length > 0 ? exact : filterByName(list, name, [(c: any) => c.name]);
  if (hit.length === 0) {
    return {
      ok: false,
      text: `系统未查询到该数据：没有找到名为「${name}」的客户，请确认名称是否正确。`,
    };
  }
  if (hit.length > 1) {
    return {
      ok: false,
      text:
        `找到 ${hit.length} 个名称包含「${name}」的客户，请补充更完整的名称：\n` +
        hit
          .slice(0, 5)
          .map((c: any) => `· ${c.name}`)
          .join('\n'),
    };
  }
  return { ok: true, customer: hit[0] };
}

/** 日期（YYYY-MM-DD）→ 当天 0 点毫秒时间戳（后端 LocalDateTime 以毫秒时间戳收发） */
function dateToTs(date: string): number {
  return new Date(`${date}T00:00:00`).getTime();
}

/**
 * 查找「待办提醒」跟进记录（有下次联系时间）
 *
 * 注意：跟进记录分页接口带 @CrmPermission(bizTypeValue/bizId)，**必须按业务对象查询**，
 * 缺 bizType/bizId 时权限切面取 bizId 会抛 NPE（表现为「系统异常」）。
 * 因此待办改期 / 标记完成必须先定位到具体客户，不能全量拉取。
 */
async function findReminderFollowups(
  keyword: string,
  customerId: number,
): Promise<any[]> {
  const res = await getFollowUpRecordPage({
    pageNo: 1,
    pageSize: 100,
    bizType: 2, // 2 = 客户，待办提醒挂在客户名下
    bizId: customerId,
  } as any);
  let rows: any[] = onlyAlive(res.list || []);
  rows = rows.filter((r: any) => r.nextTime);
  const kw = keyword.trim();
  if (kw) {
    rows = rows.filter((r: any) => String(r.content ?? '').includes(kw));
  }
  return rows;
}

function reminderFields(r: any): { label: string; value: string }[] {
  return [
    { label: '待办内容', value: String(r.content || '-') },
    { label: '截止时间', value: fmtDate(r.nextTime) },
  ];
}

async function planTodoCreate(q: string, cls: QwenClassify): Promise<AiReply> {
  const content = String(cls.content ?? '').trim();
  const date = String(cls.date ?? '').trim();
  if (!content) return { text: '请补充待办内容，例如「电话回访质保情况」。' };
  if (!date) return { text: '请补充截止时间，例如「3天后」或「9月20日」。' };
  const cust = await resolveCustomer(cls.name, q);
  if (!cust.ok) return { text: cust.text };
  return {
    text: '请确认以下待办内容，确认后我会为您创建（以跟进提醒方式记在客户名下）：',
    action: {
      type: 'todoCreate',
      title: '新建待办',
      fields: [
        { label: '客户', value: cust.customer?.name ?? '-' },
        { label: '待办内容', value: content },
        { label: '截止时间', value: date },
      ],
      payload: {
        customerId: cust.customer?.id,
        customerName: cust.customer?.name,
        content,
        date,
      },
    },
  };
}

/** 定位用户所指的那条待办提醒；0 条 / 多条时返回追问文本 */
async function pickTodoTarget(
  q: string,
  cls: QwenClassify,
): Promise<AiReply | { ok: true; row: any }> {
  const kw = String(cls.content ?? '').trim();
  // 待办提醒挂在客户名下（接口按 bizType/bizId 查询），没有客户名时先追问，绝不猜
  if (!pickName(cls.name, q)) {
    return {
      text: '待办改期 / 标记完成需要先定位到具体客户，请告诉我客户名称，例如「把固邦建材的质保回访待办改到9月25日」。',
    };
  }
  const cust = await resolveCustomer(cls.name, q);
  if (!cust.ok) return { text: cust.text };
  const rows = await findReminderFollowups(kw, cust.customer?.id);
  if (rows.length === 0) {
    return { text: '系统未查询到该数据：没有找到符合条件的待办提醒。' };
  }
  if (rows.length > 1) {
    return {
      text:
        `找到 ${rows.length} 条符合条件的待办，请补充更具体的内容关键词：\n` +
        rows
          .slice(0, 5)
          .map(
            (r) => `· ${String(r.content).slice(0, 30)}（截止 ${fmtDate(r.nextTime)}）`,
          )
          .join('\n'),
    };
  }
  return { ok: true, row: rows[0] };
}

function isAiReply(x: any): x is AiReply {
  return x && typeof x === 'object' && 'text' in x;
}

async function planTodoComplete(q: string, cls: QwenClassify): Promise<AiReply> {
  const picked = await pickTodoTarget(q, cls);
  if (isAiReply(picked)) return picked;
  const r = picked.row;
  return {
    text: '请确认要标记完成以下待办（完成后将不再提醒）：',
    action: {
      type: 'todoComplete',
      title: '标记待办完成',
      fields: reminderFields(r),
      payload: {
        id: r.id,
        bizType: r.bizType,
        bizId: r.bizId,
        type: r.type,
        content: r.content,
      },
    },
  };
}

async function planTodoReschedule(q: string, cls: QwenClassify): Promise<AiReply> {
  const date = String(cls.date ?? '').trim();
  if (!date) return { text: '请告诉我要改到哪一天，例如「改到9月20日」。' };
  const picked = await pickTodoTarget(q, cls);
  if (isAiReply(picked)) return picked;
  const r = picked.row;
  return {
    text: `请确认把以下待办改期到 ${date}：`,
    action: {
      type: 'todoReschedule',
      title: '待办改期',
      fields: [...reminderFields(r), { label: '改期至', value: date }],
      payload: {
        id: r.id,
        bizType: r.bizType,
        bizId: r.bizId,
        type: r.type,
        content: r.content,
        date,
      },
    },
  };
}

/** 解析质保档案（按客户名/质保编号/工程名匹配）；0 条 / 多条时返回追问文本 */
async function resolveWarranty(
  hintName?: string | null,
  q = '',
): Promise<AiReply | { ok: true; row: any }> {
  const name = pickName(hintName, q);
  if (!name) {
    return { text: '请告诉我客户名称或质保编号，例如「给固邦建材新建回访计划」。' };
  }
  const res = await getAfterSaleWarrantyPage({ pageNo: 1, pageSize: 100 });
  let rows: any[] = onlyAlive(res.list || []);
  rows = filterByName(rows, name, [
    (r: any) => r.customerName,
    (r: any) => r.no,
    (r: any) => r.projectName,
  ]);
  if (rows.length === 0) {
    return {
      text: `系统未查询到该数据：没有找到与「${name}」相关的质保档案，请确认后再试。`,
    };
  }
  if (rows.length > 1) {
    return {
      text:
        `找到 ${rows.length} 条质保档案，请补充质保编号以便确认：\n` +
        rows
          .slice(0, 5)
          .map((r) => `· ${r.no || '-'}（${r.customerName || '-'}）`)
          .join('\n'),
    };
  }
  return { ok: true, row: rows[0] };
}

const VISIT_TYPE_MAP: Record<string, number> = { 电话: 1, 上门: 2, 微信: 3, 邮件: 4 };
const VISIT_TYPE_LABEL: Record<number, string> = {
  1: '电话',
  2: '上门',
  3: '微信',
  4: '邮件',
};
const CYCLE_MAP: Record<string, number> = {
  每季度: 1,
  季度: 1,
  每半年: 2,
  半年: 2,
  每年: 3,
  年: 3,
  到期前: 4,
};
const CYCLE_LABEL: Record<number, string> = {
  1: '每季度',
  2: '每半年',
  3: '每年',
  4: '质保到期前重点回访',
};

async function planVisitPlanCreate(q: string, cls: QwenClassify): Promise<AiReply> {
  const w = await resolveWarranty(cls.name, q);
  if (isAiReply(w)) return w;
  const cycleKw = String(cls.cycle ?? '').trim();
  const cycleType = cycleKw
    ? (CYCLE_MAP[cycleKw] ?? (/到期/.test(cycleKw) ? 4 : 1))
    : /到期/.test(q)
      ? 4
      : 1;
  const cycleValue = cycleType === 4 ? 30 : 1;
  const visitKw = String(cls.visitType ?? '').trim();
  const visitType = VISIT_TYPE_MAP[visitKw] ?? 1;
  return {
    text: '请确认以下回访计划，确认后我会为您创建：',
    action: {
      type: 'visitPlanCreate',
      title: '新建回访计划',
      fields: [
        { label: '质保编号', value: w.row.no || '-' },
        { label: '客户', value: w.row.customerName || '-' },
        { label: '回访周期', value: CYCLE_LABEL[cycleType] ?? '-' },
        { label: '回访方式', value: VISIT_TYPE_LABEL[visitType] ?? '-' },
      ],
      payload: {
        warrantyId: w.row.id,
        warrantyNo: w.row.no,
        planType: 1,
        cycleType,
        cycleValue,
        visitType,
        startDate: String(cls.date ?? '').trim() || undefined,
      },
    },
  };
}

async function planVisitTaskCreate(q: string, cls: QwenClassify): Promise<AiReply> {
  const date = String(cls.date ?? '').trim();
  if (!date) return { text: '请告诉我回访任务执行日期，例如「9月20日」。' };
  const w = await resolveWarranty(cls.name, q);
  if (isAiReply(w)) return w;
  return {
    text: '请确认以下回访任务，确认后我会为您创建：',
    action: {
      type: 'visitTaskCreate',
      title: '新建回访任务',
      fields: [
        { label: '质保编号', value: w.row.no || '-' },
        { label: '客户', value: w.row.customerName || '-' },
        { label: '执行日期', value: date },
      ],
      payload: {
        warrantyId: w.row.id,
        warrantyNo: w.row.no,
        date,
        remark: String(cls.content ?? '').trim() || undefined,
      },
    },
  };
}

async function planQuotationCreate(q: string, cls: QwenClassify): Promise<AiReply> {
  const name = pickName(cls.name, q);
  if (!name) {
    return { text: '请告诉我客户名称，例如「给固邦建材新建报价单」。' };
  }
  return {
    text:
      '创建报价单需要产品明细（产品名称、数量、单价）。您可以：\n' +
      '1. 点击输入框右侧的 📷 按钮，上传报价图片，我会自动识别产品明细；\n' +
      '2. 或直接在对话里补充产品清单，例如「产品：憎水岩棉板 x100，单价 1200」。',
  };
}

/**
 * 执行用户已二次确认的写操作
 *
 * @returns 执行结果文本（由调用方追加为助手消息）
 */
export async function executeAction(action: PendingAction): Promise<string> {
  try {
    const p = action.payload;
    switch (action.type) {
      case 'todoCreate': {
        await createFollowUpRecord({
          bizType: 2,
          bizId: p.customerId,
          type: 1,
          content: p.content,
          nextTime: dateToTs(p.date),
          picUrls: [],
          fileUrls: [],
        } as any);
        return `待办已创建：${p.customerName} · ${p.content}（截止 ${p.date}），可在「CRM → 待办事项」中查看。`;
      }
      case 'todoComplete': {
        // 删除提醒跟进，再补一条「已完成」跟进（无未来提醒时间，自然退出待办列表）
        await deleteFollowUpRecord(p.id);
        await createFollowUpRecord({
          bizType: p.bizType,
          bizId: p.bizId,
          type: p.type ?? 1,
          content: `【已完成】${p.content}`,
          nextTime: Date.now(),
          picUrls: [],
          fileUrls: [],
        } as any);
        return '待办已标记完成。';
      }
      case 'todoReschedule': {
        await deleteFollowUpRecord(p.id);
        await createFollowUpRecord({
          bizType: p.bizType,
          bizId: p.bizId,
          type: p.type ?? 1,
          content: p.content,
          nextTime: dateToTs(p.date),
          picUrls: [],
          fileUrls: [],
        } as any);
        return `待办已改期至 ${p.date}。`;
      }
      case 'visitPlanCreate': {
        const data: any = {
          warrantyId: p.warrantyId,
          planType: p.planType,
          cycleType: p.cycleType,
          cycleValue: p.cycleValue,
          visitType: p.visitType,
        };
        if (p.startDate) data.startDate = p.startDate;
        await createAfterSaleVisitPlan(data);
        return `回访计划已创建：${p.warrantyNo || ''} · ${CYCLE_LABEL[p.cycleType] ?? ''} · ${VISIT_TYPE_LABEL[p.visitType] ?? ''}。`;
      }
      case 'visitTaskCreate': {
        const data: any = { warrantyId: p.warrantyId, taskDate: dateToTs(p.date) };
        if (p.remark) data.remark = p.remark;
        await createAfterSaleVisitTask(data);
        return `回访任务已创建：${p.warrantyNo || ''} · ${p.date}。`;
      }
      case 'quotationCreate': {
        return await executeQuotationCreate(p);
      }
      case 'contractCreate': {
        return await executeContractCreate(p);
      }
      default:
        return '未知操作类型，已取消。';
    }
  } catch (e: any) {
    const msg = String(e?.message ?? e ?? '');
    if (/没有该操作权限|无权限|403/.test(msg)) {
      return '您当前没有权限执行该操作，请联系管理员开通相应权限。';
    }
    return `执行失败：${msg || '请稍后再试'}`;
  }
}

/** 产品明细 → 产品档案：匹配不上的如实列出，不计入单据 */
async function resolveProducts(
  rawProducts: any[],
): Promise<{ items: any[]; missing: string[]; totalPrice: number }> {
  const items: any[] = [];
  const missing: string[] = [];
  for (const it of rawProducts ?? []) {
    const pname = String(it.productName ?? '').trim();
    if (!pname) continue;
    const res = await getProductPage({
      pageNo: 1,
      pageSize: 20,
      name: pname,
    } as any);
    const list: any[] = onlyAlive(res.list || []);
    const hit =
      list.find((x: any) => String(x.name) === pname) ??
      filterByName(list, pname, [(x: any) => x.name])[0];
    if (!hit) {
      missing.push(pname);
      continue;
    }
    const price = Number(it.price ?? hit.price ?? 0);
    const count = Number(it.count ?? 1);
    items.push({
      productId: hit.id,
      productName: hit.name,
      productNo: hit.no,
      productUnit: hit.unit,
      productPrice: price,
      quotationPrice: price,
      count,
      totalPrice: price * count,
    });
  }
  return {
    items,
    missing,
    totalPrice: items.reduce((s, it) => s + it.totalPrice, 0),
  };
}

/** 依据图片识别结果创建报价单：解析客户与产品档案，匹配不上的如实告知 */
async function executeQuotationCreate(p: any): Promise<string> {
  const cust = await resolveCustomer(p.customerName ?? null, '');
  if (!cust.ok) return `创建报价单失败：${cust.text}`;
  const customer = cust.customer;
  if (!customer?.ownerUserId) {
    return '创建报价单失败：无法确定报价负责人（客户缺少负责人），请到客户详情页确认后再试。';
  }
  const { items, missing, totalPrice } = await resolveProducts(p.products ?? []);
  if (items.length === 0) {
    return `创建报价单失败：${
      missing.length > 0
        ? `以下产品在产品管理中未匹配到：${missing.join('、')}`
        : '未识别到有效产品明细'
    }。`;
  }
  const name = `${customer.name}-报价单-${todayStr()}`;
  await createQuotation({
    name,
    customerId: customer.id,
    ownerUserId: customer.ownerUserId,
    orderDate: Date.now(),
    discountPercent: 100,
    totalPrice,
    products: items,
  } as any);
  return (
    `报价单已创建：${name}（共 ${items.length} 个产品，合计 ${fmtMoney(totalPrice)}）。` +
    (missing.length > 0
      ? `\n注意：以下产品未匹配到产品档案，未计入：${missing.join('、')}`
      : '')
  );
}

/**
 * 依据图片识别结果创建合同
 *
 * 业务规则（两类合同我方身份相反，绝不能混）：
 * - 采购合同(templateType=1)：我方是甲方（采购人），对方是**供应商**（客户类别=2 上游供应商）
 * - 购销合同(templateType=2)：我方是乙方（供货方），对方是**客户**（客户类别=1 下游客户）
 */
async function executeContractCreate(p: any): Promise<string> {
  // 用户在类型选择里选了「报价单（非合同单据）」：转走报价单创建逻辑
  if (p.asQuotation === true) {
    return await executeQuotationCreate({
      customerName: p.counterpartyName ?? null,
      products: p.products ?? [],
    });
  }
  const contractType = Number(p.contractType ?? 0);
  if (contractType !== 1 && contractType !== 2) {
    return '请先选择合同类型（采购合同 / 购销合同）后再执行。';
  }
  const isPurchase = contractType === 1;
  const label = isPurchase ? '采购合同' : '购销合同';
  const partyLabel = isPurchase ? '供应商' : '客户';
  const name = String(p.counterpartyName ?? '').trim();
  if (!name) {
    return `创建${label}失败：未识别到对方主体名称，请补充后再试。`;
  }
  // 采购 → 只认供应商（customerType=2）；购销 → 只认客户（customerType=1）
  const wantType = isPurchase ? 2 : 1;
  const res = await getCustomerPage({ name, pageNo: 1, pageSize: 50 } as any);
  const matched = filterByName((res.list ?? []) as any[], name, [
    (c: any) => c.name,
  ]);
  const byType = matched.filter((c: any) => Number(c.customerType) === wantType);
  const unknownType = matched.filter(
    (c: any) => c.customerType === null || c.customerType === undefined,
  );
  const hit = byType[0] ?? unknownType[0];
  if (!hit) {
    const hint =
      matched.length > 0
        ? `系统中存在名为「${name}」的档案，但客户类别不是「${isPurchase ? '上游供应商' : '下游客户'}」，请先核对该档案的客户类别。`
        : `系统中未找到「${name}」，请先在客户管理中录入（客户类别=${partyLabel}）后再建单。`;
    return `创建${label}失败：${hint}`;
  }
  if (!hit.ownerUserId) {
    return `创建${label}失败：对方档案缺少负责人，无法确定合同负责人，请先补充后再试。`;
  }
  const { items, missing, totalPrice } = await resolveProducts(p.products ?? []);
  const contractName = `${hit.name}-${label}-${todayStr()}`;
  const data: any = {
    name: contractName,
    // 采购=供应商编号；购销=客户编号（本系统供应商同样存放在客户档案中，客户类别=上游供应商）
    customerId: hit.id,
    ownerUserId: hit.ownerUserId,
    orderDate: Date.now(),
    discountPercent: 100,
    totalPrice,
    templateType: contractType,
    remark: '由 AI 助手依据上传图片识别创建',
  };
  // 对方主体写入对应的合同方：采购 → 乙方(供应商)；购销 → 甲方(客户)
  if (isPurchase) {
    data.partyBName = hit.name;
    if (p.contactName) data.partyBAgent = p.contactName;
    if (p.phone) data.partyBTel = p.phone;
    if (p.address) data.partyBAddress = p.address;
  } else {
    data.partyAName = hit.name;
    if (p.contactName) data.partyAAgent = p.contactName;
    if (p.phone) data.partyATel = p.phone;
    if (p.address) data.partyAAddress = p.address;
  }
  await createContract(data);
  return (
    `已创建${label}：${contractName}（${partyLabel}：${hit.name}` +
    (items.length > 0
      ? `，共 ${items.length} 个产品，合计 ${fmtMoney(totalPrice)}`
      : '') +
    '）。' +
    (missing.length > 0
      ? `\n注意：以下产品未匹配到产品档案，未计入：${missing.join('、')}`
      : '')
  );
}

/* ----------------------------- 图片 OCR 识别 ----------------------------- */

/** 图片 OCR：调后端多模态识别接口，返回模型 JSON 原文 */
export async function extractImageOcr(image: string, hint?: string): Promise<string> {
  const res: any = await requestClient.post(
    '/crm/ai/extract-image',
    {
      image,
      hint: hint || undefined,
    },
    { timeout: 120_000 },
  );
  return res?.content ?? '';
}

/**
 * 文档识别：上传 Word / PDF，后端抽取文本后交大模型识别
 *
 * 输出结构与图片识别完全一致，因此前端复用同一套确认卡与建单流程。
 */
export async function extractDocumentOcr(
  fileName: string,
  content: string,
  hint?: string,
): Promise<string> {
  const res: any = await requestClient.post(
    '/crm/ai/extract-file',
    {
      fileName,
      content,
      hint: hint || undefined,
    },
    { timeout: 120_000 },
  );
  return res?.content ?? '';
}

/**
 * 把图片识别结果组装为回复
 *
 * 合同场景按系统规则区分两类（我方身份相反，绝不能把供应商识别成客户）：
 * - 采购合同：我方=甲方（采购人），对方=**供应商** → 新建采购合同并关联供应商
 * - 购销合同：我方=乙方（供货方），对方=**客户**   → 新建购销合同并关联客户
 * 类型无法判定时，主动询问用户，不猜测。
 */
export async function buildOcrReply(raw: string): Promise<AiReply> {
  const parsed = parseJsonSafe<any>(raw);
  if (!parsed) {
    return { text: '图片识别失败，未能提取到有效内容，请更换更清晰的图片后重试。' };
  }
  const docType = String(parsed.docType ?? 'unknown').toLowerCase();
  const isPurchase = docType === 'purchase';
  const isSales = docType === 'sales';
  const typeBasis = String(parsed.typeBasis ?? '').toLowerCase();
  const docTitle = String(parsed.docTitle ?? '').trim();
  // 判定依据：不轻信模型的自我标注 —— 只有**标题原文里确实出现类型关键词**才直接建单，
  // 标题为「合同」「协议」或没有标题时，一律转人工确认，避免把供应商建成客户
  const typeByTitle =
    /采购|购销|销售|供货|供应|订货|购买|销货/.test(docTitle);
  const isContract = (isPurchase || isSales) && typeByTitle;
  const counterparty = String(parsed.counterpartyName ?? '').trim();
  const contactName = String(parsed.contactName ?? '').trim();
  const phone = String(parsed.phone ?? '').trim();
  const address = String(parsed.address ?? '').trim();
  const remark = String(parsed.remark ?? '').trim();
  const products: any[] = Array.isArray(parsed.products) ? parsed.products : [];

  const lines: string[] = ['已识别图片内容：'];
  if (isPurchase || isSales) {
    lines.push(
      `· 合同类型：${isPurchase ? '采购合同（我方为甲方·采购人）' : '购销合同（我方为乙方·供货方）'}${
        typeByTitle ? '' : '（由甲乙方推断，待确认）'
      }`,
    );
    lines.push(
      `· 对方主体：${counterparty || '（未识别到）'}（${isPurchase ? '供应商' : '客户'}）`,
    );
    if (parsed.partyAName) lines.push(`· 甲方：${parsed.partyAName}`);
    if (parsed.partyBName) lines.push(`· 乙方：${parsed.partyBName}`);
  } else if (docType === 'quotation') {
    lines.push('· 单据类型：报价单');
    if (counterparty) lines.push(`· 客户：${counterparty}`);
  } else if (counterparty) {
    lines.push(`· 对方主体：${counterparty}`);
  }
  for (const [label, v] of [
    ['联系人', contactName],
    ['电话', phone],
    ['地址', address],
    ['备注', remark],
  ] as [string, string][]) {
    if (v) lines.push(`· ${label}：${v}`);
  }

  const table: TableResult | undefined =
    products.length > 0
      ? {
          title: '识别到的产品明细',
          source: '图片 OCR 识别 · 请核对后再确认',
          columns: [
            { title: '产品名称', dataIndex: 'name', key: 'name' },
            { title: '数量', dataIndex: 'count', key: 'count' },
            { title: '单位', dataIndex: 'unit', key: 'unit' },
            { title: '单价', dataIndex: 'price', key: 'price' },
          ],
          rows: products.map((it, i) => ({
            id: i,
            name: it.productName || '-',
            count: it.count ?? '-',
            unit: it.unit || '-',
            price: it.price != null ? fmtMoney(it.price) : '-',
          })),
        }
      : undefined;

  if (!counterparty && !contactName && !phone && !address && !remark && products.length === 0) {
    return { text: '图片识别完成，但未提取到有效业务字段，请更换更清晰的图片后重试。' };
  }

  // ① 已判定为合同的：按类型直接生成建单确认卡（采购→供应商，购销→客户）
  if (isContract) {
    return {
      text:
        `${lines.join('\n')}\n\n核对无误后，请点击下方「确认执行」创建` +
        `${isPurchase ? '采购合同（关联供应商）' : '购销合同（关联客户）'}。`,
      table,
      action: {
        type: 'contractCreate',
        title: `新建${isPurchase ? '采购' : '购销'}合同`,
        fields: [
          {
            label: '合同类型',
            value: isPurchase ? '采购合同（我方甲方）' : '购销合同（我方乙方）',
          },
          {
            label: isPurchase ? '供应商' : '客户',
            value: counterparty || '（识别失败，请补充）',
          },
          { label: '联系人', value: contactName || '-' },
          { label: '电话', value: phone || '-' },
          { label: '产品数', value: String(products.length) },
        ],
        payload: {
          contractType: isPurchase ? 1 : 2,
          counterpartyName: counterparty || null,
          contactName: contactName || undefined,
          phone: phone || undefined,
          address: address || undefined,
          products,
        },
      },
    };
  }

  // ② 明确是报价单：新建报价单
  if (docType === 'quotation') {
    const reply: AiReply = { text: lines.join('\n'), table };
    if (products.length > 0) {
      reply.action = {
        type: 'quotationCreate',
        title: '新建报价单（依据图片识别结果）',
        fields: [
          { label: '客户', value: counterparty || '（识别失败，请补充）' },
          { label: '产品数', value: String(products.length) },
        ],
        payload: { customerName: counterparty || null, products },
      };
      reply.text += '\n\n核对无误后，请点击下方「确认执行」创建报价单。';
    }
    return reply;
  }

  // ③ 兜底：类型未能「由标题确定」时主动询问，绝不自作判断把供应商当客户
  const guessed = isPurchase ? '采购合同' : isSales ? '购销合同' : '';
  return {
    text:
      `${lines.join('\n')}\n\n` +
      (guessed
        ? `图片未明确写出合同类型（初步判断可能为「${guessed}」）。\n`
        : '') +
      '识别到合同，请问这是购销合同（我方乙方）还是采购合同（我方甲方）？' +
      '请点击下方对应类型，我会按所选类型关联客户或供应商建单。',
    table,
    action: {
      type: 'contractCreate',
      title: '请选择单据类型',
      fields: [
        {
          label: '识别依据',
          value: docTitle
            ? `标题「${docTitle}」未写明采购/购销，需您确认`
            : typeBasis === 'inferred'
              ? '标题未写明类型，由甲乙方推断'
              : '未能判断合同类型',
        },
        { label: '甲方', value: String(parsed.partyAName ?? '').trim() || '-' },
        { label: '乙方', value: String(parsed.partyBName ?? '').trim() || '-' },
        { label: '识别到的对方主体', value: counterparty || '（未识别到）' },
        { label: '联系人', value: contactName || '-' },
        { label: '电话', value: phone || '-' },
        { label: '产品数', value: String(products.length) },
      ],
      payload: {
        counterpartyName: counterparty || null,
        contactName: contactName || undefined,
        phone: phone || undefined,
        address: address || undefined,
        products,
      },
      choices: [
        { label: '采购合同（我方甲方 → 供应商）', value: { contractType: 1 } },
        { label: '购销合同（我方乙方 → 客户）', value: { contractType: 2 } },
        { label: '报价单（非合同单据）', value: { asQuotation: true } },
      ],
    },
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
    | 'quotation'
    | 'afterSale'
    | 'visitPlan'
    | 'visitTask'
    | 'visitRecord'
    | 'warranty'
    | 'help'
    | 'todoCreate'
    | 'todoComplete'
    | 'todoReschedule'
    | 'visitPlanCreate'
    | 'visitTaskCreate'
    | 'quotationCreate'
    | 'unknown';
  /** 一句话查询多个对象时的其余对象（最多 3 个，取值同 intent），如报价单+售后工单+质保档案 */
  intents?: (null | string)[];
  /** 写操作：截止/任务日期（YYYY-MM-DD 绝对日期） */
  date?: null | string;
  /** 写操作：待办内容或内容关键词 */
  content?: null | string;
  /** 写操作：回访周期原话（每季度/每半年/每年/到期前） */
  cycle?: null | string;
  /** 写操作：回访方式原话（电话/上门/微信/邮件） */
  visitType?: null | string;
  /** 用户提到的具体名称（客户名 / 公司名 / 线索名等） */
  name?: string | null;
  /**
   * 用户明确指定的人员姓名，如「张三的客户」中的张三。
   * 注意：「我 / 我的下属 / 我的团队」这类代词由模型归入 scope，不会落到这里。
   */
  ownerName?: string | null;
  /**
   * 数据归属范围：
   * mine=我负责的（默认）、subordinate=我的下属、team=我的团队（我 + 全部下属）
   */
  scope?: 'mine' | 'subordinate' | 'team' | null;
  /** 客户状态类条件（成交 / 未成交 / 跟进中…） */
  customerStatus?: string | null;
  /** 商机阶段类条件 */
  businessStage?: string | null;
  /** 客户类别：1=下游客户，2=上游供应商 */
  customerType?: null | number;
  /** 时间范围原话（本月 / 上月 / 今年 / 近7天…） */
  timeRange?: null | string;
  month?: boolean;
  /** 是否要求按人员分组展示（如「我所有下属的客户」） */
  groupByOwner?: boolean;
  /** 问题过于笼统、缺少必要筛选条件，需要先向用户确认 */
  needMoreInfo?: boolean;
  /** needMoreInfo 为 true 时给出的追问话术 */
  askMessage?: string | null;
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
  history?: string,
): Promise<QwenClassify | null> {
  try {
    // 由后端转发大模型：密钥保存在服务端，前端接触不到；
    // 也不再依赖 Vite 开发代理，打包部署后同样可用。
    // history 用于多轮追问：让模型在上一轮的查询范围上继续叠加条件
    //
    // X-Silent-Error：意图识别只是「可选增强」。AI 未配置（txgy.crm.ai.api-key 为空，
    // 见 application.yaml 的 DASHSCOPE_API_KEY）或大模型临时不可用时，这里会静默降级为
    // 关键词匹配，由 mockAiReply 返回 degraded 标记在对话流里说明；不应再弹全局红色报错，
    // 否则用户会以为整个助手坏了。
    const res: any = await requestClient.post(
      '/crm/ai/classify-intent',
      {
        query,
        history: history ?? '',
      },
      { headers: { 'X-Silent-Error': 'true' } },
    );
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
  customerType?: null | number,
  /** 数据范围：1=我负责的、3=下属负责的；不传=权限范围内全部 */
  sceneType?: number,
  /** 完整意图解析结果（写操作需要 date/content 等槽位） */
  cls?: QwenClassify,
): Promise<AiReply | null> {
  switch (intent) {
    case 'customer':
      return await replyCustomers(q, hintName, customerType, sceneType);
    case 'pool':
      return await replyPool();
    case 'clue':
      return await replyClues(q, hintName, sceneType);
    case 'business':
      return await replyBusinesses(q, hintName, sceneType);
    case 'todo':
      // 传入大模型解析出的时间范围，严格按提问口径收窄（问「今日」就只给今日需联系）
      return await replyTodos(q, sceneType, cls?.timeRange ?? null);
    case 'contact':
      return await replyContacts(q, hintName, sceneType);
    case 'contract':
      return await replyContracts(q, hintName, sceneType);
    case 'receivable':
      return await replyReceivables(q, hintName, sceneType);
    case 'receivablePlan':
      return await replyReceivablePlans(q, hintName, sceneType);
    case 'product':
      return await replyProducts();
    case 'quotation':
      return await replyQuotations(q, hintName, sceneType);
    case 'afterSale':
      return await replyAfterSaleTickets(q, hintName);
    case 'visitPlan':
      return await replyVisitPlans(q, hintName);
    case 'visitTask':
      return await replyVisitTasks(q, hintName);
    case 'visitRecord':
      return await replyVisitRecords(q, hintName);
    case 'warranty':
      return await replyWarranties(q, hintName);
    case 'help':
      // 业务规则 / 制度咨询：返回文字说明（公海回收会带真实配置天数）
      return await replyRuleAnswer(q);
    case 'todoCreate':
      return cls ? await planTodoCreate(q, cls) : null;
    case 'todoComplete':
      return cls ? await planTodoComplete(q, cls) : null;
    case 'todoReschedule':
      return cls ? await planTodoReschedule(q, cls) : null;
    case 'visitPlanCreate':
      return cls ? await planVisitPlanCreate(q, cls) : null;
    case 'visitTaskCreate':
      return cls ? await planVisitTaskCreate(q, cls) : null;
    case 'quotationCreate':
      return cls ? await planQuotationCreate(q, cls) : null;
    default:
      return null;
  }
}

/* ----------------------------- 意图分发 ----------------------------- */

/**
 * 基于已取回的数据做关键词过滤。
 * 分页接口暂不支持「客户状态 / 商机阶段」这类条件，只能取回后再筛；
 * 采用双向包含匹配，避免「已成交」与「成交」这类措辞差异导致漏筛。
 */
function filterRowsByKeyword(
  rows: Record<string, any>[],
  field: string,
  keyword: string,
): Record<string, any>[] {
  const kw = keyword.trim();
  if (!kw) return rows;
  return rows.filter((r) => {
    const v = String(r[field] ?? '').trim();
    return v !== '' && (v.includes(kw) || kw.includes(v));
  });
}

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
 *
 * @param query   用户本轮问题
 * @param history 最近几轮对话记录，供模型理解「那他们呢」这类追问
 */
export async function mockAiReply(
  query: string,
  history?: string,
): Promise<AiReply> {
  const q = query.trim();

  try {
    const cls = await classifyIntentWithQwen(q, history);
    if (cls) {
      // 【业务规则问答】问「XX规则 / 制度 / 流程 / 是什么 / 怎么算 / 区别」时只讲规则，
      //   绝不改成查列表数据：例如「客户公海回收的规则是什么」必须返回文字规则说明，
      //   而不是「系统未查询到该数据」。放在 unknown 之前，确保能力咨询（你能做什么）也能答上。
      if (cls.intent === 'help' || isRuleQuestion(q)) {
        return await replyRuleAnswer(q);
      }

      // 0. 与 CRM 业务无关的问题（闲聊等）：礼貌说明即可，
      //    不能被模型误设的 needMoreInfo 带着去反问「你要什么代码」
      if (cls.intent === 'unknown') {
        return {
          text:
            cls.summary?.trim() || '我仅提供 CRM 业务相关服务。',
        };
      }

      // 1. 缺少关键筛选条件时先友好反问，既不返回全量数据，也不生硬报错
      if (cls.needMoreInfo) {
        return {
          text:
            (cls.askMessage ?? '').trim() ||
            '这个问题范围有点大，方便再补充一下筛选条件吗？比如具体的名称、负责人员、所属范围或时间。',
        };
      }

      // 2. 数据范围显式传给查询接口（sceneType）：
      //    mine=我负责的(1)、subordinate=下属负责的(3)、team=不限定（默认权限范围，含我+下属）。
      //    「我负责的」必须显式传 1：不传时后端返回权限范围内的全部数据，
      //    对领导来说会混入下属的客户，文案「您名下」就与数据对不上了
      const sceneType =
        cls.scope === 'mine' ? 1 : cls.scope === 'subordinate' ? 3 : undefined;

      const local = await getLocalTable(
        cls.intent,
        q,
        cls.name,
        cls.customerType,
        sceneType,
        cls,
      );
      if (local) {
        // 写操作：返回待确认卡片，不走数据后处理与总结
        if (local.action) {
          return { text: local.text, action: local.action };
        }
        let text = local.text;

        // 3. 人员条件：分页接口暂不支持按负责人查询，基于已取回的数据做过滤并如实说明
        const ownerName = String(cls.ownerName ?? '').trim();
        if (ownerName && local.table?.rows?.length) {
          const rows = local.table.rows.filter((r: any) =>
            String(r.owner ?? '').includes(ownerName),
          );
          local.table = { ...local.table, rows };
          // 有结果只出表格；无匹配必须说明，否则用户会误以为系统里没有数据
          text =
            rows.length > 0
              ? ''
              : `没有找到负责人为「${ownerName}」的记录，请确认人员姓名是否正确。`;
        }

        // 4. 状态类条件（客户状态 / 商机阶段）：分页接口暂不支持，同样取回后再筛。
        //    若一条都匹配不上，则保留原数据并如实说明，
        //    避免让用户误以为「系统里没有数据」
        const statusKw = String(cls.customerStatus ?? '').trim();
        const stageKw = String(cls.businessStage ?? '').trim();
        const kwField =
          cls.intent === 'customer' && statusKw
            ? { field: 'deal', kw: statusKw }
            : cls.intent === 'business' && stageKw
              ? { field: 'stage', kw: stageKw }
              : null;
        if (kwField && local.table?.rows?.length) {
          const before = local.table.rows.length;
          const rows = filterRowsByKeyword(
            local.table.rows,
            kwField.field,
            kwField.kw,
          );
          if (rows.length > 0) {
            local.table = { ...local.table, rows };
            text = '';
          } else {
            text = `没有找到符合「${kwField.kw}」的记录，以下为该范围内的全部 ${before} 条数据：`;
          }
        }

        // 5. 需要按人员分组时，先按负责人把同一个人的数据排在一起（表格内即形成分组观感）
        if (cls.groupByOwner && (local.table?.rows?.length ?? 0) > 1) {
          local.table = {
            ...local.table,
            rows: [...local.table.rows].sort((a: any, b: any) =>
              String(a.owner ?? '').localeCompare(
                String(b.owner ?? ''),
                'zh-CN',
              ),
            ),
          };
        }

        // 6. 输出格式：只给核心业务答案 + 明细表格，不再输出任何辅助话术
        //    （待办总览 / 口径说明 / 引导提示一律不出现）
        //    有数据时不再有任何前置文字；只有查不到时才必须如实说明
        const hasRows = (local.table?.rows?.length ?? 0) > 0;
        if (!hasRows) {
          const hasName = String(cls.name ?? '').trim();
          if (!hasName && !/系统未查询到|未找到|暂无|无数据|0 条/.test(text)) {
            text = '系统未查询到该数据。';
          }
        }

        // 7. 一句话查多个对象（如「报价单 + 售后工单 + 质保档案」）：逐个追加表格
        const extraIntents = (cls.intents ?? [])
          .filter((x): x is string => Boolean(x) && x !== cls.intent)
          .slice(0, 3);
        if (extraIntents.length > 0) {
          const tables: TableResult[] = [];
          const texts: string[] = text ? [text] : [];
          if (local.table) {
            tables.push(local.table);
          }
          for (const it of extraIntents) {
            try {
              const r = await getLocalTable(
                it,
                q,
                cls.name,
                cls.customerType,
                sceneType,
              );
              if (r?.table) {
                const cnt = r.table.rows?.length ?? 0;
                // 有数据只出表；无数据才说明（指到具体是哪张表查不到）
                if (cnt > 0) {
                  if (r.text) {
                    texts.push(r.text);
                  }
                } else {
                  texts.push(`系统未查询到该数据（${r.table.title}）。`);
                }
                tables.push(r.table);
              }
            } catch {
              // 单个对象查询失败不阻断整体回答
            }
          }
          return withLimitHint({
            text: texts.filter(Boolean).join('\n'),
            table: tables[0],
            tables: tables.length > 0 ? tables : undefined,
          });
        }
        return withLimitHint({ text, table: local.table });
      }
      if (cls.summary) {
        return { text: cls.summary };
      }
    }
  } catch (e: any) {
    // 无权限：如实告知，不返回任何数据，也不降级重试
    const msg = String(e?.message ?? e ?? '');
    if (/没有该操作权限|无权限|403/.test(msg)) {
      return {
        text: '您当前没有权限查看该数据，请联系管理员开通相应权限。',
      };
    }
    // 落到下方关键词兜底
  }

  // 兜底：纯前端关键词识别（仍调用真实接口）。
  // 标记 degraded，让界面明确告知用户「AI 意图识别未生效」，而不是静默变笨
  try {
    return { ...withLimitHint(await keywordMockReply(q)), degraded: true };
  } catch (e: any) {
    const msg = String(e?.message ?? e ?? '');
    if (/没有该操作权限|无权限|403/.test(msg)) {
      return {
        text: '您当前没有权限查看该数据，请联系管理员开通相应权限。',
      };
    }
    return {
      text: '抱歉，处理您的请求时出错了，请稍后再试。',
    };
  }
}

async function keywordMockReply(query: string): Promise<AiReply> {
  const q = query.trim();

  // 规则/制度类提问（如「客户公海回收的规则是什么」）优先走规则知识库，绝不能当成查列表
  if (isRuleQuestion(q)) return await replyRuleAnswer(q);

  // 优先级：公海 > 待办 > 质保/回访/售后/报价 > 客户 > 线索 > 商机 > 联系人 > 合同 > 回款计划 > 回款 > 产品
  if (/公海/.test(q)) return await replyPool();
  // 「待办」必须排在「客户」之前：像「今日待办要联系的客户」同时含两个词，应走待办明细
  if (/待办|todo|事项/.test(q)) return await replyTodos(q);
  // 报价 / 售后同样必须排在「客户」之前：「XX客户的报价单」应走报价明细
  if (/质保/.test(q)) return await replyWarranties(q);
  if (/回访记录|历史回访/.test(q)) return await replyVisitRecords(q);
  if (/回访计划/.test(q)) return await replyVisitPlans(q);
  if (/回访任务|待回访/.test(q)) return await replyVisitTasks(q);
  if (/售后|工单/.test(q)) return await replyAfterSaleTickets(q);
  if (/报价/.test(q)) return await replyQuotations(q);
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
