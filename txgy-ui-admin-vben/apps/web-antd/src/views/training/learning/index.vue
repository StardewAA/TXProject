<script lang="ts" setup>
import type { TrainingLearningApi } from './api';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  assignCourse,
  createLiveRoom,
  deleteLiveRoom,
  getAssignCandidates,
  getCourseTrack,
  getLearningConfig,
  getLearningStats,
  getLearningTaskPage,
  getLiveRoomPage,
  updateLearningConfig,
  updateLiveRoom,
} from './api';

defineOptions({ name: 'TrainingLearning' });

// ==================== 通用字典 ====================
const TASK_STATUS_OPTIONS = [
  { label: '待学习', value: 0 },
  { label: '学习中', value: 1 },
  { label: '已完成', value: 2 },
];
const SOURCE_TYPE_OPTIONS = [
  { label: '管理员指派', value: 'assign' },
  { label: '自主选学', value: 'self' },
];
const ROOM_STATUS_OPTIONS = [
  { label: '未开始', value: 0 },
  { label: '直播中', value: 1 },
  { label: '已结束', value: 2 },
];
const CONFIG_ITEMS = [
  { key: 'danmaku_enabled', label: '直播弹幕开关', hint: 'true / false' },
  { key: 'verify_interval_min', label: '人机验证间隔（分钟）', hint: '如 1' },
  { key: 'heartbeat_interval_seconds', label: '心跳间隔（秒）', hint: '如 5' },
  { key: 'max_delta_seconds', label: '单次心跳合法 delta 上限（秒）', hint: '如 15' },
  { key: 'session_timeout_seconds', label: '心跳超时自动结束（秒）', hint: '如 30' },
  { key: 'doc_min_stay_seconds', label: '文档单页最短停留（秒）', hint: '如 5' },
  { key: 'video_complete_ratio', label: '视频完成判定比例', hint: '如 0.9' },
  { key: 'doc_complete_ratio', label: '文档完成判定比例', hint: '如 0.8' },
  { key: 'min_valid_seconds_checkin', label: '打卡最低有效秒数', hint: '如 15' },
];

const taskStatusText = (status?: number) =>
  TASK_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';
const roomStatusText = (status?: number) =>
  ROOM_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';
const roomStatusColor = (status?: number) =>
  status === 1 ? 'green' : status === 2 ? 'default' : 'blue';

// ==================== 学习任务 ====================
const taskLoading = ref(false);
const taskList = ref<TrainingLearningApi.LearnerTask[]>([]);
const taskTotal = ref(0);
const taskQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  courseTitle: '',
  status: undefined as number | undefined,
  sourceType: undefined as string | undefined,
});

async function loadTasks() {
  taskLoading.value = true;
  try {
    const res = await getLearningTaskPage({
      ...taskQuery,
      courseTitle: taskQuery.courseTitle || undefined,
    });
    taskList.value = res?.list ?? [];
    taskTotal.value = Number(res?.total ?? 0);
  } finally {
    taskLoading.value = false;
  }
}

// 指派弹窗
const assignVisible = ref(false);
const assignForm = ref({
  courseId: undefined as number | undefined,
  userIdsText: '',
  remark: '',
});
const candidates = ref<TrainingLearningApi.AssignCandidate>({});
const courseOptions = computed(() =>
  (candidates.value.courses ?? []).map((item) => ({
    label: `${item.title ?? ''}（${item.credit ?? 0} 学时）`,
    value: item.id,
  })),
);
const historyUsers = computed(() => candidates.value.users ?? []);

async function openAssign() {
  if (!candidates.value.courses) {
    candidates.value = (await getAssignCandidates()) ?? {};
  }
  assignForm.value = { courseId: undefined, userIdsText: '', remark: '' };
  assignVisible.value = true;
}

function appendUser(id?: number) {
  if (!id) return;
  const current = assignForm.value.userIdsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  if (!current.includes(String(id))) {
    current.push(String(id));
  }
  assignForm.value.userIdsText = current.join(',');
}

