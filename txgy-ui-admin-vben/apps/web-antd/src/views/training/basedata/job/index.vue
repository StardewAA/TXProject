<script lang="ts" setup>
import type { TrainingCourseApi } from '../../course/api';
import type { TrainingBaseDataApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import { getGrowthLevelList } from '../../course/api';
import {
  createJob,
  deleteJob,
  getJobDetail,
  getJobPage,
  updateJob,
} from '../api';

defineOptions({ name: 'TrainingBaseDataJob' });

/** 序列字典（依据《云培训学院-岗位工种表.xlsx》） */
const SEQ_OPTIONS = [
  { label: 'OP 操作序列', value: 'OP' },
  { label: 'TC 技术序列', value: 'TC' },
  { label: 'QC 质检检测序列', value: 'QC' },
  { label: 'MG 管理序列', value: 'MG' },
  { label: 'SP 支持序列', value: 'SP' },
];

const SEQ_NAME: Record<string, string> = {
  OP: '操作序列',
  TC: '技术序列',
  QC: '质检检测序列',
  MG: '管理序列',
  SP: '支持序列',
};

const loading = ref(false);
const list = ref<TrainingBaseDataApi.Job[]>([]);
const total = ref(0);
const levelList = ref<TrainingCourseApi.GrowthLevel[]>([]);

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  seqCode: undefined as string | undefined,
  maxLevel: undefined as string | undefined,
  status: undefined as string | undefined,
});

