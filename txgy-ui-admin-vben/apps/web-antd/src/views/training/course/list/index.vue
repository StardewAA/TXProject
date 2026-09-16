<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingCourseApi } from '../api';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import {
  Descriptions,
  DescriptionsItem,
  Input,
  message,
  Modal,
  Spin,
  Table,
  Tag,
} from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import {
  approveCourse,
  deleteCourse,
  getCourse,
  getCourseCategoryTree,
  getCourseMediaList,
  getCoursePage,
  getCourseVersionList,
  getExamOptions,
  offlineCourse,
  onlineCourse,
  publishCourseVersion,
  rejectCourse,
  submitCourse,
  withdrawCourse,
} from '../api';
import {
  COURSE_LEVEL_TEXT,
  COURSE_MEDIA_TYPE_TEXT,
  COURSE_STATUS_OPTIONS,
  GROWTH_LEVEL_TEXT,
  useGridColumns,
  useGridFormSchema,
} from '../data';
import FormModalContent from '../modules/form.vue';
import MediaModalContent from '../modules/media.vue';

defineOptions({ name: 'TrainingCourse' });

const statusColorMap: Record<number, string> = {};
COURSE_STATUS_OPTIONS.forEach((item) => {
  statusColorMap[item.value] = item.color as string;
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getCoursePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            isCurrent: true,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<TrainingCourseApi.Course>,
});

// ========== 新增/编辑弹窗 ==========
const [FormModal, formModal] = useVbenModal({
  connectedComponent: FormModalContent,
  destroyOnClose: true,
});

function handleCreate() {
  formModal.setData(null).open();
}

function handleEdit(row: TrainingCourseApi.Course) {
  formModal.setData(row).open();
}

// ========== 媒资弹窗 ==========
const [MediaModal, mediaModal] = useVbenModal({
  connectedComponent: MediaModalContent,
  destroyOnClose: true,
});

function handleMedia(row: TrainingCourseApi.Course) {
  mediaModal.setData({ id: row.id }).open();
}

