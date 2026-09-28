<script lang="ts" setup>
import type { TrainingCourseApi } from '../../course/api';
import type { TrainingBaseDataApi } from '../api';

import { computed, onMounted, ref, watch } from 'vue';

import {
  Button,
  Empty,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tabs,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import { getCourseCategoryList } from '../../course/api';
import {
  createLearningMap,
  deleteLearningMap,
  getJobList,
  getLearningMapStages,
  updateLearningMap,
} from '../api';

defineOptions({ name: 'TrainingBaseDataLearningMap' });

const STAGE_COLOR: Record<string, string> = {
  entry: 'blue',
  core: 'green',
  advanced: 'orange',
  technician: 'purple',
};

const loading = ref(false);
const jobList = ref<TrainingBaseDataApi.Job[]>([]);
const jobCode = ref<string | undefined>(undefined);
const stages = ref<TrainingBaseDataApi.LearningMapStage[]>([]);
const activeStage = ref('entry');
const courseList = ref<TrainingCourseApi.CourseCategory[]>([]);

const currentJob = computed(() =>
  jobList.value.find((item) => item.code === jobCode.value),
);

const totalCourses = computed(() =>
  stages.value.reduce((sum, item) => sum + (item.courseCount ?? 0), 0),
);

const requiredCourses = computed(() =>
  stages.value.reduce(
    (sum, item) =>
      sum + (item.courses ?? []).filter((c) => c.isRequired).length,
    0,
  ),
);

const jobOptions = () =>
  jobList.value.map((item) => ({
    label: `${item.code} ${item.name}（${item.seqName ?? ''}）`,
    value: item.code,
  }));

/** 课程级（L3）课程下拉，保证学习地图课程与课程分类的关联有效性 */
const courseOptions = computed(() =>
  courseList.value
    .filter((item) => item.level === 3)
    .map((item) => ({
      label: `${item.code} ${item.name}`,
      value: item.code,
    })),
);

async function loadJobs() {
  jobList.value = (await getJobList()) ?? [];
  if (!jobCode.value && jobList.value.length > 0) {
    jobCode.value = jobList.value[0]?.code;
  }
}

async function loadStages() {
  if (!jobCode.value) {
    stages.value = [];
    return;
  }
  loading.value = true;
  try {
    stages.value = (await getLearningMapStages(jobCode.value)) ?? [];
    if (stages.value.length > 0) {
      const first = stages.value.find((item) => (item.courseCount ?? 0) > 0);
      activeStage.value = first?.stage ?? stages.value[0]?.stage ?? 'entry';
    }
  } finally {
    loading.value = false;
  }
}

async function loadCourses() {
  courseList.value = (await getCourseCategoryList()) ?? [];
}

// ==================== 新增 / 编辑 ====================
const modalVisible = ref(false);
const modalTitle = ref('');
const saving = ref(false);
const form = ref<TrainingBaseDataApi.LearningMap>({});

function openCreate(stage: string) {
  const stageInfo = stages.value.find((item) => item.stage === stage);
  modalTitle.value = `新增学习地图课程 - ${stageInfo?.stageName ?? ''}`;
  form.value = {
    jobCode: jobCode.value,
    jobName: currentJob.value?.name,
    stage,
    courseCode: undefined,
    courseName: '',
    isRequired: true,
    sort: 0,
    remark: '',
  };
  modalVisible.value = true;
}

function openEdit(row: TrainingBaseDataApi.LearningMap) {
  modalTitle.value = '编辑学习地图课程';
  form.value = { ...row };
  modalVisible.value = true;
}

function handleCourseChange(value: any) {
  const course = courseList.value.find((item) => item.code === value);
  if (course) {
    form.value.courseName = course.name;
  }
}

async function handleSave() {
  if (!form.value.courseCode) {
    message.warning('请选择课程');
    return;
  }
  saving.value = true;
  try {
    if (form.value.id) {
      await updateLearningMap(form.value);
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      await createLearningMap(form.value);
      message.success($t('ui.actionMessage.createSuccess'));
    }
    modalVisible.value = false;
    loadStages();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: TrainingBaseDataApi.LearningMap) {
  await deleteLearningMap(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  loadStages();
}

watch(jobCode, () => {
  loadStages();
});

onMounted(async () => {
  await loadCourses();
  await loadJobs();
  await loadStages();
});
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Select
        v-model:value="jobCode"
        class="!w-96"
        :options="jobOptions()"
        show-search
        option-filter-prop="label"
        placeholder="选择工种查看学习地图"
      />
      <Tag v-if="currentJob" color="blue">{{ currentJob.seqName }}</Tag>
      <Tag v-if="currentJob?.maxLevelName" color="gold">
        最高等级：{{ currentJob.maxLevelName }}
      </Tag>
      <Tag v-if="currentJob?.learnDomainCodes" color="cyan">
        学习域：{{ currentJob.learnDomainCodes === '*' ? '全域' : currentJob.learnDomainCodes }}
      </Tag>
      <div class="ml-auto text-sm text-gray-500">
        共 {{ totalCourses }} 门课程，其中必修 {{ requiredCourses }} 门
      </div>
    </div>

    <Tabs v-model:activeKey="activeStage">
      <Tabs.TabPane
        v-for="stage in stages"
        :key="stage.stage!"
        :tab="`${stage.stageName}（${stage.courseCount ?? 0}）`"
      >
        <div class="mb-3 flex items-center gap-3">
          <Button type="primary" ghost @click="openCreate(stage.stage!)">
            新增课程
          </Button>
          <span class="text-xs text-gray-400">
            阶段编码：{{ stage.stage }} ｜ 阶段序号：{{ stage.stageNo }}
          </span>
        </div>

        <Table
          v-if="(stage.courses ?? []).length > 0"
          row-key="id"
          size="small"
          :data-source="stage.courses ?? []"
          :loading="loading"
          :pagination="false"
          :columns="[
            { title: '课程编码', dataIndex: 'courseCode', key: 'courseCode', width: 110 },
            { title: '课程名称', dataIndex: 'courseName', key: 'courseName' },
            { title: '必修', dataIndex: 'isRequired', key: 'isRequired', width: 100 },
            { title: '阶段内排序', dataIndex: 'sort', key: 'sort', width: 110 },
            { title: '操作', key: 'actions', width: 140 },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'courseCode'">
              <Tag :color="STAGE_COLOR[stage.stage!] ?? 'default'">
                {{ record.courseCode }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'isRequired'">
              <Tag :color="record.isRequired ? 'red' : 'default'">
                {{ record.isRequired ? '必修' : '选修' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button type="link" size="small" @click="openEdit(record)">
                编辑
              </Button>
              <Popconfirm
                title="确认从学习地图中移除该课程？"
                ok-text="移除"
                cancel-text="取消"
                @confirm="handleDelete(record)"
              >
                <Button type="link" danger size="small">移除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>

        <Empty v-else description="该阶段暂无课程，请点击【新增课程】维护" />
      </Tabs.TabPane>
    </Tabs>

    <div class="mt-3 text-xs text-gray-400">
      依据《云培训学院-学习地图示例.xlsx》与《数据字典与一级子模块关联》维护工种学习地图：
      按 入门段(L1→L2) → 核心段(L2→L3) → 进阶段(L3→L4) → 技师段(L4→L5) 四阶段编排课程，
      课程编码关联课程分类中的课程级（L3），工种关联岗位工种字典。
    </div>

    <Modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="560"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSave"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">所属工种</div>
          <Input :value="`${form.jobCode ?? ''} ${form.jobName ?? ''}`" disabled />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">成长阶段</div>
          <Input :value="form.stageName ?? form.stage ?? ''" disabled />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            课程（关联课程分类课程级 L3） <span class="text-red-500">*</span>
          </div>
          <Select
            v-model:value="form.courseCode"
            class="w-full"
            :options="courseOptions"
            show-search
            option-filter-prop="label"
            placeholder="请选择课程"
            @change="handleCourseChange"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">课程名称（默认取课程分类名称）</div>
          <Input v-model:value="form.courseName" :maxlength="200" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">是否必修</div>
          <Switch v-model:checked="form.isRequired" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">阶段内排序</div>
          <InputNumber v-model:value="form.sort" class="w-full" :min="0" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input.TextArea v-model:value="form.remark" :rows="2" :maxlength="500" />
        </div>
      </div>
    </Modal>
  </div>
</template>
