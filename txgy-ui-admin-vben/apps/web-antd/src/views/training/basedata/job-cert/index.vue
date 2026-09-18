<script lang="ts" setup>
import type { TrainingBaseDataApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createJobCert,
  deleteJobCert,
  getJobCertPage,
  getJobList,
  updateJobCert,
} from '../api';

defineOptions({ name: 'TrainingBaseDataJobCert' });

const loading = ref(false);
const list = ref<TrainingBaseDataApi.JobCert[]>([]);
const total = ref(0);
const jobList = ref<TrainingBaseDataApi.Job[]>([]);

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  jobCode: undefined as string | undefined,
  certName: '',
  isRequired: undefined as boolean | undefined,
});

async function load() {
  loading.value = true;
  try {
    const res = await getJobCertPage({
      ...query,
      jobCode: query.jobCode || undefined,
      certName: query.certName || undefined,
      isRequired: query.isRequired,
    });
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

async function loadJobs() {
  jobList.value = (await getJobList()) ?? [];
}

const jobOptions = () =>
  jobList.value.map((item) => ({
    label: `${item.code} ${item.name}`,
    value: item.code,
  }));

// ==================== 新增 / 编辑 ====================
const modalVisible = ref(false);
const modalTitle = ref('');
const saving = ref(false);
const form = ref<TrainingBaseDataApi.JobCert>({});

function openCreate() {
  modalTitle.value = '新增工种资格证';
  form.value = {
    jobCode: query.jobCode,
    certName: '',
    isRequired: true,
    validMonths: undefined,
    sort: 0,
    remark: '',
  };
  modalVisible.value = true;
}

function openEdit(row: TrainingBaseDataApi.JobCert) {
  modalTitle.value = '编辑工种资格证';
  form.value = { ...row };
  modalVisible.value = true;
}

async function handleSave() {
  if (!form.value.jobCode) {
    message.warning('请选择所属工种');
    return;
  }
  if (!form.value.certName?.trim()) {
    message.warning('请输入资格证名称');
    return;
  }
  saving.value = true;
  try {
    if (form.value.id) {
      await updateJobCert(form.value);
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      await createJobCert(form.value);
      message.success($t('ui.actionMessage.createSuccess'));
    }
    modalVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: TrainingBaseDataApi.JobCert) {
  await deleteJobCert(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  load();
}

onMounted(() => {
  load();
  loadJobs();
});
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Select
        v-model:value="query.jobCode"
        class="!w-56"
        :options="jobOptions()"
        allow-clear
        show-search
        option-filter-prop="label"
        placeholder="所属工种"
      />
      <Input
        v-model:value="query.certName"
        class="!w-48"
        allow-clear
        placeholder="资格证名称"
        @press-enter="load"
      />
      <Select
        v-model:value="query.isRequired"
        class="!w-36"
        :options="[
          { label: '必须持证上岗', value: true },
          { label: '非必须', value: false },
        ]"
        allow-clear
        placeholder="持证要求"
      />
      <Button type="primary" ghost @click="load">查询</Button>
      <Button type="primary" class="ml-auto" @click="openCreate">
        新增资格证
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
      :scroll="{ x: 1100 }"
      :columns="[
        { title: '工种编码', dataIndex: 'jobCode', key: 'jobCode', width: 110 },
        { title: '工种名称', dataIndex: 'jobName', key: 'jobName', width: 150 },
        { title: '资格证名称', dataIndex: 'certName', key: 'certName', width: 260 },
        { title: '持证上岗', dataIndex: 'isRequired', key: 'isRequired', width: 110 },
        { title: '有效期(月)', dataIndex: 'validMonths', key: 'validMonths', width: 110 },
        { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
        { title: '操作', key: 'actions', width: 140, fixed: 'right' },
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
        <template v-if="column.key === 'jobCode'">
          <Tag color="processing">{{ record.jobCode }}</Tag>
        </template>
        <template v-else-if="column.key === 'isRequired'">
          <Tag :color="record.isRequired ? 'red' : 'default'">
            {{ record.isRequired ? '必须' : '非必须' }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'validMonths'">
          {{ record.validMonths ?? '长期有效' }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
          <Popconfirm
            title="确认删除该资格证要求？"
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
      依据《云培训学院-各种资格证.xlsx》维护工种资格证要求：通过工种编码关联岗位工种，
      作为持证上岗校验与培训计划制定的依据。
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
          <div class="mb-1 text-sm text-gray-600">
            所属工种 <span class="text-red-500">*</span>
          </div>
          <Select
            v-model:value="form.jobCode"
            class="w-full"
            :options="jobOptions()"
            show-search
            option-filter-prop="label"
            placeholder="请选择工种"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            资格证名称 <span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="form.certName"
            :maxlength="100"
            placeholder="如 特种作业操作证（高处作业）"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">是否必须持证上岗</div>
          <Switch v-model:checked="form.isRequired" />
          <span class="ml-2 text-xs text-gray-400">必须持证方可上岗作业</span>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">有效期（月，留空表示长期有效）</div>
          <InputNumber
            v-model:value="form.validMonths"
            class="w-full"
            :min="0"
            :max="600"
            placeholder="如 36"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">排序</div>
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
