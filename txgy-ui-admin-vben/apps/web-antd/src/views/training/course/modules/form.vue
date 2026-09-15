<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';
import type { TrainingBaseDataApi } from '../../basedata/api';
import type { PickedResource } from './resource-picker.vue';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  DatePicker,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
  Table,
  Tag,
  TreeSelect,
} from 'ant-design-vue';

import { $t } from '#/locales';

import { getJobList } from '../../basedata/api';
import {
  createCourse,
  getCourse,
  getCourseCategoryTree,
  getCourseMediaList,
  getExamOptions,
  updateCourse,
} from '../api';
import {
  COURSE_BIZ_TAG_OPTIONS,
  COURSE_LEVEL_OPTIONS,
  GROWTH_LEVEL_OPTIONS,
} from '../data';
import ResourcePicker from './resource-picker.vue';

defineOptions({ name: 'TrainingCourseForm' });

const emit = defineEmits(['success']);

/** 岗位工种/分类下拉选项 */
const jobOptions = ref<{ label: string; value: string }[]>([]);
const jobMap = ref<Map<string, TrainingBaseDataApi.Job>>(new Map());
const categoryTreeData = ref<any[]>([]);
const categoryNameMap = ref<Map<string, string>>(new Map());
const examOptions = ref<{ label: string; value: number }[]>([]);

// ========== 表单字段 ==========
const editingId = ref<number>();
const code = ref('');
const title = ref('');
const coverUrl = ref('');
const level = ref<number | undefined>();
const courseLevel = ref<string | undefined>();
/** 所属分类（课程分类树：域-组-课 三级） */
const categoryId = ref<number | undefined>();
const credit = ref<number | undefined>();
const durationMinutes = ref<number | undefined>();
/** 适用岗位/工种（多选，来源：基础数据 → 岗位工种） */
const applicablePosts = ref<string[]>([]);
/** 学习域/课程组编码（按适用岗位工种自动匹配回填） */
const learnDomainCodes = ref('');
const ownerFlag = ref('');
const lecturer = ref('');
const lecturerBrief = ref('');
/** 业务标签：岗前培训 / 复工强制 / 持证上岗 */
const bizTags = ref<string[]>([]);
const isRequired = ref(false);
const isComposite = ref(false);
const compositeTags = ref('');
const examId = ref<number | undefined>();
const examRange = ref<string[] | undefined>();
const sort = ref<number | undefined>();
const summary = ref('');
const saving = ref(false);

/** 资源所属：模块一文档 / 视频 */
const pickedDocs = ref<PickedResource[]>([]);
const pickedVideos = ref<PickedResource[]>([]);
/** 编辑时保留的非文档/视频媒资（图片、VR/3D），避免同步时被解绑 */
const extraMedia = ref<TrainingCourseApi.CourseMedia[]>([]);

const getTitle = computed(() =>
  editingId.value
    ? $t('ui.actionTitle.edit', ['课程'])
    : $t('ui.actionTitle.create', ['课程']),
);

const selectedJobNames = computed(() =>
  applicablePosts.value
    .map((name) => jobMap.value.get(name))
    .filter(Boolean) as TrainingBaseDataApi.Job[],
);

/** 学习域/课程组可读展示 */
const learnDomainText = computed(() => {
  if (!learnDomainCodes.value) {
    return '';
  }
  return learnDomainCodes.value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) =>
      item === '*'
        ? '全域'
        : `${categoryNameMap.value.get(item) || item}（${item}）`,
    )
    .join('、');
});

