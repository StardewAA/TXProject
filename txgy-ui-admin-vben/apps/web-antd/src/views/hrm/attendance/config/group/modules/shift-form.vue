<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { HrmAttendanceGroupApi } from '#/api/hrm/attendance/group';

import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Checkbox, Form, TimePicker } from 'ant-design-vue';

import { HRM_WEEK_OPTIONS } from '#/views/hrm/utils/constants';
import { formatHrmAttendanceShiftDuration } from '#/views/hrm/utils/format';

defineOptions({ name: 'HrmAttendanceGroupShiftForm' });

const emit = defineEmits<{
  confirm: [shift: HrmAttendanceGroupApi.Shift, index?: number];
}>();

const editIndex = ref<number>();
const formRef = ref();
const formData = ref<HrmAttendanceGroupApi.Shift>(createDefault());

const clockInTimeRange = computed<[string, string] | undefined>({
  get: () =>
    buildTimeRange(
      formData.value.clockInStartTime,
      formData.value.clockInEndTime,
    ),
  set: (value) => {
    formData.value.clockInStartTime = value?.[0] || '';
    formData.value.clockInEndTime = value?.[1] || '';
  },
});

const clockOutTimeRange = computed<[string, string] | undefined>({
  get: () =>
    buildTimeRange(
      formData.value.clockOutStartTime,
      formData.value.clockOutEndTime,
    ),
  set: (value) => {
    formData.value.clockOutStartTime = value?.[0] || '';
    formData.value.clockOutEndTime = value?.[1] || '';
  },
});

const restTimeRange = computed<[string, string] | undefined>({
  get: () =>
    buildTimeRange(formData.value.restStartTime, formData.value.restEndTime),
  set: (value) => {
    formData.value.restStartTime = value?.[0] || '';
    formData.value.restEndTime = value?.[1] || '';
  },
});

const formRules = reactive<Record<string, Rule[]>>({
  weeks: [{ required: true, message: '工作日不能为空', trigger: 'change' }],
  startTime: [
    { required: true, message: '上班时间不能为空', trigger: 'change' },
    {
      // Q-015 / I-015：班次时间必须"下班晚于上班"；跨天班次（如 22:00→06:00）允许，
      // 但等于同一时刻时直接拦截，避免配出 0 小时班次。
      validator: async (_rule, value: string) => {
        if (!value || !formData.value.endTime) return;
        if (value === formData.value.endTime) {
          throw new Error('上班时间与下班时间不能相同');
        }
      },
      trigger: 'change',
    },
  ],
  endTime: [
    { required: true, message: '下班时间不能为空', trigger: 'change' },
    {
      validator: async (_rule, value: string) => {
        if (!value || !formData.value.startTime) return;
        if (value === formData.value.startTime) {
          throw new Error('上班时间与下班时间不能相同');
        }
      },
      trigger: 'change',
    },
  ],
  clockInTimeRange: [
    {
      validator: async () => {
        if (clockInTimeRange.value?.[0] && clockInTimeRange.value?.[1]) return;
        throw new Error('上班打卡时间段不能为空');
      },
      trigger: 'change',
    },
  ],
  clockOutTimeRange: [
    {
      validator: async () => {
        if (clockOutTimeRange.value?.[0] && clockOutTimeRange.value?.[1])
          return;
        throw new Error('下班打卡时间段不能为空');
      },
      trigger: 'change',
    },
  ],
  restTimeRange: [
    {
      validator: async () => {
        if (restTimeRange.value?.[0] && restTimeRange.value?.[1]) return;
        throw new Error('休息时间不能为空');
      },
      trigger: 'change',
    },
    {
      // 休息时间落在班次区间内才有意义（跨天班次按"跨天"判断）
      validator: async () => {
        const [restStart, restEnd] = restTimeRange.value ?? [];
        const { startTime, endTime } = formData.value;
        if (!restStart || !restEnd || !startTime || !endTime) return;
        const crossDay = endTime <= startTime;
        const toMinutes = (value: string) => {
          const [hour, minute] = value.split(':').map(Number);
          return (hour ?? 0) * 60 + (minute ?? 0);
        };
        const shiftStart = toMinutes(startTime);
        const shiftEnd = toMinutes(endTime) + (crossDay ? 24 * 60 : 0);
        const restStartValue = toMinutes(restStart) + (crossDay ? 24 * 60 : 0);
        const restEndValue = toMinutes(restEnd) + (crossDay ? 24 * 60 : 0);
        if (restStartValue >= restEndValue) {
          throw new Error('休息开始时间必须早于休息结束时间');
        }
        if (restStartValue < shiftStart || restEndValue > shiftEnd) {
          throw new Error('休息时间需要落在班次时间范围内');
        }
      },
      trigger: 'change',
    },
  ],
});

