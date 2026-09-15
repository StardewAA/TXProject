/* 电子证照 · 元数据（字典 + 规则配置）统一入口
 * 契约依据：《电子证照模块-前端接口契约-v1.0.md》§0.5（**严禁前端硬编码枚举与阈值**）、§1.1/§1.2、§10.4 自检项 1。
 * 口径：
 *   · 所有下拉选项 / 状态标签 / 终态判定 → 一律取 dicts（键名见契约：cert_status、verification_status、cert_type、
 *     file_category、verify_channel、lifecycle_type、lifecycle_status、biz_domain、revoke_type、renewal_type、
 *     material_category、person_cert_type、person_cert_status …）；
 *   · 所有阈值/天数/窗口（即将到期天数、验真幂等窗口、预警提前期…）→ 取 configs.ruleKey，且用法不得低于 legalFloor；
 *   · 字典缺失时返回空数组（宁可不显示，也不自造枚举值）。
 * 缓存：模块级单例 + 并发去重（首屏多处调用只发一次请求）。 */
import { computed, ref } from 'vue';

import { certErrorMsg, type CertificateApi, getCertConfigs, getCertDicts } from '#/api/bidding/certificate';

const dicts = ref<CertificateApi.DictMap>({});
const configs = ref<CertificateApi.RuleConfigItem[]>([]);
const loaded = ref(false);
const loading = ref(false);
const error = ref('');
let inflight: null | Promise<void> = null;

/** 加载字典与配置（幂等；force=true 强制刷新） */
export function loadCertMeta(force = false): Promise<void> {
  if (!force && loaded.value) return Promise.resolve();
  if (inflight) return inflight;
  loading.value = true;
  error.value = '';
  let firstErr = '';
  inflight = Promise.all([
    getCertDicts().catch((e: unknown) => {
      firstErr ||= certErrorMsg(e);
      return {} as CertificateApi.DictMap;
    }),
    getCertConfigs().catch((e: unknown) => {
      firstErr ||= certErrorMsg(e);
      return { items: [] } as CertificateApi.RuleConfigData;
    }),
  ])
    .then(([d, c]) => {
      dicts.value = d ?? {};
      configs.value = c?.items ?? [];
      // 失败原因（如 403：后端 /bidding/meta/* 需要 bidding:certificate:query）留档给页面做降级提示
      error.value = firstErr;
      loaded.value = Object.keys(dicts.value).length > 0 || configs.value.length > 0;
    })
    .catch((e: unknown) => {
      error.value = e instanceof Error ? e.message : String(e);
    })
    .finally(() => {
      loading.value = false;
      inflight = null;
    });
  return inflight;
}

/** dictItem.tagType → antd Tag color（仅展示层映射，不是业务枚举） */
const TAG_COLOR: Record<string, string> = {
  success: 'green',
  warning: 'orange',
  error: 'red',
  info: 'blue',
  default: 'default',
};
/** 契约字典中文兜底（《电子证照模块-前端接口契约》§3.1~§3.11）。
 * 后端字典优先；仅在字典缺失或缺项时使用，避免界面直接显示英文 code。
 */
