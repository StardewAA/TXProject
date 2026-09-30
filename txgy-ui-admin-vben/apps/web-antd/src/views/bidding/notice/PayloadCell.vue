<!-- 单个权威字段格：label + 控件（text/multiline/时间14位/是-否 Select）；线稿清爽、方正圆润字体、无选中高亮 -->
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
const p2 = (n: number): string => String(n).padStart(2, '0');

/** 当前时间 → 规范 14 位 yyyyMMddHHmmss（《VO数据对象规范》日期时间型；后端 FlowTimerServiceImpl.parseSpecTime 同一格式） */
function now14(): string {
  const d = new Date();
  return d.getFullYear() + p2(d.getMonth() + 1) + p2(d.getDate()) + p2(d.getHours()) + p2(d.getMinutes()) + p2(d.getSeconds());
}

/** 兼容输入归一化：ISO（yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss）自动转 14 位，
 *  避免用户手填 ISO 导致后端时间驱动调度解析失败（parseSpecTime 非法值静默不注册，无报错）。 */
function to14(v: unknown): string {
  if (v === null || v === undefined) return '';
  const s = String(v).trim();
  if (!s) return '';
  if (/^[0-9]{14}$/.test(s)) return s;
  // ISO（yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss / 分隔符兼容 - / / 空格）自动转 14 位
  const iso = /^([0-9]{4})[-/]([0-9]{2})[-/]([0-9]{2})[T ]([0-9]{2}):([0-9]{2})(?::([0-9]{2}))?$/.exec(s);
  if (iso) {
    const y = iso[1] ?? '', mo = iso[2] ?? '', d = iso[3] ?? '', h = iso[4] ?? '', mi = iso[5] ?? '', se = iso[6] ?? '00';
    return y + mo + d + h + mi + se;
  }
  return s;
}
function setV(v: unknown): void {
  emit('update:value', v == null ? '' : to14(v));
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
      <Input :id="`pc-${ f.key}`" :value="value" class="pc-in" placeholder="yyyyMMddHHmmss（如 20260917170912）" @update:value="setV" />
      <Button size="small" class="pc-now" @click="setV(now14())">现在</Button>
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
.pc-lb { margin: 0 0 5px; display: flex; gap: 4px; align-items: center; font-size: 13.5px; font-weight: 500; color: hsl(var(--foreground)); }
.pc-ast { color: hsl(var(--destructive)); }
.pc-ctl, .pc-line { width: 100%; }
.pc-line { display: flex; gap: 6px; align-items: center; }
.pc-in { width: 100%; font-size: 13.5px; }
/* 输入控件边框/聚焦态交给 antd 主题（对齐平台其它模块），仅保留字号与字体 */
:deep(.ant-input), :deep(.ant-select-selector), :deep(.ant-input-affix-wrapper) { font-size: 13.5px; font-family: 'Ubuntu','PingFang SC','Microsoft YaHei','Noto Sans CJK SC',sans-serif; }
.pc-now { font-size: 12.5px; height: 30px; padding: 0 10px; white-space: nowrap; }
.pc-rm { margin-top: 3px; font-size: 12px; line-height: 1.5; color: hsl(var(--muted-foreground)); }
</style>
