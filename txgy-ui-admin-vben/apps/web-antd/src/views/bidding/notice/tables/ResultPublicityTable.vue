<!-- 表4/4 · 中标结果公示（WIN_RESULT_PUBLICITY）——定标后对中标人的结果公示
     线稿清爽：白底细边、直角、方正圆润字体、提示字大而清晰、选中无整体高亮。
     业务（10号令第6条 结果公示载明中标人名称 + VO数据规范）：中标人名称/代码类别/统一社会信用代码、
     价款形式与中标金额、工期质量、中标说明；联合体成员（代码/名称）。
     公示标题/正文（NOTICE_NAME/NOTICE_CONTENT）在第1、3节已填，保存自动并入；本表不重复。 -->
<script setup lang="ts">
import type { NoticeApi } from '#/api/bidding/notice';

import { computed } from 'vue';

import PayloadCell from '../PayloadCell.vue';
import { buildRowMap, type RowSpec, type TableGroup } from '../typeFieldRow';

const props = defineProps<{
  typeMeta?: NoticeApi.NoticeTypeMeta | null;
  values: Record<string, string>;
}>();
const emit = defineEmits<{ (e: 'update:values', v: Record<string, string>): void }>();

const rows = computed(() => buildRowMap('WIN_RESULT_PUBLICITY', props.typeMeta));
function get(k: string): string { return props.values[k] ?? ''; }
function set(k: string, v: string): void {
  const next = { ...props.values };
  if (v === '') delete next[k]; else next[k] = v;
  emit('update:values', next);
}
function pick(g: TableGroup): RowSpec[] {
  return g.keys.map((k) => rows.value.get(k)).filter((f): f is RowSpec => !!f);
}
const groups: TableGroup[] = [
  { title: '一、公示指向与期限', hint: '项目/标段（必填）', keys: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODE', 'NOTICE_MEDIA', 'NOTICE_NATURE', 'BULLETIN_TYPE'] },
  { title: '二、中标人', hint: '中标人名称与代码（GB 32100-2015 统一社会信用代码）', keys: ['WIN_BIDDER_NAME', 'BIDDER_CODE_TYPE', 'WIN_BIDDER_CODE'] },
  { title: '三、中标价格', hint: '价款形式/金额/币种（10号令第6条 结果公示）', keys: ['PRICE_FORM_CODE', 'BID_AMOUNT', 'BID_CURRENCY', 'OTHER_BID_PRICE'] },
  { title: '四、履约与说明', hint: '中标工期/质量等级/中标说明（结果公示载明事项）', keys: ['TIME_LIMIT', 'QUALITY', 'WIN_INSTRUCTION', 'CHANGE_CONTENT', 'IS_CHANGE', 'OTHER_CONTENT'] },
  { title: '五、联合体成员', hint: '联合体中标的载明成员（可选）', keys: ['UNION_CODE', 'UNION_NAME'] },
];
</script>

<template>
  <div class="tbl4">
    <section v-for="g in groups" :key="g.title" class="tbl4-sec">
      <div class="tbl4-sec-h">
        <span class="tbl4-sec-t">{{ g.title }}</span>
        <span v-if="g.hint" class="tbl4-sec-hint">{{ g.hint }}</span>
      </div>
      <div class="tbl4-grid">
        <PayloadCell
          v-for="f in pick(g)"
          :key="f.key"
          :f="f"
          :value="get(f.key)"
          :wide="f.input === 'multiline'"
          @update:value="(v) => set(f.key, v)"
        />
      </div>
    </section>
  </div>
</template>
