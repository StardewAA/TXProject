<script lang="ts" setup>
import type { TrainingPlanApi } from '../api';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Alert,
  Button,
  message,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Upload,
} from 'ant-design-vue';

import {
  allocateClassStudents,
  getAllClasses,
  getStudentImportTemplate,
  parseClassStudentsImport,
} from '../api';

/**
 * 批量导入学员并手动指定培训班
 *
 * - 上传 Excel 学员名单（按手机号匹配系统账号），解析后展示匹配到的学员名单；
 * - 由教务在名单中逐行「手动选择」要加入的培训班，留空表示该学员本次不入班；
 * - 提交后按明细入班（自动生成学习任务），单个培训班在班学员上限 50 人。
 */
defineOptions({ name: 'TrainingPlanImportAllocate' });

const emit = defineEmits(['success']);

/** 单个培训班在班学员上限，与后端 ClassStudentServiceImpl.MAX_STUDENT_SIZE 保持一致 */
const CLASS_MAX_STUDENT = 50;

/** 名单行（key 取 Excel 行号，重复手机号也能唯一定位） */
interface ImportRow extends TrainingPlanApi.ImportParseUser {
  key: string;
  classId?: number;
}

const classes = ref<TrainingPlanApi.Class[]>([]);
const classLoading = ref(false);
const users = ref<ImportRow[]>([]);
const failRows = ref<
  { key: string; mobile?: string; reason?: string; rowNumber?: number }[]
>([]);
const file = ref<File>();
const parsing = ref(false);
const submitting = ref(false);
const batchClassId = ref<number>();
const result = ref<TrainingPlanApi.ImportAllocateResult>();

/** 可入班的培训班（草稿 / 进行中，与后端 isEditableForStudent 保持一致） */
const allocatableClasses = computed(() =>
  classes.value.filter((item) => item.status === 0 || item.status === 1),
);

/** 本次已指定到各班的人数 */
const assignedCountMap = computed(() => {
  const map = new Map<number, number>();
  users.value.forEach((item) => {
    if (item.classId != null) {
      map.set(item.classId, (map.get(item.classId) ?? 0) + 1);
    }
  });
  return map;
});

/** 培训班下拉：展示「名称（剩余 N 个名额）」，名额已满的置灰不可选 */
const classSelectOptions = computed(() =>
  allocatableClasses.value.map((item) => {
    const serverRemaining = Math.max(
      0,
      CLASS_MAX_STUDENT - (item.studentCount ?? 0),
    );
    const left = Math.max(
      0,
      serverRemaining - (assignedCountMap.value.get(item.id!) ?? 0),
    );
    return {
      label: `${item.name}（剩余 ${left} 个名额）`,
      value: item.id!,
      disabled: left <= 0,
    };
  }),
);

/** 已指定培训班的学员 */
const assignedUsers = computed(() =>
  users.value.filter((item) => item.classId != null),
);

const columns = [
  { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 70 },
  { title: '学员姓名', dataIndex: 'userName', key: 'userName', width: 130 },
  { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 140 },
  { title: '部门/班组', dataIndex: 'deptName', key: 'deptName' },
  { title: '入班培训班（留空则不入班）', key: 'classId', width: 320 },
  { title: '操作', key: 'action', width: 90 },
];

const failColumns = [
  { title: '行号', dataIndex: 'rowNumber', key: 'rowNumber', width: 70 },
  { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 140 },
  { title: '失败原因', dataIndex: 'reason', key: 'reason' },
];

const resultColumns = [
  { title: '培训班名称', dataIndex: 'className', key: 'className' },
  { title: '入班前', dataIndex: 'beforeCount', key: 'beforeCount', width: 80 },
  { title: '本次入班', dataIndex: 'addedCount', key: 'addedCount', width: 90 },
  { title: '入班后', dataIndex: 'afterCount', key: 'afterCount', width: 80 },
  { title: '剩余名额', dataIndex: 'remaining', key: 'remaining', width: 90 },
  { title: '本次入班学员', key: 'users' },
];

