<script setup lang="ts">
import { ref } from 'vue';

import { MapDialog } from '#/components/map';

import { Button, Input } from 'ant-design-vue';

const props = defineProps<{
  formApi?: { setFieldValue: (field: string, value: unknown) => Promise<void> | void };
  modelValue?: string;
  values?: { latitude?: number; longitude?: number };
}>();

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const mapDialogRef = ref<InstanceType<typeof MapDialog>>();

function updateAddress(value: string) {
  emit('update:modelValue', value);
  if (!value) {
    void props.formApi?.setFieldValue('longitude', undefined);
    void props.formApi?.setFieldValue('latitude', undefined);
  }
}

function openMap() {
  mapDialogRef.value?.open(props.values?.longitude, props.values?.latitude);
}

function handleMapConfirm(data: { address: string; latitude: string; longitude: string }) {
  if (!props.modelValue?.trim() && data.address) updateAddress(data.address);
  void props.formApi?.setFieldValue('longitude', Number(data.longitude));
  void props.formApi?.setFieldValue('latitude', Number(data.latitude));
}
</script>

<template>
  <div class="flex w-full items-center gap-2">
    <Input :value="modelValue" allow-clear :maxlength="255" placeholder="请输入详细工作地址" @update:value="updateAddress" />
    <Button class="shrink-0" @click="openMap">地图选点</Button>
    <span v-if="values?.longitude != null && values?.latitude != null" class="whitespace-nowrap text-xs text-gray-500">
      {{ Number(values.longitude).toFixed(6) }}, {{ Number(values.latitude).toFixed(6) }}
    </span>
    <MapDialog ref="mapDialogRef" @confirm="handleMapConfirm" />
  </div>
</template>
