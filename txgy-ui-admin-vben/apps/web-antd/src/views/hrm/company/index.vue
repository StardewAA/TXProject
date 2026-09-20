<script lang="ts" setup>
import type { HrmCompanyProfileApi } from '#/api/hrm/company';
import type { Rule } from 'ant-design-vue/es/form';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';

import {
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Switch,
  Tag,
  Textarea,
} from 'ant-design-vue';

import {
  deleteCompanyProfile,
  getCompanyProfileList,
  saveCompanyProfile,
} from '#/api/hrm/company';

defineOptions({ name: 'HrmCompanyProfile' });

const { hasAccessByCodes } = useAccess();
const loading = ref(false);
const list = ref<HrmCompanyProfileApi.CompanyProfile[]>([]);
const activeId = ref<number>();
const modalOpen = ref(false);
const saving = ref(false);
const formRef = ref();

const active = computed(() =>
  list.value.find((item) => item.id === activeId.value),
);

/** 工商信息字段（企查查式展示） */
const businessFields = computed(() => {
  const item = active.value;
  if (!item) return [];
  return [
    { label: '统一社会信用代码', value: item.creditCode },
    { label: '法定代表人', value: item.legalPerson },
    { label: '成立日期', value: item.establishDate },
    { label: '注册资本', value: item.registeredCapital },
    { label: '企业类型', value: item.companyType },
    {
      label: '参保人数',
      value: item.insuredCount == null ? undefined : `${item.insuredCount} 人`,
    },
    { label: '联系电话', value: item.phone },
    { label: '住所', value: item.address, span: 2 },
  ];
});

const formState = reactive<Record<string, any>>({});

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
};

async function getList() {
  loading.value = true;
  try {
    list.value = (await getCompanyProfileList()) ?? [];
    if (
      list.value.length > 0 &&
      !list.value.some((item) => item.id === activeId.value)
    ) {
      activeId.value =
        list.value.find((item) => item.defaultStatus)?.id ?? list.value[0]?.id;
    }
  } finally {
    loading.value = false;
  }
}

function resetForm(data?: HrmCompanyProfileApi.CompanyProfile) {
  Object.keys(formState).forEach((key) => delete formState[key]);
  Object.assign(formState, data ?? { defaultStatus: list.value.length === 0, sort: 0 });
}

function handleCreate() {
  resetForm();
  modalOpen.value = true;
}

function handleEdit(record?: HrmCompanyProfileApi.CompanyProfile) {
  if (!record) return;
  resetForm({ ...record });
  modalOpen.value = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    const id = await saveCompanyProfile({ ...formState });
    message.success('保存成功');
    modalOpen.value = false;
    await getList();
    if (typeof id === 'number') {
      activeId.value = id;
    }
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id?: number) {
  if (!id) return;
  await deleteCompanyProfile(id);
  message.success('删除成功');
  activeId.value = undefined;
  await getList();
}

onMounted(getList);
</script>