function buildTimeRange(startTime?: string, endTime?: string) {
  return startTime && endTime
    ? ([startTime, endTime] as [string, string])
    : undefined;
}

/**
 * 时间轴：以「上班时间 → 下班时间」为横轴（0%~100%），把休息时段叠加在轴内（Q-015 / I-015）。
 *
 * 用「相对上班时间的偏移」计算，天然支持跨天班次（如 22:00 → 次日 06:00），不需要拆两天。
 */
function toMinutes(time?: string) {
  if (!time) {
    return undefined;
  }
  const [hour, minute] = time.split(':').map(Number);
  return (hour ?? 0) * 60 + (minute ?? 0);
}

const timelineOverflow = computed(() => {
  const start = toMinutes(formData.value.startTime);
  const end = toMinutes(formData.value.endTime);
  return start !== undefined && end !== undefined && end <= start;
});

const timelineStartLabel = computed(() => formData.value.startTime || '--:--');
const timelineEndLabel = computed(
  () =>
    `${formData.value.endTime || '--:--'}${timelineOverflow.value ? '（次日）' : ''}`,
);

const timelineSegments = computed(() => {
  const segments: Array<{ className: string; key: string; left: number; title: string; width: number }> = [];
  const start = toMinutes(formData.value.startTime);
  const end = toMinutes(formData.value.endTime);
  if (start === undefined || end === undefined) {
    return segments;
  }
  const dayMinutes = 24 * 60;
  const shiftLength = (end - start + dayMinutes) % dayMinutes || dayMinutes;
  // 上班时段占满整条轴
  segments.push({
    className: 'bg-blue-500',
    key: 'work',
    left: 0,
    title: `上班 ${formData.value.startTime} - ${formData.value.endTime}`,
    width: 100,
  });
  const restStart = toMinutes(restTimeRange.value?.[0]);
  const restEnd = toMinutes(restTimeRange.value?.[1]);
  if (restStart !== undefined && restEnd !== undefined) {
    // 休息在班次内的相对偏移与长度（跨天同样适用）
    const restOffset = (restStart - start + dayMinutes) % dayMinutes;
    const restLength = (restEnd - restStart + dayMinutes) % dayMinutes;
    if (restLength > 0 && restOffset >= 0 && restOffset < shiftLength) {
      const left = Math.max(0, (restOffset / shiftLength) * 100);
      const width = Math.min(100 - left, (restLength / shiftLength) * 100);
      if (width > 0) {
        segments.push({
          className: 'bg-orange-400',
          key: 'rest',
          left,
          title: `休息 ${restTimeRange.value?.[0]} - ${restTimeRange.value?.[1]}`,
          width,
        });
      }
    }
  }
  return segments;
});

function createDefault(): HrmAttendanceGroupApi.Shift {
  return {
    weeks: [1, 2, 3, 4, 5],
    startTime: '09:00',
    endTime: '18:00',
    clockInStartTime: '05:00',
    clockInEndTime: '17:59',
    clockOutStartTime: '09:01',
    clockOutEndTime: '04:59',
    restStartTime: '12:00',
    restEndTime: '13:00',
    excludeRestTime: false,
  };
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    emit(
      'confirm',
      {
        ...formData.value,
        weeks: [...formData.value.weeks].toSorted((a, b) => a - b),
      },
      editIndex.value,
    );
    await modalApi.close();
  },
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const payload = modalApi.getData<{
      index?: number;
      shift?: HrmAttendanceGroupApi.Shift;
    }>();
    editIndex.value = payload?.index;
    formData.value = payload?.shift
      ? { ...payload.shift, weeks: [...payload.shift.weeks] }
      : createDefault();
  },
});

