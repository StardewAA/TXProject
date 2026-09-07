<script lang="ts" setup>
import type { MallShopApi } from '#/api/mall/product/shop';
import { computed, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { ElMessage } from 'element-plus';
import { useVbenForm } from '#/adapter/form';
import { createShop, getShop, updateShop } from '#/api/mall/product/shop';
import { $t } from '#/locales';
import { useFormSchema } from '../data';
const emit = defineEmits(['success']);
const formData = ref<MallShopApi.Shop>();
const getTitle = computed(() => formData.value?.id ? $t('ui.actionTitle.edit', ['企业店铺']) : $t('ui.actionTitle.create', ['企业店铺']));
const [Form, formApi] = useVbenForm({ commonConfig: { componentProps: { class: 'w-full' }, formItemClass: 'col-span-2', labelWidth: 100 }, layout: 'horizontal', schema: useFormSchema(), showDefaultActions: false });
const [Modal, modalApi] = useVbenModal({
  async onConfirm() { const { valid } = await formApi.validate(); if (!valid) return; modalApi.lock(); try { const data = await formApi.getValues() as MallShopApi.Shop; await (formData.value?.id ? updateShop(data) : createShop(data)); await modalApi.close(); emit('success'); ElMessage.success($t('ui.actionMessage.operationSuccess')); } finally { modalApi.unlock(); } },
  async onOpenChange(isOpen: boolean) { if (!isOpen) { formData.value = undefined; await formApi.resetForm(); return; } const data = modalApi.getData<MallShopApi.Shop>(); if (!data?.id) { await formApi.resetForm(); return; } modalApi.lock(); try { formData.value = await getShop(data.id); await formApi.setValues(formData.value); } finally { modalApi.unlock(); } },
});
</script>
<template><Modal :title="getTitle" class="w-[520px]"><Form class="mx-4" /></Modal></template>