const docColumns = [
  { title: '文档名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '引用ID', dataIndex: 'mediaRefId', key: 'mediaRefId', width: 100 },
  { title: '操作', key: 'action', width: 80 },
];

const videoColumns = [
  { title: '视频名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '引用ID', dataIndex: 'mediaRefId', key: 'mediaRefId', width: 100 },
  { title: '时长(秒)', dataIndex: 'durationSeconds', key: 'durationSeconds', width: 100 },
  { title: '操作', key: 'action', width: 80 },
];

// ========== 选项加载 ==========
async function loadJobs() {
  const list = await getJobList();
  jobOptions.value = list.map((job) => ({
    label: `${job.name}（${job.code}）`,
    value: job.name!,
  }));
  jobMap.value = new Map(list.map((job) => [job.name!, job]));
}

function buildCategoryTree(nodes?: TrainingCourseApi.CourseCategory[]): any[] {
  if (!nodes?.length) {
    return [];
  }
  return nodes.map((node) => {
    const children = buildCategoryTree(node.children);
    return {
      title: node.code ? `${node.name}（${node.code}）` : node.name,
      value: node.id,
      code: node.code,
      key: node.id,
      children: children.length > 0 ? children : undefined,
    };
  });
}

function collectCategoryNames(
  nodes: TrainingCourseApi.CourseCategory[] | undefined,
  map: Map<string, string>,
) {
  nodes?.forEach((node) => {
    if (node.code) {
      map.set(node.code, node.name || node.code);
    }
    collectCategoryNames(node.children, map);
  });
}

async function loadCategories() {
  const tree = await getCourseCategoryTree();
  categoryTreeData.value = buildCategoryTree(tree);
  const map = new Map<string, string>();
  collectCategoryNames(tree, map);
  categoryNameMap.value = map;
}

async function loadExamOptions() {
  const list = await getExamOptions();
  examOptions.value = list.map((item: any) => ({
    label:
      item.status === 'published'
        ? item.name
        : `${item.name}（${item.status === 'draft' ? '草稿' : item.status}）`,
    value: item.id,
  }));
}

// ========== 岗位工种 → 学习域/课程组 联动 ==========
/**
 * 依据所选岗位工种在「岗位工种表」中配置的 learnDomainCodes 自动匹配学习域/课程组：
 * 取并集，任一岗位为全域（*）则结果为全域
 */
function syncLearnDomainFromJobs() {
  const codes = new Set<string>();
  let all = false;
  selectedJobNames.value.forEach((job) => {
    const raw = job.learnDomainCodes || '';
    if (!raw.trim()) {
      return;
    }
    raw
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .forEach((item) => {
        if (item === '*') {
          all = true;
        } else {
          codes.add(item);
        }
      });
  });
  learnDomainCodes.value = all ? '*' : [...codes].join(',');
}

function handlePostsChange() {
  syncLearnDomainFromJobs();
}

// ========== 资源所属：模块一文档/视频 ==========
const [ResourcePickerModal, resourcePickerApi] = useVbenModal({
  connectedComponent: ResourcePicker,
  destroyOnClose: true,
});

function openPicker(mediaType: number) {
  const isDoc = mediaType === 1;
  resourcePickerApi
    .setData({
      mediaType,
      selected: isDoc ? pickedDocs.value : pickedVideos.value,
      onConfirm: (list: PickedResource[]) => {
        if (isDoc) {
          pickedDocs.value = list;
        } else {
          pickedVideos.value = list;
        }
      },
    })
    .open();
}

function removePicked(mediaType: number, row: PickedResource) {
  if (mediaType === 1) {
    pickedDocs.value = pickedDocs.value.filter((item) => item.key !== row.key);
  } else {
    pickedVideos.value = pickedVideos.value.filter((item) => item.key !== row.key);
  }
}

// ========== 提交 ==========
function parseJsonArray(raw?: string): string[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
  } catch {
    return [];
  }
}

function buildMediaList(): TrainingCourseApi.CourseMedia[] {
  const list: TrainingCourseApi.CourseMedia[] = [];
  // 保留既有图片 / VR 媒资，避免保存时被解绑
  extraMedia.value.forEach((item) => list.push(item));
  [...pickedDocs.value, ...pickedVideos.value].forEach((item, index) => {
    list.push({
      mediaType: item.mediaType,
      name: item.name,
      url: item.url,
      mediaRefId: item.mediaRefId,
      sizeBytes: item.sizeBytes,
      durationSeconds: item.durationSeconds,
      sort: index,
    });
  });
  return list;
}

async function handleConfirm() {
  if (!title.value.trim()) {
    message.warning('课程名称不能为空');
    return;
  }
  if (examId.value && (!examRange.value || examRange.value.length !== 2)) {
    message.warning('已选择结课考试试卷，请同时设置结课考试时间');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingCourseApi.Course> & Record<string, any> = {
      id: editingId.value,
      code: code.value?.trim() || undefined,
      title: title.value.trim(),
      coverUrl: coverUrl.value?.trim() || undefined,
      level: level.value,
      courseLevel: courseLevel.value,
      categoryId: categoryId.value,
      credit: credit.value,
      durationMinutes: durationMinutes.value,
      applicablePosts: JSON.stringify(applicablePosts.value),
      learnDomainCodes: learnDomainCodes.value || undefined,
      ownerFlag: ownerFlag.value?.trim() || undefined,
      lecturer: lecturer.value?.trim() || undefined,
      lecturerBrief: lecturerBrief.value?.trim() || undefined,
      bizTags: JSON.stringify(bizTags.value),
      isRequired: isRequired.value,
      isComposite: isComposite.value,
      compositeTags: compositeTags.value?.trim() || undefined,
      examId: examId.value,
      examStartTime: examRange.value?.[0],
      examEndTime: examRange.value?.[1],
      sort: sort.value,
      summary: summary.value?.trim() || undefined,
      mediaList: buildMediaList(),
    };
    await (editingId.value ? updateCourse(payload) : createCourse(payload));
    message.success($t('ui.actionMessage.operationSuccess'));
    modalApi.close();
    emit('success');
  } finally {
    saving.value = false;
  }
}

function resetForm() {
  editingId.value = undefined;
  code.value = '';
  title.value = '';
  coverUrl.value = '';
  level.value = undefined;
  courseLevel.value = undefined;
  categoryId.value = undefined;
  credit.value = undefined;
  durationMinutes.value = undefined;
  applicablePosts.value = [];
  learnDomainCodes.value = '';
  ownerFlag.value = '';
  lecturer.value = '';
  lecturerBrief.value = '';
  bizTags.value = [];
  isRequired.value = false;
  isComposite.value = false;
  compositeTags.value = '';
  examId.value = undefined;
  examRange.value = undefined;
  sort.value = undefined;
  summary.value = '';
  pickedDocs.value = [];
  pickedVideos.value = [];
  extraMedia.value = [];
}

const [Modal, modalApi] = useVbenModal({
  onConfirm: handleConfirm,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      resetForm();
      return;
    }
    resetForm();
    // 选项数据与详情并行加载，减少等待
    await Promise.all([loadJobs(), loadCategories(), loadExamOptions()]);
    const data = modalApi.getData<TrainingCourseApi.Course>();
    if (!data || !data.id) {
      return;
    }
    editingId.value = data.id;
    const detail = await getCourse(data.id);
    code.value = detail.code || '';
    title.value = detail.title || '';
    coverUrl.value = detail.coverUrl || '';
    level.value = detail.level;
    courseLevel.value = detail.courseLevel;
    categoryId.value = detail.categoryId;
    credit.value = detail.credit;
    durationMinutes.value = detail.durationMinutes;
    applicablePosts.value = parseJsonArray(detail.applicablePosts);
    learnDomainCodes.value = detail.learnDomainCodes || '';
    ownerFlag.value = detail.ownerFlag || '';
    lecturer.value = detail.lecturer || '';
    lecturerBrief.value = detail.lecturerBrief || '';
    bizTags.value = parseJsonArray(detail.bizTags);
    isRequired.value = Boolean(detail.isRequired);
    isComposite.value = Boolean(detail.isComposite);
    compositeTags.value = detail.compositeTags || '';
    examId.value = detail.examId;
    if (detail.examStartTime && detail.examEndTime) {
      examRange.value = [detail.examStartTime, detail.examEndTime];
    }
    sort.value = detail.sort;
    summary.value = detail.summary || '';
    // 资源所属回显：文档/视频拆分展示，图片与 VR 媒资保留原样
    const mediaList = await getCourseMediaList(data.id);
    const docs: PickedResource[] = [];
    const videos: PickedResource[] = [];
    const extras: TrainingCourseApi.CourseMedia[] = [];
    mediaList.forEach((media) => {
      if (media.mediaType === 1) {
        docs.push({
          key: `1-${media.mediaRefId || media.url}`,
          mediaType: 1,
          name: media.name || '',
          url: media.url || '',
          mediaRefId: media.mediaRefId,
          sizeBytes: media.sizeBytes,
        });
      } else if (media.mediaType === 2) {
        videos.push({
          key: `2-${media.mediaRefId || media.url}`,
          mediaType: 2,
          name: media.name || '',
          url: media.url || '',
          mediaRefId: media.mediaRefId,
          sizeBytes: media.sizeBytes,
          durationSeconds: media.durationSeconds,
        });
      } else {
        extras.push(media);
      }
    });
    pickedDocs.value = docs;
    pickedVideos.value = videos;
    extraMedia.value = extras;
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-[840px]">
    <div class="mx-4 flex max-h-[65vh] flex-col gap-3 overflow-y-auto pr-2">
      <!-- 课程编码 -->
      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">课程编码</span>
        <Input v-model:value="code" class="flex-1" :maxlength="64" placeholder="选填，审核通过后自动锁定" />
      </div>

      <!-- 课程名称 -->
      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">
          课程名称 <span class="text-red-500">*</span>
        </span>
        <Input v-model:value="title" class="flex-1" :maxlength="200" placeholder="请输入课程名称" />
      </div>

      <!-- 封面 -->
      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">封面地址</span>
        <Input v-model:value="coverUrl" class="flex-1" placeholder="https://…（资源中心媒资地址）" />
      </div>

      <!-- 课程级别 -->
      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">课程级别</span>
        <Select
          v-model:value="level"
          class="flex-1"
          allow-clear
          :options="[...COURSE_LEVEL_OPTIONS]"
          placeholder="请选择课程级别（初级/中级/高级）"
        />
      </div>

      <!-- 所属分类（课程管理 → 分类管理，三级树） -->
      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">所属分类</span>
        <div class="flex-1">
          <TreeSelect
            v-model:value="categoryId"
            class="w-full"
            allow-clear
            tree-default-expand-all
            placeholder="请选择所属分类（分类管理中维护：域 / 课程组 / 课程，支持三级）"
            :tree-data="categoryTreeData"
          />
          <div class="mt-1 text-xs text-gray-400">
            分类来源：课程管理 → 分类管理；后端返回树形结构后前端递归展开渲染，一级/二级/三级均可选择。
          </div>
        </div>
      </div>

      <!-- 成长等级 -->
      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">成长等级</span>
        <Select
          v-model:value="courseLevel"
          class="flex-1"
          allow-clear
          :options="[...GROWTH_LEVEL_OPTIONS]"
          placeholder="请选择成长等级（六级成长体系）"
        />
      </div>

      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">课程学分</span>
        <InputNumber v-model:value="credit" class="flex-1" :min="0" :precision="2" placeholder="请输入学分" />
      </div>

      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">总时长(分钟)</span>
        <InputNumber v-model:value="durationMinutes" class="flex-1" :min="0" placeholder="请输入课程总时长" />
      </div>

      <!-- 适用岗位/工种 + 学习域/课程组联动 -->
      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">适用岗位/工种</span>
        <div class="flex-1">
          <Select
            v-model:value="applicablePosts"
            class="w-full"
            mode="multiple"
            allow-clear
            :options="jobOptions"
            :max-tag-count="6"
            placeholder="从岗位工种表中选择（可多选），自动匹配学习域/课程组"
            @change="handlePostsChange"
          />
        </div>
      </div>

      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">学习域/课程组</span>
        <div class="flex-1">
          <Input :value="learnDomainText" disabled placeholder="选择适用岗位/工种后自动匹配填充" />
          <div class="mt-1 text-xs text-gray-400">
            依据岗位工种表配置的学习域/课程组自动匹配（多岗位取并集，出现全域则记为全域）。
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">资源归属</span>
        <Input v-model:value="ownerFlag" class="flex-1" placeholder="如 DEPT_AI_FACTORY（资源中心 owner_flag 快照）" />
      </div>

      <!-- 资源所属：模块一文档/视频 -->
      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">资源所属</span>
        <div class="flex-1 flex flex-col gap-2">
          <div class="flex items-center gap-2">
            <Button size="small" type="primary" ghost @click="openPicker(1)">选择文档资源</Button>
            <Button size="small" type="primary" ghost @click="openPicker(2)">选择视频资源</Button>
            <span class="text-xs text-gray-400">
              对接模块一「资源中心」，支持绑定多个文档与多个视频
            </span>
          </div>
          <div v-if="pickedDocs.length > 0">
            <div class="mb-1 text-xs text-gray-500">已选文档（{{ pickedDocs.length }}）</div>
            <Table
              row-key="key"
              size="small"
              :columns="docColumns"
              :data-source="pickedDocs"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <Tag color="blue">文档</Tag>{{ record.name }}
                </template>
                <template v-if="column.key === 'action'">
                  <a class="text-red-500" @click="removePicked(1, record)">移除</a>
                </template>
              </template>
            </Table>
          </div>
          <div v-if="pickedVideos.length > 0">
            <div class="mb-1 text-xs text-gray-500">已选视频（{{ pickedVideos.length }}）</div>
            <Table
              row-key="key"
              size="small"
              :columns="videoColumns"
              :data-source="pickedVideos"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <Tag color="purple">视频</Tag>{{ record.name }}
                </template>
                <template v-if="column.key === 'action'">
                  <a class="text-red-500" @click="removePicked(2, record)">移除</a>
                </template>
              </template>
            </Table>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">主讲教师</span>
        <Input v-model:value="lecturer" class="flex-1" placeholder="请输入主讲教师" />
      </div>

      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">讲师简介</span>
        <Input.TextArea v-model:value="lecturerBrief" class="flex-1" :rows="2" placeholder="请输入讲师简介" />
      </div>

      <!-- 业务标签（三种） -->
      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">业务标签</span>
        <div class="flex-1">
          <Select
            v-model:value="bizTags"
            class="w-full"
            mode="multiple"
            allow-clear
            :options="[...COURSE_BIZ_TAG_OPTIONS]"
            placeholder="请选择业务标签（岗前培训 / 复工强制 / 持证上岗）"
          />
        </div>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">是否必修</span>
        <Switch v-model:checked="isRequired" />
        <span class="ml-8 text-right text-sm text-gray-600">复合课程</span>
        <Switch v-model:checked="isComposite" />
      </div>

      <div v-if="isComposite" class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">复合子模块标签</span>
        <Input v-model:value="compositeTags" class="flex-1" placeholder='JSON 数组，如 ["管道安装","防腐保温"]' />
      </div>

      <!-- 结课考试：对接模块三 -->
      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">结课考试</span>
        <div class="flex-1 flex flex-col gap-2">
          <Select
            v-model:value="examId"
            class="w-full"
            allow-clear
            :options="examOptions"
            placeholder="选择模块三「考试与认证」中的试卷（未发布试卷会标注草稿）"
          />
          <DatePicker.RangePicker
            v-model:value="examRange as any"
            class="w-full"
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="['结课考试开始时间', '结课考试结束时间']"
          />
          <div class="text-xs text-gray-400">
            选定试卷与考试时间后，本门课的结课考试会同步展现在模块三「考试与认证」的考试安排中。
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-32 text-right text-sm text-gray-600">排序</span>
        <InputNumber v-model:value="sort" class="flex-1" :min="0" placeholder="数字越小越靠前" />
      </div>

      <div class="flex items-start gap-2">
        <span class="w-32 pt-1 text-right text-sm text-gray-600">课程简介</span>
        <Input.TextArea v-model:value="summary" class="flex-1" :rows="3" placeholder="请输入课程简介" />
      </div>
    </div>

    <ResourcePickerModal />
  </Modal>
</template>
