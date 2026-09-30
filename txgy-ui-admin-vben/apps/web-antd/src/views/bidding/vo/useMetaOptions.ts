/* 统一候选选项（24-统一候选接口-options-契约-2026-09-15）——通用封装
 * 契约要点：
 *   · GET /bidding/meta/options 按 type 返回统一形状 {value,label,extra,disabled,status}；
 *   · value 为雪花 ID 十进制字符串，前端**原样回传、禁止 Number()**（精度风险）；
 *   · disabled=true 置灰但保留展示（失效证照、离职人员），**不要过滤**；
 *   · status 为该行业务状态 code，配合 /bidding/meta/dicts 字典展示。
 * 用法：const { options, loading, searchOptions } = useMetaOptions('SUBJECT');
 * 生成 antd select options 请用 toSelectOptions（把 extra 拼进 label 区分同名对象）。
 */
import { ref } from 'vue';

import { type CertificateApi, getMetaOptions } from '#/api/bidding/certificate';

export function useMetaOptions(type: CertificateApi.OptionType) {
  const options = ref<CertificateApi.OptionItem[]>([]);
  const loading = ref(false);

  async function load(keyword = ''): Promise<void> {
    loading.value = true;
    try {
      options.value = await getMetaOptions(type, keyword || undefined, 50);
    } catch {
      // 候选接口静默失败（X-Silent-Error）：降级为空列表，不弹全局错误（表单另有手输兜底时仍可填写）
      options.value = [];
    } finally {
      loading.value = false;
    }
  }

  /** 远程搜索（antd select show-search + filter-option=false） */
  function searchOptions(input: string): void {
    void load(input);
  }

  void load();

  return { loading, options, refresh: () => load(), searchOptions };
}

/** OptionItem[] → antd select options（label/value/disabled；extra 拼进 label 区分同名对象） */
export function toSelectOptions(items: CertificateApi.OptionItem[]): {
  disabled?: boolean;
  label: string;
  value: string;
}[] {
  return items.map((it) => ({
    label: it.extra ? `${it.label}（${it.extra}）` : it.label,
    value: it.value,
    disabled: it.disabled === true,
  }));
}