const namePagination = {
  pageSize: 10,
  showSizeChanger: false,
  showTotal: (total: number) => `共 ${total} 名学员`,
};

const resultSummary = computed(() => {
  const data = result.value;
  if (!data) {
    return '';
  }
  const submitted = assignedUsers.value.length;
  const success = data.successCount ?? 0;
  return (
    `已提交 ${submitted} 人次，实际入班 ${success} 人次` +
    (submitted > success ? `（${submitted - success} 人次已在班自动跳过）` : '')
  );
});

async function loadClasses() {
  classLoading.value = true;
  try {
    classes.value = await getAllClasses();
    if (allocatableClasses.value.length === 0) {
      message.warning('暂无可入班的培训班，请先创建「草稿/进行中」的培训班并绑定课程');
    }
  } catch {
    classes.value = [];
    message.error('加载培训班列表失败，请检查网络或权限后重试');
  } finally {
    classLoading.value = false;
  }
}

/** 解析名单（上传即解析，不落库） */
async function parseFile() {
  if (!file.value) {
    return;
  }
  parsing.value = true;
  try {
    const data = await parseClassStudentsImport(file.value);
    users.value = (data?.users ?? []).map((item) => ({
      ...item,
      key: `row-${item.rowNumber}`,
    }));
    failRows.value = (data?.failRows ?? []).map((item, index) => ({
      ...item,
      key: `fail-${index}`,
    }));
    if (users.value.length === 0) {
      message.warning('未匹配到系统账号，请检查 Excel 中的手机号');
    }
  } finally {
    parsing.value = false;
  }
}

function beforeUpload(f: File) {
  file.value = f;
  result.value = undefined;
  users.value = [];
  failRows.value = [];
  void parseFile();
  return false;
}

function handleRowClassChange(record: ImportRow, value?: number) {
  record.classId = value ?? undefined;
}

/** 从本次名单中移除该学员（仅不参与本次导入，不影响系统账号） */
function handleRemoveRow(record: ImportRow) {
  users.value = users.value.filter((item) => item.key !== record.key);
}

/** 批量指定：把当前名单中的学员统一指定到某个培训班 */
function handleBatchAssign() {
  if (batchClassId.value == null) {
    message.warning('请先选择要批量指定的培训班');
    return;
  }
  users.value.forEach((item) => {
    item.classId = batchClassId.value;
  });
}

function handleClearAssign() {
  users.value.forEach((item) => {
    item.classId = undefined;
  });
}

async function handleDownloadTemplate() {
  const data = await getStudentImportTemplate();
  downloadFileFromBlobPart({
    fileName: '培训班学员导入模板.xls',
    source: data,
  });
}

function userNames(list?: TrainingPlanApi.AllocateUser[]) {
  const names = (list ?? []).map(
    (item) => item.userName || `用户${item.userId}`,
  );
  return names.length > 0 ? names.join('、') : '-';
}

async function handleSubmit() {
  if (assignedUsers.value.length === 0) {
    message.warning('请先在名单中为要入班的学员选择培训班');
    return;
  }
  const items: TrainingPlanApi.ImportAllocateItem[] = assignedUsers.value.map(
    (item) => ({ userId: item.userId, classId: item.classId }),
  );
  submitting.value = true;
  try {
    result.value = await allocateClassStudents(items);
    message.success(`入班完成：成功 ${result.value?.successCount ?? 0} 人次`);
    await loadClasses();
    emit('success');
  } finally {
    submitting.value = false;
  }
}

const [ModalWrap, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    file.value = undefined;
    users.value = [];
    failRows.value = [];
    batchClassId.value = undefined;
    result.value = undefined;
    if (!isOpen) {
      return;
    }
    await loadClasses();
  },
});
</script>