async function handleAssign() {
  const userIds = assignForm.value.userIdsText
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map(Number)
    .filter((item) => !Number.isNaN(item));
  if (!assignForm.value.courseId) {
    message.warning('请选择要指派的课程');
    return;
  }
  if (userIds.length === 0) {
    message.warning('请填写至少一个学员用户 ID');
    return;
  }
  const added = await assignCourse({
    courseId: assignForm.value.courseId,
    userIds,
    remark: assignForm.value.remark || undefined,
  });
  message.success(`指派完成，本次新增 ${added ?? 0} 条学习任务`);
  assignVisible.value = false;
  loadTasks();
}

// ==================== 直播房间 ====================
const roomLoading = ref(false);
const roomList = ref<TrainingLearningApi.LiveRoom[]>([]);
const roomTotal = ref(0);
const roomQuery = reactive({ pageNo: 1, pageSize: 10 });

async function loadRooms() {
  roomLoading.value = true;
  try {
    const res = await getLiveRoomPage(roomQuery);
    roomList.value = res?.list ?? [];
    roomTotal.value = Number(res?.total ?? 0);
  } finally {
    roomLoading.value = false;
  }
}

const roomModalVisible = ref(false);
const roomModalTitle = ref('');
const roomForm = ref<TrainingLearningApi.LiveRoom>({ status: 0, sort: 0 });

function openCreateRoom() {
  roomModalTitle.value = '新增直播房间';
  roomForm.value = { status: 0, sort: 0 };
  roomModalVisible.value = true;
}

function openEditRoom(row: TrainingLearningApi.LiveRoom) {
  roomModalTitle.value = '编辑直播房间';
  roomForm.value = { ...row };
  roomModalVisible.value = true;
}

async function handleSaveRoom() {
  if (!roomForm.value.title?.trim()) {
    message.warning('请输入直播标题');
    return;
  }
  if (roomForm.value.id) {
    await updateLiveRoom(roomForm.value);
    message.success($t('ui.actionMessage.operationSuccess'));
  } else {
    await createLiveRoom(roomForm.value);
    message.success($t('ui.actionMessage.createSuccess'));
  }
  roomModalVisible.value = false;
  loadRooms();
}

