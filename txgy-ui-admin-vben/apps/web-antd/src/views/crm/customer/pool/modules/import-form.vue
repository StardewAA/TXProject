<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Upload } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { importCustomer, importCustomerTemplate } from '#/api/crm/customer';

const emit = defineEmits(['success']);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: [
    {
      fieldName: 'file',
      label: '客户数据',
      component: 'Upload',
      rules: 'required',
      help: '仅允许导入 xls、xlsx 格式文件',
    },
    {
      fieldName: 'updateSupport',
      label: '是否覆盖',
      component: 'Switch',
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
      help: '是否更新已经存在的客户数据（按客户名称匹配）',
    },
  ],
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单：不传负责人，导入的客户直接进入公海
    const data = await formApi.getValues();
    try {
      const res: any = await importCustomer({
        file: data.file,
        updateSupport: data.updateSupport ?? false,
      });
      // 关闭并提示导入结果
      await modalApi.close();
      emit('success');
      const created = res?.createCustomerNames?.length ?? 0;
      const updated = res?.updateCustomerNames?.length ?? 0;
      const failed = Object.keys(res?.failureCustomerNames ?? {}).length;
      message.success(
        `导入完成：新增 ${created} 条，更新 ${updated} 条，失败 ${failed} 条`,
      );
    } finally {
      modalApi.unlock();
    }
  },
});

/** 上传前 */
function beforeUpload(file: FileType) {
  formApi.setFieldValue('file', file);
  return false;
}

/** 下载模版 */
async function handleDownload() {
  const data = await importCustomerTemplate();
  downloadFileFromBlobPart({ fileName: '客户导入模板.xls', source: data });
}
</script>

<template>
  <Modal title="客户公海批量导入" class="w-1/3">
    <div class="mb-2 px-4 text-xs text-muted-foreground">
      导入的客户不带负责人，将直接进入公海，可由销售人员自行领取。
    </div>
    <Form class="mx-4">
      <template #file>
        <div class="w-full">
          <Upload
            :max-count="1"
            accept=".xls,.xlsx"
            :before-upload="beforeUpload"
          >
            <Button type="primary"> 选择 Excel 文件 </Button>
          </Upload>
        </div>
      </template>
    </Form>
    <template #prepend-footer>
      <div class="flex flex-auto items-center">
        <Button @click="handleDownload"> 下载导入模板 </Button>
      </div>
    </template>
  </Modal>
</template>