<template>
  <div class="p-4">
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[320px_1fr]">
      <!-- 左：用工主体列表 -->
      <Card :loading="loading" size="small" title="用工主体">
        <template #extra>
          <Button
            v-if="hasAccessByCodes(['hrm:employee:update'])"
            size="small"
            type="primary"
            @click="handleCreate"
          >
            新增
          </Button>
        </template>
        <Empty v-if="list.length === 0" description="暂无用工主体，请先新增" />
        <div v-else class="flex flex-col gap-2">
          <div
            v-for="item in list"
            :key="item.id"
            class="cursor-pointer rounded-md border p-3 transition hover:border-blue-400"
            :class="
              item.id === activeId
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-200'
            "
            @click="activeId = item.id"
          >
            <div class="flex items-center gap-2">
              <span class="truncate font-medium">{{ item.name }}</span>
              <Tag v-if="item.defaultStatus" color="blue">默认主体</Tag>
            </div>
            <div class="mt-1 truncate text-xs text-gray-500">
              {{ item.creditCode || '统一社会信用代码未填写' }}
            </div>
          </div>
        </div>
      </Card>

      <!-- 右：主体详情（企查查式） -->
      <Card :loading="loading" size="small">
        <Empty v-if="!active" description="请选择左侧用工主体" />
        <template v-else>
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2">
                <span class="text-xl font-semibold">{{ active.name }}</span>
                <Tag v-if="active.defaultStatus" color="blue">默认用工主体</Tag>
                <Tag :color="active.creditCode ? 'green' : 'orange'">
                  {{ active.creditCode ? '信息已登记' : '信息待补充' }}
                </Tag>
              </div>
              <div class="mt-1 text-xs text-gray-500">
                合同甲方信息将自动带入劳务合同模板
              </div>
            </div>
            <div
              v-if="hasAccessByCodes(['hrm:employee:update'])"
              class="flex gap-2"
            >
              <Button size="small" @click="handleEdit(active)">编辑</Button>
              <Popconfirm
                title="确认删除该用工主体吗？"
                @confirm="handleDelete(active?.id)"
              >
                <Button danger size="small">删除</Button>
              </Popconfirm>
            </div>
          </div>

          <div class="mt-4 border-t pt-4">
            <div class="mb-2 text-sm font-medium text-gray-700">工商信息</div>
            <div class="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
              <div
                v-for="field in businessFields"
                :key="field.label"
                class="flex text-sm"
                :class="field.span === 2 ? 'sm:col-span-2' : ''"
              >
                <span class="w-32 shrink-0 text-gray-500">{{ field.label }}</span>
                <span class="break-all">{{ field.value || '-' }}</span>
              </div>
            </div>
          </div>

          <div class="mt-4 border-t pt-4">
            <div class="mb-2 text-sm font-medium text-gray-700">
              银行与开票信息
            </div>
            <div class="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
              <div class="flex text-sm">
                <span class="w-32 shrink-0 text-gray-500">开户银行</span>
                <span class="break-all">{{ active.bankName || '-' }}</span>
              </div>
              <div class="flex text-sm">
                <span class="w-32 shrink-0 text-gray-500">银行账号</span>
                <span class="break-all">{{ active.bankAccount || '-' }}</span>
              </div>
            </div>
          </div>

          <div class="mt-4 border-t pt-4">
            <div class="mb-2 text-sm font-medium text-gray-700">商业保险</div>
            <div class="text-sm leading-6">
              {{ active.commercialInsurance || '-' }}
            </div>
          </div>

          <div class="mt-4 border-t pt-4">
            <div class="mb-2 text-sm font-medium text-gray-700">经营范围</div>
            <div class="text-sm leading-6 whitespace-pre-wrap">
              {{ active.businessScope || '-' }}
            </div>
          </div>

          <div v-if="active.remark" class="mt-4 border-t pt-4">
            <div class="mb-2 text-sm font-medium text-gray-700">备注</div>
            <div class="text-sm leading-6">{{ active.remark }}</div>
          </div>
        </template>
      </Card>
    </div>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="formState.id ? '编辑用工主体' : '新增用工主体'"
      width="820px"
      @ok="handleSubmit"
    >
      <Form
        ref="formRef"
        :label-col="{ span: 7 }"
        :model="formState"
        :rules="rules"
        class="pt-2"
      >
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <Form.Item class="md:col-span-2" label="公司名称" name="name">
            <Input
              v-model:value="formState.name"
              placeholder="如：秦川化工产业园发展有限公司"
            />
          </Form.Item>
          <Form.Item label="统一社会信用代码" name="creditCode">
            <Input v-model:value="formState.creditCode" />
          </Form.Item>
          <Form.Item label="法定代表人" name="legalPerson">
            <Input v-model:value="formState.legalPerson" />
          </Form.Item>
          <Form.Item label="成立日期" name="establishDate">
            <DatePicker
              v-model:value="formState.establishDate"
              class="w-full"
              value-format="YYYY-MM-DD"
            />
          </Form.Item>
          <Form.Item label="注册资本" name="registeredCapital">
            <Input
              v-model:value="formState.registeredCapital"
              placeholder="如：1000万元人民币"
            />
          </Form.Item>
          <Form.Item label="企业类型" name="companyType">
            <Input
              v-model:value="formState.companyType"
              placeholder="如：有限责任公司(自然人投资或控股)"
            />
          </Form.Item>
          <Form.Item label="参保人数" name="insuredCount">
            <InputNumber
              v-model:value="formState.insuredCount"
              :min="0"
              class="w-full"
            />
          </Form.Item>
          <Form.Item label="联系电话" name="phone">
            <Input v-model:value="formState.phone" />
          </Form.Item>
          <Form.Item label="住所" name="address">
            <Input v-model:value="formState.address" />
          </Form.Item>
          <Form.Item label="开户银行" name="bankName">
            <Input v-model:value="formState.bankName" />
          </Form.Item>
          <Form.Item label="银行账号" name="bankAccount">
            <Input v-model:value="formState.bankAccount" />
          </Form.Item>
          <Form.Item
            class="md:col-span-2"
            label="商业保险说明"
            name="commercialInsurance"
          >
            <Textarea
              v-model:value="formState.commercialInsurance"
              :rows="2"
              placeholder="如：雇主责任险 80 万元/人，团体意外险 30 万元/人"
            />
          </Form.Item>
          <Form.Item class="md:col-span-2" label="经营范围" name="businessScope">
            <Textarea
              v-model:value="formState.businessScope"
              :rows="3"
              placeholder="营业执照上的经营范围"
            />
          </Form.Item>
          <Form.Item label="默认主体" name="defaultStatus">
            <Switch v-model:checked="formState.defaultStatus" />
          </Form.Item>
          <Form.Item label="显示顺序" name="sort">
            <InputNumber v-model:value="formState.sort" class="w-full" />
          </Form.Item>
          <Form.Item class="md:col-span-2" label="备注" name="remark">
            <Input v-model:value="formState.remark" />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  </div>
</template>
