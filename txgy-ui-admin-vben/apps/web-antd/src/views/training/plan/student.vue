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
  Select,
  Space,
  Table,
  Tag,
  Upload,
} from 'ant-design-vue';

import { getSimpleUserList } from '#/api/system/user';

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
const query = ref<{ userName?: string; deptName?: string }>({});
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

// ========== 添加学员（勾选批量加入，自动生成学习任务） ==========
const userOptions = ref<{ label: string; value: number }[]>([]);
const addVisible = ref(false);
const addUserIds = ref<number[]>([]);
const addLoading = ref(false);

async function openAddModal() {
  addUserIds.value = [];
  if (userOptions.value.length === 0) {
    try {
      const users = await getSimpleUserList();
      userOptions.value = (users ?? [])
        .filter((item) => item.status === 0 && item.id != null)
        .map((item) => ({ label: item.nickname, value: item.id! }));
    } catch {
      userOptions.value = [];
    }
  }
  addVisible.value = true;
}

const addableOptions = computed(() =>
  userOptions.value.filter((item) => !activeUserIds.value.has(item.value)),
);

async function handleAddStudents() {
  if (!currentClass.value?.id || addUserIds.value.length === 0) {
    message.warning('请先勾选要添加的学员');
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
        <Button type="primary" :disabled="!writable" @click="openAddModal">
          添加学员
        </Button>
        <Button :disabled="!writable" @click="importVisible = true">
          批量导入学员
        </Button>
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

    <!-- 添加学员弹窗 -->
    <Modal
      v-model:open="addVisible"
      title="添加学员"
      :footer="null"
      width="620px"
    >
      <p class="mb-2 text-xs text-gray-500">
        勾选人员后批量加入班级，系统将自动生成「课程 × 学员」学习任务。
      </p>
      <Space class="mb-3 w-full">
        <Select
          v-model:value="addUserIds"
          mode="multiple"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="选择学员（可多选）"
          style="width: 100%"
          :options="addableOptions"
        />
      </Space>
      <div class="text-right">
        <Space>
          <Button @click="addVisible = false">取消</Button>
          <Button type="primary" :loading="addLoading" @click="handleAddStudents">
            确认添加
          </Button>
        </Space>
      </div>
    </Modal>

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
