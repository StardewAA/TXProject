<script lang="ts" setup>
import type { CmsSeoMeta } from '#/api/cms/content-ext';

import {
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Textarea,
} from 'ant-design-vue';
import { reactive, ref } from 'vue';

import { getSeoMeta, saveSeoMeta } from '#/api/cms/content-ext';

defineOptions({ name: 'CmsContentSeo' });

const loading = ref(false);
const saving = ref(false);
const query = reactive({ contentId: undefined as number | undefined, contentType: 'POLICY' });
const form = reactive<CmsSeoMeta>({});

const contentTypeOptions = [
  { label: '政策法规', value: 'POLICY' },
  { label: '标准规范', value: 'STANDARD' },
  { label: '材料参数', value: 'MATERIAL' },
  { label: '工艺案例', value: 'PROCESS' },
  { label: '定额资料', value: 'QUOTA' },
];

async function load() {
  if (!query.contentId) {
    message.warning('请输入内容 ID');
    return;
  }
  loading.value = true;
  try {
    const result = await getSeoMeta(query.contentType, query.contentId);
    Object.keys(form).forEach((key) => delete (form as any)[key]);
    Object.assign(form, query, result ?? {});
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!form.contentId || !form.contentType) {
    message.warning('请先查询需要设置 SEO 的内容');
    return;
  }
  saving.value = true;
  try {
    await saveSeoMeta({ ...form });
    message.success('SEO 信息已保存');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Card :bordered="false" title="SEO 元信息设置">
    <Space class="seo-query" wrap>
      <Select v-model:value="query.contentType" :options="contentTypeOptions" style="width: 160px" />
      <InputNumber v-model:value="query.contentId" :min="1" placeholder="内容 ID" style="width: 160px" />
      <Button :loading="loading" type="primary" @click="load">查询</Button>
    </Space>

    <Form class="seo-form" layout="vertical">
      <FormItem label="SEO 标题（建议 30 字内）">
        <Input v-model:value="form.seoTitle" :maxlength="120" show-count />
      </FormItem>
      <FormItem label="关键词（英文逗号分隔）">
        <Input v-model:value="form.seoKeywords" placeholder="如：设备更新,贷款贴息,陕西政策" />
      </FormItem>
      <FormItem label="页面描述（建议 120 字内）">
        <Textarea v-model:value="form.seoDescription" :maxlength="300" :rows="4" show-count />
      </FormItem>
      <FormItem label="规范链接（canonical）">
        <Input v-model:value="form.canonicalUrl" placeholder="https://..." />
      </FormItem>
      <Button :loading="saving" type="primary" @click="submit">保存</Button>
    </Form>
  </Card>
</template>

<style scoped>
.seo-query {
  margin-bottom: 18px;
}

.seo-form {
  max-width: 720px;
}
</style>