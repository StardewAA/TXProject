<!-- 表1/4 · 招标公告与资格预审公告（TENDER_NOTICE）——招标人对外发布的招标公告文书
     线稿清爽：白底细边、直角、方正圆润字体、提示字大而清晰、选中无整体高亮。
     业务（10号令第5条 公告载明事项 + VO数据规范）：载明项目概况/资金来源、获取招标文件的办法、
     投标资格要求与是否接受联合体、递交截止与方式、开标时间方式、招标人及媒介。
     标题/正文在第1、3节已填，保存时自动并入 NOTICE_NAME/NOTICE_CONTENT；本表不重复。
     报送参数（平台码/时间戳等政务公开字段）不在本表出现（系统/平台侧）。 -->
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

const rows = computed(() => buildRowMap('TENDER_NOTICE', props.typeMeta));
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
  { title: '一、项目与标段', hint: '招标项目/标段基础辨识（必填）', keys: ['TENDER_PROJECT_CODE', 'NOTICE_BID_NUM', 'BID_SECTION_CODES'] },
  { title: '二、招标文件获取', hint: '10号令第5条 载明获取招标文件的办法', keys: ['NOMINAL_FEE', 'FILE_RECEIVE_ADDRESS', 'DOC_GET_START_TIME', 'DOC_GET_END_TIME', 'TENDER_DOC_GET_METHOD'] },
  { title: '三、投标文件递交与开标', hint: '递交截止时间/方式、开标时间方式（招标投标法第16条）', keys: ['BID_DOC_REFER_END_TIME', 'BID_DOC_REFER_METHOD', 'BID_URL', 'BID_OPEN_TIME', 'BID_OPEN_METHOD', 'IS_POSTPONE', 'QUAL_TYPE'] },
  { title: '四、资格要求与联合体', hint: '资格能力要求与是否接受联合体（10号令第5条）', keys: ['SYNDICATED_FLAG', 'CONSORTIUM_REQUIREMENT', 'COMPANY_QUALI_DEMAND', 'PM_QUALI_DEMAND', 'BID_OTHER_CONDITION'] },
  { title: '五、发布媒介与公告性质', hint: '公告发布媒介/公告性质/公告类型（10号令第5条）', keys: ['NOTICE_MEDIA', 'URL', 'NOTICE_NATURE', 'BULLETIN_TYPE'] },
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

<style>
/* 四张公告表共享线稿样式（前缀 tbl4，注意组件外唯一） */
.tbl4 { display: flex; flex-direction: column; gap: 10px; }
.tbl4-sec { border: 1px solid #e4e7ea; background: #fbfcfc; padding: 10px 12px 2px; }
.tbl4-sec-h { display: flex; align-items: baseline; gap: 8px; margin-bottom: 10px; border-bottom: 1px solid #eceff1; padding-bottom: 6px; }
.tbl4-sec-t { font-size: 14px; font-weight: 700; color: #303a46; }
.tbl4-sec-hint { font-size: 12.5px; color: #8a949e; }
.tbl4-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; row-gap: 11px; padding-bottom: 8px; }
.tbl4-grid > .pc-cell.pc-wide { grid-column: 1 / -1; }
</style>