async function handleDeleteRoom(row: TrainingLearningApi.LiveRoom) {
  await deleteLiveRoom(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  loadRooms();
}

// ==================== 学习统计 & 轨迹 ====================
const statLoading = ref(false);
const statList = ref<TrainingLearningApi.LearningStat[]>([]);

async function loadStats() {
  statLoading.value = true;
  try {
    statList.value = (await getLearningStats()) ?? [];
  } finally {
    statLoading.value = false;
  }
}

const trackVisible = ref(false);
const trackLoading = ref(false);
const trackQuery = ref({ userId: undefined as number | undefined, courseId: undefined as number | undefined });
const trackData = ref<TrainingLearningApi.CourseTrack | null>(null);

function openTrack(row: TrainingLearningApi.LearningStat) {
  trackQuery.value = { userId: undefined, courseId: row.courseId };
  trackData.value = null;
  trackVisible.value = true;
}

async function loadTrack() {
  const { userId, courseId } = trackQuery.value;
  if (!userId || !courseId) {
    message.warning('请输入学员用户 ID');
    return;
  }
  trackLoading.value = true;
  try {
    trackData.value = (await getCourseTrack(userId, courseId)) ?? null;
  } finally {
    trackLoading.value = false;
  }
}

// ==================== 全局配置 ====================
const configMap = ref<Record<string, string>>({});
const configEditVisible = ref(false);
const configForm = ref({ key: '', value: '' });

async function loadConfig() {
  configMap.value = (await getLearningConfig()) ?? {};
}

function openEditConfig(key: string) {
  configForm.value = { key, value: configMap.value[key] ?? '' };
  configEditVisible.value = true;
}

async function handleSaveConfig() {
  await updateLearningConfig(configForm.value);
  message.success($t('ui.actionMessage.operationSuccess'));
  configEditVisible.value = false;
  loadConfig();
}

onMounted(() => {
  loadTasks();
  loadRooms();
  loadStats();
  loadConfig();
});
</script>

<template>
  <Page auto-content-height>
    <Tabs type="card">
      <!-- ============ 学习任务 ============ -->
      <Tabs.TabPane key="task" tab="学习任务">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <Input
            v-model:value="taskQuery.courseTitle"
            class="!w-56"
            allow-clear
            placeholder="课程名称"
            @press-enter="loadTasks"
          />
          <Select
            v-model:value="taskQuery.status"
            class="!w-36"
            allow-clear
            placeholder="学习状态"
            :options="TASK_STATUS_OPTIONS"
            @change="loadTasks"
          />
          <Select
            v-model:value="taskQuery.sourceType"
            class="!w-40"
            allow-clear
            placeholder="任务来源"
            :options="SOURCE_TYPE_OPTIONS"
            @change="loadTasks"
          />
          <Button type="primary" ghost @click="loadTasks">查询</Button>
          <Button type="primary" class="ml-auto" @click="openAssign">
            课程指派
          </Button>
        </div>

        <Table
          row-key="id"
          :data-source="taskList"
          :loading="taskLoading"
          :pagination="{
            current: taskQuery.pageNo,
            pageSize: taskQuery.pageSize,
            total: taskTotal,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条`,
          }"
          :columns="[
            { title: '学员 ID', dataIndex: 'userId', key: 'userId', width: 100 },
            { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
            { title: '来源', key: 'sourceType', width: 120, align: 'center' },
            { title: '状态', key: 'status', width: 100, align: 'center' },
            { title: '学习进度', key: 'progress', width: 180 },
            { title: '已获学时', key: 'credit', width: 120, align: 'center' },
            { title: '完成时间', dataIndex: 'completedAt', key: 'completedAt', width: 180 },
          ]"
          @change="
            (pager: any) => {
              taskQuery.pageNo = pager.current;
              taskQuery.pageSize = pager.pageSize;
              loadTasks();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'sourceType'">
              <Tag :color="record.sourceType === 'self' ? 'purple' : 'blue'">
                {{ record.sourceType === 'self' ? '自主选学' : '指派' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag
                :color="
                  record.status === 2
                    ? 'green'
                    : record.status === 1
                      ? 'orange'
                      : 'default'
                "
              >
                {{ taskStatusText(record.status) }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'progress'">
              <Progress
                :percent="Number(record.progressPercent ?? 0)"
                size="small"
              />
            </template>
            <template v-else-if="column.key === 'credit'">
              {{ record.creditEarned ?? 0 }} / {{ record.creditTotal ?? 0 }}
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <!-- ============ 直播房间 ============ -->
      <Tabs.TabPane key="room" tab="直播房间">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <Button type="primary" class="ml-auto" @click="openCreateRoom">
            新增房间
          </Button>
        </div>
        <Table
          row-key="id"
          :data-source="roomList"
          :loading="roomLoading"
          :pagination="{
            current: roomQuery.pageNo,
            pageSize: roomQuery.pageSize,
            total: roomTotal,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条`,
          }"
          :columns="[
            { title: '直播标题', dataIndex: 'title', key: 'title' },
            { title: '讲师', dataIndex: 'lecturerName', key: 'lecturerName', width: 120 },
            { title: '状态', key: 'status', width: 100, align: 'center' },
            { title: '计划开始', dataIndex: 'planStartAt', key: 'planStartAt', width: 180 },
            { title: '在线人数', dataIndex: 'viewerCount', key: 'viewerCount', width: 100, align: 'center' },
            { title: '操作', key: 'actions', width: 140, align: 'center' },
          ]"
          @change="
            (pager: any) => {
              roomQuery.pageNo = pager.current;
              roomQuery.pageSize = pager.pageSize;
              loadRooms();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="roomStatusColor(record.status)">
                {{ roomStatusText(record.status) }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button type="link" size="small" @click="openEditRoom(record)">
                编辑
              </Button>
              <Popconfirm title="确认删除该直播房间？" @confirm="handleDeleteRoom(record)">
                <Button type="link" danger size="small">删除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <!-- ============ 学习统计 ============ -->
      <Tabs.TabPane key="stat" tab="学习统计/轨迹">
        <Table
          row-key="courseId"
          :data-source="statList"
          :loading="statLoading"
          :pagination="false"
          :columns="[
            { title: '课程', dataIndex: 'courseTitle', key: 'courseTitle' },
            { title: '任务数', dataIndex: 'taskTotal', key: 'taskTotal', width: 90, align: 'center' },
            { title: '待学习', dataIndex: 'todoCount', key: 'todoCount', width: 90, align: 'center' },
            { title: '学习中', dataIndex: 'learningCount', key: 'learningCount', width: 90, align: 'center' },
            { title: '已完成', dataIndex: 'completedCount', key: 'completedCount', width: 90, align: 'center' },
            { title: '完成率(%)', dataIndex: 'completedRate', key: 'completedRate', width: 100, align: 'center' },
            { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 100, align: 'center' },
            { title: '操作', key: 'actions', width: 120, align: 'center' },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'actions'">
              <Button type="link" size="small" @click="openTrack(record)">
                查看轨迹
              </Button>
            </template>
          </template>
        </Table>
        <div class="mt-2 text-xs text-gray-400">
          统计口径：按课程汇总全部学习任务，进度与学时由服务端实时计算。
        </div>
      </Tabs.TabPane>

      <!-- ============ 全局配置 ============ -->
      <Tabs.TabPane key="config" tab="防挂机配置">
        <Table
          row-key="key"
          :data-source="CONFIG_ITEMS"
          :pagination="false"
          :columns="[
            { title: '配置项', dataIndex: 'label', key: 'label' },
            { title: '配置键', dataIndex: 'key', key: 'key', width: 240 },
            { title: '当前值', key: 'value', width: 160 },
            { title: '说明', dataIndex: 'hint', key: 'hint', width: 160 },
            { title: '操作', key: 'actions', width: 100, align: 'center' },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'value'">
              <Tag color="geekblue">{{ configMap[record.key] ?? '-' }}</Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button type="link" size="small" @click="openEditConfig(record.key)">
                修改
              </Button>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>
    </Tabs>

    <!-- 课程指派弹窗 -->
    <Modal
      v-model:open="assignVisible"
      title="课程指派"
      :width="560"
      ok-text="确定指派"
      cancel-text="取消"
      @ok="handleAssign"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            指派课程 <span class="text-red-500">*</span>
          </div>
          <Select
            v-model:value="assignForm.courseId"
            class="w-full"
            placeholder="请选择已上架课程"
            :options="courseOptions"
            show-search
            option-filter-prop="label"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            学员用户 ID（多个用逗号分隔）<span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="assignForm.userIdsText"
            placeholder="如：3015,3016"
          />
          <div v-if="historyUsers.length" class="mt-2 flex flex-wrap gap-2">
            <span class="text-xs text-gray-400">历史学员：</span>
            <Button
              v-for="user in historyUsers"
              :key="user.id"
              size="small"
              type="dashed"
              @click="appendUser(user.id)"
            >
              {{ user.nickname || `用户${user.id}` }}
            </Button>
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input
            v-model:value="assignForm.remark"
            :maxlength="100"
            placeholder="可填写培训批次等说明"
          />
        </div>
        <div class="text-xs text-gray-400">
          已存在的学习任务会自动跳过，仅统计本次新增数量。
        </div>
      </div>
    </Modal>

    <!-- 直播房间编辑弹窗 -->
    <Modal
      v-model:open="roomModalVisible"
      :title="roomModalTitle"
      :width="560"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSaveRoom"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            直播标题 <span class="text-red-500">*</span>
          </div>
          <Input v-model:value="roomForm.title" :maxlength="100" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm text-gray-600">讲师姓名</div>
            <Input v-model:value="roomForm.lecturerName" :maxlength="30" />
          </div>
          <div>
            <div class="mb-1 text-sm text-gray-600">状态</div>
            <Select
              v-model:value="roomForm.status"
              class="w-full"
              :options="ROOM_STATUS_OPTIONS"
            />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm text-gray-600">计划开始时间</div>
            <Input
              v-model:value="roomForm.planStartAt"
              placeholder="2026-09-10 14:00:00"
            />
          </div>
          <div>
            <div class="mb-1 text-sm text-gray-600">计划结束时间</div>
            <Input
              v-model:value="roomForm.planEndAt"
              placeholder="2026-09-10 15:00:00"
            />
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">直播流地址</div>
          <Input v-model:value="roomForm.streamUrl" placeholder="http://..." />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">回放地址</div>
          <Input v-model:value="roomForm.replayVideoUrl" placeholder="http://..." />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm text-gray-600">课时（小时）</div>
            <InputNumber v-model:value="roomForm.creditHours" class="w-full" />
          </div>
          <div>
            <div class="mb-1 text-sm text-gray-600">排序</div>
            <InputNumber v-model:value="roomForm.sort" class="w-full" />
          </div>
        </div>
      </div>
    </Modal>

    <!-- 学习轨迹抽屉 -->
    <Modal
      v-model:open="trackVisible"
      title="课程学习轨迹"
      :width="860"
      :footer="null"
    >
      <div class="mb-3 flex flex-wrap items-center gap-3">
        <Input
          v-model:value="trackQuery.userId"
          class="!w-48"
          placeholder="学员用户 ID"
        />
        <Button type="primary" ghost :loading="trackLoading" @click="loadTrack">
          查询
        </Button>
        <span v-if="trackData" class="text-sm text-gray-500">
          {{ trackData.nickname || `用户${trackData.userId}` }} ·
          {{ trackData.courseTitle }} · 有效
          {{ trackData.totalValidSeconds ?? 0 }} 秒 · 已获
          {{ trackData.creditEarned ?? 0 }} 学时
        </span>
      </div>
      <Table
        v-if="trackData"
        row-key="mediaId"
        :data-source="trackData.mediaTracks ?? []"
        :pagination="false"
        :columns="[
          { title: '媒资', dataIndex: 'name', key: 'name' },
          { title: '类型', dataIndex: 'mediaTypeName', key: 'mediaTypeName', width: 90, align: 'center' },
          { title: '会话数', dataIndex: 'sessionCount', key: 'sessionCount', width: 90, align: 'center' },
          { title: '有效秒', dataIndex: 'totalValidSeconds', key: 'totalValidSeconds', width: 100, align: 'center' },
          { title: '进度(%)', dataIndex: 'ratioPercent', key: 'ratioPercent', width: 100, align: 'center' },
          { title: '已获学时', dataIndex: 'creditEarned', key: 'creditEarned', width: 100, align: 'center' },
        ]"
      >
        <template #expandedRowRender="{ record }">
          <Table
            row-key="sessionId"
            size="small"
            :data-source="record.sessions ?? []"
            :pagination="false"
            :columns="[
              { title: '开始时间', dataIndex: 'startAt', key: 'startAt' },
              { title: '结束时间', dataIndex: 'endAt', key: 'endAt' },
              { title: '有效秒', dataIndex: 'validSeconds', key: 'validSeconds' },
              { title: '结束原因', dataIndex: 'endReason', key: 'endReason' },
              { title: '折算学时', dataIndex: 'creditEarned', key: 'creditEarned' },
            ]"
          />
        </template>
      </Table>
      <div v-else class="py-6 text-center text-sm text-gray-400">
        请输入学员用户 ID 后查询学习轨迹
      </div>
    </Modal>

    <!-- 配置修改弹窗 -->
    <Modal
      v-model:open="configEditVisible"
      :title="`修改配置：${configForm.key}`"
      :width="480"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSaveConfig"
    >
      <div class="py-2">
        <div class="mb-1 text-sm text-gray-600">配置值</div>
        <Input v-model:value="configForm.value" placeholder="请输入配置值" />
      </div>
    </Modal>
  </Page>
</template>
