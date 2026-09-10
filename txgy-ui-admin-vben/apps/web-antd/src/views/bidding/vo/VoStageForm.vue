<script setup lang="ts">
/* VO 环节表单（通用）：按规范 VO 分组渲染字段 + 平台注入参数折叠 + 本环节附件面板（可多个）。
 * 严格照录 VO：字段中文名/英文名/类型/必填/公开范围/备注全部来自 voSpec.gen.ts（自动生成）。
 * 分区口径（重要）：VO 的「公开范围」是披露范围，不是"谁填报"——
 *   · 企业填报 = 全部字段 - 平台注入字段（PLATFORM_MAINTAINED_CODES：平台码/时间戳/统一交易标识码/附件关联标识号）；
 *   · 政务公开字段照常由企业填写，仅额外标注"政务"（表示只向政务平台报送、不对外公开）；
 *   · 平台注入字段折叠只读展示，说明由平台/系统维护。 */
import { computed, ref } from 'vue';

import { Tag } from 'ant-design-vue';

import { isPlatformMaintained } from './voFieldControl';
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
/** 企业填报字段（全部字段 - 平台注入） */
const editableFields = computed(() => allFields.value.filter((f) => !isPlatformMaintained(f)));
/** 平台注入字段（折叠只读） */
const platformFields = computed(() => allFields.value.filter((f) => isPlatformMaintained(f)));
/** 披露范围为政务公开的字段数（仅提示披露口径，不影响填报） */
const govCount = computed(() => allFields.value.filter((f) => f.pub === 'GOVERNMENT').length);
const platformOpen = ref(false);

function fieldsOf(codes: string[]): VoField[] {
  const byCode = new Map(allFields.value.map((f) => [f.code, f]));
  return codes.map((c) => byCode.get(c)).filter((f): f is VoField => !!f);
}
const groupsResolved = computed(() =>
  props.groups
    .map((g) => ({ ...g, list: fieldsOf(g.codes).filter((f) => !isPlatformMaintained(f)) }))
    .filter((g) => g.list.length > 0),
);
/** 未列入任何分组的可填字段（防漏：兜底展示） */
const restFields = computed(() => {
  const used = new Set(props.groups.flatMap((g) => g.codes));
  return editableFields.value.filter((f) => !used.has(f.code));
});

const requiredEditable = computed(() => editableFields.value.filter((f) => f.required));
const missing = computed(() =>
  requiredEditable.value.filter((f) => !(props.fields[f.code] ?? '').trim()).map((f) => f.label + '（' + f.code + '）'),
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
          必填 {{ requiredEditable.length - missing.length }}/{{ requiredEditable.length }}
        </Tag>
        <Tag v-if="govCount">政务披露口径 {{ govCount }} 项</Tag>
        <Tag v-if="platformFields.length">平台注入 {{ platformFields.length }} 项</Tag>
      </div>
    </header>

    <div v-if="missing.length" class="vs-warn">
      规范必填未填：{{ missing.slice(0, 10).join('、') }}<template v-if="missing.length > 10"> …</template>
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
        <span class="vs-sec-t">其它规范字段</span>
        <span class="vs-sec-hint">VO 未分组兜底（防漏字段）</span>
      </div>
      <VoFieldGrid :fields="restFields" :model-value="fields" :read-only="readOnly" @update:model-value="(v) => emit('update:fields', v)" />
    </section>

    <section v-if="platformFields.length" class="vs-sec vs-gov">
      <div class="vs-sec-h vs-gov-h" role="button" tabindex="0" @click="platformOpen = !platformOpen" @keydown.enter="platformOpen = !platformOpen">
        <span class="vs-sec-t">平台注入参数（{{ platformFields.length }} 项）</span>
        <span class="vs-sec-hint">平台标识码 / 统一交易标识码 / 时间戳 / 附件关联标识号：由发布工具、交易平台与系统维护，企业表单不收集</span>
        <Tag :color="platformOpen ? 'orange' : 'default'">{{ platformOpen ? '收起' : '展开查看' }}</Tag>
      </div>
      <div v-if="platformOpen" class="vs-gov-b">
        <VoFieldGrid :fields="platformFields" :model-value="fields" read-only :columns="2" />
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
