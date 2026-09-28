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
/* 与招聘门户其他页面同一套配色（值同 index.vue 的 .recruit-page）。
   这个组件只被在线简历页与资料完善弹窗用到，没有别的模块引用。
   触发器高度对齐 AntD 的 40px 控件基准：原先是 52px，和同一行的输入框差一截。 */
.month-picker-control {
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --resume-placeholder: color-mix(in srgb, var(--job-muted) 58%, #fff);
  position: relative;
  min-width: 0;
}

.month-picker-trigger {
  display: flex;
  width: 100%;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  border: 1px solid var(--job-line);
  border-radius: 9px;
  background: #fff;
  color: var(--job-ink);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  text-align: left;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.month-picker-trigger.placeholder {
  color: var(--resume-placeholder);
}

.month-picker-trigger:hover,
.month-picker-trigger.open,
.month-picker-trigger:focus-visible {
  border-color: var(--job-blue);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--job-blue) 14%, transparent);
  outline: none;
}

.month-picker-trigger > :deep(svg) {
  flex: 0 0 auto;
  color: var(--job-blue);
}

.month-picker-popover {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  left: 0;
  width: min(100%, 330px);
  overflow: hidden;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 16px 36px rgb(27 42 61 / 16%);
}

.month-picker-header {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 34px;
  align-items: center;
  min-height: 46px;
  padding: 0 8px;
  border-bottom: 1px solid var(--job-line);
  color: var(--job-muted);
  text-align: center;
}

.month-picker-header strong {
  color: var(--job-ink);
  font-size: 13px;
  font-weight: 600;
}

.month-picker-header button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--job-muted);
  cursor: pointer;
  transition: background 160ms ease, color 160ms ease;
}

.month-picker-header button:hover:not(:disabled),
.month-picker-header button:focus-visible {
  background: var(--job-blue-soft);
  color: var(--job-blue);
  outline: none;
}

.month-picker-header button:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.month-picker-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 5px;
  padding: 10px;
}

.month-picker-grid button {
  min-height: 36px;
  border: 0;
  border-radius: 7px;
  background: #fff;
  color: var(--job-ink);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  transition: background 160ms ease, color 160ms ease;
}

.month-picker-grid button:hover:not(:disabled),
.month-picker-grid button:focus-visible {
  background: var(--job-blue-soft);
  color: var(--job-blue);
  outline: none;
}

.month-picker-grid button.selected {
  background: var(--job-blue);
  color: #fff;
  font-weight: 600;
}

.month-picker-grid button.disabled,
.month-picker-grid button:disabled {
  color: color-mix(in srgb, var(--job-muted) 38%, #fff);
  cursor: not-allowed;
}

.month-picker-grid button.disabled:hover,
.month-picker-grid button:disabled:hover {
  background: #fff;
}
</style>
