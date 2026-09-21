<script lang="ts" setup>
import type { Key } from 'ant-design-vue/es/_util/type';

import type { TrainingPlanApi } from './api';

import type { SystemUserApi } from '#/api/system/user';

import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart, handleTree } from '@vben/utils';

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
  TreeSelect,
  Upload,
} from 'ant-design-vue';

import { getSimpleDeptList } from '#/api/system/dept';
import { getSimplePostList } from '#/api/system/post';
import UserSelectDialog from '#/views/system/user/components/select-dialog.vue';

import {
  addClassStudents,
  getAllClassStudents,
  getClassStudentPage,
  getStudentImportTemplate,
  importClassStudents,
  previewScopeStudents,
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
    loading.value = false;
    return;
  }
  loading.value = true;
  // 兜底：请求异常或长时间挂起时也确保 loading 关闭，避免页面永久转圈
  const guardTimer = setTimeout(() => {
    loading.value = false;
  }, 8000);
  try {
    const data = await getClassStudentPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      classId: currentClass.value.id,
      ...query.value,
    });
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    clearTimeout(guardTimer);
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

// ========== 挑选学员入班：逐个勾选 / 按部门岗位批量 ==========
const addLoading = ref(false);
const userSelectRef = ref<InstanceType<typeof UserSelectDialog>>();

/** 逐个挑选：复用系统「人员选择」（部门树 + 分页勾选），可从劳务部门逐级筛选 */
function openUserPicker() {
  userSelectRef.value?.open([], { multiple: true });
}

/** 人员选择弹窗回调 */
async function handleUserPicked(rows: SystemUserApi.User[]) {
  await joinClass(
    rows.map((item) => item.id).filter((id): id is number => id != null),
  );
}

/**
 * 统一入班入口：剔除已在班人员 → 按班级剩余名额裁剪 → 调用入班接口
 * @param userIds 候选学员的用户编号
 */
async function joinClass(userIds: number[]) {
  if (!currentClass.value?.id) {
    return;
  }
  const uniqueIds = [...new Set(userIds)];
  const candidates = uniqueIds.filter((id) => !activeUserIds.value.has(id));
  const skipped = uniqueIds.length - candidates.length;
  if (candidates.length === 0) {
    message.warning(
      skipped > 0 ? `所选 ${skipped} 人已在班中，无需重复添加` : '请先勾选学员',
    );
    return;
  }
  if (studentLimited.value && remainingQuota.value === 0) {
    message.warning(`班级学员已达上限 ${CLASS_MAX_STUDENT} 人，无法继续添加`);
    return;
  }
  const overflow = studentLimited.value
    ? Math.max(0, candidates.length - remainingQuota.value)
    : 0;
  const accepted = studentLimited.value
    ? candidates.slice(0, remainingQuota.value)
    : candidates;
  addLoading.value = true;
  try {
    const added = await addClassStudents(currentClass.value.id, accepted);
    message.success(
      `已添加 ${added} 名学员` +
        (skipped ? `，${skipped} 人已在班自动跳过` : '') +
        (overflow ? `，另有 ${overflow} 人因名额不足未加入` : ''),
    );
    scopeVisible.value = false;
    importVisible.value = false;
    await Promise.all([loadStudents(), loadActiveIds()]);
  } catch (error) {
    message.error((error as Error)?.message || '添加学员失败，请稍后重试');
  } finally {
    addLoading.value = false;
  }
}

// ========== 按部门 / 岗位批量导入（如整批导入劳务部门学员） ==========
const scopeVisible = ref(false);
const scopeLoading = ref(false);
const deptTree = ref<Record<string, any>[]>([]);
const postOptions = ref<{ label: string; value: number }[]>([]);
const scopeDeptIds = ref<number[]>([]);
const scopePostIds = ref<number[]>([]);
const scopeIncludeChild = ref(true);
const scopePreview = ref<TrainingPlanApi.ScopePreviewResult>();
const scopeSelectedIds = ref<number[]>([]);

const scopeUserColumns = [
  { title: '学员姓名', dataIndex: 'nickname', key: 'nickname' },
  { title: '部门/班组', dataIndex: 'deptName', key: 'deptName' },
  { title: '入班情况', key: 'inClass', width: 120 },
];

async function openScopeModal() {
  scopeDeptIds.value = [];
  scopePostIds.value = [];
  scopeIncludeChild.value = true;
  scopePreview.value = undefined;
  scopeSelectedIds.value = [];
  scopeVisible.value = true;
  if (deptTree.value.length === 0) {
    try {
      deptTree.value = handleTree(await getSimpleDeptList());
    } catch {
      deptTree.value = [];
    }
  }
  if (postOptions.value.length === 0) {
    try {
      postOptions.value = (await getSimplePostList())
        ?.filter((item) => item.status === 0 && item.id != null)
        .map((item) => ({ label: item.name, value: item.id! }));
    } catch {
      postOptions.value = [];
    }
  }
}

/** 预览命中名单：命中人数、已在班人数、可新增人数与剩余名额 */
async function handleScopePreview() {
  if (!currentClass.value?.id) {
    return;
  }
  if (scopeDeptIds.value.length === 0 && scopePostIds.value.length === 0) {
    message.warning('请至少选择一个部门或岗位');
    return;
  }
  scopeLoading.value = true;
  try {
    const data = await previewScopeStudents({
      classId: currentClass.value.id,
      deptIds: scopeDeptIds.value,
      postIds: scopePostIds.value,
      includeChildDept: scopeIncludeChild.value,
    });
    scopePreview.value = data;
    // 默认勾选未在班人员，且不超过班级剩余名额
    const addable = (data?.users ?? []).filter((item) => !item.inClass);
    const limit = Math.min(data?.remaining ?? 0, addable.length);
    scopeSelectedIds.value = addable
      .slice(0, limit)
      .map((item) => item.userId)
      .filter((id): id is number => id != null);
  } finally {
    scopeLoading.value = false;
  }
}

