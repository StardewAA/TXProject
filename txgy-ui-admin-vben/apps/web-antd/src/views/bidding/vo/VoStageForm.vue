<script setup lang="ts">
/* VO 环节表单（通用）：按规范 VO 分组渲染字段 + 政务公开参数折叠 + 本环节附件面板（可多个）。
 * 严格照录 VO：字段中文名/英文名/类型/必填/公开范围/备注全部来自 voSpec.gen.ts（自动生成）。
 * 企业填报只收「社会公开」字段；「政务公开」字段=报送平台/监管参数，折叠只读展示（不要求企业手填）。 */
import { computed, ref } from 'vue';

import { Tag } from 'ant-design-vue';

import { splitByPublic } from './voFieldControl';
import { VO_SPEC, type VoField } from './voSpec.gen';
import AttachmentsPanel from './AttachmentsPanel.vue';
import VoFieldGrid from './VoFieldGrid.vue';
import type { AttachmentRecord } from './attachmentStages';

export interface VoGroup {
  title: string;
  hint?: string;
  codes: string[];
}

const props = withDefaults(
  defineProps<{
    vo: string;
    stage: string;
    title: string;
    groups: VoGroup[];
    fields: Record<string, string>;
    attachments: AttachmentRecord[];
    readOnly?: boolean;
    attachmentTitle?: string;
  }>(),
  { readOnly: false, attachmentTitle: '附件（可加多个）' },
);
const emit = defineEmits<{
  (e: 'update:fields', v: Record<string, string>): void;
  (e: 'update:attachments', v: AttachmentRecord[]): void;
}>();

const dataset = computed(() => VO_SPEC[props.vo]);
const allFields = computed<VoField[]>(() => dataset.value?.fields ?? []);
const split = computed(() => splitByPublic(allFields.value));
const govOpen = ref(false);

function fieldsOf(codes: string[]): VoField[] {
  const byCode = new Map(allFields.value.map((f) => [f.code, f]));
  return codes.map((c) => byCode.get(c)).filter((f): f is VoField => !!f);
}
const groupsResolved = computed(() =>
  props.groups
    .map((g) => ({ ...g, list: fieldsOf(g.codes).filter((f) => f.pub !== 'GOVERNMENT') }))
    .filter((g) => g.list.length > 0),
);
/** 未列入任何分组的社会公开字段（防漏：兜底展示） */
const restFields = computed(() => {
  const used = new Set(props.groups.flatMap((g) => g.codes));
  return split.value.social.filter((f) => !used.has(f.code));
});

const requiredSocial = computed(() => split.value.social.filter((f) => f.required));
const missing = computed(() =>
  requiredSocial.value.filter((f) => !(props.fields[f.code] ?? '').trim()).map((f) => f.label + '（' + f.code + '）'),
);
const filled = computed(() => allFields.value.filter((f) => (props.fields[f.code] ?? '').trim()).length);
</script>

<template>
  <div class="vs">
    <header class="vs-h">
      <div class="vs-h-l">
        <span class="vs-t">{{ title }}</span>
        <span class="vs-vo">VO：{{ dataset?.cn }}（{{ vo }}）· 规范字段 {{ allFields.length }} 项</span>
      </div>
      <div class="vs-h-r">
        <Tag color="blue">已填 {{ filled }}</Tag>
        <Tag :color="missing.length ? 'red' : 'green'">
          社会公开必填 {{ requiredSocial.length - missing.length }}/{{ requiredSocial.length }}
        </Tag>
        <Tag v-if="split.government.length">政务公开参数 {{ split.government.length }} 项</Tag>
      </div>
    </header>

    <div v-if="missing.length" class="vs-warn">
      规范必填（社会公开）未填：{{ missing.slice(0, 10).join('、') }}<template v-if="missing.length > 10"> …</template>
    </div>

    <section v-for="g in groupsResolved" :key="g.title" class="vs-sec">
      <div class="vs-sec-h">
        <span class="vs-sec-t">{{ g.title }}</span>
        <span v-if="g.hint" class="vs-sec-hint">{{ g.hint }}</span>
      </div>
      <VoFieldGrid :fields="g.list" :model-value="fields" :read-only="readOnly" @update:model-value="(v) => emit('update:fields', v)" />
    </section>

    <section v-if="restFields.length" class="vs-sec">
      <div class="vs-sec-h">
        <span class="vs-sec-t">其它社会公开字段</span>
        <span class="vs-sec-hint">VO 未分组兜底（防漏字段）</span>
      </div>
      <VoFieldGrid :fields="restFields" :model-value="fields" :read-only="readOnly" @update:model-value="(v) => emit('update:fields', v)" />
    </section>

    <section v-if="split.government.length" class="vs-sec vs-gov">
      <div class="vs-sec-h vs-gov-h" role="button" tabindex="0" @click="govOpen = !govOpen" @keydown.enter="govOpen = !govOpen">
        <span class="vs-sec-t">政务公开报送参数（{{ split.government.length }} 项）</span>
        <span class="vs-sec-hint">规范公开范围=政务公开：由交易平台/发布工具与系统维护，企业表单不收集</span>
        <Tag :color="govOpen ? 'orange' : 'default'">{{ govOpen ? '收起' : '展开查看' }}</Tag>
      </div>
      <div v-if="govOpen" class="vs-gov-b">
        <VoFieldGrid :fields="split.government" :model-value="fields" read-only :columns="2" />
      </div>
    </section>

    <AttachmentsPanel
      :model-value="attachments"
      :stage="stage"
      :title="attachmentTitle"
      :disabled="readOnly"
      @update:model-value="(v) => emit('update:attachments', v)"
    />
  </div>
</template>

<style scoped>
.vs { display: flex; flex-direction: column; gap: 12px; }
.vs-h {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #d9dfe5;
}
.vs-h-l { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.vs-t { font-size: 15.5px; font-weight: 800; color: #232b36; letter-spacing: 0.3px; }
.vs-vo { font-size: 12px; color: #78838f; }
.vs-h-r { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.vs-warn {
  padding: 8px 11px;
  font-size: 12.5px;
  line-height: 1.6;
  color: #a12a20;
  background: #fdf3f2;
  border: 1px solid #f0c9c4;
}
.vs-sec { background: #fff; border: 1px solid #e4e8ec; padding: 11px 13px 13px; }
.vs-sec-h {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  border-bottom: 1px solid #eef1f4;
  padding-bottom: 6px;
}
.vs-sec-t { font-size: 14px; font-weight: 700; color: #303a46; }
.vs-sec-hint { font-size: 12px; color: #8a949e; }
.vs-gov-h { cursor: pointer; user-select: none; }
.vs-gov-b { padding-top: 2px; }
</style>
