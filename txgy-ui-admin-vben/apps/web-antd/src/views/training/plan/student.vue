<script lang="ts" setup>
import type { TrainingPlanApi } from './api';

import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Button,
  Checkbox,
  Input,
  message,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Upload,
} from 'ant-design-vue';

import type { SystemUserApi } from '#/api/system/user';

import { UserSelectDialog } from '#/views/system/user/components';

import {
  addClassStudents,
  getAllClassStudents,
  getClassStudentPage,
  getStudentImportTemplate,
  importClassStudents,
  removeClassStudent,
} from './api';
import ClassBar from './class-bar.vue';

defineOptions({ name: 'TrainingPlanStudent' });

/** 当前班级（由顶部班级选择条联动） */
const currentClass = ref<TrainingPlanApi.Class>();
/** 可维护学员：草稿 / 进行中 */
const writable = computed(
  () =>
    currentClass.value != null &&
    (currentClass.value.status === 0 || currentClass.value.status === 1),
);

// ========== 在班学员分页 ==========
const loading = ref(false);
const rows = ref<TrainingPlanApi.ClassStudent[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const query = ref<{ deptName?: string; userName?: string; }>({});
/** 在班学员 ID 集合（用于添加弹窗去重） */
const activeUserIds = ref<Set<number>>(new Set());

async function loadActiveIds() {
  if (!currentClass.value?.id) {
    activeUserIds.value = new Set();
    return;
  }
  const list = await getAllClassStudents(currentClass.value.id);
  activeUserIds.value = new Set(
    list.map((item) => item.userId!).filter(Boolean),
  );
}

async function loadStudents() {
  if (!currentClass.value?.id) {
    rows.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const data = await getClassStudentPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      classId: currentClass.value.id,
      ...query.value,
    });
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function handleClassChange(row?: TrainingPlanApi.Class) {
  currentClass.value = row;
  pageNo.value = 1;
  if (row?.id) {
    loadStudents();
    loadActiveIds();
  } else {
    rows.value = [];
    total.value = 0;
    activeUserIds.value = new Set();
  }
}

function handleSearch() {
  pageNo.value = 1;
  loadStudents();
}

function handleTableChange(pagination: any) {
  pageNo.value = pagination.current;
  pageSize.value = pagination.pageSize;
  loadStudents();
}

// ========== 添加学员（从「系统管理 → 用户管理」人员中选择，批量入班并自动生成学习任务） ==========
const userSelectRef = ref<InstanceType<typeof UserSelectDialog>>();
const addVisible = ref(false);
const addLoading = ref(false);
/** 已选待入班人员（已在班的在选择时即过滤） */
const pendingUsers = ref<SystemUserApi.User[]>([]);
const addUserIds = computed(() =>
  pendingUsers.value.map((item) => item.id!).filter((id) => id != null),
);
const addColumns = [
  { title: '姓名', dataIndex: 'nickname', key: 'nickname', width: 120 },
  { title: '登录账号', dataIndex: 'username', key: 'username', width: 140 },
  { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 130 },
  { title: '部门', dataIndex: 'deptName', key: 'deptName' },
  { title: '操作', key: 'action', width: 80 },
];

/** 打开「人员选择」弹窗（来源：系统管理 - 用户管理的人员列表，支持按部门/姓名/手机号检索、多选） */
function openUserSelect() {
  userSelectRef.value?.open([], { multiple: true });
}

/** 人员选择确认：过滤已在班人员后进入入班确认弹窗 */
function handleUsersSelected(rows: SystemUserApi.User[]) {
  const inClassCount = rows.filter((item) =>
    activeUserIds.value.has(item.id!),
  ).length;
  pendingUsers.value = rows.filter(
    (item) => item.id != null && !activeUserIds.value.has(item.id),
  );
  if (pendingUsers.value.length === 0) {
    message.warning('所选人员均已在班，无需重复加入');
    return;
  }
  if (inClassCount > 0) {
    message.info(`${inClassCount} 名人员已在班，已自动跳过`);
  }
  addVisible.value = true;
}

function removePendingUser(userId?: number) {
  pendingUsers.value = pendingUsers.value.filter(
    (item) => item.id !== userId,
  );
}

async function handleAddStudents() {
  if (!currentClass.value?.id || addUserIds.value.length === 0) {
    message.warning('请先选择要添加的学员');
    return;
  }
  addLoading.value = true;
  try {
    const added = await addClassStudents(
      currentClass.value.id,
      addUserIds.value,
    );
    message.success(`已添加 ${added} 名学员（已在班自动跳过）`);
    addVisible.value = false;
    await Promise.all([loadStudents(), loadActiveIds()]);
  } finally {
    addLoading.value = false;
  }
}

async function handleRemoveStudent(row: TrainingPlanApi.ClassStudent) {
  if (!currentClass.value?.id || row.userId == null) {
    return;
  }
  await removeClassStudent(currentClass.value.id, row.userId);
  message.success('已移出该学员（历史记录保留，将不再收到督促）');
  await Promise.all([loadStudents(), loadActiveIds()]);
}

// ========== 批量导入学员（Excel，按手机号匹配账号） ==========
const importVisible = ref(false);
const importFile = ref<File>();
const importing = ref(false);
const importResult = ref<TrainingPlanApi.ImportStudentResult>();
/** 未匹配到系统账号时，是否自动创建学员账号（登录名=手机号、昵称=姓名） */
const autoCreateUser = ref(false);
/** 导入失败明细弹窗 */
const failDetailVisible = ref(false);
const failColumns = [
  { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 80 },
  { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 150 },
  { title: '失败原因', dataIndex: 'reason', key: 'reason' },
];

function beforeUpload(file: File) {
  importFile.value = file;
  importResult.value = undefined;
  return false;
}

async function handleDownloadTemplate() {
  const data = await getStudentImportTemplate();
  downloadFileFromBlobPart({ fileName: '培训班学员导入模板.xls', source: data });
}

async function handleImport() {
  if (!currentClass.value?.id || !importFile.value) {
    message.warning('请先选择 Excel 文件');
    return;
  }
  importing.value = true;
  try {
    const result = await importClassStudents(
      currentClass.value.id,
      importFile.value,
      autoCreateUser.value,
    );
    importResult.value = result;
    const failCount = result?.failRows?.length ?? 0;
    const createdCount = result?.createdUserCount ?? 0;
    message.success(
      `导入完成：成功新增 ${result?.successCount ?? 0} 人` +
        (createdCount ? `，自动创建账号 ${createdCount} 个` : '') +
        (failCount ? `，失败 ${failCount} 行` : ''),
    );
    importVisible.value = false;
    failDetailVisible.value = failCount > 0;
    await Promise.all([loadStudents(), loadActiveIds()]);
  } finally {
    importing.value = false;
  }
}

// ========== 在班人数与名额（工具栏展示；「批量导入学员并指定培训班」入口在培训班管理列表页） ==========
/**
 * 单个培训班在班学员上限，与后端 ClassStudentServiceImpl.MAX_STUDENT_SIZE 保持一致
 * 0 表示不限制人数（已取消培训班 50 人上限）；如需恢复限制，改为具体数值即可
 */
const CLASS_MAX_STUDENT = 0;
/** 是否限制班级人数（上限为 0 时表示不限制） */
const studentLimited = computed(() => CLASS_MAX_STUDENT > 0);

/** 当前在班人数（用于展示「在班 X/50」与剩余名额） */
const inClassTotalCount = computed(() => activeUserIds.value.size);
/** 剩余可加入名额（不限制人数时为 Infinity） */
const remainingQuota = computed(() =>
  studentLimited.value
    ? Math.max(0, CLASS_MAX_STUDENT - inClassTotalCount.value)
    : Number.POSITIVE_INFINITY,
);

// ========== 表格列 ==========
const columns = [
  { title: '学员姓名', dataIndex: 'userName', key: 'userName', width: 140 },
  { title: '用户 ID', dataIndex: 'userId', key: 'userId', width: 100 },
  { title: '部门/班组', dataIndex: 'deptName', key: 'deptName' },
  { title: '加入来源', key: 'source', width: 130 },
  { title: '入班时间', dataIndex: 'joinTime', key: 'joinTime', width: 180 },
  { title: '操作', key: 'action', width: 110 },
];
</script>

<template>
  <Page auto-content-height>
    <ClassBar @change="handleClassChange" />

    <template v-if="currentClass">
      <!-- 工具栏 -->
      <Space class="mb-3" wrap>
        <Button type="primary" :disabled="!writable" @click="openUserSelect">
          添加学员
        </Button>
        <Button :disabled="!writable" @click="importVisible = true">
          批量导入学员
        </Button>
        <Tag :color="studentLimited && remainingQuota === 0 ? 'red' : 'blue'">
          在班 {{ inClassTotalCount
          }}<template v-if="studentLimited">/{{ CLASS_MAX_STUDENT }}</template>
        </Tag>
        <Input
          v-model:value="query.userName"
          allow-clear
          placeholder="学员姓名"
          style="width: 160px"
          @press-enter="handleSearch"
        />
        <Input
          v-model:value="query.deptName"
          allow-clear
          placeholder="部门/班组"
          style="width: 160px"
          @press-enter="handleSearch"
        />
        <Button @click="handleSearch">查询</Button>
      </Space>

      <Table
        row-key="id"
        size="small"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 名学员`,
        }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'source'">
            <Tag v-if="record.source === 2" color="processing">批量导入</Tag>
            <Tag v-else-if="record.source === 3" color="success">按对象筛选</Tag>
            <Tag v-else-if="record.source === 5" color="warning">导入分班</Tag>
            <Tag v-else>手动添加</Tag>
          </template>
          <template v-if="column.key === 'action'">
            <Popconfirm
              v-if="writable"
              title="确认将该学员移出培训班？（历史记录保留，学习任务作废）"
              @confirm="handleRemoveStudent(record)"
            >
              <Button type="link" danger size="small">移除</Button>
            </Popconfirm>
            <span v-else class="text-gray-400">-</span>
          </template>
        </template>
      </Table>
    </template>
    <div
      v-else
      class="mt-10 text-center text-sm text-gray-400"
    >
      请先在上方选择培训班（暂无班级请先到培训班管理创建并开班）
    </div>

    <!-- 添加学员弹窗（人员来自「系统管理 → 用户管理」） -->
    <Modal
      v-model:open="addVisible"
      title="添加学员"
      :footer="null"
      width="760px"
    >
      <p class="mb-2 text-xs text-gray-500">
        确认以下人员加入班级，系统将自动生成「课程 × 学员」学习任务。
      </p>
      <Table
        :columns="addColumns"
        :data-source="pendingUsers"
        :pagination="false"
        :scroll="{ y: 260 }"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <Button type="link" danger size="small" @click="removePendingUser(record.id)">
              移除
            </Button>
          </template>
        </template>
      </Table>
      <div class="mt-3 text-right">
        <Space>
          <Button @click="addVisible = false">取消</Button>
          <Button
            type="primary"
            :disabled="addUserIds.length === 0"
            :loading="addLoading"
            @click="handleAddStudents"
          >
            确认添加（{{ addUserIds.length }} 人）
          </Button>
        </Space>
      </div>
    </Modal>

    <!-- 人员选择弹窗（系统管理 - 用户管理人员列表） -->
    <UserSelectDialog ref="userSelectRef" @selected="handleUsersSelected" />

    <!-- 批量导入弹窗 -->
    <Modal
      v-model:open="importVisible"
      title="批量导入学员"
      :footer="null"
      width="640px"
    >
      <div class="mb-3 rounded bg-gray-50 p-2 text-xs text-gray-500">
        <p>
          1. 先下载导入模板，在模板中按行填写学员<strong>姓名</strong>与<strong>手机号</strong>（系统账号按手机号匹配）。
        </p>
        <p>
          2. 选择填写好的 Excel 文件后点击「开始导入」，成功学员将自动加入班级并生成学习任务。
        </p>
        <p>
          3.
          若学员尚未开通系统账号：勾选下方「自动创建学员账号」后，系统将按「登录名=手机号、昵称=姓名」自动开户（初始密码为系统配置的初始密码）；不勾选则仅在失败明细中提示。
        </p>
      </div>
      <div class="mb-2">
        <Checkbox v-model:checked="autoCreateUser">
          自动创建学员账号（名册中存在、平台尚未开户的学员）
        </Checkbox>
      </div>
      <Space class="mb-3" wrap>
        <Upload
          :max-count="1"
          accept=".xls,.xlsx"
          :before-upload="beforeUpload"
        >
          <Button>选择 Excel 文件</Button>
        </Upload>
        <span v-if="importFile" class="text-xs text-gray-600">
          {{ importFile?.name }}
        </span>
        <Button @click="handleDownloadTemplate">下载导入模板</Button>
      </Space>
      <div class="text-right">
        <Space>
          <Button @click="importVisible = false">关闭</Button>
          <Button
            type="primary"
            :loading="importing"
            :disabled="!importFile"
            @click="handleImport"
          >
            开始导入
          </Button>
        </Space>
      </div>
    </Modal>

    <!-- 导入失败明细弹窗 -->
    <Modal
      v-model:open="failDetailVisible"
      title="导入失败明细"
      :footer="null"
      width="640px"
    >
      <Table
        :columns="failColumns"
        :data-source="importResult?.failRows ?? []"
        :pagination="false"
        row-key="rowNumber"
        size="small"
      />
    </Modal>

  </Page>
</template>
