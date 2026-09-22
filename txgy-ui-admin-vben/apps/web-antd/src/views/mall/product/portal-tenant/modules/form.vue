<script lang="ts" setup>
import type { MallPortalTenantApi } from '#/api/mall/product/portal-tenant';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  getPortalTenant,
  updatePortalTenant,
} from '#/api/mall/product/portal-tenant';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import {
  canOpenPortalTenant,
  normalizePortalTenantSlug,
} from '../model';

const emit = defineEmits(['success']);
const formData = ref<MallPortalTenantApi.PortalTenant>();
const isLegacyPublicTenant = computed(
  () => !canOpenPortalTenant(formData.value?.tenantId),
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: { class: 'w-full' },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    modalApi.lock();
    try {
      const values = (await formApi.getValues()) as MallPortalTenantApi.UpdateParams;
      values.tenantSlug = normalizePortalTenantSlug(values.tenantSlug);
      if (values.openStatus === 0 && isLegacyPublicTenant.value) {
        message.warning('3000 号历史公共租户不能开放到平台门户');
        return;
      }
      await updatePortalTenant(values);
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      await formApi.resetForm();
      return;
    }
    const row = modalApi.getData<MallPortalTenantApi.PortalTenant>();
    if (!row?.id) return;
    modalApi.lock();
    try {
      formData.value = await getPortalTenant(row.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="编辑平台租户目录" class="w-[620px]">
    <Alert
      v-if="isLegacyPublicTenant"
      class="mx-4 mb-4"
      message="3000 号历史公共租户只能保持关闭，不能重新开放到平台门户。"
      show-icon
      type="warning"
    />
    <Form class="mx-4" />
  </Modal>
</template>
