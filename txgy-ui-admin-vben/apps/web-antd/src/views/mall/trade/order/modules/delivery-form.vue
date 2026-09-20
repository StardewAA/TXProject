<script lang="ts" setup>
import type { MallOrderApi } from '#/api/mall/trade/order';

import { onBeforeUnmount, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { deliveryOrder, getOrder } from '#/api/mall/trade/order';

import { canDeliver } from '../../components/workbench-model';
import { useDeliveryFormSchema } from '../data';

const emit = defineEmits(['success']);
const access = useAccessStore();
const currentScope = () => `${access.tenantId}:${access.visitTenantId}:${access.accessToken}`;
let openedScope = '', loadVersion = 0;

const formData = ref<{ id: number; expressType: string; logisticsId?: number; logisticsNo: string }>({
  id: 0,
  expressType: 'express',
  logisticsId: undefined,
  logisticsNo: '',
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useDeliveryFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const scope = openedScope;
    if (!scope || scope !== currentScope()) return;
    const { valid } = await formApi.validate();
    if (!valid || scope !== currentScope() || scope !== openedScope) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = await formApi.getValues();
    if (scope !== currentScope() || scope !== openedScope) {
      modalApi.unlock();
      return;
    }
    if (data.expressType === 'none') {
      // 无需发货的情况
      data.logisticsId = 0;
      data.logisticsNo = '';
    }
    try {
      await deliveryOrder(data as MallOrderApi.OrderUpdateDeliveryReqVO);
      if (scope !== currentScope() || scope !== openedScope) return;
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success('发货请求已提交，请稍后查看订单状态');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    const version = ++loadVersion;
    if (!isOpen) {
      openedScope = '';
      return;
    }
    const scope = currentScope();
    openedScope = scope;
    // 加载数据
    const data = modalApi.getData<MallOrderApi.Order>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      // 用户主动进入才重新读取业务状态；轮询不覆盖正在输入的运单。
      const latest = await getOrder(data.id);
      if (version !== loadVersion || scope !== currentScope() || scope !== openedScope) return;
      if (!latest || !canDeliver(latest)) {
        message.warning(latest?.deliveryBlockReason || '订单状态已变化，当前不可发货，请刷新核对');
        await modalApi.close();
        emit('success');
        return;
      }
      formData.value = {
        id: latest.id!,
        expressType: latest.logisticsId === 0 ? 'none' : 'express',
        logisticsId: latest.logisticsId,
        logisticsNo: latest.logisticsNo || '',
      };
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
watch(currentScope, () => { ++loadVersion; openedScope = ''; void modalApi.close(); });
onBeforeUnmount(() => { ++loadVersion; openedScope = ''; });
</script>

<template>
  <Modal title="发货" class="w-1/3">
    <Form class="mx-4" />
  </Modal>
</template>
