<script lang="ts" setup>
import type { ErpSupplierApi } from '#/api/erp/purchase/supplier';

import { computed, reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  createSupplierRiskEvent,
  getSupplierCredit,
  replaceSupplierAdmissionCategories,
  replaceSupplierQualifications,
  updateSupplierCreditStatus,
  updateSupplierRiskEventStatus,
} from '#/api/erp/purchase/supplier';

const credit = ref<ErpSupplierApi.SupplierCredit>();
const supplier = ref<ErpSupplierApi.Supplier>();
const title = computed(() => `${supplier.value?.name || '供应商'}资信详情`);
const closingRiskEventId = ref<number>();
const savingAdmissionCategories = ref(false);
const savingQualifications = ref(false);
let rowKeySeed = 0;

const statusForm = reactive<ErpSupplierApi.SupplierCreditStatus>({
  supplierId: 0,
  snapshotVersion: 0,
  authStatus: 'NORMAL',
  qualificationStatus: 'VALID',
  riskLevel: 'LOW',
  purchaseLimitStatus: 'NONE',
  freezeReason: '',
});

const riskForm = reactive<ErpSupplierApi.SupplierRiskEventCreate>({
  supplierId: 0,
  eventType: 'MANUAL_WARNING',
  severity: 2,
  blocksPurchase: false,
  description: '',
});

const authStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待认证', value: 'PENDING' },
  { label: '认证通过', value: 'APPROVED' },
  { label: '驳回', value: 'REJECTED' },
  { label: '正常', value: 'NORMAL' },
  { label: '冻结', value: 'FROZEN' },
  { label: '注销', value: 'CANCELLED' },
];
const qualificationStatusOptions = [
  { label: '有效', value: 'VALID' },
  { label: '即将到期', value: 'EXPIRING_SOON' },
  { label: '已到期', value: 'EXPIRED' },
  { label: '已撤销', value: 'REVOKED' },
  { label: '待复核', value: 'PENDING_REVIEW' },
];
const riskLevelOptions = [
  { label: '低风险', value: 'LOW' },
  { label: '中风险', value: 'MEDIUM' },
  { label: '高风险', value: 'HIGH' },
  { label: '重大风险', value: 'CRITICAL' },
];
const purchaseLimitOptions = [
  { label: '无限制', value: 'NONE' },
  { label: '风险提醒', value: 'WARNING' },
  { label: '部分受限', value: 'LIMITED' },
  { label: '冻结', value: 'FROZEN' },
  { label: '黑名单', value: 'BLACKLISTED' },
];
const riskEventTypeOptions = [
  { label: '资质过期', value: 'QUALIFICATION_EXPIRED' },
  { label: '到货延期', value: 'DELIVERY_DELAY' },
  { label: '质检不合格', value: 'QUALITY_FAILURE' },
  { label: '重大退货', value: 'MAJOR_RETURN' },
  { label: '价格异常', value: 'PRICE_EXCEPTION' },
  { label: '结算争议', value: 'SETTLEMENT_DISPUTE' },
  { label: '合同违约', value: 'CONTRACT_BREACH' },
  { label: '人工风险提醒', value: 'MANUAL_WARNING' },
  { label: '其他', value: 'OTHER' },
];

function statusText(value?: string) {
  const map: Record<string, string> = {
    APPROVED: '认证通过',
    NORMAL: '正常',
    VALID: '有效',
    NONE: '无限制',
    LOW: '低风险',
    WARNING: '预警',
    LIMITED: '受限',
    FROZEN: '冻结',
    BLACKLISTED: '黑名单',
    EXPIRED: '已过期',
    EXPIRING_SOON: '即将到期',
    REVOKED: '已撤销',
    PENDING_REVIEW: '待复核',
    PENDING: '待确认',
    CONFIRMED: '已确认',
    PROCESSING: '处理中',
    CLOSED: '已关闭',
    MEDIUM: '中风险',
    HIGH: '高风险',
    CRITICAL: '重大风险',
  };
  return map[value || ''] || value || '-';
}

