<script setup lang="ts">
/* 实体创建/编辑弹窗（项目 / 招标项目 / 标段通用）
 * 字段：严格按 VO 规范分组渲染（VoStageForm：规范字段 + 平台注入折叠）；
 * 附件：任何实体都可挂多个附件（VoStageForm 内嵌 AttachmentsPanel），保存时按规范并入 ATTACHMENT_CODE；
 * 写入：payload（规范字段全集）+ 主列 camelCase → 后端 /save（编号+名称必填，其余不阻断）。 */
import { computed, ref, watch } from 'vue';

import { Button, message, Modal } from 'ant-design-vue';

import { attachmentCodeOf, type AttachmentRecord } from './attachmentStages';
import { loadAttachments, saveAttachments } from './attachmentStore';
import { assembleSave, type EntityDef, recordToFields } from './entityForms';
import VoStageForm from './VoStageForm.vue';

const props = withDefaults(
  defineProps<{
    def: EntityDef;
    open: boolean;
    /** 上级选择项（可选，用于归属下拉） */
    parentLabel?: string;
    /** 新建时预置字段（如上级归属编号） */
    preset?: Record<string, string>;
    /** 编辑时传入后端记录（含 fields 与主列） */
    record?: null | Record<string, unknown>;
    /** 保存接口（后端 /save） */
    save: (data: Record<string, unknown>) => Promise<unknown>;
  }>(),
  { record: null, preset: () => ({}), parentLabel: '' },
);
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const fields = ref<Record<string, string>>({});
const attachments = ref<AttachmentRecord[]>([]);
const saving = ref(false);
const idValue = computed<number | string | undefined>(() => {
  const r = props.record;
  const id = r ? (r.id as number | string | undefined) : undefined;
  return id ?? undefined;
});
const title = computed(() => (idValue.value === undefined ? '创建' + props.def.title : '编辑' + props.def.title));

function reset(): void {
  const base = recordToFields(props.def, props.record);
  fields.value = { ...props.preset, ...base };
  const code = fields.value[props.def.codeField] ?? '';
  attachments.value = loadAttachments(props.def.key, code);
}
watch(
  () => [props.open, props.record, props.preset],
  () => {
    if (props.open) reset();
  },
  { immediate: true, deep: true },
);

async function submit(): Promise<void> {
  const code = (fields.value[props.def.codeField] ?? '').trim();
  const name = (fields.value[props.def.nameField] ?? '').trim();
  if (!code || !name) {
    message.warning('请先填写' + props.def.title + '编号与名称（后端必填：' + props.def.codeField + ' / ' + props.def.nameField + '）');
    return;
  }
  saving.value = true;
  try {
    const attCode = attachmentCodeOf(attachments.value);
    const data = assembleSave(props.def, fields.value, attCode, idValue.value);
    await props.save(data);
    saveAttachments(props.def.key, code, attachments.value);
    message.success(props.def.title + '已保存：' + code);
    emit('saved');
    emit('update:open', false);
  } catch (e) {
    message.error('保存失败：' + ((e as Error).message || String(e)));
  } finally {
    saving.value = false;
  }
}
function close(): void {
  emit('update:open', false);
}
/* VoStageForm 为受控组件：显式接收回写，避免模板内直接给 ref 赋值 */
function onFields(v: Record<string, string>): void {
  fields.value = v;
}
function onAttachments(v: AttachmentRecord[]): void {
  attachments.value = v;
}
</script>

<template>
  <Modal :open="open" :title="title" :width="1120" :footer="null" :destroy-on-close="true" @cancel="close">
    <div class="em">
      <div class="em-tip">
        字段与分组照录 VO 规范（{{ def.vo }}）；平台注入参数（平台码/时间戳/统一交易标识码）由平台维护，折叠只读；
        下方附件可挂多个，保存时按规范写入 ATTACHMENT_CODE（多个标识号半角分号隔开）。
      </div>
      <VoStageForm
        :vo="def.vo"
        :stage="def.stage"
        :title="`${def.title }（${ def.vo }）`"
        :groups="def.groups"
        :fields="fields"
        :attachments="attachments"
        :attachment-title="`${def.title }附件（采集项见下，可加多个）`"
        @update:fields="onFields"
        @update:attachments="onAttachments"
      />
      <div class="em-foot">
        <Button @click="close">取消</Button>
        <Button type="primary" :loading="saving" @click="submit">保存</Button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.em { display: flex; flex-direction: column; gap: 12px; }
.em-tip { font-size: 12.5px; line-height: 1.7; color: #5b6672; background: #f7f9fb; border: 1px solid #e4e8ec; padding: 8px 11px; }
.em-foot { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; border-top: 1px solid #eef1f4; }
</style>
