<script lang="ts" setup>
import type { ReportDatasetApi } from '#/api/report/dataset';

import { computed } from 'vue';

const props = defineProps<{
  modelValue?: ReportDatasetApi.Param[];
}>();

const emit = defineEmits<{
  'update:modelValue': [ReportDatasetApi.Param[]];
}>();

const list = computed(() => props.modelValue ?? []);

const typeOptions = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '日期时间', value: 'datetime' },
];

function updateRow(index: number, key: string, value: any) {
  const next = [...list.value];
  next[index] = { ...next[index], [key]: value };
  emit('update:modelValue', next);
}

function addRow() {
  emit('update:modelValue', [
    ...list.value,
    { name: '', label: '', type: 'string', required: false, defaultValue: '' },
  ]);
}

function removeRow(index: number) {
  const next = [...list.value];
  next.splice(index, 1);
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="rounded border border-dashed p-3">
    <div class="mb-2 flex items-center justify-between">
      <span class="text-sm font-medium">
        查询参数（可选，SQL 中用 <code>${name}</code> 占位）
      </span>
      <a-button size="small" @click="addRow">新增参数</a-button>
    </div>

    <div v-if="list.length === 0" class="text-xs text-gray-400">
      暂无参数。若 SQL 中使用了 ${xxx} 占位符，必须在此声明，否则查询会被拒绝。
    </div>

    <div
      v-for="(row, index) in list"
      :key="index"
      class="mb-2 flex items-center gap-2"
    >
      <a-input
        :value="row.name"
        placeholder="参数名"
        style="width: 22%"
        @update:value="(v: string) => updateRow(index, 'name', v)"
      />
      <a-input
        :value="row.label"
        placeholder="显示名"
        style="width: 22%"
        @update:value="(v: string) => updateRow(index, 'label', v)"
      />
      <a-select
        :value="row.type ?? 'string'"
        :options="typeOptions"
        style="width: 16%"
        @update:value="(v: string) => updateRow(index, 'type', v)"
      />
      <a-checkbox
        :checked="row.required ?? false"
        @update:checked="(v: boolean) => updateRow(index, 'required', v)"
      >
        必填
      </a-checkbox>
      <a-input
        :value="row.defaultValue"
        placeholder="默认值"
        style="width: 20%"
        @update:value="(v: string) => updateRow(index, 'defaultValue', v)"
      />
      <a-button danger size="small" type="link" @click="removeRow(index)">
        删除
      </a-button>
    </div>
  </div>
</template>