function createRowKey() {
  rowKeySeed += 1;
  return `credit-row-${rowKeySeed}`;
}

async function loadCredit() {
  if (!supplier.value?.id) return;
  credit.value = await getSupplierCredit(supplier.value.id);
  credit.value.qualifications = (credit.value.qualifications || []).map((item) => ({
    ...item,
    key: item.id ? `qualification-${item.id}` : createRowKey(),
  }));
  credit.value.admissionCategories = (credit.value.admissionCategories || []).map((item) => ({
    ...item,
    key: item.id ? `admission-${item.id}` : createRowKey(),
  }));
  Object.assign(statusForm, {
    supplierId: credit.value.supplierId,
    snapshotVersion: credit.value.snapshotVersion || 0,
    authStatus: credit.value.authStatus,
    qualificationStatus: credit.value.qualificationStatus,
    riskLevel: credit.value.riskLevel,
    purchaseLimitStatus: credit.value.purchaseLimitStatus,
    freezeReason: credit.value.freezeReason || '',
  });
  Object.assign(riskForm, {
    supplierId: credit.value.supplierId,
    eventType: 'MANUAL_WARNING',
    severity: 2,
    blocksPurchase: false,
    description: '',
  });
}

function addQualification() {
  if (!credit.value) return;
  credit.value.qualifications.push({
    key: createRowKey(),
    qualificationName: '',
    qualificationNo: '',
    validFrom: undefined,
    validTo: undefined,
    status: 'VALID',
    requiredFlag: false,
    remark: '',
  });
}

function removeQualification(index: number) {
  credit.value?.qualifications.splice(index, 1);
}

async function saveQualifications() {
  if (!credit.value) return;
  savingQualifications.value = true;
  try {
    await replaceSupplierQualifications({
      supplierId: credit.value.supplierId,
      items: credit.value.qualifications.map(({ id, key, ...item }) => item),
    });
    message.success('供应商资质已保存');
    await loadCredit();
  } finally {
    savingQualifications.value = false;
  }
}

function addAdmissionCategory() {
  if (!credit.value) return;
  credit.value.admissionCategories.push({
    key: createRowKey(),
    categoryId: undefined,
    categoryName: '',
    validFrom: undefined,
    validTo: undefined,
    status: 'VALID',
    remark: '',
  });
}

function removeAdmissionCategory(index: number) {
  credit.value?.admissionCategories.splice(index, 1);
}

async function saveAdmissionCategories() {
  if (!credit.value) return;
  savingAdmissionCategories.value = true;
  try {
    await replaceSupplierAdmissionCategories({
      supplierId: credit.value.supplierId,
      items: credit.value.admissionCategories.map(({ id, key, ...item }) => item),
    });
    message.success('供应商准入品类已保存');
    await loadCredit();
  } finally {
    savingAdmissionCategories.value = false;
  }
}

async function saveCreditStatus() {
  if (!credit.value) return;
  await updateSupplierCreditStatus({
    ...statusForm,
    snapshotVersion: (credit.value.snapshotVersion || 0) + 1,
  });
  message.success('资信状态已保存');
  await loadCredit();
}

async function handleCreateRiskEvent() {
  if (!credit.value || !riskForm.description?.trim()) {
    message.warning('请填写风险事件说明');
    return;
  }
  await createSupplierRiskEvent({
    ...riskForm,
    supplierId: credit.value.supplierId,
    description: riskForm.description.trim(),
  });
  message.success('风险事件已新增');
  await loadCredit();
}