defineExpose({
  open(shift?: HrmAttendanceGroupApi.Shift, index?: number) {
    modalApi.setData({ shift, index }).open();
  },
});
</script>

<template>
  <Modal
    :title="editIndex === undefined ? '新增班次' : '编辑班次'"
    class="w-[760px]"
  >
    <Form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      class="mx-4"
      layout="vertical"
    >
      <Form.Item label="工作日" name="weeks">
        <Checkbox.Group
          v-model:value="formData.weeks"
          :options="
            HRM_WEEK_OPTIONS.map((item) => ({
              label: item.label,
              value: item.value,
            }))
          "
        />
      </Form.Item>
      <Alert
        class="mb-4"
        message="打卡窗口需覆盖对应的上下班时间；结束时间早于开始时间时按次日计算，例如 18:00 至次日 04:59。"
        show-icon
        type="info"
      />
      <div class="grid grid-cols-2 gap-4">
        <Form.Item label="上班时间" name="startTime">
          <TimePicker
            v-model:value="formData.startTime"
            class="w-full"
            format="HH:mm"
            value-format="HH:mm"
          />
        </Form.Item>
        <Form.Item label="下班时间" name="endTime">
          <TimePicker
            v-model:value="formData.endTime"
            class="w-full"
            format="HH:mm"
            value-format="HH:mm"
          />
        </Form.Item>
      </div>
      <Form.Item label="上班打卡时间段" name="clockInTimeRange">
        <TimePicker.RangePicker
          v-model:value="clockInTimeRange"
          class="w-full"
          format="HH:mm"
          value-format="HH:mm"
        />
      </Form.Item>
      <Form.Item label="下班打卡时间段" name="clockOutTimeRange">
        <TimePicker.RangePicker
          v-model:value="clockOutTimeRange"
          class="w-full"
          format="HH:mm"
          value-format="HH:mm"
        />
      </Form.Item>
      <Form.Item label="休息时间" name="restTimeRange">
        <div class="flex w-full items-center gap-4">
          <TimePicker.RangePicker
            v-model:value="restTimeRange"
            class="flex-1"
            format="HH:mm"
            value-format="HH:mm"
          />
          <Checkbox v-model:checked="formData.excludeRestTime">
            不计入工作时长
          </Checkbox>
        </div>
      </Form.Item>
      <!-- Q-015 / I-015：班次时间轴可视化——把上班/休息/下班画成一条时间轴，配置一眼看懂 -->
      <Form.Item label="时间轴预览">
        <div class="w-full">
          <!-- 时间轴底色用主题令牌，暗色下不会出现一条浅色带（问题 8：全系统主题一致性） -->
          <div class="bg-muted relative h-6 w-full overflow-hidden rounded">
            <div
              v-for="segment in timelineSegments"
              :key="segment.key"
              class="absolute top-0 h-6"
              :class="segment.className"
              :style="{ left: `${segment.left}%`, width: `${segment.width}%` }"
              :title="segment.title"
            ></div>
          </div>
          <div class="text-muted-foreground mt-1 flex justify-between text-xs">
            <span>{{ timelineStartLabel }}</span>
            <span>{{ timelineEndLabel }}</span>
          </div>
          <div class="mt-1 flex flex-wrap items-center gap-3 text-xs">
            <span class="flex items-center gap-1">
              <i class="inline-block h-2 w-3 rounded bg-blue-500"></i>上班时段
            </span>
            <span class="flex items-center gap-1">
              <i class="inline-block h-2 w-3 rounded bg-orange-400"></i>休息
              {{
                formData.excludeRestTime ? '（不计入工作时长）' : '（计入工作时长）'
              }}
            </span>
            <span class="flex items-center gap-1">
              <i class="bg-border inline-block h-2 w-3 rounded"></i>非工作时段
            </span>
            <span v-if="timelineOverflow" class="text-orange-500">
              当前班次跨天，横轴按 24 小时铺满
            </span>
          </div>
        </div>
      </Form.Item>
      <Form.Item label="合计工作时长">
        <span>{{ formatHrmAttendanceShiftDuration(formData) }}</span>
      </Form.Item>
    </Form>
  </Modal>
</template>