const CONTRACT_LABELS: Record<string, Record<string, string>> = {
  cert_status: { ACTIVE: '正常', EXPIRING_SOON: '即将到期', EXPIRED: '已过期', SUSPENDED: '暂扣', REVOKED: '吊销', CANCELLED: '注销' },
  verification_status: { UNVERIFIED: '未验真', VERIFYING: '验真中', VERIFIED: '已验真', INVALID: '验真无效' },
  file_category: { ORIGINAL: '原件', COPY: '加注件', CHANGE_PAGE: '变更页', RENEWAL_PAGE: '延续页', E_CERT: '电子证照' },
  lifecycle_type: { CHANGE: '变更', RENEW: '续期', REVOKE: '注销吊销', ANNUAL: '年检' },
  lifecycle_status: { PENDING: '待办', REVIEWING: '审核中', APPROVED: '已通过', EFFECTIVE: '已生效', REJECTED: '已驳回' },
  biz_domain: { CERT: '证照', PERSON_CERT: '员工证书', ENTERPRISE_QUAL: '企业资质', SAFETY_PERMIT: '安许证' },
  qual_status: { VALID: '正常', EXPIRING_SOON: '即将到期', EXPIRED: '已过期', PENDING_RENEW: '延续中', LIMITED: '限制', REVOKED: '吊销' },
  qual_apply_status: { DRAFT: '草稿', SUBMITTED: '已提交', ACCEPTED: '已受理', REVIEWING: '审核中', APPROVED: '已批准', PUBLISHED: '公示中', ISSUED: '已办结', REJECTED: '已退回' },
  safety_permit_status: { VALID: '正常', EXPIRING_SOON: '即将到期', EXPIRED: '已过期', SUSPENDED: '暂扣', REVOKED: '吊销' },
  person_cert_status: { VALID: '正常', EXPIRING_SOON: '即将到期', EXPIRED: '已过期', SUSPENDED: '暂扣', REVOKED: '吊销', CANCELLED: '注销' },
  person_cert_type: { ID_CARD: '身份证', EDUCATION_CERT: '学历证', DEGREE_CERT: '学位证', TITLE_CERT: '职称证', REG_PRACTICE_CERT: '注册执业证', SAFETY_ASSESS_CERT: '安全考核证', SKILL_CERT: '技能证', TRAINING_CERT: '培训证', OTHER: '其他' },
  revoke_type: { REVOKE: '吊销', CANCEL: '注销', SUSPEND: '暂扣', RESTORE: '恢复' },
  renewal_type: { RENEW: '延续', REISSUE: '补换证' },
  material_result: { PASS: '齐备', RISK: '部分缺失或临近到期', FAIL: '必备材料缺失' },
  material_status: { READY: '齐备', EXPIRING: '临近到期', MISSING: '缺失', EXPIRED: '已过期' },
  safety_risk_flag: { NORMAL: '正常', YELLOW: '黄色预警', RED: '红色预警', INVALID: '失效', FREQUENT: '频繁预警' },
  person_risk_flag: { NORMAL: '正常', ABNORMAL: '一年异常', FREQUENT: '频繁异常' },
  review_result: { '1': '通过', '0': '不通过' },
};

export interface DictTag {
  label: string;
  color: string;
  terminal: boolean;
}

export function useCertMeta() {
  void loadCertMeta();

  /** 某字典的原始项（按 sort 升序） */
  function dictItems(key: string): CertificateApi.DictItem[] {
    return [...(dicts.value[key] ?? [])].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  }
  /** 下拉选项（antd Select :options 直接可用） */
  function dictOptions(key: string): Array<{ label: string; value: string }> {
    const items = dictItems(key);
    if (items.length > 0) return items.map((i) => ({ label: i.label, value: i.value }));
    return Object.entries(CONTRACT_LABELS[key] ?? {}).map(([value, label]) => ({ label, value }));
  }
  /** 中文名：后端字典优先，契约中文兜底，未知值才回显原值 */
  function dictLabel(key: string, value?: null | string): string {
    if (!value) return '—';
    return dicts.value[key]?.find((i) => i.value === value)?.label ?? CONTRACT_LABELS[key]?.[value] ?? value;
  }
  /** 标签（label + 颜色 + 是否终态） */
  function dictTag(key: string, value?: null | string): DictTag {
    const hit = value ? dicts.value[key]?.find((i) => i.value === value) : undefined;
    if (!hit) return { label: (value && CONTRACT_LABELS[key]?.[value]) || value || '—', color: 'default', terminal: false };
    return { label: hit.label, color: TAG_COLOR[hit.tagType] ?? 'default', terminal: hit.terminal === true };
  }
  /** 是否终态（终态禁止再流转，用于禁用状态操作项） */
  function isTerminal(key: string, value?: null | string): boolean {
    return !!value && dicts.value[key]?.some((i) => i.value === value && i.terminal) === true;
  }
  /** 原始配置项 */
  function configItem(ruleKey: string): CertificateApi.RuleConfigItem | undefined {
    return configs.value.find((i) => i.ruleKey === ruleKey);
  }
  /** 配置值（缺配置回退 fallback；调用方不得把 fallback 写成"业务硬编码阈值"，仅作接口不可用兜底） */
  function configValue<T = unknown>(ruleKey: string, fallback: T): T {
    const v = configItem(ruleKey)?.value;
    return (v === undefined || v === null ? fallback : (v as T));
  }
  /** 配置数值（含小数/整数统一转 number） */
  function configNumber(ruleKey: string, fallback: number): number {
    const v = configValue<unknown>(ruleKey, fallback);
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
  }
  /** 法定下限（配置预警天数时不得低于该值） */
  function legalFloor(ruleKey: string): null | { max?: number; min?: number } {
    return configItem(ruleKey)?.legalFloor ?? null;
  }

  return {
    /** 已加载完成 */
    metaLoaded: computed(() => loaded.value),
    metaLoading: computed(() => loading.value),
    metaError: computed(() => error.value),
    reloadMeta: () => loadCertMeta(true),
    dictItems,
    dictOptions,
    dictLabel,
    dictTag,
    isTerminal,
    configItem,
    configValue,
    configNumber,
    legalFloor,
  };
}
