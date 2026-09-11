<!-- 表3/4 · 中标候选人公示（WIN_CANDIDATE_PUBLICITY）——评标后对推荐中标候选人的公示
     线稿清爽：白底细边、直角、方正圆润字体、提示字大而清晰、选中无整体高亮。
     业务（10号令第6条 候选人公示载明：排序/名称/报价/质量/工期/评标情况、项目负责人姓名+证书、
     资格能力条件、异议渠道与方式；投标法实施条例第54条 公示期≥3日）：
       中标候选人推荐排序 1/2/3 各自：名称/统一社会信用代码/项目负责人及证书/中标价/获奖情况。
     公示标题/正文（PUBLICITY_NAME/PUBLICITY_CONTENT）在第1、3节已填，保存自动并入；本表不重复。 -->
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

const rows = computed(() => buildRowMap('WIN_CANDIDATE_PUBLICITY', props.typeMeta));
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
  { title: '一、公示指向与期限', hint: '项目/标段 + 公示期（实施条例第54条：公示期不得少于3日）', keys: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODES', 'PUBLICITY_TYPE', 'NOTICE_MEDIA', 'PUBLICITY_START_TIME', 'PUBLICITY_END_TIME'] },
  { title: '二、第一中标候选人', hint: '排序第一（必填：名称/信用代码/项目负责人及中标价）', keys: ['RECOMMEND_RANKING_FIRST', 'RECOMMEND_RANKING_FIRST_LEGAL_CODE', 'RECOMMEND_RANKING_FIRST_LEGAL_PM_NAME', 'RECOMMEND_RANKING_FIRST_LEGAL_PM_CERTIFICATES', 'RECOMMEND_RANKING_FIRST_WIN_PRICE', 'RECOMMEND_RANKING_FIRST_AWARD'] },
  { title: '三、第二中标候选人', hint: '排序第二（项目负责人及中标价必填）', keys: ['RECOMMEND_RANKING_SECOND', 'RECOMMEND_RANKING_SECOND_LEGAL_CODE', 'RECOMMEND_RANKING_SECOND_LEGAL_PM_NAME', 'RECOMMEND_RANKING_SECOND_LEGAL_PM_CERTIFICATES', 'RECOMMEND_RANKING_SECOND_WIN_PRICE', 'RECOMMEND_RANKING_SECOND_AWARD'] },
  { title: '四、第三中标候选人', hint: '排序第三（项目负责人及中标价必填）', keys: ['RECOMMEND_RANKING_THIRED', 'RECOMMEND_RANKING_THIRED_EGAL_CODE', 'RECOMMEND_RANKING_THIRED_EGAL_PM_NAME', 'RECOMMEND_RANKING_THIRED_EGAL_PM_CERTIFICATES', 'RECOMMEND_RANKING_THIRED_WIN_PRICE', 'RECOMMEND_RANKING_THIRED_AWARD'] },
  { title: '五、异议与其它', hint: '10号令第6条 载明异议渠道和方式；变更/其它说明', keys: ['OBJECTION_METHOD', 'IS_CHANGE', 'OTHER_CONTENT'] },
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
