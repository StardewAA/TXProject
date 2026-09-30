<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { HrmSalaryRateLimitApi } from '#/api/hrm/salary/config/rate-limit';

import { computed, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import {
  getSalaryRateLimitList,
  HrmSalaryRateLimitBizType,
  updateSalaryRateLimit,
} from '#/api/hrm/salary/config/rate-limit';

defineOptions({ name: 'HrmSalaryRateLimit' });

const { hasAccessByCodes } = useAccess();
const canEdit = () => hasAccessByCodes(['hrm:salary:rate-limit:update']);

const loading = ref(false);
const activeTab = ref(String(HrmSalaryRateLimitBizType.ATTENDANCE_RULE));
const list = ref<HrmSalaryRateLimitApi.RateLimit[]>([]);

const modalOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const formState = ref<HrmSalaryRateLimitApi.RateLimit>({});

const isAttendance = computed(
  () => formState.value.bizType === HrmSalaryRateLimitBizType.ATTENDANCE_RULE,
);

const rules: Record<string, Rule[]> = {
  rateLimit: [{ required: true, message: '比例上限不能为空', trigger: 'blur' }],
  corporateRateLimit: [
    { required: true, message: '公司缴纳比例上限不能为空', trigger: 'blur' },
  ],
  personalRateLimit: [
    { required: true, message: '个人缴纳比例上限不能为空', trigger: 'blur' },
  ],
};

function filterList(bizType: number) {
  return list.value.filter((item) => item.bizType === bizType);
}

async function getList() {
  loading.value = true;
  try {
    list.value = await getSalaryRateLimitList();
  } finally {
    loading.value = false;
  }
}

function openEdit(record: HrmSalaryRateLimitApi.RateLimit) {
  formState.value = { ...record };
  modalOpen.value = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    // 按业务类型只提交对应字段，避免把另一类字段写成 null
    const payload: HrmSalaryRateLimitApi.RateLimit = {
      id: formState.value.id,
      status: formState.value.status,
      remark: formState.value.remark,
    };
    if (isAttendance.value) {
      payload.rateLimit = formState.value.rateLimit;
    } else {
      payload.corporateRateLimit = formState.value.corporateRateLimit;
      payload.personalRateLimit = formState.value.personalRateLimit;
    }
    await updateSalaryRateLimit(payload);
    message.success('已保存，后续保存考勤计薪规则/参保方案将按新上限校验');
    modalOpen.value = false;
    await getList();
  } finally {
    saving.value = false;
  }
}

onMounted(getList);
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <Alert
        class="mb-3"
        message="比例上限按项配置：考勤计薪规则、参保方案保存时按此校验，超出时会明确提示是哪一项、填了多少、上限多少。停用某项表示该项不做上限校验。"
        show-icon
        type="info"
      />

      <Tabs v-model:active-key="activeTab">
        <Tabs.TabPane
          :key="String(HrmSalaryRateLimitBizType.ATTENDANCE_RULE)"
          tab="考勤计薪项"
        >
          <Table
            :columns="[
              { title: '计薪项', dataIndex: 'itemName', minWidth: 180 },
              { title: '编码', dataIndex: 'itemCode', width: 200 },
              { title: '比例上限(%)', dataIndex: 'rateLimit', width: 140 },
              { title: '状态', key: 'status', width: 100 },
              { title: '备注', dataIndex: 'remark' },
              { title: '操作', key: 'action', width: 100, fixed: 'right' },
            ]"
            :data-source="
              filterList(HrmSalaryRateLimitBizType.ATTENDANCE_RULE)
            "
            :loading="loading"
            :pagination="false"
            :row-key="(r: any) => String(r.id)"
            bordered
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <Tag :color="record.status === 0 ? 'success' : 'default'">
                  {{ record.status === 0 ? '启用' : '停用' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button
                  v-if="canEdit()"
                  type="link"
                  @click="openEdit(record)"
                >
                  编辑
                </Button>
                <span v-else>-</span>
              </template>
            </template>
          </Table>
        </Tabs.TabPane>

        <Tabs.TabPane
          :key="String(HrmSalaryRateLimitBizType.INSURANCE_SCHEME)"
          tab="参保项（社保/公积金）"
        >
          <Table
            :columns="[
              { title: '参保项', dataIndex: 'itemName', minWidth: 180 },
              { title: '类型编码', dataIndex: 'itemCode', width: 110 },
              {
                title: '公司缴纳比例上限(%)',
                dataIndex: 'corporateRateLimit',
                width: 180,
              },
              {
                title: '个人缴纳比例上限(%)',
                dataIndex: 'personalRateLimit',
                width: 180,
              },
              { title: '状态', key: 'status', width: 100 },
              { title: '备注', dataIndex: 'remark' },
              { title: '操作', key: 'action', width: 100, fixed: 'right' },
            ]"
            :data-source="
              filterList(HrmSalaryRateLimitBizType.INSURANCE_SCHEME)
            "
            :loading="loading"
            :pagination="false"
            :row-key="(r: any) => String(r.id)"
            :scroll="{ x: 1200 }"
            bordered
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <Tag :color="record.status === 0 ? 'success' : 'default'">
                  {{ record.status === 0 ? '启用' : '停用' }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button
                  v-if="canEdit()"
                  type="link"
                  @click="openEdit(record)"
                >
                  编辑
                </Button>
                <span v-else>-</span>
              </template>
            </template>
          </Table>
        </Tabs.TabPane>
      </Tabs>
    </Card>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="`比例上限：${formState.itemName || ''}`"
      width="520px"
      @ok="handleSubmit"
    >
      <Form
        ref="formRef"
        :label-col="{ span: 8 }"
        :model="formState"
        :rules="rules"
        class="mt-4"
      >
        <Form.Item label="业务类型">
          <span>
            {{
              isAttendance
                ? '考勤计薪规则'
                : '参保方案（社保/公积金）'
            }}
          </span>
        </Form.Item>
        <Form.Item v-if="isAttendance" label="比例上限(%)" name="rateLimit">
          <InputNumber
            v-model:value="formState.rateLimit as any"
            :min="0"
            :precision="2"
            addon-after="%"
            class="w-full"
          />
        </Form.Item>
        <template v-else>
          <Form.Item
            label="公司上限(%)"
            name="corporateRateLimit"
          >
            <InputNumber
              v-model:value="formState.corporateRateLimit as any"
              :min="0"
              :precision="2"
              addon-after="%"
              class="w-full"
            />
          </Form.Item>
          <Form.Item
            label="个人上限(%)"
            name="personalRateLimit"
          >
            <InputNumber
              v-model:value="formState.personalRateLimit as any"
              :min="0"
              :precision="2"
              addon-after="%"
              class="w-full"
            />
          </Form.Item>
        </template>
        <Form.Item label="状态" name="status">
          <Select
            v-model:value="formState.status"
            :options="[
              { label: '启用（按上限校验）', value: 0 },
              { label: '停用（不校验）', value: 1 },
            ]"
          />
        </Form.Item>
        <Form.Item label="备注" name="remark">
          <Input.TextArea
            v-model:value="formState.remark"
            :maxlength="255"
            :rows="2"
          />
        </Form.Item>
      </Form>
    </Modal>
  </Page>
</template>
