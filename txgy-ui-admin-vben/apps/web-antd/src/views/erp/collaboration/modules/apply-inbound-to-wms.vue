<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Select, Table } from 'ant-design-vue';

import { applyInboundToWms } from '#/api/erp/collaboration';
import type { ErpPurchaseInApi } from '#/api/erp/purchase/in';
import { getPurchaseInPage } from '#/api/erp/purchase/in';
import { getWarehouseSimpleList } from '#/api/erp/stock/warehouse';

const emit = defineEmits(['success']);

/** 已审批（status=20）的采购入库单列表 */
const purchaseInList = ref<ErpPurchaseInApi.PurchaseIn[]>([]);
const loading = ref(false);

/** 选中的采购入库单 */
const selectedRowKeys = ref<number[]>([]);
const selectedRow = ref<ErpPurchaseInApi.PurchaseIn | null>(null);

/** 目标 ERP 仓库（需配置 wmsWarehouseCode） */
const allWarehouses = ref<Array<{ id: number; name: string; wmsWarehouseCode?: string }>>([]);
const targetWarehouseId = ref<number>();

/** 提交中 */
const submitting = ref(false);

/** 仅配置了 WMS 仓库编码的仓库 */
const wmsWarehouses = computed(() =>
  allWarehouses.value.filter(
    (w) => w.wmsWarehouseCode && w.wmsWarehouseCode.trim(),
  ),
);

/** 已审批 + WMS 可推送状态 */
const tableColumns = [
  { title: '入库单号', dataIndex: 'no', width: 180 },
  { title: '入库时间', dataIndex: 'inTime', width: 160 },
  { title: '合计数量', dataIndex: 'totalCount', width: 100 },
  { title: '供应商', dataIndex: 'supplierId', width: 120 },
  { title: 'WMS 状态', dataIndex: 'wmsStatus', width: 130 },
];

const [Modal, modalApi] = useVbenModal({
  onConfirm() {
    handleSubmit();
  },
  onOpenChange(isOpen) {
    if (isOpen) {
      selectedRowKeys.value = [];
      selectedRow.value = null;
      targetWarehouseId.value = undefined;
      loadData();
    }
  },
});

async function loadData() {
  loading.value = true;
  try {
    // 1. 加载已审批的采购入库单
    const pageResp = await getPurchaseInPage({
      pageNo: 1,
      pageSize: 100,
      status: 20, // 已审批
    } as any);
    // 过滤：只显示可推送的（wmsStatus 为空、NOT_SUBMITTED 或 FAILED）
    purchaseInList.value = (pageResp?.list ?? []).filter((item) => {
      const s = item.wmsStatus;
      return !s || s === 'NOT_SUBMITTED' || s === 'FAILED';
    });

    // 2. 加载仓库列表
    const warehouses = (await getWarehouseSimpleList()) as any;
    allWarehouses.value = warehouses ?? [];
    if (wmsWarehouses.value.length === 0) {
      message.warning(
        '没有配置 WMS 仓库编码的仓库，无法推送入库。请先在仓库管理中设置。',
      );
    }
  } catch (e) {
    message.error('加载数据失败');
  } finally {
    loading.value = false;
  }
}

function handleRowSelectionChange(keys: number[], rows: ErpPurchaseInApi.PurchaseIn[]) {
  selectedRowKeys.value = keys;
  selectedRow.value = rows.length > 0 ? rows[0] : null;
}

async function handleSubmit() {
  if (!selectedRow.value) {
    message.error('请选择要推送的采购入库单');
    return;
  }
  if (!targetWarehouseId.value) {
    message.error('请选择目标 ERP 仓库');
    return;
  }
  submitting.value = true;
  modalApi.lock();
  try {
    const resp = await applyInboundToWms({
      purchaseInId: selectedRow.value.id!,
      targetErpWarehouseId: targetWarehouseId.value,
    });
    if (resp?.accepted) {
      message.success(
        `已提交，WMS 处理中（请求ID：${resp.requestId ?? '-'}）`,
      );
    } else {
      message.warning('WMS 未受理');
    }
    modalApi.close();
    emit('success');
  } finally {
    submitting.value = false;
    modalApi.unlock();
  }
}
</script>

<template>
  <Modal class="w-[720px]" title="推送入库到 WMS" confirm-text="推送">
    <div class="p-4">
      <div class="mb-3 text-sm">
        选择已审批的 ERP 采购入库单，一键推送到 WMS 走 BPM 审批入库
      </div>

      <Table
        :columns="tableColumns"
        :data-source="purchaseInList"
        :row-selection="{
          type: 'radio',
          selectedRowKeys,
          onChange: handleRowSelectionChange,
        }"
        :loading="loading"
        :pagination="false"
        bordered
        size="small"
        row-key="id"
        class="mb-3 max-h-[280px]"
        scroll-y
      />

      <div v-if="purchaseInList.length === 0 && !loading" class="py-4 text-center text-gray-400">
        暂无已审批且可推送的采购入库单
      </div>

      <div class="mb-1 text-sm">目标 ERP 仓库 *</div>
      <Select
        v-model:value="targetWarehouseId"
        :options="wmsWarehouses"
        :field-names="{ label: 'name', value: 'id' }"
        class="w-full"
        placeholder="请选择目标仓库（仅显示已配置 WMS 编码的仓库）"
        show-search
        option-filter-prop="label"
        :disabled="!selectedRow"
      />

      <div class="mt-3 text-xs text-gray-400">
        提示：同一采购入库单只能推送到一个 WMS 仓库；WMS 入库只接受 RAW_MATERIAL 物料。
      </div>
    </div>
  </Modal>
</template>
