<script lang="ts" setup>
import type { CrmAfterSaleVisitPlanApi } from '#/api/crm/after-sale/plan';
import type { WarrantyOption } from '#/api/crm/after-sale/warranty';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createAfterSaleVisitPlan,
  getAfterSaleVisitPlan,
  updateAfterSaleVisitPlan,
} from '#/api/crm/after-sale/plan';
import {
  getAfterSaleWarranty,
  getInWarrantyOptionsByCustomer,
} from '#/api/crm/after-sale/warranty';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmAfterSaleVisitPlanApi.VisitPlan>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['回访计划'])
    : $t('ui.actionTitle.create', ['回访计划']);
});

/**
 * 构造「质保档案」下拉的完整属性
 *
 * updateSchema 会覆盖 componentProps，因此每次都整体给出，
 * 避免把 showSearch / allowClear 等能力覆盖丢失
 */
function buildWarrantyProps(
  overrides: Record<string, any> = {},
): Record<string, any> {
  return {
    options: [] as WarrantyOption[],
    loading: false,
    disabled: true,
    placeholder: '请先选择客户名称',
    allowClear: true,
    showSearch: true,
    optionFilterProp: 'label',
    ...overrides,
  };
}

/** 更新「质保档案」下拉属性 */
async function updateWarrantySelect(overrides: Record<string, any> = {}) {
  await formApi.updateSchema([
    { fieldName: 'warrantyId', componentProps: buildWarrantyProps(overrides) },
  ]);
}

/**
 * 按客户加载「质保中」的质保档案候选
 *
 * @param customerId     客户编号；为空表示未选客户，下拉置空并禁用
 * @param keepWarrantyId 编辑态需要保留回显的已绑定档案编号
 */
async function loadWarrantyOptions(
  customerId?: number,
  keepWarrantyId?: number,
) {
  if (!customerId) {
    await updateWarrantySelect();
    return;
  }
  await updateWarrantySelect({ loading: true, disabled: true });
  try {
    const options = await getInWarrantyOptionsByCustomer(customerId);
    if (
      keepWarrantyId &&
      !options.some((item) => item.value === keepWarrantyId)
    ) {
      // 已绑定档案已不在「质保中」（例如质保到期）：补一条禁用项用于回显，界面不至于空白，
      // 同时避免用户改选到其他非「质保中」的档案
      options.unshift({
        label: `${formData.value?.warrantyNo ?? ''} - ${
          formData.value?.projectName ?? ''
        }（当前绑定，非质保中）`,
        value: keepWarrantyId,
        disabled: true,
      });
    }
    await updateWarrantySelect({
      options,
      loading: false,
      disabled: options.length === 0,
      placeholder:
        options.length === 0
          ? '该客户名下暂无「质保中」的质保档案'
          : '请选择质保档案（仅质保中）',
    });
  } catch {
    await updateWarrantySelect({ placeholder: '质保档案加载失败，请重新选择客户' });
  }
}

/** 客户名称变更：清空已选质保档案，并重新拉取该客户名下「质保中」的档案 */
async function handleCustomerChange(customerId?: number) {
  await formApi.setValues({ warrantyId: undefined });
  await loadWarrantyOptions(customerId);
}

/** 上一次的客户编号：用于判断客户是否真的变化，避免同一客户重复请求与回填时误清空 */
let lastCustomerId: number | undefined;

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  wrapperClass: 'grid-cols-2',
  schema: useFormSchema(),
  showDefaultActions: false,
  // 切换客户名称：清空已选质保档案，并重新拉取该客户名下「质保中」的档案
  handleValuesChange(values) {
    const customerId = (values as { customerId?: number } | undefined)
      ?.customerId;
    if (customerId === lastCustomerId) {
      return;
    }
    lastCustomerId = customerId;
    void handleCustomerChange(customerId);
  },
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const data = (await formApi.getValues()) as CrmAfterSaleVisitPlanApi.VisitPlan & {
      customerId?: number;
    };
    // 客户名称仅用于联动筛选「质保中」的质保档案，不参与计划数据提交（后端 VO 无 customerId 字段）
    delete data.customerId;
    try {
      await (formData.value?.id
        ? updateAfterSaleVisitPlan(data)
        : createAfterSaleVisitPlan(data));
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
      lastCustomerId = undefined;
      return;
    }
    const data = modalApi.getData<CrmAfterSaleVisitPlanApi.VisitPlan>();
    // 1. 新增：质保档案下拉置空并禁用，等待选择客户名称后再联动加载
    if (!data || !data.id) {
      await updateWarrantySelect();
      return;
    }
    // 2. 编辑：回填计划后，按已绑定的质保档案反查其客户，再加载该客户的「质保中」档案
    modalApi.lock();
    try {
      formData.value = await getAfterSaleVisitPlan(data.id);
      await formApi.setValues(formData.value);
      const warrantyId = formData.value?.warrantyId;
      if (warrantyId) {
        const warranty = await getAfterSaleWarranty(warrantyId);
        if (warranty?.customerId) {
          // 先记录客户编号，避免回填动作被当成「切换客户」而清空已绑定的质保档案
          lastCustomerId = warranty.customerId;
          await formApi.setValues({ customerId: warranty.customerId });
          await loadWarrantyOptions(warranty.customerId, warrantyId);
        }
      }
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <Form class="mx-4" />
  </Modal>
</template>