async function load() {
  loading.value = true;
  try {
    const res = await getJobPage({
      ...query,
      name: query.name || undefined,
      seqCode: query.seqCode || undefined,
      maxLevel: query.maxLevel || undefined,
      status: query.status || undefined,
    });
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

async function loadLevels() {
  levelList.value = (await getGrowthLevelList()) ?? [];
}

// ==================== 新增 / 编辑 ====================
const modalVisible = ref(false);
const modalTitle = ref('');
const saving = ref(false);
const form = ref<TrainingBaseDataApi.Job>({});

function resetForm() {
  form.value = {
    code: '',
    name: '',
    seqCode: 'OP',
    seqName: '操作序列',
    bizCategory: '',
    learnDomain: '',
    learnDomainCodes: '',
    keySkills: '',
    maxLevel: undefined,
    sort: 0,
    status: 'active',
    remark: '',
  };
}

function openCreate() {
  modalTitle.value = '新增岗位工种';
  resetForm();
  modalVisible.value = true;
}

function openEdit(row: TrainingBaseDataApi.Job) {
  modalTitle.value = '编辑岗位工种';
  form.value = { ...row };
  modalVisible.value = true;
}

function handleSeqChange(value: any) {
  form.value.seqName = SEQ_NAME[String(value)] ?? '';
}

async function handleSave() {
  if (!form.value.code?.trim()) {
    message.warning('请输入工种编码');
    return;
  }
  if (!form.value.name?.trim()) {
    message.warning('请输入工种名称');
    return;
  }
  if (!form.value.seqCode) {
    message.warning('请选择所属序列');
    return;
  }
  saving.value = true;
  try {
    if (form.value.id) {
      await updateJob(form.value);
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      await createJob(form.value);
      message.success($t('ui.actionMessage.createSuccess'));
    }
    modalVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: TrainingBaseDataApi.Job) {
  try {
    await deleteJob(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess'));
    load();
  } catch {
    // 关联引用校验失败时由全局拦截器提示
  }
}

// ==================== 工种全景（资格证 + 学习地图） ====================
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<TrainingBaseDataApi.JobDetail>({});

async function openDetail(row: TrainingBaseDataApi.Job) {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = {};
  try {
    detail.value = (await getJobDetail(row.code!)) ?? {};
  } finally {
    detailLoading.value = false;
  }
}

const domainTags = (codes?: string) =>
  (codes ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

onMounted(() => {
  load();
  loadLevels();
});
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Input
        v-model:value="query.name"
        class="!w-48"
        allow-clear
        placeholder="工种名称"
        @press-enter="load"
      />
      <Select
        v-model:value="query.seqCode"
        class="!w-44"
        :options="SEQ_OPTIONS"
        allow-clear
        placeholder="所属序列"
      />
      <Select
        v-model:value="query.maxLevel"
        class="!w-40"
        :options="
          levelList.map((item) => ({ label: item.name, value: item.code }))
        "
        allow-clear
        placeholder="最高成长等级"
      />
      <Select
        v-model:value="query.status"
        class="!w-32"
        :options="[
          { label: '启用', value: 'active' },
          { label: '停用', value: 'disabled' },
        ]"
        allow-clear
        placeholder="状态"
      />
      <Button type="primary" ghost @click="load">查询</Button>
      <Button
        type="primary"
        class="ml-auto"
        @click="openCreate"
      >
        新增工种
      </Button>
    </div>

    <Table
      row-key="id"
      :data-source="list"
      :loading="loading"
      :pagination="{
        current: query.pageNo,
        pageSize: query.pageSize,
        total: total,
        showSizeChanger: true,
        showTotal: (t: number) => `共 ${t} 条`,
      }"
      :scroll="{ x: 1500 }"
      :columns="[
        { title: '工种编码', dataIndex: 'code', key: 'code', width: 100 },
        { title: '工种名称', dataIndex: 'name', key: 'name', width: 150 },
        { title: '所属序列', dataIndex: 'seqName', key: 'seqName', width: 130 },
        { title: '业务类目', dataIndex: 'bizCategory', key: 'bizCategory', width: 110 },
        {
          title: '学习域/课程组',
          dataIndex: 'learnDomainCodes',
          key: 'learnDomainCodes',
          width: 190,
        },
        {
          title: '关键技能',
          dataIndex: 'keySkills',
          key: 'keySkills',
          width: 240,
          ellipsis: true,
        },
        {
          title: '最高成长等级',
          dataIndex: 'maxLevelName',
          key: 'maxLevelName',
          width: 120,
        },
        {
          title: '资格证',
          dataIndex: 'certCount',
          key: 'certCount',
          width: 80,
          align: 'center',
        },
        {
          title: '地图课程',
          dataIndex: 'mapCourseCount',
          key: 'mapCourseCount',
          width: 90,
          align: 'center',
        },
        { title: '操作', key: 'actions', width: 200, fixed: 'right' },
      ]"
      @change="
        (pager: any) => {
          query.pageNo = pager.current;
          query.pageSize = pager.pageSize;
          load();
        }
      "
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <Tag color="processing">{{ record.code }}</Tag>
        </template>
        <template v-else-if="column.key === 'seqName'">
          <Tag color="blue">{{ record.seqName }}</Tag>
        </template>
        <template v-else-if="column.key === 'learnDomainCodes'">
          <template v-if="record.learnDomainCodes === '*'">
            <Tag color="purple">全域</Tag>
          </template>
          <template v-else>
            <Tag
              v-for="item in domainTags(record.learnDomainCodes)"
              :key="item"
              color="cyan"
            >
              {{ item }}
            </Tag>
          </template>
        </template>
        <template v-else-if="column.key === 'maxLevelName'">
          <Tag v-if="record.maxLevelName" color="gold">
            {{ record.maxLevelName }}
          </Tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'certCount'">
          {{ record.certCount ?? 0 }}
        </template>
        <template v-else-if="column.key === 'mapCourseCount'">
          {{ record.mapCourseCount ?? 0 }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openDetail(record)">
            全景
          </Button>
          <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
          <Popconfirm
            title="确认删除该岗位工种？存在资格证或学习地图引用时不可删除。"
            ok-text="删除"
            cancel-text="取消"
            @confirm="handleDelete(record)"
          >
            <Button type="link" danger size="small">删除</Button>
          </Popconfirm>
        </template>
      </template>
    </Table>

    <div class="mt-2 text-xs text-gray-400">
      依据《数据字典与一级子模块关联》维护岗位工种字典：学习域/课程组关联课程分类（域-组-课三级），
      最高成长等级关联成长等级字典，资格证与学习地图通过工种编码建立关联。
    </div>

    <Modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="620"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSave"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            工种编码 <span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="form.code"
            :maxlength="20"
            placeholder="如 OP-02 / TC-01 / QC-01"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            工种名称 <span class="text-red-500">*</span>
          </div>
          <Input v-model:value="form.name" :maxlength="100" placeholder="如 防腐涂装工" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            所属序列 <span class="text-red-500">*</span>
          </div>
          <Select
            v-model:value="form.seqCode"
            class="w-full"
            :options="SEQ_OPTIONS"
            @change="handleSeqChange"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">业务类目</div>
          <Input
            v-model:value="form.bizCategory"
            :maxlength="200"
            placeholder="如 一/二/三/二十三"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">学习域/课程组（原始文本）</div>
          <Input
            v-model:value="form.learnDomain"
            :maxlength="200"
            placeholder="如 A,B01-B03 / 全域"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            学习域/课程组编码（关联课程分类，逗号分隔；* 表示全域）
          </div>
          <Input
            v-model:value="form.learnDomainCodes"
            :maxlength="500"
            placeholder="如 A,B01,B02,B03"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">关键技能</div>
          <Input.TextArea
            v-model:value="form.keySkills"
            :rows="2"
            :maxlength="500"
            placeholder="如 除锈等级判定、喷砂设备操作"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">最高成长等级（关联成长等级字典）</div>
          <Select
            v-model:value="form.maxLevel"
            class="w-full"
            :options="
              levelList.map((item) => ({ label: item.name, value: item.code }))
            "
            allow-clear
            placeholder="如 高级技师"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">排序</div>
          <InputNumber v-model:value="form.sort" class="w-full" :min="0" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">状态</div>
          <Select
            v-model:value="form.status"
            class="w-full"
            :options="[
              { label: '启用', value: 'active' },
              { label: '停用', value: 'disabled' },
            ]"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input.TextArea v-model:value="form.remark" :rows="2" :maxlength="500" />
        </div>
      </div>
    </Modal>

    <Drawer
      v-model:open="detailVisible"
      :title="`工种全景 - ${detail.job?.name ?? ''}`"
      :width="900"
    >
      <div v-if="detailLoading" class="py-10 text-center text-gray-400">
        加载中...
      </div>
      <div v-else class="flex flex-col gap-5">
        <Descriptions :column="2" size="small" bordered>
          <Descriptions.Item label="工种编码">
            {{ detail.job?.code }}
          </Descriptions.Item>
          <Descriptions.Item label="工种名称">
            {{ detail.job?.name }}
          </Descriptions.Item>
          <Descriptions.Item label="所属序列">
            {{ detail.job?.seqName }}
          </Descriptions.Item>
          <Descriptions.Item label="业务类目">
            {{ detail.job?.bizCategory || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="学习域/课程组">
            {{ detail.job?.learnDomain || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="最高成长等级">
            {{ detail.job?.maxLevelName || '-' }}
          </Descriptions.Item>
          <Descriptions.Item label="关键技能" :span="2">
            {{ detail.job?.keySkills || '-' }}
          </Descriptions.Item>
        </Descriptions>

        <div>
          <div class="mb-2 font-medium">
            资格证要求（{{ detail.certs?.length ?? 0 }}）
          </div>
          <Table
            row-key="id"
            size="small"
            :data-source="detail.certs ?? []"
            :pagination="false"
            :columns="[
              { title: '资格证名称', dataIndex: 'certName', key: 'certName' },
              { title: '持证上岗', dataIndex: 'isRequired', key: 'isRequired', width: 110 },
              { title: '有效期(月)', dataIndex: 'validMonths', key: 'validMonths', width: 110 },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'isRequired'">
                <Tag :color="record.isRequired ? 'red' : 'default'">
                  {{ record.isRequired ? '必须' : '非必须' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'validMonths'">
                {{ record.validMonths ?? '长期有效' }}
              </template>
            </template>
          </Table>
        </div>

        <div v-for="stage in detail.stages ?? []" :key="stage.stage">
          <div class="mb-2 font-medium">
            {{ stage.stageName }}
            <span class="ml-2 text-xs text-gray-400">
              共 {{ stage.courseCount ?? 0 }} 门课程
            </span>
          </div>
          <Table
            row-key="id"
            size="small"
            :data-source="stage.courses ?? []"
            :pagination="false"
            :columns="[
              { title: '课程编码', dataIndex: 'courseCode', key: 'courseCode', width: 110 },
              { title: '课程名称', dataIndex: 'courseName', key: 'courseName' },
              { title: '必修', dataIndex: 'isRequired', key: 'isRequired', width: 90 },
              { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'courseCode'">
                <Tag color="processing">{{ record.courseCode }}</Tag>
              </template>
              <template v-else-if="column.key === 'isRequired'">
                <Tag :color="record.isRequired ? 'red' : 'default'">
                  {{ record.isRequired ? '必修' : '选修' }}
                </Tag>
              </template>
            </template>
          </Table>
        </div>
      </div>
    </Drawer>
  </div>
</template>
