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
    return dictItems(key).map((i) => ({ label: i.label, value: i.value }));
  }
  /** 中文名（未知值回显原值，避免空白） */
  function dictLabel(key: string, value?: null | string): string {
    if (!value) return '—';
    return dicts.value[key]?.find((i) => i.value === value)?.label ?? value;
  }
  /** 标签（label + 颜色 + 是否终态） */
  function dictTag(key: string, value?: null | string): DictTag {
    const hit = value ? dicts.value[key]?.find((i) => i.value === value) : undefined;
    if (!hit) return { label: value || '—', color: 'default', terminal: false };
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