<template>
  <ModalWrap title="批量导入学员并指定培训班" class="w-4/5">
    <div class="mb-3 rounded bg-gray-50 p-2 text-xs text-gray-500">
      <p>
        1. 下载导入模板，按行填写学员<strong>手机号</strong>（系统账号按手机号匹配，未匹配的行列入失败明细）。
      </p>
      <p>
        2. 上传后系统自动解析学员名单，请在下表中
        <strong>逐行手动选择</strong>
        要加入的培训班；留空表示该学员本次不入班。
      </p>
      <p>
        3. 单个培训班在班学员上限
        <strong>{{ CLASS_MAX_STUDENT }}</strong>
        人，超出时会提示各班剩余名额。
      </p>
    </div>

    <Space class="mb-3" wrap>
      <Upload
        :max-count="1"
        :show-upload-list="false"
        accept=".xls,.xlsx"
        :before-upload="beforeUpload"
      >
        <Button :loading="parsing">选择 Excel 并解析</Button>
      </Upload>
      <span v-if="file" class="text-xs text-gray-600">{{ file?.name }}</span>
      <Button @click="handleDownloadTemplate">下载导入模板</Button>
      <Tag v-if="users.length > 0" color="blue">
        匹配成功 {{ users.length }} 人
      </Tag>
      <Tag
        v-if="users.length > 0"
        :color="assignedUsers.length === users.length ? 'green' : 'orange'"
      >
        已指定 {{ assignedUsers.length }}/{{ users.length }}
      </Tag>
      <Tag v-if="failRows.length > 0" color="red">
        解析失败 {{ failRows.length }} 行
      </Tag>
    </Space>

    <template v-if="users.length > 0">
      <Space class="mb-2" wrap>
        <Select
          v-model:value="batchClassId"
          :options="classSelectOptions"
          :loading="classLoading"
          show-search
          allow-clear
          option-filter-prop="label"
          placeholder="选择培训班后批量指定全部学员"
          style="width: 320px"
        />
        <Button @click="handleBatchAssign">批量指定</Button>
        <Button @click="handleClearAssign">清空指定</Button>
      </Space>

      <Table
        row-key="key"
        size="small"
        :columns="columns"
        :data-source="users"
        :loading="parsing"
        :pagination="namePagination"
        :scroll="{ y: 300 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <Popconfirm
              title="确认从本次导入名单中移除该学员？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleRemoveRow(record)"
            >
              <Button type="link" danger size="small">删除</Button>
            </Popconfirm>
          </template>
          <template v-if="column.key === 'classId'">
            <Select
              :value="record.classId"
              :options="classSelectOptions"
              :loading="classLoading"
              show-search
              allow-clear
              option-filter-prop="label"
              placeholder="请选择要加入的培训班"
              style="width: 100%"
              @change="(value: any) => handleRowClassChange(record, value)"
            />
          </template>
        </template>
      </Table>

      <div class="mt-3 text-right">
        <Space>
          <Button @click="modalApi.close()">关闭</Button>
          <Button
            type="primary"
            :loading="submitting"
            :disabled="assignedUsers.length === 0"
            @click="handleSubmit"
          >
            提交入班（{{ assignedUsers.length }} 人）
          </Button>
        </Space>
      </div>
    </template>

    <Alert
      v-else
      class="mt-2"
      type="info"
      show-icon
      message="请选择 Excel 文件，系统将自动解析学员名单后手动指定培训班"
    />

    <template v-if="failRows.length > 0">
      <div class="mt-4 mb-2 text-sm font-medium text-gray-700">
        解析失败明细（{{ failRows.length }} 行，这些行未匹配到系统账号，不会入班）
      </div>
      <Table
        row-key="key"
        size="small"
        :columns="failColumns"
        :data-source="failRows"
        :pagination="false"
      />
    </template>

    <template v-if="result">
      <div class="mt-4 mb-2 text-sm font-medium text-gray-700">入班结果</div>
      <Alert type="success" show-icon :message="resultSummary" />
      <Table
        class="mt-2"
        row-key="classId"
        size="small"
        :columns="resultColumns"
        :data-source="result.classResults ?? []"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'users'">
            <span class="text-xs text-gray-600">{{ userNames(record.users) }}</span>
          </template>
        </template>
      </Table>
    </template>
  </ModalWrap>
</template>
