<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Switch,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createGrowthLevel,
  deleteGrowthLevel,
  getGrowthLevelPage,
  updateGrowthLevel,
} from '../api';

defineOptions({ name: 'TrainingCourseGrowthLevel' });

const loading = ref(false);
const list = ref<TrainingCourseApi.GrowthLevel[]>([]);
const total = ref(0);
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
});

async function load() {
  loading.value = true;
  try {
    const res = await getGrowthLevelPage({
      ...query,
      name: query.name || undefined,
    });
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

const modalVisible = ref(false);
const modalTitle = ref('');
const form = ref<TrainingCourseApi.GrowthLevel>({
  sort: 0,
  levelNo: undefined,
  maxApprentice: 0,
  needReview: false,
});
const saving = ref(false);

function openCreate() {
  modalTitle.value = '新增成长等级';
  form.value = {
    sort: 0,
    levelNo: undefined,
    maxApprentice: 0,
    needReview: false,
  };
  modalVisible.value = true;
}

function openEdit(row: TrainingCourseApi.GrowthLevel) {
  modalTitle.value = '编辑成长等级';
  form.value = { ...row };
  modalVisible.value = true;
}

async function handleSave() {
  if (!form.value.code?.trim()) {
    message.warning('请输入等级编码');
    return;
  }
  if (!form.value.levelNo) {
    message.warning('请输入等级序号');
    return;
  }
  if (!form.value.name?.trim()) {
    message.warning('请输入等级名称');
    return;
  }
  saving.value = true;
  try {
    if (form.value.id) {
      await updateGrowthLevel(form.value);
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      await createGrowthLevel(form.value);
      message.success($t('ui.actionMessage.createSuccess'));
    }
    modalVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: TrainingCourseApi.GrowthLevel) {
  await deleteGrowthLevel(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  load();
}

onMounted(load);
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Input
        v-model:value="query.name"
        class="!w-56"
        allow-clear
        placeholder="等级名称"
        @press-enter="load"
      />
      <Button type="primary" ghost @click="load">查询</Button>
      <Button type="primary" class="ml-auto" @click="openCreate">
        新增等级
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
        showTotal: (total: number) => `共 ${total} 条`,
      }"
      :scroll="{ x: 1200 }"
      :columns="[
        { title: '等级序号', dataIndex: 'levelNo', key: 'levelNo', width: 90 },
        { title: '等级名称', dataIndex: 'name', key: 'name', width: 110 },
        { title: '等级编码', dataIndex: 'code', key: 'code', width: 190 },
        { title: '定位', dataIndex: 'positionDesc', key: 'positionDesc', width: 180 },
        {
          title: '独立作业权限',
          dataIndex: 'workPermission',
          key: 'workPermission',
          width: 170,
        },
        {
          title: '带徒上限',
          dataIndex: 'maxApprentice',
          key: 'maxApprentice',
          width: 100,
          align: 'center',
        },
        {
          title: '人工评审',
          dataIndex: 'needReview',
          key: 'needReview',
          width: 100,
          align: 'center',
        },
        { title: '参考对标', dataIndex: 'reference', key: 'reference', width: 120 },
        {
          title: '备注',
          dataIndex: 'remark',
          key: 'remark',
          ellipsis: true,
          width: 180,
        },
        { title: '操作', key: 'actions', width: 130, fixed: 'right' },
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
        <template v-else-if="column.key === 'maxApprentice'">
          {{ record.maxApprentice ?? 0 }} 人
        </template>
        <template v-else-if="column.key === 'needReview'">
          <Tag v-if="record.needReview" color="gold">需评审</Tag>
          <Tag v-else color="default">免评审</Tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
          <Popconfirm
            title="确认删除该成长等级？"
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
      依据方案《成长等级表》六级人才成长体系维护：1 学徒（全程监护）、2 初级工（限简单工序）、3
      中级工（独立主体工序）、4 高级工（带徒 2 人）、5 技师（带徒 5 人）、6
      高级技师（需人工评审）。已被课程成长等级标记引用的等级禁止删除。
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
            等级编码 <span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="form.code"
            :maxlength="20"
            placeholder="如 APPRENTICE / TECHNICIAN / SENIOR_TECHNICIAN"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            等级序号 <span class="text-red-500">*</span>
          </div>
          <InputNumber
            v-model:value="form.levelNo"
            class="w-full"
            :min="1"
            :max="9"
            placeholder="1=学徒 2=初级工 3=中级工 4=高级工 5=技师 6=高级技师"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            等级名称 <span class="text-red-500">*</span>
          </div>
          <Input v-model:value="form.name" :maxlength="50" placeholder="如 学徒 / 高级技师" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">定位</div>
          <Input
            v-model:value="form.positionDesc"
            :maxlength="255"
            placeholder="如 入门，跟随作业 / 工艺优化、班组技术负责"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">独立作业权限</div>
          <Input
            v-model:value="form.workPermission"
            :maxlength="255"
            placeholder="如 否（全程监护）/ 是（含复杂工况）/ 是+技术决策"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">带徒上限（人）</div>
          <InputNumber v-model:value="form.maxApprentice" class="w-full" :min="0" :max="999" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">人工评审</div>
          <Switch v-model:checked="form.needReview" />
          <span class="ml-2 text-xs text-gray-400">高级技师晋级需人工评审</span>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">参考对标</div>
          <Input
            v-model:value="form.reference"
            :maxlength="100"
            placeholder="如 初级工前 / 五级·初级 / 一级·高级技师"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">排序（小值靠前）</div>
          <InputNumber v-model:value="form.sort" class="w-full" :min="0" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input.TextArea
            v-model:value="form.remark"
            :rows="2"
            :maxlength="500"
            placeholder="备注"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>
