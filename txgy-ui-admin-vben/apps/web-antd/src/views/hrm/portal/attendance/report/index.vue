<script lang="ts" setup>
import type { AttendanceMonthDetail } from '#/api/hrm/portal/attendance/statistics';
import type { HrmPortalAttendanceClockApi } from '#/api/hrm/portal/attendance/clock';

import { computed, onActivated, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart, formatDate } from '@vben/utils';

import { Alert, Button, Card, DatePicker, Spin, Tag, message } from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  createAttendanceClock,
  getAttendanceClockDetail,
} from '#/api/hrm/portal/attendance/clock';
import {
  exportAttendanceMonthDetail,
  getAttendanceMonthDetail,
} from '#/api/hrm/portal/attendance/statistics';
import { ImageUpload } from '#/components/upload';
import { checkHrmPortalAccess } from '#/views/hrm/utils/employee';
import { formatHrmDays } from '#/views/hrm/utils/format';

import AttendanceCalendar from './AttendanceCalendar.vue';
import AttendanceLeaveList from './AttendanceLeaveList.vue';

defineOptions({ name: 'HrmPortalAttendanceReport' });

const router = useRouter();
const route = useRoute();
const { hasAccessByCodes } = useAccess();

const accessible = ref(false);
const loading = ref(false);
const exportLoading = ref(false);
const selectedMonth = ref(
  dayjs(String(route.query.month || '')).isValid()
    ? dayjs(String(route.query.month)).startOf('month')
    : dayjs().startOf('month'),
);
const calendarDate = ref(selectedMonth.value.toDate());
const monthDetail = ref<AttendanceMonthDetail>();
const leaveListRef = ref<InstanceType<typeof AttendanceLeaveList>>();

/** 今日打卡（员工自助打卡：后端要求带现场照片，定位打卡时需经纬度） */
const clockLoading = ref(false);
const clockSubmitting = ref(false);
const clockDetail = ref<HrmPortalAttendanceClockApi.ClockDetail>();
const clockPhotoUrls = ref<string[]>([]);

/** 打完卡按钮文案：按后端返回的"下一次可打卡动作"渲染 */
const clockButtonText = computed(() => {
  const next = clockDetail.value?.nextClock;
  if (!next) {
    return '今日无需打卡';
  }
  const onDuty = next.type === 1;
  switch (next.buttonStatus) {
    case 0: {
      return onDuty ? '未到上班打卡时间' : '未到下班打卡时间';
    }
    case 1: {
      return onDuty ? '上班打卡' : '下班打卡';
    }
    case 2: {
      return '更新打卡';
    }
    case 3: {
      return '迟到打卡';
    }
    case 4: {
      return '早退打卡';
    }
    default: {
      return '打卡';
    }
  }
});

const clockButtonDisabled = computed(
  () =>
    !clockDetail.value?.nextClock ||
    clockDetail.value?.nextClock?.buttonStatus === 0,
);

/** 考勤组只开 WiFi 打卡时，浏览器读不到 SSID/MAC，只能提示去移动端 */
const wifiOnly = computed(
  () =>
    Boolean(clockDetail.value?.openWifiCard) &&
    !clockDetail.value?.openPointCard,
);

/** 打卡时间展示（应打卡/实际打卡） */
function formatClockTime(value?: Date | string) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : formatDate(date, 'HH:mm');
}

function clockTypeName(type?: number) {
  if (type === 1) {
    return '上班';
  }
  return type === 2 ? '下班' : '打卡';
}

/** 读取定位：失败（拒绝授权/超时）返回 undefined，由调用方决定提示 */
function resolveCurrentPosition() {
  return new Promise<GeolocationPosition | undefined>((resolve) => {
    if (!navigator.geolocation) {
      resolve(undefined);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve(position),
      () => resolve(undefined),
      { enableHighAccuracy: true, timeout: 8000 },
    );
  });
}

/** 获得今日打卡详情 */
async function loadClockDetail() {
  clockLoading.value = true;
  try {
    clockDetail.value = await getAttendanceClockDetail();
  } catch {
    clockDetail.value = undefined;
  } finally {
    clockLoading.value = false;
  }
}

