<script setup lang="ts">
/* 材料完备性「逐项明细」共享组件（2026-09-21 #3 抽公共件）。
 *
 * 合并前：同一段「两条汇总提示 + 逐项明细表」在两个页面各写一遍，且已经分叉 ——
 *   · views/bidding/certificate/modules/material-check.vue（证书域自查）
 *     —— 状态列走 statusTag()（字典优先 + 兜底），列宽 1000，无「类型 code」列
 *   · views/bidding/qualification/index.vue 页签⑤（资质域完备性明细）
 *     —— 状态列直接 dictTag('material_status')（字典没注册就显示原始 code），列宽 1250，多一列「类型 code」
 *   连 RISK 结论文案都写歪了（见 ./materialMeta.ts 顶部说明）。
 * 现在：渲染只此一处，宿主页面只负责"取数 + 结论卡片"；差异用下面的 props 表达，不再复制代码。
 *
 * 依赖：字典 material_status / material_category（useCertMeta 单例，字典没注册自动回落契约兜底文案）。 */
import type { CertificateApi } from '#/api/bidding/certificate';

import { computed } from 'vue';

import { useCertMeta } from '../certificate/useCertMeta';
import { materialStatusFallback } from './materialMeta';

const props = withDefaults(
  defineProps<{
    /** 完备性出参（EnterpriseMaterialCompletenessRespVO；items 为逐项明细） */
    data: CertificateApi.MaterialCompleteness;
    /** 明细为空时的文案（两处宿主页面口径不同，故做成入参） */
    emptyText?: string;
    /** 表格横向滚动宽度（列宽口径随宿主页面密度不同） */
    scrollX?: number;
    /** 是否显示「缺失/过期」「临近到期」两条汇总提示（默认显示） */
    showAlerts?: boolean;
    /** 是否显示「类型 code」列（materialType 原始码；默认不显示，资质页需要排查时打开） */
    showTypeCode?: boolean;
  }>(),
  {
    emptyText: '无逐项明细：自查未返回逐项结果，可重新执行「自查」',
    scrollX: 1250,
    showAlerts: true,
    showTypeCode: false,
  },
);

const { dictLabel, dictOptions, dictTag } = useCertMeta();

/* 列定义 = 合并前两处的并集：
 *   · 「类型 code」列原只有资质页有（materialType 原始码，排查用），改由 showTypeCode 控制，默认关闭；
 *   · 「取数来源」列原两处标题不同（证书域「取数来源」/ 资质页「匹配记录」）—— 统一为「取数来源」，
 *     因为该列放的是"这条材料是匹配到哪条记录来的"（refName），叫法要指向取数关系而不是匹配动作。 */
const columns = computed(() => [
  { title: '材料名称', dataIndex: 'materialLabel', key: 'materialLabel', width: 240 },
  ...(props.showTypeCode
    ? [{ title: '类型 code', dataIndex: 'materialType', key: 'materialType', width: 170 }]
    : []),
  { title: '大类', dataIndex: 'category', key: 'category', width: 110 },
  { title: '必备', dataIndex: 'mandatory', key: 'mandatory', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '有效期至', dataIndex: 'validTo', key: 'validTo', width: 120 },
  { title: '取数来源', dataIndex: 'refName', key: 'refName', width: 180, ellipsis: true },
  { title: '说明', dataIndex: 'message', key: 'message', ellipsis: true },
]);

/** 状态标签：字典优先；字典未注册时用契约兜底（否则会直接把 READY/MISSING 这种原始 code 显示给使用者） */
function statusTag(status?: string) {
  const s = status ?? '';
  if (dictOptions('material_status').length > 0) return dictTag('material_status', s);
  return materialStatusFallback(s);
}
</script>

<template>
  <div>
    <a-alert
      v-if="props.showAlerts && data.missingItems?.length"
      class="mb-2"
      type="error"
      show-icon
      :message="`缺失/过期 ${data.missingItems.length} 项`"
      :description="data.missingItems.join('、')"
    />
    <a-alert
      v-if="props.showAlerts && data.expiringItems?.length"
      class="mb-3"
      type="warning"
      show-icon
      :message="`临近到期 ${data.expiringItems.length} 项`"
      :description="data.expiringItems.join('、')"
    />

    <a-table
      class="ct-table"
      :columns="columns"
      :data-source="data.items ?? []"
      :pagination="false"
      row-key="materialType"
      size="small"
      :scroll="{ x: props.scrollX }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'category'">
          {{ dictLabel('material_category', record.category) }}
        </template>
        <template v-else-if="column.key === 'mandatory'">
          <a-tag :color="record.mandatory ? 'red' : 'default'">{{ record.mandatory ? '必备' : '非必备' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusTag(record.status).color">{{ statusTag(record.status).label }}</a-tag>
        </template>
        <template v-else-if="column.key === 'validTo'">{{ record.validTo || '长期' }}</template>
      </template>
      <template #emptyText>
        <div class="ct-empty">{{ props.emptyText }}</div>
      </template>
    </a-table>
  </div>
</template>
