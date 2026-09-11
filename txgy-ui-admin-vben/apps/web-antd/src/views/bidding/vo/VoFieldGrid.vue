<script setup lang="ts">
/* VO 字段网格：按规范字段（中文名/英文名/类型/必填/公开范围/备注）渲染控件。
 * 严格照录 VO：字段英文名以徽标展示、必填以 * 标注、政务公开以标签区分、值域备注随字段展示。
 * 线稿清爽风：白底细边、直角、方正圆润字、提示加大、选中无整体高亮；长字段占整行、短字段并排。 */
import { computed } from 'vue';

import { Button, Input, InputNumber, Select } from 'ant-design-vue';

import { YES_NO_OPTIONS, dtypeText, isGovernment, isYesNoField, nowIsoLocal, voControl } from './voFieldControl';
import type { VoField } from './voSpec.gen';

const props = withDefaults(
  defineProps<{
    fields: VoField[];
    modelValue: Record<string, string>;
    readOnly?: boolean;
    columns?: number;
  }>(),
  { readOnly: false, columns: 2 },
);
const emit = defineEmits<{ (e: 'update:modelValue', v: Record<string, string>): void }>();

const gridStyle = computed(() => ({ gridTemplateColumns: `repeat(${props.columns}, minmax(0, 1fr))` }));

function get(code: string): string {
  return props.modelValue[code] ?? '';
}
function set(code: string, v: unknown): void {
  const next = { ...props.modelValue };
  const s = v === null || v === undefined ? '' : String(v);
  if (s === '') delete next[code];
  else next[code] = s;
  emit('update:modelValue', next);
}
function wide(f: VoField): boolean {
  return voControl(f) === 'multiline' || voControl(f) === 'url';
}
</script>

<template>
  <div class="vf-grid" :style="gridStyle">
    <div v-for="f in fields" :key="f.code" class="vf-cell" :class="{ 'vf-wide': wide(f) }">
      <label class="vf-lb" :for="'vf-' + f.code">
        <span v-if="f.required" class="vf-ast" title="规范必填=是">*</span>
        <span class="vf-label">{{ f.label }}</span>
        <code class="vf-code" :title="'规范字段英文名：' + f.code">{{ f.code }}</code>
        <span v-if="isGovernment(f)" class="vf-gov" title="规范公开范围=政务公开（报送平台/监管参数）">政务</span>
      </label>

      <template v-if="readOnly">
        <div class="vf-ro">{{ get(f.code) || '—' }}</div>
      </template>
      <template v-else-if="voControl(f) === 'boolean'">
        <Select
          :id="'vf-' + f.code"
          :value="get(f.code)"
          class="vf-in"
          style="width: 100%"
          allow-clear
          :placeholder="isYesNoField(f) ? '是 / 否（1/0）' : '请选择'"
          @update:value="(v) => set(f.code, v)"
        >
          <Select.Option v-for="o in YES_NO_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</Select.Option>
        </Select>
      </template>
      <template v-else-if="voControl(f) === 'time'">
        <div class="vf-line">
          <Input :id="'vf-' + f.code" :value="get(f.code)" class="vf-in" placeholder="yyyy-MM-ddTHH:mm:ss" @update:value="(v) => set(f.code, v)" />
          <Button size="small" class="vf-now" @click="set(f.code, nowIsoLocal())">现在</Button>
        </div>
      </template>
      <template v-else-if="voControl(f) === 'amount' || voControl(f) === 'number'">
        <InputNumber :id="'vf-' + f.code" :value="get(f.code)" class="vf-in" style="width: 100%" :placeholder="dtypeText(f)" @update:value="(v) => set(f.code, v)" />
      </template>
      <template v-else-if="voControl(f) === 'multiline'">
        <Input.TextArea :id="'vf-' + f.code" :value="get(f.code)" :rows="3" class="vf-in" placeholder="请输入" @update:value="(v) => set(f.code, v)" />
      </template>
      <template v-else>
        <Input :id="'vf-' + f.code" :value="get(f.code)" class="vf-in" :placeholder="dtypeText(f)" @update:value="(v) => set(f.code, v)" />
      </template>

      <div class="vf-meta">
        <span class="vf-dtype">{{ dtypeText(f) }}</span>
        <span v-if="f.note" class="vf-note">{{ f.note }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vf-grid {
  display: grid;
  column-gap: 18px;
  row-gap: 12px;
}
.vf-cell {
  min-width: 0;
}
.vf-wide {
  grid-column: 1 / -1;
}
.vf-lb {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin-bottom: 5px;
  font-size: 13.5px;
  font-weight: 600;
  color: #2e3947;
  line-height: 1.4;
}
.vf-label {
  letter-spacing: 0.2px;
}
.vf-ast {
  color: #d93025;
  font-weight: 700;
}
.vf-code {
  font-size: 10.5px;
  font-weight: 400;
  color: #8a949e;
  background: #f6f8fa;
  border: 1px solid #e3e8ed;
  border-radius: 2px;
  padding: 0 4px;
  letter-spacing: 0.03em;
}
.vf-gov {
  font-size: 10.5px;
  color: #a05a00;
  background: #fdf6e6;
  border: 1px solid #ecd9a8;
  border-radius: 2px;
  padding: 0 4px;
}
.vf-in {
  width: 100%;
  font-size: 13.5px;
}
.vf-line {
  display: flex;
  gap: 6px;
  align-items: center;
}
.vf-now {
  white-space: nowrap;
  border: 1px solid #c9cfd6;
  border-radius: 3px;
  color: #2b6cb0;
  font-size: 12.5px;
  height: 30px;
  padding: 0 10px;
  background: #f7f9fb;
}
.vf-meta {
  margin-top: 3px;
  font-size: 11.5px;
  color: #78838f;
  line-height: 1.55;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.vf-dtype {
  color: #9aa4af;
  white-space: nowrap;
}
.vf-ro {
  font-size: 13.5px;
  color: #2e3947;
  border-bottom: 1px dashed #d5dbe1;
  padding-bottom: 3px;
  min-height: 22px;
  word-break: break-all;
}
:deep(.ant-input),
:deep(.ant-select-selector),
:deep(.ant-input-number),
:deep(.ant-input-affix-wrapper) {
  border: 1px solid #c9cfd6;
  border-radius: 3px;
  font-size: 13.5px;
  font-family: 'Ubuntu', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif;
}
:deep(.ant-input:focus),
:deep(.ant-input-focused),
:deep(.ant-select-focused .ant-select-selector),
:deep(.ant-input-number-focused) {
  border-color: #2b6cb0;
  box-shadow: none;
}
:deep(.ant-input:hover),
:deep(.ant-select:not(.ant-select-disabled):hover .ant-select-selector),
:deep(.ant-input-number:hover) {
  border-color: #2b6cb0;
}
</style>
