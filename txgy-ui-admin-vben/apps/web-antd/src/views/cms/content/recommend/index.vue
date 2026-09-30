<script lang="ts" setup>
import type { CmsRecommendSlot } from '#/api/cms/content-ext';

import {
  Button,
  Card,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';
import { onMounted, reactive, ref } from 'vue';

import { deleteRecommend, getRecommendPage, saveRecommend } from '#/api/cms/content-ext';

defineOptions({ name: 'CmsContentRecommend' });

const loading = ref(false);
const saving = ref(false);
const modalVisible = ref(false);
const list = ref<CmsRecommendSlot[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);

const positionOptions = [
  { label: '门户首页 Banner', value: 'HOME_BANNER' },
  { label: '门户侧边栏', value: 'PORTAL_SIDEBAR' },
  { label: '门户列表推荐', value: 'PORTAL_LIST' },
];

const columns = [
  { dataIndex: 'name', key: 'name', title: '推荐位名称' },
  { dataIndex: 'position', key: 'position', title: '展示位置', width: 160 },
  { dataIndex: 'title', key: 'title', title: '推荐内容标题' },
  { key: 'image', title: '推荐图', width: 90 },
  { dataIndex: 'sort', key: 'sort', title: '排序', width: 80 },
  { key: 'period', title: '投放时间', width: 220 },
  { key: 'status', title: '状态', width: 90 },
  { key: 'action', title: '操作', width: 140 },
];

const form = reactive<Partial<CmsRecommendSlot>>({});

async function load() {
  loading.value = true;
  try {
    const result = await getRecommendPage({ pageNo: pageNo.value, pageSize: pageSize.value });
    list.value = result.list ?? [];
    total.value = Number(result.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.keys(form).forEach((key) => delete (form as any)[key]);
  Object.assign(form, { sort: 0, status: 0 });
  modalVisible.value = true;
}

function openEdit(row: CmsRecommendSlot) {
  Object.assign(form, { ...row });
  modalVisible.value = true;
}

async function submit() {
  if (!form.name || !form.code || !form.position) {
    message.warning('请填写名称、编码与展示位置');
    return;
  }
  saving.value = true;
  try {
    await saveRecommend({ ...form });
    message.success('推荐位已保存');
    modalVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(id: number) {
  await deleteRecommend(id);
  message.success('推荐位已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <Card :bordered="false">
    <template #title>推荐位管理</template>
    <template #extra>
      <Space>
        <Button type="primary" @click="openCreate">新增推荐位</Button>
        <Button @click="load">刷新</Button>
      </Space>
    </template>
    <Table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="{ current: pageNo, pageSize, total, onChange: (p: number) => { pageNo = p; load(); } }"
      :row-key="(row: CmsRecommendSlot) => row.id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'position'">
          <Tag color="blue">{{ record.position }}</Tag>
        </template>
        <template v-else-if="column.key === 'image'">
          <img v-if="record.imageUrl" :src="record.imageUrl" alt="" class="slot-thumb" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'period'">
          {{ record.startTime || '不限' }} ~ {{ record.endTime || '不限' }}
        </template>
        <template v-else-if="column.key === 'status'">
          <Tag :color="record.status === 0 ? 'green' : 'default'">
            {{ record.status === 0 ? '上线' : '下线' }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <a @click="openEdit(record)">编辑</a>
            <Popconfirm title="确认删除该推荐位？" @confirm="remove(record.id)">
              <a class="danger">删除</a>
            </Popconfirm>
          </Space>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="modalVisible"
      :confirm-loading="saving"
      :title="form.id ? '编辑推荐位' : '新增推荐位'"
      @ok="submit"
    >
      <Form layout="vertical">
        <FormItem label="推荐位名称" required>
          <Input v-model:value="form.name" placeholder="如：首页头条" />
        </FormItem>
        <FormItem label="推荐位编码" required>
          <Input v-model:value="form.code" placeholder="唯一编码，如 home_top" />
        </FormItem>
        <FormItem label="展示位置" required>
          <Select v-model:value="form.position" :options="positionOptions" />
        </FormItem>
        <FormItem label="推荐内容标题">
          <Input v-model:value="form.title" />
        </FormItem>
        <FormItem label="推荐图片地址">
          <Input v-model:value="form.imageUrl" placeholder="/files/assets/... 图片地址" />
        </FormItem>
        <FormItem v-if="form.imageUrl" label="图片预览">
          <img :src="form.imageUrl" alt="" class="slot-preview" />
        </FormItem>
        <FormItem label="跳转地址">
          <Input v-model:value="form.targetUrl" placeholder="https:// 或站内路径" />
        </FormItem>
        <FormItem label="排序（越小越靠前）">
          <InputNumber v-model:value="form.sort" :min="0" style="width: 100%" />
        </FormItem>
        <FormItem label="投放开始">
          <DatePicker v-model:value="form.startTime" show-time style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </FormItem>
        <FormItem label="投放结束">
          <DatePicker v-model:value="form.endTime" show-time style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="form.status"
            :options="[
              { label: '上线', value: 0 },
              { label: '下线', value: 1 },
            ]"
          />
        </FormItem>
      </Form>
    </Modal>
  </Card>
</template>

<style scoped>
.slot-thumb {
  width: 68px;
  height: 40px;
  object-fit: cover;
  border-radius: 6px;
}

.slot-preview {
  width: 100%;
  max-height: 160px;
  object-fit: cover;
  border-radius: 8px;
}

.danger {
  color: #b03a2e;
}
</style>