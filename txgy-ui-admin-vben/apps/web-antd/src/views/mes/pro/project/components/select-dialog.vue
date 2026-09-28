<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProProjectApi } from '#/api/mes/pro/project';

import { nextTick, ref } from 'vue';

import { Button, message, Modal } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getProjectPage } from '#/api/mes/pro/project';

import {
  useProjectSelectGridColumns,
  useProjectSelectGridFormSchema,
} from '../data';

defineOptions({ name: 'MesProProjectSelectDialog' });

const emit = defineEmits<{
  selected: [rows: MesProProjectApi.Project[]];
}>();

const open = ref(false); // 弹窗是否打开
const multiple = ref(true); // 是否多选
const selectedRows = ref<MesProProjectApi.Project[]>([]); // 已选项目列表
const preSelectedIds = ref<number[]>([]); // 预选项目编号列表

/** 获取多选记录，包含 VXE reserve 跨页记录 */
function getMultipleSelectedRows() {
  const selectedMap = new Map<number, MesProProjectApi.Project>();
  const records = [
    ...(gridApi.grid.getCheckboxReserveRecords?.() ?? []),
    ...(gridApi.grid.getCheckboxRecords?.() ?? []),
  ] as MesProProjectApi.Project[];
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

/** 处理单选变化 */
function handleRadioChange(row: MesProProjectApi.Project) {
  selectedRows.value = [row];
}

/** 多选模式下切换行勾选 */
async function toggleMultipleRow(row: MesProProjectApi.Project) {
  const selected = gridApi.grid.isCheckedByCheckboxRow(row);
  await gridApi.grid.setCheckboxRow(row, !selected);
  selectedRows.value = getMultipleSelectedRows();
}

/** 处理行双击 */
async function handleCellDblclick({ row }: { row: MesProProjectApi.Project }) {
  if (multiple.value) {
    await toggleMultipleRow(row);
    return;
  }
  selectedRows.value = [row];
  await gridApi.grid.setRadioRow(row);
  handleConfirm();
}

/** 回显预选项目 */
async function applyPreSelection() {
  if (preSelectedIds.value.length === 0) {
    return;
  }
  const rows = gridApi.grid.getTableData().fullData as MesProProjectApi.Project[];
  for (const row of rows) {
    if (
      row.id === undefined ||
      row.id === null ||
      !preSelectedIds.value.includes(row.id)
    ) {
      continue;
    }
    if (multiple.value) {
      await gridApi.grid.setCheckboxRow(row, true);
    } else {
      await gridApi.grid.setRadioRow(row);
      selectedRows.value = [row];
      return;
    }
  }
  if (multiple.value) {
    selectedRows.value = getMultipleSelectedRows();
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useProjectSelectGridFormSchema(),
  },
  gridOptions: {
    columns: useProjectSelectGridColumns(true),
    height: 520,
    keepSource: true,
    checkboxConfig: {
      highlight: true,
      range: true,
      reserve: true,
    },
    radioConfig: {
      highlight: true,
      trigger: 'row',
    },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getProjectPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
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
  } as VxeTableGridOptions<MesProProjectApi.Project>,
  gridEvents: {
    cellDblclick: handleCellDblclick,
    checkboxAll: handleCheckboxSelectChange,
    checkboxChange: handleCheckboxSelectChange,
    radioChange: ({ row }: { row: MesProProjectApi.Project }) => {
      handleRadioChange(row);
    },
  },
});

/** 重置查询和选择状态 */
async function resetQueryState() {
  selectedRows.value = [];
  await gridApi.grid.clearCheckboxRow();
  await gridApi.grid.clearCheckboxReserve();
  await gridApi.grid.clearRadioRow();
  await gridApi.formApi.resetForm();
}

/** 打开项目选择弹窗 */
async function openModal(
  selectedIds?: number[],
  options?: { multiple?: boolean },
) {
  open.value = true;
  multiple.value = options?.multiple ?? true;
  preSelectedIds.value = selectedIds || [];
  await nextTick();
  gridApi.setGridOptions({
    columns: useProjectSelectGridColumns(multiple.value),
  });
  await resetQueryState();
  await gridApi.query();
  await nextTick();
  await applyPreSelection();
}

/** 关闭项目选择弹窗 */
async function closeModal() {
  open.value = false;
  await resetQueryState();
}

/** 确认选择项目 */
function handleConfirm() {
  const rows = multiple.value ? getMultipleSelectedRows() : selectedRows.value;
  if (rows.length === 0) {
    message.warning(multiple.value ? '请至少选择一条数据' : '请选择一条数据');
    return;
  }
  emit('selected', multiple.value ? rows : [rows[0]!]);
  open.value = false;
}

defineExpose({ open: openModal });
</script>

<template>
  <Modal
    v-model:open="open"
    title="项目选择"
    width="70%"
    :destroy-on-close="true"
    @ok="handleConfirm"
    @cancel="closeModal"
  >
    <Grid table-title="项目列表" />
    <template #footer>
      <Button @click="closeModal"> 取消 </Button>
      <Button type="primary" @click="handleConfirm"> 确定 </Button>
    </template>
  </Modal>
</template>