/** 员工自助打卡 */
async function handleClock() {
  const next = clockDetail.value?.nextClock;
  if (!next) {
    message.warning('今日无需打卡');
    return;
  }
  if (next.buttonStatus === 0) {
    message.warning('还未到打卡时间');
    return;
  }
  const photoUrl = clockPhotoUrls.value?.[0];
  if (!photoUrl) {
    message.warning('请先上传打卡现场照片');
    return;
  }
  let latitude: number | undefined;
  let longitude: number | undefined;
  if (clockDetail.value?.openPointCard) {
    const position = await resolveCurrentPosition();
    if (!position) {
      message.error('打卡需要定位权限，请在浏览器允许定位后重试');
      return;
    }
    latitude = Number(position.coords.latitude.toFixed(6));
    longitude = Number(position.coords.longitude.toFixed(6));
  }
  clockSubmitting.value = true;
  try {
    await createAttendanceClock({ latitude, longitude, photoUrl });
    message.success('打卡成功');
    clockPhotoUrls.value = [];
    await Promise.all([loadClockDetail(), loadData()]);
  } finally {
    clockSubmitting.value = false;
  }
}

const attendanceCycle = computed(() => {
  const month = selectedMonth.value;
  return `${formatDate(month.toDate(), 'MM')}月01日~${formatDate(month.toDate(), 'MM')}月${formatDate(
    month.endOf('month').toDate(),
    'DD',
  )}日`;
});

const summaryItems = computed(() => [
  {
    label: '应出勤天数',
    value: monthDetail.value?.summary?.attendDays ?? 0,
    unit: '天',
  },
  {
    label: '实际出勤天数',
    value: formatHrmDays(monthDetail.value?.summary?.actualDays),
    unit: '天',
  },
  {
    label: '迟到',
    value: monthDetail.value?.summary?.lateCount ?? 0,
    unit: '次',
  },
  {
    label: '早退',
    value: monthDetail.value?.summary?.earlyCount ?? 0,
    unit: '次',
  },
  {
    label: '缺卡',
    value: monthDetail.value?.summary?.misscardCount ?? 0,
    unit: '次',
  },
]);

/** 禁用未来日期 */
function disabledFutureDate(current: dayjs.Dayjs) {
  return current.startOf('month').isAfter(dayjs().startOf('month'));
}

/** 获得月度考勤详情 */
async function loadData() {
  loading.value = true;
  try {
    monthDetail.value = await getAttendanceMonthDetail(
      selectedMonth.value.year(),
      selectedMonth.value.month() + 1,
    );
  } finally {
    loading.value = false;
  }
}

/** 月份切换操作 */
function handleMonthChange(value: dayjs.Dayjs | string) {
  const month = dayjs(value).startOf('month');
  selectedMonth.value = month;
  calendarDate.value = month.toDate();
  loadData();
}

/** 打开请假表单 */
function openLeaveForm() {
  leaveListRef.value?.openCreate();
}

/** 导出操作 */
async function handleExport() {
  const month = selectedMonth.value;
  try {
    exportLoading.value = true;
    const data = await exportAttendanceMonthDetail(
      month.year(),
      month.month() + 1,
    );
    downloadFileFromBlobPart({
      fileName: `${formatDate(month.toDate(), 'YYYY年MM月')}个人考勤日报.xls`,
      source: data,
    });
  } finally {
    exportLoading.value = false;
  }
}

/** 页面激活时刷新考勤报告 */
/** 员工端「考勤报表」初始化：首次进入用 onMounted 兜底（未被 KeepAlive 缓存时 onActivated 不触发） */
async function initPortalPage() {
  accessible.value = await checkHrmPortalAccess(router);
  if (!accessible.value) {
    return;
  }
  await Promise.all([
    loadData(),
    loadClockDetail(),
    leaveListRef.value?.refresh(),
  ]);
}

onMounted(initPortalPage);
onActivated(() => {
  if (accessible.value) {
    void Promise.all([
      loadData(),
      loadClockDetail(),
      leaveListRef.value?.refresh(),
    ]);
  }
});
</script>