// ========== 删除 ==========
async function handleDelete(row: TrainingCourseApi.Course) {
  await deleteCourse(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleRefresh();
}

// ========== 审核状态机动作 ==========
async function handleSubmit(row: TrainingCourseApi.Course) {
  await submitCourse(row.id!);
  message.success('已提交审核');
  handleRefresh();
}

async function handleWithdraw(row: TrainingCourseApi.Course) {
  await withdrawCourse(row.id!);
  message.success('已撤审，课程回到草稿');
  handleRefresh();
}

async function handleApprove(row: TrainingCourseApi.Course) {
  await approveCourse(row.id!);
  message.success('已审核通过，可上架');
  handleRefresh();
}

async function handleOnline(row: TrainingCourseApi.Course) {
  await onlineCourse(row.id!);
  message.success('已上架');
  handleRefresh();
}

async function handleOffline(row: TrainingCourseApi.Course) {
  await offlineCourse(row.id!);
  message.success('已下架');
  handleRefresh();
}

async function handlePublishVersion(row: TrainingCourseApi.Course) {
  await publishCourseVersion(row.id!);
  message.success('新版本已生成（草稿状态，需重新审核上架）');
  handleRefresh();
}

// ========== 驳回弹窗 ==========
const rejectState = ref<{ visible: boolean; course: TrainingCourseApi.Course | null; reason: string }>({
  visible: false,
  course: null,
  reason: '',
});

function handleReject(row: TrainingCourseApi.Course) {
  rejectState.value = { visible: true, course: row, reason: '' };
}

async function confirmReject() {
  if (!rejectState.value.course) {
    return;
  }
  if (!rejectState.value.reason.trim()) {
    message.warning('驳回原因不能为空');
    return;
  }
  await rejectCourse(rejectState.value.course.id!, rejectState.value.reason.trim());
  message.success('已驳回，课程回到草稿');
  rejectState.value.visible = false;
  handleRefresh();
}

// ========== 版本历史弹窗 ==========
const versionState = ref<{
  visible: boolean;
  courseId?: number;
  title?: string;
  rows: TrainingCourseApi.Course[];
}>({ visible: false, rows: [] });

async function handleVersion(row: TrainingCourseApi.Course) {
  const rows = await getCourseVersionList(row.id!);
  versionState.value = { visible: true, courseId: row.id, title: row.title, rows };
}

const versionColumns = [
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '标题', dataIndex: 'title', key: 'title', minWidth: 200 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '当前生效', key: 'current', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
];

function versionStatusText(status?: number) {
  const found = COURSE_STATUS_OPTIONS.find((item) => item.value === status);
  return found ? found.label : String(status ?? '');
}

// ========== 课程详情弹窗（点击课程名称触发） ==========
const detailState = ref<{
  visible: boolean;
  loading: boolean;
  course: TrainingCourseApi.Course | null;
  mediaList: TrainingCourseApi.CourseMedia[];
  categoryPath: string;
  examName: string;
  examTimeText: string;
  postList: string[];
  bizTagList: string[];
}>({
  visible: false,
  loading: false,
  course: null,
  mediaList: [],
  categoryPath: '',
  examName: '',
  examTimeText: '',
  postList: [],
  bizTagList: [],
});

function parseArrayField(raw?: string): string[] {
  try {
    const parsed = JSON.parse(raw || '[]');
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
  } catch {
    return [];
  }
}

/** 在分类树中递归查找分类全路径（域 / 课程组 / 课程） */
function findCategoryPath(
  nodes: TrainingCourseApi.CourseCategory[] | undefined,
  id?: number,
  trail: string[] = [],
): string {
  if (!nodes || !id) {
    return '';
  }
  for (const node of nodes) {
    const next = [...trail, node.name || ''];
    if (node.id === id) {
      return next.filter(Boolean).join(' / ');
    }
    const found = findCategoryPath(node.children, id, next);
    if (found) {
      return found;
    }
  }
  return '';
}

async function handleShowDetail(row: TrainingCourseApi.Course) {
  detailState.value = {
    visible: true,
    loading: true,
    course: row,
    mediaList: [],
    categoryPath: '',
    examName: '',
    examTimeText: '',
    postList: [],
    bizTagList: [],
  };
  try {
    const [detail, mediaList, tree, examOptions] = await Promise.all([
      getCourse(row.id!),
      getCourseMediaList(row.id!).catch(() => []),
      getCourseCategoryTree().catch(() => []),
      getExamOptions().catch(() => []),
    ]);
    detailState.value.course = detail;
    detailState.value.mediaList = mediaList;
    detailState.value.categoryPath = findCategoryPath(tree, detail.categoryId);
    detailState.value.examName =
      examOptions.find((item) => item.id === detail.examId)?.name ||
      (detail.examId ? `试卷 #${detail.examId}` : '');
    detailState.value.examTimeText =
      detail.examStartTime && detail.examEndTime
        ? `${detail.examStartTime} ~ ${detail.examEndTime}`
        : '';
    detailState.value.postList = parseArrayField(detail.applicablePosts);
    detailState.value.bizTagList = parseArrayField(detail.bizTags);
  } finally {
    detailState.value.loading = false;
  }
}

const detailMediaColumns = [
  { title: '类型', dataIndex: 'mediaType', key: 'mediaType', width: 100 },
  { title: '名称', dataIndex: 'name', key: 'name', minWidth: 200 },
  { title: '引用ID', dataIndex: 'mediaRefId', key: 'mediaRefId', width: 100 },
  { title: '时长(秒)', dataIndex: 'durationSeconds', key: 'durationSeconds', width: 100 },
];

// ========== 操作按钮（按状态机分支） ==========
function buildActions(row: TrainingCourseApi.Course) {
  const actions: any[] = [];
  const status = row.status;
  if (status === 0) {
    // 草稿：编辑 / 提交审核 / 绑定媒资 / 删除
    actions.push(
      {
        label: '编辑',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        onClick: () => handleEdit(row),
      },
      {
        label: '提交审核',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: {
          title: '确认提交该课程审核？',
          confirm: () => handleSubmit(row),
        },
      },
      {
        label: '媒资',
        type: 'link',
        icon: ACTION_ICON.ADD,
        onClick: () => handleMedia(row),
      },
      {
        label: '删除',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: {
          title: '确认删除该课程？',
          confirm: () => handleDelete(row),
        },
      },
    );
  } else if (status === 1) {
    // 待审核：审核通过 / 驳回 / 撤审 / 版本
    actions.push(
      {
        label: '通过',
        type: 'link',
        icon: ACTION_ICON.EDIT,
        popConfirm: {
          title: '确认审核通过？',
          confirm: () => handleApprove(row),
        },
      },
      {
        label: '驳回',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        onClick: () => handleReject(row),
      },
      {
        label: '撤审',
        type: 'link',
        popConfirm: {
          title: '确认撤回审核？',
          confirm: () => handleWithdraw(row),
        },
      },
      {
        label: '版本',
        type: 'link',
        onClick: () => handleVersion(row),
      },
    );
  } else if (status === 2) {
    // 审核通过（待上架）：上架 / 发布新版本 / 版本
    actions.push(
      {
        label: '上架',
        type: 'link',
        icon: ACTION_ICON.VIEW,
        popConfirm: {
          title: '确认上架？上架后学员可学习',
          confirm: () => handleOnline(row),
        },
      },
      {
        label: '发布新版本',
        type: 'link',
        icon: ACTION_ICON.ADD,
        popConfirm: {
          title: '确认基于当前版本发布新版本？（新版本需重新审核）',
          confirm: () => handlePublishVersion(row),
        },
      },
      {
        label: '版本',
        type: 'link',
        onClick: () => handleVersion(row),
      },
    );
  } else if (status === 3) {
    // 已上架：下架 / 发布新版本 / 版本
    actions.push(
      {
        label: '下架',
        type: 'link',
        danger: true,
        popConfirm: {
          title: '确认下架？',
          confirm: () => handleOffline(row),
        },
      },
      {
        label: '发布新版本',
        type: 'link',
        icon: ACTION_ICON.ADD,
        popConfirm: {
          title: '确认发布新版本？（新版本需重新审核）',
          confirm: () => handlePublishVersion(row),
        },
      },
      {
        label: '版本',
        type: 'link',
        onClick: () => handleVersion(row),
      },
    );
  } else if (status === 4) {
    // 已下架：发布新版本 / 版本
    actions.push(
      {
        label: '发布新版本',
        type: 'link',
        icon: ACTION_ICON.ADD,
        popConfirm: {
          title: '确认发布新版本？（新版本需重新审核）',
          confirm: () => handlePublishVersion(row),
        },
      },
      {
        label: '版本',
        type: 'link',
        onClick: () => handleVersion(row),
      },
    );
  }
  return actions;
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <MediaModal @success="handleRefresh" />

    <!-- 驳回原因弹窗 -->
    <Modal
      v-model:open="rejectState.visible"
      title="驳回课程（原因将通知提交人）"
      :ok-button-props="{ disabled: !rejectState.reason.trim() }"
      @ok="confirmReject"
    >
      <Input.TextArea
        v-model:value="rejectState.reason"
        :rows="3"
        :maxlength="500"
        placeholder="请填写驳回原因（必填）"
      />
    </Modal>

    <!-- 版本历史弹窗 -->
    <Modal v-model:open="versionState.visible" title="版本历史" :footer="null" class="w-2/3">
      <Table
        row-key="id"
        size="small"
        :columns="versionColumns"
        :data-source="versionState.rows"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag :color="statusColorMap[record.status] || 'default'">
              {{ versionStatusText(record.status) }}
            </Tag>
          </template>
          <template v-if="column.key === 'current'">
            <Tag v-if="record.isCurrent" color="success">当前生效</Tag>
            <span v-else class="text-gray-400">历史</span>
          </template>
        </template>
      </Table>
    </Modal>

    <!-- 课程详情弹窗：点击课程名称弹出课程全部信息 -->
    <Modal
      v-model:open="detailState.visible"
      :title="`课程详情 - ${detailState.course?.title || ''}`"
      :footer="null"
      class="w-3/4"
    >
      <Spin :spinning="detailState.loading">
        <Descriptions bordered size="small" :column="2" class="mt-2">
          <DescriptionsItem label="课程名称">
            {{ detailState.course?.title || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="课程编码">
            {{ detailState.course?.code || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="课程级别">
            {{
              detailState.course?.level != null
                ? COURSE_LEVEL_TEXT[detailState.course.level] || '-'
                : '-'
            }}
          </DescriptionsItem>
          <DescriptionsItem label="成长等级">
            {{
              detailState.course?.courseLevel
                ? GROWTH_LEVEL_TEXT[detailState.course.courseLevel] ||
                  detailState.course.courseLevel
                : '-'
            }}
          </DescriptionsItem>
          <DescriptionsItem label="所属分类">
            {{ detailState.categoryPath || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="课程状态">
            {{
              COURSE_STATUS_OPTIONS.find(
                (item) => item.value === detailState.course?.status,
              )?.label || '-'
            }}
          </DescriptionsItem>
          <DescriptionsItem label="课程学分">
            {{ detailState.course?.credit ?? '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="总时长(分钟)">
            {{ detailState.course?.durationMinutes ?? '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="适用岗位/工种">
            <template v-if="detailState.postList.length > 0">
              <Tag
                v-for="post in detailState.postList"
                :key="post"
                color="blue"
                class="mb-1"
              >
                {{ post }}
              </Tag>
            </template>
            <span v-else class="text-gray-400">-</span>
          </DescriptionsItem>
          <DescriptionsItem label="学习域/课程组">
            {{ detailState.course?.learnDomainCodes || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="资源归属">
            {{ detailState.course?.ownerFlag || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="业务标签">
            <template v-if="detailState.bizTagList.length > 0">
              <Tag
                v-for="tag in detailState.bizTagList"
                :key="tag"
                color="gold"
                class="mb-1"
              >
                {{ tag }}
              </Tag>
            </template>
            <span v-else class="text-gray-400">-</span>
          </DescriptionsItem>
          <DescriptionsItem label="是否必修">
            <Tag v-if="detailState.course?.isRequired" color="orange">必修</Tag>
            <span v-else class="text-gray-400">选修</span>
          </DescriptionsItem>
          <DescriptionsItem label="复合课程">
            <Tag v-if="detailState.course?.isComposite" color="purple">复合</Tag>
            <span v-else class="text-gray-400">-</span>
          </DescriptionsItem>
          <DescriptionsItem label="复合子模块标签">
            {{ detailState.course?.compositeTags || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="结课考试试卷">
            {{ detailState.examName || '未设置' }}
          </DescriptionsItem>
          <DescriptionsItem label="结课考试时间" :span="2">
            {{ detailState.examTimeText || '未设置' }}
          </DescriptionsItem>
          <DescriptionsItem label="主讲教师">
            {{ detailState.course?.lecturer || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="排序">
            {{ detailState.course?.sort ?? '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="讲师简介" :span="2">
            {{ detailState.course?.lecturerBrief || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="课程简介" :span="2">
            {{ detailState.course?.summary || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="版本 / 当前生效" :span="2">
            版本 {{ detailState.course?.version ?? '-' }} /
            <Tag v-if="detailState.course?.isCurrent" color="success">
              当前生效
            </Tag>
            <span v-else class="text-gray-400">历史版本</span>
          </DescriptionsItem>
        </Descriptions>

        <div class="mt-4 mb-1 font-medium">资源所属（模块一：资源中心）</div>
        <Table
          v-if="detailState.mediaList.length > 0"
          row-key="id"
          size="small"
          :columns="detailMediaColumns"
          :data-source="detailState.mediaList"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'mediaType'">
              <Tag color="blue">
                {{ COURSE_MEDIA_TYPE_TEXT[record.mediaType] || record.mediaType }}
              </Tag>
            </template>
          </template>
        </Table>
        <div v-else class="text-sm text-gray-400">暂未绑定任何资源</div>
      </Spin>
    </Modal>

    <Grid table-title="课程管理（模块二：核心-难）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['课程']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleCreate,
            },
          ]"
        />
      </template>

      <template #title="{ row }">
        <a class="text-blue-600 hover:underline" @click="handleShowDetail(row)">
          {{ row.title }}
        </a>
      </template>

      <template #level="{ row }">
        <span>
          {{ row.level != null ? (COURSE_LEVEL_TEXT[row.level] || '-') : '-' }}
        </span>
      </template>
      <template #courseLevel="{ row }">
        <Tag v-if="row.courseLevel" color="processing">
          {{ GROWTH_LEVEL_TEXT[row.courseLevel] || row.courseLevel }}
        </Tag>
        <span v-else class="text-gray-400">-</span>
      </template>
      <template #isRequired="{ row }">
        <Tag v-if="row.isRequired" color="orange">必修</Tag>
        <span v-else class="text-gray-400">选修</span>
      </template>
      <template #isComposite="{ row }">
        <Tag v-if="row.isComposite" color="purple">复合</Tag>
        <span v-else class="text-gray-400">-</span>
      </template>
      <template #status="{ row }">
        <Tag
          :color="row.status != null ? (statusColorMap[row.status] || 'default') : 'default'"
        >
          {{ COURSE_STATUS_OPTIONS.find((item) => item.value === row.status)?.label }}
        </Tag>
      </template>
      <template #mediaType="{ row }">
        <span>
          {{ row.mediaType != null ? (COURSE_MEDIA_TYPE_TEXT[row.mediaType] ?? '-') : '-' }}
        </span>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>
  </Page>
</template>
