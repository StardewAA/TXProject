<script lang="ts" setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Col, message, Row, Select } from 'ant-design-vue';

defineOptions({ name: 'AnalyticsSkeletonPage' });

const router = useRouter();
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
      <section
        class="relative overflow-hidden rounded-2xl px-6 py-7 text-white shadow-sm md:px-9"
        style="background: linear-gradient(125deg, #475569 0%, #475569dc 44%, #111827 115%)"
      >
        <div class="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div class="mb-2 text-sm text-white/75">
              云培训学院 / 模块 09 / {{ props.skeletonNo }}
            </div>
            <h1 class="m-0 text-2xl font-semibold md:text-3xl">{{ props.title }}</h1>
            <p v-if="props.description" class="mb-0 mt-3 text-sm leading-6 text-white/80">
              {{ props.description }}
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-3">
            <Button v-if="props.showExport" :loading="exportLoading" ghost @click="handleExport">
              导出
            </Button>
            <Button ghost @click="router.push('/training/workbench')"> 返回工作台 </Button>
          </div>
        </div>
      </section>

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