<template>
  <Page v-if="accessible">
    <div class="flex flex-col gap-4">
      <Card title="今日打卡">
        <Spin :spinning="clockLoading">
          <div class="mb-3 flex flex-wrap items-center gap-3 text-sm">
            <span>考勤组：{{ clockDetail?.groupName || '—' }}</span>
            <span class="text-gray-500">
              班次：{{ clockDetail?.shiftTitle || '—' }}
            </span>
            <Tag v-if="clockDetail?.restDay" color="default">休息日</Tag>
            <Tag v-if="clockDetail?.openPointCard" color="blue">需定位</Tag>
            <Tag v-if="clockDetail?.openWifiCard" color="purple">需WiFi</Tag>
          </div>

          <div
            class="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
          >
            <div class="rounded border px-3 py-2">
              <span class="block text-xs text-gray-500">上班应打卡</span>
              <strong>{{ formatClockTime(clockDetail?.onDutyAttendanceTime) }}</strong>
            </div>
            <div class="rounded border px-3 py-2">
              <span class="block text-xs text-gray-500">下班应打卡</span>
              <strong>{{ formatClockTime(clockDetail?.offDutyAttendanceTime) }}</strong>
            </div>
            <div
              v-for="(item, index) in clockDetail?.timeline || []"
              :key="`${item.type}-${index}`"
              class="rounded border px-3 py-2"
            >
              <span class="block text-xs text-gray-500">
                {{ clockTypeName(item.type) }}打卡
              </span>
              <strong :class="item.missCard ? 'text-red-500' : ''">
                {{ item.clockTime ? formatClockTime(item.clockTime) : '未打卡' }}
              </strong>
              <span v-if="item.missCard" class="ml-2 text-xs text-red-500">
                缺卡
              </span>
              <span v-else-if="item.address" class="ml-2 text-xs text-gray-400">
                {{ item.address }}
              </span>
            </div>
          </div>

          <Alert
            v-if="wifiOnly"
            class="mb-3"
            message="本考勤组仅支持 WiFi 打卡：浏览器无法读取 WiFi 名称/MAC，请用移动端在现场打卡点打卡。"
            show-icon
            type="warning"
          />

          <div class="flex flex-wrap items-end gap-4">
            <ImageUpload
              v-model="clockPhotoUrls"
              :max-number="1"
              :max-size="10"
              directory="hrm/attendance/clock"
              help-text="打卡需上传现场照片"
            />
            <Button
              :disabled="clockButtonDisabled"
              :loading="clockSubmitting"
              type="primary"
              @click="handleClock"
            >
              {{ clockButtonText }}
            </Button>
          </div>
        </Spin>
      </Card>

      <Card title="考勤报表">
      <Spin :spinning="loading">
        <div class="mb-4 flex flex-wrap items-center gap-3">
          <DatePicker
            v-model:value="selectedMonth"
            :allow-clear="false"
            :disabled-date="disabledFutureDate"
            format="YYYY年MM月"
            picker="month"
            @change="handleMonthChange"
          />
          <span class="text-sm text-gray-500">
            考勤周期（{{ attendanceCycle }}）
          </span>
          <Button
            v-if="hasAccessByCodes(['hrm:portal:attendance:leave'])"
            type="primary"
            @click="openLeaveForm"
          >
            请假申请
          </Button>
          <Button :loading="exportLoading" type="default" @click="handleExport">
            导出考勤
          </Button>
        </div>

        <!-- 统计块：小屏 2 列、平板 3 列、桌面 5 列，避免手机上挤成一条（阶段五 5.2） -->
        <div
          class="mb-4 grid grid-cols-2 gap-px rounded border sm:grid-cols-3 lg:grid-cols-5"
        >
          <div
            v-for="(item, index) in summaryItems"
            :key="item.label"
            class="min-h-20 px-4 py-3"
            :class="index < summaryItems.length - 1 ? 'border-r' : ''"
          >
            <span class="mb-1 block text-xs text-gray-500">{{
              item.label
            }}</span>
            <strong class="text-2xl font-medium">{{ item.value }}</strong>
            <small class="ml-1 text-gray-500">{{ item.unit }}</small>
          </div>
        </div>

        <AttendanceCalendar
          :calendar-date="calendarDate"
          :daily-details="monthDetail?.dailyDetails"
          :loading="loading"
          :selected-month="selectedMonth.toDate()"
        />
        <AttendanceLeaveList ref="leaveListRef" @changed="loadData" />
      </Spin>
      </Card>
    </div>
  </Page>
</template>
