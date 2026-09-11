<!-- 单个权威字段格：label + 控件（text/multiline/时间ISO/是-否 Select）；线稿清爽、方正圆润字体、无选中高亮 -->
<script setup lang="ts">
import { Button, Input, Select } from 'ant-design-vue';

export interface FieldRow {
  key: string;
  label: string;
  required: boolean;
  input?: string; // text|multiline|time|boolean|select
  remark?: string;
  dtype?: string;
  options?: Array<{ label: string; value: string; }>;
}

const props = defineProps<{ f: FieldRow; value: string; wide?: boolean }>();
const emit = defineEmits<{ (e: 'update:value', v: string): void }>();
function setV(v: unknown): void {
  emit('update:value', v == null ? '' : String(v));
}
function nowIso(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + 'T' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':00';
}
</script>

<template>
  <div class="pc-cell" :class="{ 'pc-wide': wide || f.input === 'multiline' }">
    <label class="pc-lb" :for="`pc-${ f.key}`"><span v-if="f.required" class="pc-ast">*</span>{{ f.label }}</label>
    <div v-if="f.input === 'multiline'" class="pc-ctl">
      <Input.TextArea :id="`pc-${ f.key}`" :value="value" :rows="3" class="pc-in" :placeholder="f.remark || '请输入'" @update:value="setV" />
    </div>
    <div v-else-if="f.input === 'boolean'" class="pc-ctl">
      <Select :id="`pc-${ f.key}`" :value="value" class="pc-in" style="width: 100%" :placeholder="f.remark || '是 / 否'" @update:value="setV">
        <Select.Option value="1">是（1）</Select.Option>
        <Select.Option value="0">否（0）</Select.Option>
      </Select>
    </div>
    <div v-else-if="f.input === 'select' || (f.options && f.options.length)" class="pc-ctl">
      <Select :id="`pc-${ f.key}`" :value="value" class="pc-in" style="width: 100%" allow-clear :placeholder="f.remark || '请选择'" @update:value="setV">
        <Select.Option v-for="o in f.options" :key="o.value" :value="o.value">{{ o.label }}</Select.Option>
      </Select>
    </div>
    <div v-else-if="f.input === 'time'" class="pc-line">
      <Input :id="`pc-${ f.key}`" :value="value" class="pc-in" placeholder="yyyy-MM-ddTHH:mm:ss" @update:value="setV" />
      <Button size="small" class="pc-now" @click="setV(nowIso())">现在</Button>
    </div>
    <div v-else class="pc-ctl">
      <Input :id="`pc-${ f.key}`" :value="value" class="pc-in" :placeholder="f.remark || '请输入'" @update:value="setV" />
    </div>
    <div v-if="f.remark && f.input !== 'multiline'" class="pc-rm">{{ f.remark }}</div>
  </div>
</template>

<style scoped>
.pc-cell { min-width: 0; display: flex; flex-direction: column; }
.pc-cell.pc-wide { grid-column: 1 / -1; }
.pc-lb { font-size: 13.5px; font-weight: 600; color: #2e3947; margin: 0 0 5px; letter-spacing: .2px; display: flex; align-items: center; gap: 4px; }
.pc-ast { color: #d93025; }
.pc-ctl, .pc-line { width: 100%; }
.pc-line { display: flex; gap: 6px; align-items: center; }
.pc-in { width: 100%; font-size: 13.5px; }
:deep(.ant-input), :deep(.ant-select-selector), :deep(.ant-input-affix-wrapper) { border: 1px solid #c9cfd6; border-radius: 3px; font-size: 13.5px; font-family: 'Ubuntu','PingFang SC','Microsoft YaHei','Noto Sans CJK SC',sans-serif; }
:deep(.ant-input:focus), :deep(.ant-input-focused), :deep(.ant-select-focused .ant-select-selector) { border-color: #2b6cb0; box-shadow: none; }
:deep(.ant-input:hover), :deep(.ant-select:not(.ant-select-disabled):hover .ant-select-selector) { border-color: #2b6cb0; }
.pc-now { border: 1px solid #c9cfd6; border-radius: 3px; color: #2b6cb0; font-size: 12.5px; height: 30px; padding: 0 10px; background: #f7f9fb; white-space: nowrap; }
.pc-rm { margin-top: 3px; font-size: 12px; color: #6b7681; line-height: 1.5; }
</style>
