<!-- 表2/4 · 变更公告（CHANGE_NOTICE）——对已发布公告的变更（延期/调整）说明文书
     线稿清爽：白底细边、直角、方正圆润字体、提示字大而清晰、选中无整体高亮。
     业务（10号令 第5条 变更依法公告 + VO数据规范）：原公告指向（项目/标段）、变更内容与变更后时间。
     标题/正文在第1、3节已填，保存时自动并入 NOTICE_NAME/NOTICE_CONTENT；本表不重复。 -->
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

const rows = computed(() => buildRowMap('CHANGE_NOTICE', props.typeMeta));
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
  { title: '一、原公告指向', hint: '被变更的原招标项目/标段（必填）', keys: ['TENDER_PROJECT_CODE', 'NOTICE_BID_NUM', 'BID_SECTION_CODES'] },
  { title: '二、变更内容与时间', hint: '载明变更事项与变更后的截止时间（10号令 变更依法公告）；投标截止时间（BID_END_TIME）将覆盖原投标截止调度（BIDDING_ENDED），格式须为 yyyyMMddHHmmss', keys: ['CHANGE_CONTENT', 'BID_END_TIME', 'IS_POSTPONE'] },
  { title: '三、发布媒介与公告类型', keys: ['NOTICE_MEDIA', 'URL', 'BULLETIN_TYPE'] },
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