async function handleCloseRiskEvent(event: ErpSupplierApi.SupplierRiskEvent) {
  if (!event.id || event.status === 'CLOSED') return;
  closingRiskEventId.value = event.id;
  try {
    await updateSupplierRiskEventStatus(
      event.id,
      'CLOSED',
      event.resolution || '已完成风险处置',
    );
    message.success('风险事件已关闭');
    await loadCredit();
  } finally {
    closingRiskEventId.value = undefined;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!credit.value) return;
    modalApi.lock();
    try {
      await saveCreditStatus();
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      credit.value = undefined;
      supplier.value = undefined;
      return;
    }
    supplier.value = modalApi.getData<ErpSupplierApi.Supplier>();
    if (!supplier.value?.id) return;
    modalApi.lock();
    try {
      await loadCredit();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="title" class="w-[1100px]">
    <div v-if="credit" class="p-4">
      <a-alert
        :message="`当前资信版本：${credit.snapshotVersion || 0}，最近同步：${credit.lastSyncTime || '暂无'}`"
        show-icon
        type="info"
      />
      <a-divider orientation="left">资信状态</a-divider>
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="认证状态">
              <a-select v-model:value="statusForm.authStatus" :options="authStatusOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="资质状态">
              <a-select
                v-model:value="statusForm.qualificationStatus"
                :options="qualificationStatusOptions"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="风险等级">
              <a-select v-model:value="statusForm.riskLevel" :options="riskLevelOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="采购限制">
              <a-select
                v-model:value="statusForm.purchaseLimitStatus"
                :options="purchaseLimitOptions"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="冻结/限制原因">
              <a-textarea
                v-model:value="statusForm.freezeReason"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                placeholder="请输入冻结或采购限制原因"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>

      <a-descriptions bordered :column="4" size="small">
        <a-descriptions-item label="认证状态">
          {{ statusText(statusForm.authStatus) }}
        </a-descriptions-item>
        <a-descriptions-item label="资质状态">
          {{ statusText(statusForm.qualificationStatus) }}
        </a-descriptions-item>
        <a-descriptions-item label="风险等级">
          {{ statusText(statusForm.riskLevel) }}
        </a-descriptions-item>
        <a-descriptions-item label="采购限制">
          {{ statusText(statusForm.purchaseLimitStatus) }}
        </a-descriptions-item>
        <a-descriptions-item label="冻结原因" :span="4">
          {{ statusForm.freezeReason || '-' }}
        </a-descriptions-item>
      </a-descriptions>

      <div class="mb-2 flex items-center justify-between">
        <a-divider class="mb-0" orientation="left">供应商资质</a-divider>
        <div class="flex gap-2">
          <a-button size="small" @click="addQualification">新增资质</a-button>
          <a-button
            :loading="savingQualifications"
            size="small"
            type="primary"
            @click="saveQualifications"
          >
            保存资质
          </a-button>
        </div>
      </div>
      <a-table
        :data-source="credit.qualifications"
        :pagination="false"
        size="small"
        row-key="key"
      >
        <a-table-column key="qualificationName" title="资质名称" :width="180">
          <template #default="{ record }">
            <a-input v-model:value="record.qualificationName" placeholder="请输入资质名称" />
          </template>
        </a-table-column>
        <a-table-column key="qualificationNo" title="编号" :width="150">
          <template #default="{ record }">
            <a-input v-model:value="record.qualificationNo" placeholder="请输入编号" />
          </template>
        </a-table-column>
        <a-table-column key="validFrom" title="有效期起" :width="165">
          <template #default="{ record }">
            <a-date-picker
              v-model:value="record.validFrom"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </template>
        </a-table-column>
        <a-table-column key="validTo" title="有效期至" :width="165">
          <template #default="{ record }">
            <a-date-picker
              v-model:value="record.validTo"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </template>
        </a-table-column>
        <a-table-column key="status" title="状态">
          <template #default="{ record }">
            <a-select v-model:value="record.status" :options="qualificationStatusOptions" />
          </template>
        </a-table-column>
        <a-table-column key="requiredFlag" title="必要资质">
          <template #default="{ record }">
            <a-switch v-model:checked="record.requiredFlag" />
          </template>
        </a-table-column>
        <a-table-column key="actions" title="操作" :width="70">
          <template #default="{ index }">
            <a-button danger size="small" type="link" @click="removeQualification(index)">
              删除
            </a-button>
          </template>
        </a-table-column>
      </a-table>

      <div class="mb-2 mt-4 flex items-center justify-between">
        <a-divider class="mb-0" orientation="left">准入品类</a-divider>
        <div class="flex gap-2">
          <a-button size="small" @click="addAdmissionCategory">新增品类</a-button>
          <a-button
            :loading="savingAdmissionCategories"
            size="small"
            type="primary"
            @click="saveAdmissionCategories"
          >
            保存品类
          </a-button>
        </div>
      </div>
      <a-table
        :data-source="credit.admissionCategories"
        :pagination="false"
        size="small"
        row-key="key"
      >
        <a-table-column key="categoryId" title="分类ID" :width="100">
          <template #default="{ record }">
            <a-input-number v-model:value="record.categoryId" :min="1" class="w-full" />
          </template>
        </a-table-column>
        <a-table-column key="categoryName" title="品类" :width="180">
          <template #default="{ record }">
            <a-input v-model:value="record.categoryName" placeholder="请输入品类名称" />
          </template>
        </a-table-column>
        <a-table-column key="validFrom" title="生效时间" :width="165">
          <template #default="{ record }">
            <a-date-picker
              v-model:value="record.validFrom"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </template>
        </a-table-column>
        <a-table-column key="validTo" title="失效时间" :width="165">
          <template #default="{ record }">
            <a-date-picker
              v-model:value="record.validTo"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </template>
        </a-table-column>
        <a-table-column key="status" title="状态">
          <template #default="{ record }">
            <a-select v-model:value="record.status" :options="qualificationStatusOptions" />
          </template>
        </a-table-column>
        <a-table-column key="actions" title="操作" :width="70">
          <template #default="{ index }">
            <a-button danger size="small" type="link" @click="removeAdmissionCategory(index)">
              删除
            </a-button>
          </template>
        </a-table-column>
      </a-table>

      <a-divider orientation="left">风险事件</a-divider>
      <a-form layout="vertical" class="mb-4">
        <a-row :gutter="12" align="bottom">
          <a-col :span="5">
            <a-form-item label="事件类型">
              <a-select v-model:value="riskForm.eventType" :options="riskEventTypeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="3">
            <a-form-item label="等级">
              <a-input-number v-model:value="riskForm.severity" :min="1" :max="5" class="w-full" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="禁止采购">
              <a-switch v-model:checked="riskForm.blocksPurchase" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="事件说明">
              <a-input v-model:value="riskForm.description" placeholder="请输入事件说明" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-button type="primary" block @click="handleCreateRiskEvent">新增事件</a-button>
          </a-col>
        </a-row>
      </a-form>
      <a-table
        :data-source="credit.riskEvents"
        :pagination="false"
        size="small"
        row-key="id"
      >
        <a-table-column key="eventType" title="事件类型" data-index="eventType" />
        <a-table-column key="status" title="状态">
          <template #default="{ record }">{{ statusText(record.status) }}</template>
        </a-table-column>
        <a-table-column key="severity" title="等级" data-index="severity" />
        <a-table-column key="blocksPurchase" title="禁止采购">
          <template #default="{ record }">{{ record.blocksPurchase ? '是' : '否' }}</template>
        </a-table-column>
        <a-table-column key="description" title="说明" data-index="description" />
        <a-table-column key="actions" title="操作" :width="90">
          <template #default="{ record }">
            <a-button
              v-if="record.status !== 'CLOSED'"
              :loading="closingRiskEventId === record.id"
              size="small"
              type="link"
              @click="handleCloseRiskEvent(record)"
            >
              关闭
            </a-button>
          </template>
        </a-table-column>
      </a-table>
    </div>
  </Modal>
</template>
