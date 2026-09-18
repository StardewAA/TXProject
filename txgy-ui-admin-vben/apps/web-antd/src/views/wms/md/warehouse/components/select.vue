<script lang="ts" setup>
import type { SelectValue } from 'ant-design-vue/es/select';

import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';

import { computed, onMounted, ref, watch } from 'vue';

import { Select } from 'ant-design-vue';

import { getWarehouse, getWarehouseSimpleList } from '#/api/wms/md/warehouse';

defineOptions({ name: 'WmsWarehouseSelect', inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    allowClear?: boolean;
    disabled?: boolean;
    modelValue?: number | string;
    placeholder?: string;
  }>(),
  {
    allowClear: true,
    disabled: false,
    modelValue: undefined,
    placeholder: '请选择仓库',
  },
);

const emit = defineEmits<{
  change: [warehouse: undefined | WmsWarehouseApi.Warehouse];
  'update:modelValue': [value: number | undefined];
}>();

const loading = ref(false);
const warehouseList = ref<WmsWarehouseApi.Warehouse[]>([]);

const options = computed(() =>
  warehouseList.value
    .filter((warehouse) => warehouse.id !== undefined)
    .map((warehouse) => ({
      disabled:
        warehouse.status !== undefined &&
        warehouse.status !== 0 &&
        warehouse.id !== Number(props.modelValue),
      label: warehouse.name,
      value: warehouse.id,
    })),
);

/** 选中变化 */
function handleChange(value: SelectValue) {
  const warehouseId = typeof value === 'number' ? value : undefined;
  emit('update:modelValue', warehouseId);
  emit(
    'change',
    warehouseList.value.find((warehouse) => warehouse.id === warehouseId),
  );
}

/** 查询仓库精简列表 */
async function getList() {
  loading.value = true;
  try {
    warehouseList.value = await getWarehouseSimpleList();
    await ensureSelectedWarehouse(props.modelValue);
  } finally {
    loading.value = false;
  }
}

/** 编辑草稿单据时，允许回显已绑定的关闭仓库，但不将其作为新选项。 */
async function ensureSelectedWarehouse(id?: number | string) {
  const warehouseId = Number(id);
  if (
    !Number.isFinite(warehouseId) ||
    warehouseList.value.some((warehouse) => warehouse.id === warehouseId)
  ) {
    return;
  }
  try {
    const warehouse = await getWarehouse(warehouseId);
    if (warehouse?.id) {
      warehouseList.value = [...warehouseList.value, warehouse];
    }
  } catch {
    // 保留原有表单行为，查询失败时由 Select 显示原始值。
  }
}

onMounted(() => {
  getList();
});

watch(
  () => props.modelValue,
  (value) => {
    void ensureSelectedWarehouse(value);
  },
  { immediate: true },
);
</script>

<template>
  <Select
    v-bind="$attrs"
    :allow-clear="allowClear"
    :disabled="disabled"
    :loading="loading"
    :options="options"
    :placeholder="placeholder"
    :value="modelValue"
    class="w-full"
    option-filter-prop="label"
    show-search
    @change="handleChange"
  />
</template>
