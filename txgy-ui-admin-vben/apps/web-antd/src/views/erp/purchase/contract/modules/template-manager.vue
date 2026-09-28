<script lang="ts" setup>
import type { ErpPurchaseContractApi } from '#/api/erp/purchase/contract';

import { reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';

import {
  createPurchaseContractTemplate,
  deletePurchaseContractTemplate,
  getPurchaseContractTemplateList,
  updatePurchaseContractTemplate,
} from '#/api/erp/purchase/contract';

const rows = ref<ErpPurchaseContractApi.ContractTemplate[]>([]);
const editing = ref(false);
const form = reactive<ErpPurchaseContractApi.ContractTemplate>({ status: 0, defaultFlag: false });

async function load() {
  rows.value = await getPurchaseContractTemplateList();
}

function reset(row?: ErpPurchaseContractApi.ContractTemplate) {
  for (const key of Object.keys(form)) delete (form as any)[key];
  Object.assign(form, row ?? { status: 0, defaultFlag: false, version: 'v1.0' });
  editing.value = !!row?.id;
}

async function save() {
  if (!form.name || !form.version || !form.content) {
    message.warning('请填写模板名称、版本和通用条款');
    return;
  }
  await (editing.value ? updatePurchaseContractTemplate(form) : createPurchaseContractTemplate(form));
  message.success('合同模板已保存');
  reset();
  await load();
}

async function remove(id?: number) {
  if (!id) return;
  await deletePurchaseContractTemplate(id);
  message.success('合同模板已删除');
  await load();
}

const [Modal] = useVbenModal({
  async onOpenChange(open) {
    if (!open) return;
    reset();
    await load();
  },
});
</script>

<template>
  <Modal title="采购合同条款模板" class="w-4/5" :footer="false">
    <div class="grid grid-cols-5 gap-5 px-5 pb-5">
      <div class="col-span-2">
        <div class="mb-3 text-base font-semibold">已有模板</div>
        <a-table :data-source="rows" :pagination="false" row-key="id" size="small" bordered>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="版本" data-index="version" :width="90" />
          <a-table-column title="默认" :width="70"><template #default="{ record }">{{ record.defaultFlag ? '是' : '否' }}</template></a-table-column>
          <a-table-column title="操作" :width="130"><template #default="{ record }">
            <a-button type="link" @click="reset(record)">编辑</a-button>
            <a-popconfirm title="确认删除该模板？" @confirm="remove(record.id)"><a-button type="link" danger>删除</a-button></a-popconfirm>
          </template></a-table-column>
        </a-table>
      </div>
      <div class="col-span-3">
        <div class="mb-3 text-base font-semibold">{{ editing ? '编辑模板' : '新增模板' }}</div>
        <a-form layout="vertical">
          <a-row :gutter="16">
            <a-col :span="14"><a-form-item label="模板名称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="版本" required><a-input v-model:value="form.version" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="默认"><a-switch v-model:checked="form.defaultFlag" /></a-form-item></a-col>
          </a-row>
          <a-form-item label="通用法律条款" required><a-textarea v-model:value="form.content" :rows="13" /></a-form-item>
          <a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item>
          <div class="flex justify-end gap-2"><a-button @click="reset()">清空</a-button><a-button type="primary" @click="save">保存模板</a-button></div>
        </a-form>
      </div>
    </div>
  </Modal>
</template>
