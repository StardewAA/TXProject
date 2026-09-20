<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Select } from 'ant-design-vue';

import { getCertificateTypeList } from '#/api/hrm/certificate/type';

defineOptions({ name: 'HrmCertificateTypeSelect', inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    /** 是否只加载启用状态的证件类型，表单录入时使用 */
    onlyEnabled?: boolean;
  }>(),
  {
    onlyEnabled: true,
  },
);

const options = ref<{ label: string; value: number }[]>([]);
const loading = ref(false);

async function loadOptions() {
  loading.value = true;
  try {
    const list = await getCertificateTypeList(
      props.onlyEnabled ? 0 : undefined,
    );
    options.value = list.map((item) => ({
      label: item.name!,
      value: item.id!,
    }));
  } finally {
    loading.value = false;
  }
}

onMounted(loadOptions);
</script>

<template>
  <Select
    v-bind="$attrs"
    :loading="loading"
    :options="options"
    allow-clear
    placeholder="请选择证件类型"
    show-search
    :filter-option="
      (input: string, option: any) =>
        String(option?.label ?? '')
          .toLowerCase()
          .includes(input.toLowerCase())
    "
  />
</template>
