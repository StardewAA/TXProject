<script lang="ts" setup>
import type { ErpCollaborationApi } from '#/api/erp/collaboration';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Input, InputNumber, message, Select, Table, Textarea, DatePicker } from 'ant-design-vue';
import dayjs from 'dayjs';

import { confirmCollaboration, getCollaborationDetail } from '#/api/erp/collaboration';
import { getAccountSimpleList } from '#/api/erp/finance/account';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';

const emit = defineEmits(['success']);

const id = ref<number>();
/** 确认动作：ACCEPTED / REJECTED / NEED_SUPPLEMENT */
const action = ref<'ACCEPTED' | 'REJECTED' | 'NEED_SUPPLEMENT'>('ACCEPTED');
const detail = ref<ErpCollaborationApi.CollaborationDetail>();
const supplierId = ref<number>();
const accountId = ref<number>();
const orderTime = ref<string>();
const reason = ref('');
const items = ref<ErpCollaborationApi.ConfirmItem[]>([]);
const submitting = ref(false);

const accounts = ref<{ id: number; name: string }[]>([]);
const suppliers = ref<{ id: number; name: string }[]>([]);

const isAccept = computed(() => action.value === 'ACCEPTED');

const title = computed(() => {
  if (action.value === 'ACCEPTED') return '接受协同单并生成采购订单草稿';
  if (action.value === 'REJECTED') return '拒绝协同单';
  return '要求补充资料';
});

const [Modal, modalApi] = useVbenModal({
  onConfirm() {
    handleSubmit();
  },
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{
        id: number;
        action: 'ACCEPTED' | 'REJECTED' | 'NEED_SUPPLEMENT';
      }>();
      if (!data?.id) return;
      id.value = data.id;
      action.value = data.action;
      reason.value = '';
      orderTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss');
      loadDetail(data.id);
      loadOptions();
    }
  },
});

async function loadDetail(collabId: number) {
  detail.value = await getCollaborationDetail(collabId);
  // 预填确认明细：优先参考价/税率
  items.value = (detail.value?.items || []).map((item) => ({
    lineNo: item.lineNo,
    productPrice: item.referencePrice ?? 0,
    taxPercent: item.taxRate ?? 13,
  }));
}

async function loadOptions() {
  try {
    const [supplierList, accountList] = await Promise.all([
      getSupplierSimpleList(),
      getAccountSimpleList(),
    ]);
    suppliers.value = supplierList as any;
    accounts.value = accountList as any;
  } catch (error) {
    // 下拉数据加载失败不阻塞弹窗
  }
}

function updateItem(lineNo: number, field: 'productPrice' | 'taxPercent', value: any) {
  const item = items.value.find((temp) => temp.lineNo === lineNo);
  if (item) {
    (item as any)[field] = value;
  }
}

async function handleSubmit() {
  if (!id.value) return;
  if (!isAccept.value && !reason.value?.trim()) {
    message.error(action.value === 'REJECTED' ? '请填写拒绝原因' : '请填写需要补充的资料说明');
    return;
  }
  if (isAccept.value) {
    if (!supplierId.value) {
      message.error('请选择供应商');
      return;
    }
    if (!orderTime.value) {
      message.error('请选择采购时间');
      return;
    }
    if (items.value.some((item) => item.productPrice == null || item.productPrice < 0)) {
      message.error('请填写所有明细行的采购单价');
      return;
    }
  }
  submitting.value = true;
  modalApi.lock();
  try {
    await confirmCollaboration({
      id: id.value,
      action: action.value,
      reason: reason.value || undefined,
      supplierId: isAccept.value ? supplierId.value : undefined,
      accountId: isAccept.value ? accountId.value : undefined,
      orderTime: isAccept.value ? orderTime.value : undefined,
      items: isAccept.value ? items.value : undefined,
    });
    message.success('业务确认成功');
    modalApi.close();
    emit('success');
  } finally {
    submitting.value = false;
    modalApi.unlock();
  }
}

const itemColumns = [
  { title: '行号', dataIndex: 'lineNo', width: 60 },
  { title: '物料', dataIndex: 'materialNameSnapshot' },
  { title: '需求数量', dataIndex: 'shortageQuantity', width: 90 },
  {
    title: '采购单价',
    dataIndex: 'productPrice',
    width: 130,
  },
  {
    title: '税率(%)',
    dataIndex: 'taxPercent',
    width: 110,
  },
];
</script>

<template>
  <Modal class="w-[820px]" :title="title" confirm-text="提交确认">
    <div class="p-4">
      <div v-if="isAccept" class="mb-4 grid grid-cols-3 gap-3">
        <div>
          <div class="mb-1 text-sm">供应商 *</div>
          <Select
            v-model:value="supplierId"
            :options="suppliers"
            :field-names="{ label: 'name', value: 'id' }"
            class="w-full"
            placeholder="请选择供应商"
            show-search
            option-filter-prop="label"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">采购时间 *</div>
          <DatePicker
            v-model:value="orderTime"
            class="w-full"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择采购时间"
          />
        </div>
        <div>
          <div class="mb-1 text-sm">结算账户</div>
          <Select
            v-model:value="accountId"
            :options="accounts"
            :field-names="{ label: 'name', value: 'id' }"
            class="w-full"
            placeholder="请选择结算账户（可选）"
            allow-clear
          />
        </div>
      </div>

      <Table
        :columns="itemColumns"
        :data-source="detail?.items || []"
        :pagination="false"
        bordered
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'productPrice'">
            <InputNumber
              v-if="isAccept"
              class="w-full"
              :min="0"
              :precision="2"
              :value="
                items.find((item) => item.lineNo === record.lineNo)?.productPrice
              "
              @update:value="(val: any) => updateItem(record.lineNo, 'productPrice', val)"
            />
            <span v-else>{{ record.referencePrice ?? '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'taxPercent'">
            <InputNumber
              v-if="isAccept"
              class="w-full"
              :min="0"
              :max="100"
              :precision="2"
              :value="
                items.find((item) => item.lineNo === record.lineNo)?.taxPercent
              "
              @update:value="(val: any) => updateItem(record.lineNo, 'taxPercent', val)"
            />
            <span v-else>{{ record.taxRate ?? '-' }}</span>
          </template>
        </template>
      </Table>

      <div class="mt-4">
        <div class="mb-1 text-sm">
          {{ isAccept ? '备注' : action === 'REJECTED' ? '拒绝原因 *' : '需要补充的资料说明 *' }}
        </div>
        <Textarea
          v-model:value="reason"
          :rows="3"
          :placeholder="
            isAccept
              ? '请输入备注（可选）'
              : action === 'REJECTED'
                ? '请输入拒绝原因'
                : '请说明需要补充的资料'
          "
        />
      </div>
    </div>
  </Modal>
</template>