async function handleScopeAdd() {
  if (scopeSelectedIds.value.length === 0) {
    message.warning('请先勾选要加入的学员');
    return;
  }
  await joinClass(scopeSelectedIds.value);
}

/** 已在班人员不可重复勾选 */
function scopeRowDisabled(record: TrainingPlanApi.ScopePreviewUser) {
  return { disabled: Boolean(record.inClass) };
}

function handleScopeSelectionChange(keys: Key[]) {
  scopeSelectedIds.value = keys.map((key) => Number(key));
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

// ========== 在班人数与名额（工具栏展示） ==========
/**
 * 单个培训班在班学员上限，与后端 ClassStudentServiceImpl.MAX_STUDENT_SIZE 保持一致
 * 0 表示不限制人数（已取消培训班 50 人上限）；如需恢复限制，改为具体数值即可
 */
const CLASS_MAX_STUDENT = 0;
/** 是否限制班级人数 */
const studentLimited = computed(() => CLASS_MAX_STUDENT > 0);

/** 当前在班人数（用于展示「在班 X」与剩余名额） */
const inClassTotalCount = computed(() => activeUserIds.value.size);
/** 剩余可加入名额（不限制时为 Infinity） */
const remainingQuota = computed(() =>
  studentLimited.value
    ? Math.max(0, CLASS_MAX_STUDENT - inClassTotalCount.value)
    : Number.POSITIVE_INFINITY,
);

/** 「按部门/岗位导入」入口开关：需求调整，暂时隐藏该组件 */
const SCOPE_IMPORT_ENABLED = false;

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
        <Button type="primary" :disabled="!writable" @click="openUserPicker">
          添加学员
        </Button>
        <Button
          v-if="SCOPE_IMPORT_ENABLED"
          :disabled="!writable"
          @click="openScopeModal"
        >
          按部门/岗位导入
        </Button>
        <Button :disabled="!writable" @click="importVisible = true">
          批量导入学员
        </Button>
        <Tag :color="studentLimited && remainingQuota === 0 ? 'red' : 'blue'">
          在班 {{ inClassTotalCount }}<template v-if="studentLimited">/{{ CLASS_MAX_STUDENT }}</template>
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

    <!-- 逐个挑选学员：系统人员选择（部门树 + 分页勾选） -->
    <UserSelectDialog ref="userSelectRef" @selected="handleUserPicked" />

    <!-- 按部门 / 岗位整批导入（SCOPE_IMPORT_ENABLED=false 时隐藏） -->
    <Modal
      v-if="SCOPE_IMPORT_ENABLED"
      v-model:open="scopeVisible"
      title="按部门/岗位导入学员"
      :footer="null"
      width="860px"
    >
      <p class="mb-3 rounded bg-gray-50 p-2 text-xs text-gray-500">
        按部门（可含下级）与岗位圈选参训人员：选择<strong>劳务部门</strong>及其「一线劳务人员」岗位即可批量带出该部门人员，
        确认勾选后批量入班并自动生成「课程 × 学员」学习任务。
      </p>
      <Space class="mb-3 w-full" wrap>
        <TreeSelect
          v-model:value="scopeDeptIds"
          allow-clear
          multiple
          show-search
          tree-checkable
          tree-default-expand-all
          tree-node-filter-prop="name"
          :max-tag-count="3"
          :field-names="{ label: 'name', value: 'id', children: 'children' }"
          :tree-data="deptTree"
          placeholder="选择部门（如劳务部门，可多选）"
          style="min-width: 320px"
        />
        <Select
          v-model:value="scopePostIds"
          allow-clear
          mode="multiple"
          show-search
          option-filter-prop="label"
          :options="postOptions"
          placeholder="选择岗位（如一线劳务人员，可多选）"
          style="min-width: 240px"
        />
        <Checkbox v-model:checked="scopeIncludeChild">含下级部门</Checkbox>
        <Button :loading="scopeLoading" @click="handleScopePreview">
          预览名单
        </Button>
      </Space>

      <template v-if="scopePreview">
        <p class="mb-2 text-xs text-gray-600">
          命中 {{ scopePreview.matchedCount ?? 0 }} 人，其中已在班
          {{ scopePreview.inClassCount ?? 0 }} 人，可新增
          {{ scopePreview.addableCount ?? 0 }} 人；当前在班
          {{ scopePreview.inClassTotal ?? 0 }}/{{
            scopePreview.maxSize ?? CLASS_MAX_STUDENT
          }}
          人，剩余名额 {{ scopePreview.remaining ?? 0 }} 个。
        </p>
        <Table
          row-key="userId"
          size="small"
          :columns="scopeUserColumns"
          :data-source="scopePreview.users ?? []"
          :pagination="{ pageSize: 8, showSizeChanger: false }"
          :row-selection="{
            selectedRowKeys: scopeSelectedIds,
            getCheckboxProps: scopeRowDisabled,
            onChange: handleScopeSelectionChange,
          }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'inClass'">
              <Tag v-if="record.inClass" color="default">已在班</Tag>
              <Tag v-else color="blue">可加入</Tag>
            </template>
          </template>
        </Table>
      </template>

      <div class="mt-3 text-right">
        <Space>
          <Button @click="scopeVisible = false">取消</Button>
          <Button
            type="primary"
            :loading="addLoading"
            :disabled="scopeSelectedIds.length === 0"
            @click="handleScopeAdd"
          >
            确认导入（{{ scopeSelectedIds.length }} 人）
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
