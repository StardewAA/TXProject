/* 标书审查 · 字典统一入口（管理端 + 个人端共用）
 *
 * 口径（《招投标模块开发规范》§4.4 + 本模块 API 头注释）：
 *   · 状态 / 结论 / 级别 / 步骤 / 来源 / 复核状态 / 类别 / 类型 / 检查方式 一律取字典，禁止页面硬编码中文；
 *   · 取数走 GET /bidding/meta/dicts?types=bid_review_*（一次请求取齐 10 类）；
 *   · 字典拉取失败（如后端未放行权限）时用本地兜底表，页面只降级不报错、不白屏；
 *   · 缓存为模块级单例 + 并发去重：首屏多处调用只发一次请求。
 *
 * 为什么要有它：改造前 TaskPanel / ReportPanel / RulePanel 各自硬编码了 statusTag、severityTag、
 * verdictLabel 与 6 组 options（三份重复），改字典不生效、加一枚举要改三个文件。
 */
import { ref } from 'vue';

import { type BidReviewApi, getBidReviewDicts } from '#/api/bidding/bidReview';

const dicts = ref<BidReviewApi.DictMap>({});
const loaded = ref(false);
const loading = ref(false);
const error = ref('');
let inflight: null | Promise<void> = null;

/** dictItem.tagType → antd Tag color（仅展示层映射，不是业务枚举） */
const TAG_COLOR: Record<string, string> = {
  success: 'green',
  warning: 'orange',
  error: 'red',
  info: 'blue',
  default: 'default',
};

/**
 * 本地兜底（与 bidding_dict_data 里的 bid_review_* 同值）。
 *
 * 只在"字典还没到 / 字典接口失败"时使用，保证界面不会出现英文 code；
 * 字典一旦到达即以字典为准（后端改 label 前端跟着变）。
 */
const LOCAL_LABELS: Record<string, Record<string, string>> = {
  bid_review_status: {
    QUEUED: '排队中',
    RUNNING: '审查中',
    SUCCESS: '已完成',
    FAILED: '已失败',
    CANCELED: '已取消',
  },
  bid_review_verdict: { PASS: '通过', RISK: '存在风险', FAIL: '不通过' },
  bid_review_severity: { HIGH: '高危', MEDIUM: '中危', LOW: '低危' },
  bid_review_step: { PARSE: '解析文件', RULE: '准备审查规则', GENERATE: '逐条审查', REPORT: '生成报告' },
  bid_review_scene: { PORTAL_SELF: '个人端自助', ADMIN_TASK: '管理端建单' },
  bid_review_issue_status: { OPEN: '待复核', CONFIRMED: '已确认', IGNORED: '已忽略', FIXED: '已整改' },
  bid_review_issue_source: { AI: 'AI 判断', RULE: '规则匹配', MANUAL: '人工补录' },
  bid_review_rule_category: {
    FORM: '形式评审',
    QUALIFICATION: '资格条件',
    RESPONSE: '响应性评审',
    BUSINESS: '商务评审',
    TECH: '技术评审',
    PRICE: '价格评审',
  },
  bid_review_rule_type: { DISQUALIFY: '废标项', QUALIFICATION: '资格要求', FORMAT: '格式要求', SCORING: '评分项' },
  bid_review_check_mode: { RULE: '规则匹配', AI: 'AI 语义判断' },
};

/** 本地兜底色（字典里的 color_type 到了就以后端为准） */
const LOCAL_COLORS: Record<string, Record<string, string>> = {
  bid_review_status: { SUCCESS: 'green', FAILED: 'red', RUNNING: 'blue', QUEUED: 'default', CANCELED: 'default' },
  bid_review_verdict: { PASS: 'green', RISK: 'orange', FAIL: 'red' },
  bid_review_severity: { HIGH: 'red', MEDIUM: 'orange', LOW: 'default' },
  bid_review_issue_status: { OPEN: 'blue', CONFIRMED: 'green', IGNORED: 'default', FIXED: 'green' },
};

/** 加载字典（幂等；force=true 强制刷新） */
export function loadBiddingDict(force = false): Promise<void> {
  if (!force && loaded.value) {
    return Promise.resolve();
  }
  if (inflight) {
    return inflight;
  }
  loading.value = true;
  error.value = '';
  inflight = getBidReviewDicts()
    .then((data) => {
      dicts.value = data ?? {};
      loaded.value = Object.keys(dicts.value).length > 0;
      if (!loaded.value) {
        error.value = '字典为空，已降级为本地文案';
      }
    })
    .catch((e: unknown) => {
      // 不抛：字典是展示增强，取不到也应让页面正常打开（最终由页面顶部提示降级原因）
      error.value = e instanceof Error ? e.message : String(e);
    })
    .finally(() => {
      loading.value = false;
      inflight = null;
    });
  return inflight;
}

export interface DictTagResult {
  label: string;
  color: string;
  terminal: boolean;
}

export function useBiddingDict() {
  void loadBiddingDict();

  /** 某字典的原始项（按 sort 升序） */
  function dictItems(key: string): BidReviewApi.DictItem[] {
    return [...(dicts.value[key] ?? [])].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  }

  /** 下拉选项（antd Select :options 直接可用） */
  function dictOptions(key: string): Array<{ label: string; value: string }> {
    const items = dictItems(key);
    if (items.length > 0) {
      return items.map((i) => ({ label: i.label, value: i.value }));
    }
    return Object.entries(LOCAL_LABELS[key] ?? {}).map(([value, label]) => ({ label, value }));
  }

  /** 中文名：后端字典优先 → 本地兜底 → 回显原值 */
  function dictLabel(key: string, value?: null | string): string {
    if (!value) {
      return '—';
    }
    return (
      dicts.value[key]?.find((i) => i.value === value)?.label ?? LOCAL_LABELS[key]?.[value] ?? value
    );
  }

  /** 标签（label + 颜色 + 是否终态） */
  function dictTag(key: string, value?: null | string): DictTagResult {
    const hit = value ? dicts.value[key]?.find((i) => i.value === value) : undefined;
    if (!hit) {
      return {
        label: (value && LOCAL_LABELS[key]?.[value]) || value || '—',
        color: (value && LOCAL_COLORS[key]?.[value]) || 'default',
        terminal: false,
      };
    }
    return {
      label: hit.label,
      color: TAG_COLOR[hit.tagType] ?? LOCAL_COLORS[key]?.[hit.value] ?? 'default',
      terminal: hit.terminal === true,
    };
  }

  return {
    dictItems,
    dictLabel,
    dictOptions,
    dictTag,
    error,
    loaded,
    loading,
    reload: () => loadBiddingDict(true),
  };
}
