<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Col, message, Row, Select } from 'ant-design-vue';

import PageHeaderBar from '../../components/page-header-bar.vue';

defineOptions({ name: 'AnalyticsSkeletonPage' });

const props = defineProps<{
  description?: string;
  showExport?: boolean;
  skeletonNo: string;
  title: string;
}>();

const exportLoading = ref(false);

function handleExport() {
  exportLoading.value = true;
  setTimeout(() => {
    exportLoading.value = false;
    message.info('导出功能开发中');
  }, 300);
}
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4 pb-4">
      <PageHeaderBar
        :crumb="`云培训学院 / 模块 09 / ${props.skeletonNo}`"
        :title="props.title"
        :description="props.description"
      >
        <template v-if="props.showExport" #actions>
          <Button :loading="exportLoading" size="small" @click="handleExport">
            导出
          </Button>
        </template>
      </PageHeaderBar>

      <Card :bordered="false" title="筛选条件（骨架）">
        <Row :gutter="16">
          <Col :span="6">
            <Select class="w-full" disabled placeholder="筛选条件一（开发中）" />
          </Col>
          <Col :span="6">
            <Select class="w-full" disabled placeholder="筛选条件二（开发中）" />
          </Col>
          <Col :span="6">
            <Select class="w-full" disabled placeholder="筛选条件三（开发中）" />
          </Col>
          <Col :span="6">
            <Button class="w-full" disabled> 查询 </Button>
          </Col>
        </Row>
      </Card>

      <Card :bordered="false">
        <div class="flex flex-col items-center justify-center py-16">
          <div class="mb-3 text-lg font-medium">功能开发中</div>
          <div class="mt-2 max-w-xl text-center text-sm leading-6 text-gray-500">
            本页面为模块九框架骨架，业务逻辑将在后续迭代中实现（接口已就绪，见
            <span class="font-mono">/admin-api/training/analytics/**</span>）。
          </div>
        </div>
      </Card>
    </div>
  </Page>
</template>
