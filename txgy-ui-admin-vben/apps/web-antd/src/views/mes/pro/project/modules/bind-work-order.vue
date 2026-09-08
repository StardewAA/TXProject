<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProWorkOrderApi } from '#/api/mes/pro/workorder';

import { nextTick, ref } from 'vue';

import { Button, message, Modal } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { bindWorkOrders } from '#/api/mes/pro/project';
import { getWorkOrderPage } from '#/api/mes/pro/workorder';
import {
  useWorkOrderSelectGridColumns,
  useWorkOrderSelectGridFormSchema,
} from '#/views/mes/pro/workorder/data';

defineOptions({ name: 'MesProProjectBindWorkOrder' });

const emit = defineEmits<{ success: [] }>();

const open = ref(false); // 弹窗是否打开
const projectId = ref<number>(); // 项目编号
const selectedRows = ref<MesProWorkOrderApi.WorkOrder[]>([]); // 已选工单

/** 获取多选记录（VXE reserve 跨页记录 + 当前页记录） */
function getMultipleSelectedRows() {
  const selectedMap = new Map<number, MesProWorkOrderApi.WorkOrder>();
  const records = [
    ...(gridApi.grid.getCheckboxReserveRecords?.() ?? []),
    ...(gridApi.grid.getCheckboxRecords?.() ?? []),
  ] as MesProWorkOrderApi.WorkOrder[];
  records.forEach((row) => {
    const rowId = row.id;
    if (rowId !== undefined && rowId !== null) {
      selectedMap.set(rowId, row);
    }
  });
  return [...selectedMap.values()];
}

/** 处理勾选变化 */
function handleCheckboxSelectChange() {
  selectedRows.value = getMultipleSelectedRows();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useWorkOrderSelectGridFormSchema(),
  },
  gridOptions: {
    columns: useWorkOrderSelectGridColumns(true),
    height: 520,
    keepSource: true,
    checkboxConfig: {
      highlight: true,
      range: true,
      reserve: true,
    },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getWorkOrderPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            noProject: true, // 固定只查未挂接项目的工单
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<MesProWorkOrderApi.WorkOrder>,
  gridEvents: {
    checkboxAll: handleCheckboxSelectChange,
    checkboxChange: handleCheckboxSelectChange,
  },
});

/** 打开挂接工单弹窗 */
async function openModal(id: number) {
  projectId.value = id;
  open.value = true;
  await nextTick();
  await gridApi.grid.clearCheckboxRow();
  await gridApi.grid.clearCheckboxReserve();
  await gridApi.formApi.resetForm();
  await gridApi.query();
}

/** 关闭弹窗 */
function closeModal() {
  open.value = false;
  selectedRows.value = [];
}

/** 确认挂接 */
async function handleConfirm() {
  const rows = getMultipleSelectedRows();
  if (rows.length === 0) {
    message.warning('请至少选择一条生产工单');
    return;
  }
  if (!projectId.value) {
    return;
  }
  const ids = rows
    .map((row) => row.id)
    .filter((id) => id !== undefined && id !== null) as number[];
  try {
    await bindWorkOrders(projectId.value, ids);
    message.success(`已挂接 ${rows.length} 个生产工单`);
    open.value = false;
    emit('success');
  } catch {
    // 异常提示由全局拦截器处理
  }
}

defineExpose({ open: openModal });
</script>

<template>
  <Modal
    v-model:open="open"
    title="挂接生产工单"
    width="70%"
    :destroy-on-close="true"
    @cancel="closeModal"
  >
    <div class="mb-2 text-orange-500">
      仅展示未挂接任何项目的生产工单，可跨页多选。
    </div>
    <Grid table-title="生产工单列表" />
    <template #footer>
      <Button @click="closeModal"> 取消 </Button>
      <Button type="primary" @click="handleConfirm"> 确定挂接 </Button>
    </template>
  </Modal>
</template>
