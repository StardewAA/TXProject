<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Input,
  Modal,
  Select,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import {
  auditExpertApplication,
  type CmsExpertApplication,
  listExpertApplications,
} from '#/api/cms/expert-consult';

defineOptions({ name: 'CmsExpertApplyAudit' });

const loading = ref(false);
const rows = ref<CmsExpertApplication[]>([]);
const auditStatus = ref<number | undefined>(undefined);
const rejectVisible = ref(false);
const rejectRemark = ref('');
const currentId = ref<number | undefined>(undefined);

const columns = [
  { title: '专家姓名', dataIndex: 'expertName', width: 120 },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  { title: '职称', dataIndex: 'professionalTitle', width: 120 },
  { title: '所属单位', dataIndex: 'company', width: 180 },
  { title: '服务领域', dataIndex: 'serviceFields', ellipsis: true },
  { title: '来源', dataIndex: 'applyChannel', width: 100 },
  { title: '等级', dataIndex: 'expertLevel', width: 110 },
  { title: '状态', dataIndex: 'auditStatus', width: 100 },
  { title: '申请时间', dataIndex: 'applyTime', width: 170 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await listExpertApplications(auditStatus.value);
  } finally {
    loading.value = false;
  }
}

async function audit(id: number, pass: boolean, remark?: string) {
  await auditExpertApplication({ id, pass, remark });
  message.success(pass ? '已通过' : '已驳回');
  await load();
}

function openReject(id: number) {
  currentId.value = id;
  rejectRemark.value = '';
  rejectVisible.value = true;
}

async function confirmReject() {
  if (currentId.value === undefined) {
    return;
  }
  await audit(currentId.value, false, rejectRemark.value);
  rejectVisible.value = false;
}

onMounted(load);
</script>

<template>
  <Page title="专家入驻审核">
    <Card>
      <div class="mb-4 flex items-center gap-3">
        <Select
          v-model:value="auditStatus"
          allow-clear
          placeholder="全部状态"
          style="width: 160px"
          @change="load"
        >
          <Select.Option :value="0">待审核</Select.Option>
          <Select.Option :value="1">已通过</Select.Option>
          <Select.Option :value="2">已驳回</Select.Option>
        </Select>
        <Button @click="load">刷新</Button>
      </div>

      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :scroll="{ x: 1400 }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'auditStatus'">
            <Tag v-if="record.auditStatus === 0" color="orange">待审核</Tag>
            <Tag v-else-if="record.auditStatus === 1" color="green">已通过</Tag>
            <Tag v-else color="red">已驳回</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'expertLevel'">
            <Tag v-if="record.expertLevel === 'INVITED'">特邀</Tag>
            <Tag v-else-if="record.expertLevel === 'CERTIFIED'" color="blue">
              专业认证
            </Tag>
            <Tag v-else>基础认证</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'applyChannel'">
            {{ record.applyChannel === 'SELF_APPLY' ? '自助申请' : '企业邀请' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Button size="small" type="link" @click="audit(record.id, true)">
              通过
            </Button>
            <Button danger size="small" type="link" @click="openReject(record.id)">
              驳回
            </Button>
          </template>
        </template>
      </Table>
    </Card>

    <Modal v-model:open="rejectVisible" title="驳回申请" @ok="confirmReject">
      <Input.TextArea
        v-model:value="rejectRemark"
        :rows="3"
        placeholder="请填写驳回理由（会展示给申请人）"
      />
    </Modal>
  </Page>
</template>
