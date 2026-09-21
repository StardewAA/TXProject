<script lang="ts" setup>
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

import { getWarehouse } from '#/api/wms/md/warehouse';
import { useDescription } from '#/components/description';

defineOptions({ name: 'WmsWarehouseDetail' });

const detailData = ref<WmsWarehouseApi.Warehouse>({});
const [Descriptions] = useDescription({
  bordered: true,
  column: 2,
  schema: [
    { field: 'name', label: '仓库名称', render: (val) => val || '-' },
    { field: 'code', label: '仓库编号', render: (val) => val || '-' },
    { field: 'warehouseType', label: '仓库类型', render: (val) => val === 'FINISHED_GOODS' ? '成品仓' : val === 'RAW_MATERIAL' ? '原料仓' : val || '-' },
    { field: 'status', label: '仓库状态', render: (val) => getDictLabel(DICT_TYPE.COMMON_STATUS, val) || '-' },
    { field: 'organizationName', label: '所属组织/部门', render: (val) => val || '-' },
    { field: 'city', label: '所在城市', render: (val) => val || '-' },
    { field: 'address', label: '仓库地址', render: (val) => val || '-' },
    { field: 'principal', label: '负责人', render: (val) => val || '-' },
    { field: 'principalPhone', label: '负责人联系电话', render: (val) => val || '-' },
    { field: 'remark', label: '备注', render: (val) => val || '-' },
    { field: 'sort', label: '排序', render: (val) => val ?? '-' },
    { field: 'creator', label: '创建人', render: (val) => val || '-' },
    { field: 'createTime', label: '创建时间', render: (val) => formatDateTime(val) || '-' },
    { field: 'updateTime', label: '最后更新时间', render: (val) => formatDateTime(val) || '-' },
    { field: 'updater', label: '最后更新人', render: (val) => val || '-' },
  ],
  useCard: false,
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) { detailData.value = {}; return; }
    const data = modalApi.getData<{ id?: number }>();
    if (!data?.id) return;
    modalApi.lock();
    try { detailData.value = await getWarehouse(data.id); } finally { modalApi.unlock(); }
  },
});
</script>

<template>
  <Modal title="仓库详情" class="w-2/3" :show-cancel-button="false" :show-confirm-button="false">
    <Descriptions :data="detailData" class="mx-4" />
  </Modal>
</template>
