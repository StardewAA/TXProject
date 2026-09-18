<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Descriptions,
  DescriptionsItem,
  Input,
  message,
  Select,
  Table,
  Textarea,
} from 'ant-design-vue';

import { applyInboundToWms } from '#/api/erp/collaboration';
import { getWarehouseSimpleList } from '#/api/erp/stock/warehouse';

const emit = defineEmits(['success']);

interface PurchaseInSummary {
  id?: number;
  no?: string;
  inTime?: any;
  totalCount?: number;
  items?: Array<{
    productName?: string;
    count?: number;
    productUnitName?: string;
  }>;
}

const purchaseIn = ref<PurchaseInSummary>({});
const targetWarehouseId = ref<number>();
const remark = ref('');
const submitting = ref(false);

/** 全部仓库列表 */
const allWarehouses = ref<
  Array<{
    id: number;
    name: string;
    wmsWarehouseCode?: string;
  }>
>([]);

/** 仅配置了 WMS 仓库编码的仓库 */
const wmsWarehouses = computed(() =>
  allWarehouses.value.filter(
    (w) => w.wmsWarehouseCode && w.wmsWarehouseCode.trim(),
  ),
);

const itemColumns = [
  { title: '产品', dataIndex: 'productName', width: 250 },
  { title: '数量', dataIndex: 'count', width: 120 },
  { title: '单位', dataIndex: 'productUnitName', width: 100 },
];

const [Modal, modalApi] = useVbenModal({
  onConfirm() {
    handleSubmit();
  },
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ purchaseIn: PurchaseInSummary }>();
      purchaseIn.value = data?.purchaseIn ?? {};
      targetWarehouseId.value = undefined;
      remark.value = '';
      loadOptions();
    }
  },
});

async function loadOptions() {
  try {
    const list = await getWarehouseSimpleList();
    allWarehouses.value = list as any;
    if (wmsWarehouses.value.length === 0) {
      message.warning(
        '没有配置 WMS 仓库编码的仓库，无法申请入库。请在仓库管理中设置 WMS 仓库编码。',
      );
    }
  } catch {
    // 下拉数据加载失败不阻塞弹窗
  }
}

async function handleSubmit() {
  if (!purchaseIn.value.id) {
    message.error('采购入库单 ID 缺失，请重新打开');
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
      purchaseInId: purchaseIn.value.id,
      targetErpWarehouseId: targetWarehouseId.value!,
      remark: remark.value?.trim() || undefined,
    });
    if (resp?.accepted) {
      message.success(
        `已提交，WMS 处理中（请求ID：${resp.requestId ?? '-'}）`,
      );
    } else {
      message.warning('WMS 未受理，请查看后端日志');
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
  <Modal class="w-[760px]" title="申请入库到 WMS" confirm-text="提交申请">
    <div class="p-4">
      <Descriptions :column="2" bordered size="small" class="mb-4">
        <DescriptionsItem label="入库单号">{{
          purchaseIn.no ?? '-'
        }}</DescriptionsItem>
        <DescriptionsItem label="合计数量">{{
          purchaseIn.totalCount ?? '-'
        }}</DescriptionsItem>
      </Descriptions>

      <div class="mb-2 text-sm font-medium">入库明细</div>
      <Table
        :columns="itemColumns"
        :data-source="purchaseIn.items ?? []"
        :pagination="false"
        bordered
        size="small"
        class="mb-4"
      />

      <div class="mb-4">
        <div class="mb-1 text-sm">
          目标 ERP 仓库 *（仅显示已配置 WMS 编码的仓库）
        </div>
        <Select
          v-model:value="targetWarehouseId"
          :options="wmsWarehouses"
          :field-names="{ label: 'name', value: 'id' }"
          class="w-full"
          placeholder="请选择目标仓库"
          show-search
          option-filter-prop="label"
        />
      </div>

      <div>
        <div class="mb-1 text-sm">备注（可选）</div>
        <Textarea
          v-model:value="remark"
          :rows="3"
          :maxlength="200"
          show-count
          placeholder="如有需要可填写申请说明"
        />
      </div>

      <div class="mt-3 text-xs text-gray-400">
        提示：WMS 入库只接受 RAW_MATERIAL 物料；同一申请只能包含一个仓库；目标仓库必须配置 WMS 仓库编码。
      </div>
    </div>
  </Modal>
</template>
