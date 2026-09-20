<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

const props = withDefaults(
  defineProps<{
    maxYear?: number;
    minYear?: number;
    modelValue?: string;
    placeholder?: string;
    width?: string;
  }>(),
  {
    maxYear: () => new Date().getFullYear(),
    minYear: () => new Date().getFullYear() - 59,
    placeholder: '请选择年月',
    width: '100%',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string | undefined];
}>();

const pickerOpen = ref(false);
const pickerMode = ref<'month' | 'year'>('year');
const pickerYear = ref(props.maxYear);
const pickerDecadeStart = computed(() => Math.floor(pickerYear.value / 10) * 10);
const pickerYears = computed(() =>
  Array.from({ length: 10 }, (_, index) => pickerDecadeStart.value + index),
);
const pickerMonths = Array.from({ length: 12 }, (_, index) => index + 1);
const canMoveToPreviousDecade = computed(
  () => pickerDecadeStart.value > Math.floor(props.minYear / 10) * 10,
);
const canMoveToNextDecade = computed(
  () => pickerDecadeStart.value < Math.floor(props.maxYear / 10) * 10,
);

function getModelYear() {
  const year = Number(props.modelValue?.slice(0, 4));
  return Number.isInteger(year) && year >= props.minYear && year <= props.maxYear
    ? year
    : undefined;
}

function openPicker() {
  pickerYear.value = getModelYear() || props.maxYear;
  pickerMode.value = 'year';
  pickerOpen.value = true;
}

function closePicker() {
  pickerOpen.value = false;
  pickerMode.value = 'year';
}

function moveDecade(direction: number) {
  if (direction < 0 && !canMoveToPreviousDecade.value) return;
  if (direction > 0 && !canMoveToNextDecade.value) return;
  pickerYear.value += direction * 10;
}

function selectYear(year: number) {
  if (year < props.minYear || year > props.maxYear) return;
  pickerYear.value = year;
  pickerMode.value = 'month';
}

function selectMonth(month: number) {
  emit(
    'update:modelValue',
    `${pickerYear.value}-${String(month).padStart(2, '0')}`,
  );
  closePicker();
}

function handleOutside(event: MouseEvent) {
  const target = event.target;
  if (!(target instanceof Element) || !target.closest('.month-picker-control')) {
    closePicker();
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleOutside);
});
</script>

<template>
  <div
    class="month-picker-control"
    :style="{ width: props.width, maxWidth: '100%' }"
  >
    <button
      class="month-picker-trigger"
      :class="{ placeholder: !modelValue, open: pickerOpen }"
      type="button"
      aria-haspopup="dialog"
      :aria-expanded="pickerOpen"
      @click="pickerOpen ? closePicker() : openPicker()"
    >
      <span>{{ modelValue || placeholder }}</span>
      <IconifyIcon icon="lucide:calendar-days" />
    </button>

    <div
      v-if="pickerOpen"
      class="month-picker-popover"
      role="dialog"
      aria-label="选择年月"
      @mousedown.stop
    >
      <header class="month-picker-header">
        <button
          v-if="pickerMode === 'month'"
          type="button"
          aria-label="返回年份选择"
          @click="pickerMode = 'year'"
        >
          <IconifyIcon icon="lucide:chevron-left" />
        </button>
        <button
          v-else
          type="button"
          aria-label="上一个十年"
          :disabled="!canMoveToPreviousDecade"
          @click="moveDecade(-1)"
        >
          <IconifyIcon icon="lucide:chevron-left" />
        </button>
        <strong>{{ pickerMode === 'year' ? `${pickerDecadeStart} - ${pickerDecadeStart + 9} 年` : `${pickerYear} 年` }}</strong>
        <button
          v-if="pickerMode === 'month'"
          type="button"
          aria-label="下一个月选择"
          disabled
        >
          <IconifyIcon icon="lucide:chevron-right" />
        </button>
        <button
          v-else
          type="button"
          aria-label="下一个十年"
          :disabled="!canMoveToNextDecade"
          @click="moveDecade(1)"
        >
          <IconifyIcon icon="lucide:chevron-right" />
        </button>
      </header>

      <div v-if="pickerMode === 'year'" class="month-picker-grid month-year-grid">
        <button
          v-for="year in pickerYears"
          :key="year"
          :class="{ selected: getModelYear() === year, disabled: year < minYear || year > maxYear }"
          type="button"
          :disabled="year < minYear || year > maxYear"
          @click="selectYear(year)"
        >
          {{ year }}
        </button>
      </div>
      <div v-else class="month-picker-grid month-month-grid">
        <button
          v-for="month in pickerMonths"
          :key="month"
          :class="{ selected: modelValue === `${pickerYear}-${String(month).padStart(2, '0')}` }"
          type="button"
          @click="selectMonth(month)"
        >
          {{ String(month).padStart(2, '0') }} 月
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.month-picker-control { position: relative; min-width: 0; }
.month-picker-trigger {
  display: flex;
  width: 100%;
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  border: 1px solid #cfe4e8;
  border-radius: 8px;
  background: #fff;
  color: #123f49;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color .18s ease, box-shadow .18s ease;
}
.month-picker-trigger.placeholder { color: #86a6ae; }
.month-picker-trigger:hover,
.month-picker-trigger.open,
.month-picker-trigger:focus-visible {
  border-color: #0f8f8c;
  box-shadow: 0 0 0 2px rgb(47 128 237 / 12%);
  outline: none;
}
.month-picker-trigger > :deep(svg) { flex: 0 0 auto; color: #2f80ed; }
.month-picker-popover {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  left: 0;
  width: min(100%, 330px);
  overflow: hidden;
  border: 1px solid #d7e9ed;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 16px 36px rgb(18 63 73 / 16%);
}
.month-picker-header {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 34px;
  align-items: center;
  min-height: 48px;
  padding: 0 8px;
  border-bottom: 1px solid #e7f1f5;
  color: #496b7a;
  text-align: center;
}
.month-picker-header strong { color: #2f5d73; font-size: 13px; font-weight: 600; }
.month-picker-header button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #9bb8c4;
  cursor: pointer;
}
.month-picker-header button:hover:not(:disabled),
.month-picker-header button:focus-visible {
  background: #e8f7f8;
  color: #0f8f8c;
  outline: none;
}
.month-picker-header button:disabled { cursor: not-allowed; opacity: .35; }
.month-picker-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 5px;
  padding: 10px;
}
.month-picker-grid button {
  min-height: 38px;
  border: 0;
  border-radius: 5px;
  background: #fff;
  color: #496b7a;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.month-picker-grid button:hover:not(:disabled),
.month-picker-grid button:focus-visible {
  background: #eaf3ff;
  color: #0f777d;
  outline: none;
}
.month-picker-grid button.selected { background: #0f8f8c; color: #fff; font-weight: 600; }
.month-picker-grid button.disabled,
.month-picker-grid button:disabled { color: #c4d3d9; cursor: not-allowed; }
.month-picker-grid button.disabled:hover,
.month-picker-grid button:disabled:hover { background: #fff; }
</style>
