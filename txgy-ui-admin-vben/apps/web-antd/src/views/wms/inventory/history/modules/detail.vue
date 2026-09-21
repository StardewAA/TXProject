<script lang="ts" setup>
import type { WmsInventoryHistoryApi } from '#/api/wms/inventory/history';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

import { useDescription } from '#/components/description';
import { formatPrice, formatQuantity } from '#/views/wms/utils/format';

defineOptions({ name: 'WmsInventoryHistoryDetail' });

const detailData = ref<WmsInventoryHistoryApi.InventoryHistory>({});

const [Descriptions] = useDescription({
  bordered: true,
  column: 2,
  useCard: false,
  schema: [
    { field: 'orderNo', label: '单据号', render: (val) => val || '-' },
    {
      field: 'orderType',
      label: '单据类型',
      render: (val) => getDictLabel(DICT_TYPE.WMS_ORDER_TYPE, val) || '-',
    },
    { field: 'itemName', label: '商品名称', render: (val) => val || '-' },
    { field: 'itemCode', label: '商品编号', render: (val) => val || '-' },
    { field: 'skuName', label: '规格名称', render: (val) => val || '-' },
    { field: 'skuCode', label: '规格编号', render: (val) => val || '-' },
    { field: 'warehouseName', label: '仓库', render: (val) => val || '-' },
    { field: 'unit', label: '计量单位', render: (val) => val || '-' },
    {
      field: 'beforeQuantity',
      label: '操作前',
      render: (val) => formatQuantity(val) || '-',
    },
    {
      field: 'afterQuantity',
      label: '操作后',
      render: (val) => formatQuantity(val) || '-',
    },
    {
      field: 'quantity',
      label: '变化数量',
      render: (val) => formatQuantity(val) || '-',
    },
    {
      field: 'price',
      label: '单价(元)',
      render: (val) => formatPrice(val) || '-',
    },
    {
      field: 'totalPrice',
      label: '金额变化(元)',
      render: (val) => formatPrice(val) || '-',
    },
    {
      field: 'creatorName',
      label: '操作人',
      render: (val, data) => val || data?.creator || '-',
    },
    {
      field: 'createTime',
      label: '操作时间',
      render: (val) => formatDateTime(val) || '-',
    },
    { field: 'remark', label: '备注', render: (val) => val || '-' },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    detailData.value = isOpen
      ? (modalApi.getData<WmsInventoryHistoryApi.InventoryHistory>() ?? {})
      : {};
  },
});
</script>

<template>
  <Modal
    title="库存流水详情"
    class="w-2/3"
    :show-cancel-button="false"
    :show-confirm-button="false"
  >
    <Descriptions :data="detailData" class="mx-4" />
  </Modal>
</template>
