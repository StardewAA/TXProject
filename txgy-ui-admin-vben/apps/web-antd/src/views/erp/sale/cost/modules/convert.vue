<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { getAccountSimpleList } from '#/api/erp/finance/account';
import { convertCostResult } from '#/api/erp/sale/cost';
import { getCustomerSimpleList } from '#/api/erp/sale/customer';
import { getSimpleUserList } from '#/api/system/user';

const emit = defineEmits(['success']);

const customers = ref<any[]>([]);
const users = ref<any[]>([]);
const accounts = ref<any[]>([]);

const form = reactive<any>({
  id: undefined,
  customerId: undefined,
  saleUserId: undefined,
  accountId: undefined,
  warehouseCode: undefined,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!form.customerId) {
      message.warning('请选择客户');
      return;
    }
    modalApi.lock();
    try {
      // convertedType = 2 销售订单：后端会真实建单并回填 convertedId
      const orderId = await convertCostResult({ ...form, convertedType: 2 });
      await modalApi.close();
      message.success(
        orderId ? `已生成销售订单（编号 ${orderId}）` : '转换成功',
      );
      emit('success');
    } catch (error: any) {
      message.error(error?.message || '转换失败');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = modalApi.getData<{ id?: number }>();
    Object.assign(form, {
      id: data?.id,
      customerId: undefined,
      saleUserId: undefined,
      accountId: undefined,
      warehouseCode: undefined,
    });
    const [customerList, userList, accountList] = await Promise.all([
      getCustomerSimpleList(),
      getSimpleUserList(),
      getAccountSimpleList(),
    ]);
    customers.value = customerList as any[];
    users.value = userList as any[];
    accounts.value = accountList as any[];
  },
});
</script>

<template>
  <Modal title="转销售订单" class="w-1/2">
    <a-form layout="vertical" class="px-4">
      <a-form-item label="客户" required>
        <a-select
          v-model:value="form.customerId"
          show-search
          option-filter-prop="label"
          placeholder="请选择客户"
        >
          <a-select-option
            v-for="c in customers"
            :key="c.id"
            :value="c.id"
            :label="c.name"
            >{{ c.name }}</a-select-option
          >
        </a-select>
      </a-form-item>
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="销售员">
            <a-select
              v-model:value="form.saleUserId"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="可不选"
            >
              <a-select-option
                v-for="u in users"
                :key="u.id"
                :value="u.id"
                :label="u.nickname"
                >{{ u.nickname }}</a-select-option
              >
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="结算账户">
            <a-select
              v-model:value="form.accountId"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="可不选"
            >
              <a-select-option
                v-for="a in accounts"
                :key="a.id"
                :value="a.id"
                :label="a.name"
                >{{ a.name }}</a-select-option
              >
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="仓库编码">
            <a-input
              v-model:value="form.warehouseCode"
              placeholder="如 WH01，可留空"
            />
          </a-form-item>
        </a-col>
      </a-row>
      <a-alert
        type="info"
        show-icon
        message="将按造价明细生成销售订单"
        description="新订单为草稿状态，需另行提交审批；同时回填溯源（来源类型=造价结果）与业务链号 COST-<造价单号>。"
      />
    </a-form>
  </Modal>
</template>
