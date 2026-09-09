<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Alert, Button, Card, Spin, Table, Tag } from 'ant-design-vue';

import type { ReviewReport } from '#/api/cms/review';
import { listReviews } from '#/api/cms/review';

defineOptions({ name: 'CmsReviewRecord' });

const router = useRouter();
const busy = ref(false);
const error = ref('');
const records = ref<ReviewReport[]>([]);
const decisionNames = { PASS: '通过', WARNING: '警告通过', FAIL: '未通过' };
const columns = [
  { title: '文档名称', key: 'title' },
  { title: '审查结论', key: 'decision', width: 110 },
  { title: '去向', key: 'destination', width: 160 },
  { title: '提交时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 120 },
];

async function refresh() {
  busy.value = true;
  try {
    records.value = await listReviews(100);
    error.value = '';
  } catch {
    error.value = '无法读取审查记录，请检查服务、数据库迁移和账号权限。';
  } finally {
    busy.value = false;
  }
}

function open(id: string) {
  router.push(`/cms/audit/report?id=${id}`);
}

onMounted(refresh);
</script>

<template>
  <main class="review-page">
    <header>
      <h1>审查记录</h1>
      <p>查看已提交文档的审查结论、核验状态与入库去向。</p>
    </header>
    <Alert v-if="error" type="error" :message="error" show-icon />
    <Card title="审查记录">
      <template #extra><Button :disabled="busy" @click="refresh">刷新</Button></template>
      <Spin :spinning="busy">
        <Table
          :columns="columns"
          :data-source="records"
          row-key="id"
          :pagination="{ pageSize: 10, showSizeChanger: true }"
          :locale="{ emptyText: '暂无审查记录' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">{{ record.metadata.title }}</template>
            <template v-else-if="column.key === 'decision'">
              <Tag :color="record.decision === 'FAIL' ? 'red' : record.decision === 'WARNING' ? 'orange' : 'green'">
                {{ decisionNames[record.decision as keyof typeof decisionNames] }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'destination'">
              {{ record.admitted ? '已入知识库' : '数据库待核验 / 退回' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <Button type="link" @click="open(record.id)">查看报告</Button>
            </template>
          </template>
        </Table>
      </Spin>
    </Card>
  </main>
</template>

<style scoped>
.review-page { padding: 24px; display: grid; gap: 20px; }
h1 { font-size: 26px; font-weight: 650; margin: 0 0 8px; }
p { color: #64748b; margin: 8px 0; }
</style>
