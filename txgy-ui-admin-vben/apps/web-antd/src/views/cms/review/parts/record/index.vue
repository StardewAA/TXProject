<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import type { ReviewDetail, ReviewReport } from '#/api/cms/review';
import { getReview, listReviews, manualReview } from '#/api/cms/review';
import '../../../_shared/cms-glass-theme.css';

defineOptions({ name: 'CmsReviewRecord' });

const route = useRoute();
const busy = ref(false);
const error = ref('');
const records = ref<ReviewReport[]>([]);
const selected = ref<ReviewDetail>();
const detailOpen = ref(false);
const manualNote = ref('');
const decisionNames = {
  PENDING: '分析中',
  PASS: '建议通过',
  WARNING: '建议关注',
  FAIL: '建议驳回',
};
const workflowNames = {
  AI_REVIEWING: 'AI 初审中',
  PENDING_MANUAL: '待人工复核',
  APPROVED: '已人工发布',
  REJECTED: '已人工驳回',
};
const columns = [
  { title: '文档名称', key: 'title' },
  { title: '审查结论', key: 'decision', width: 110 },
  { title: '工作流状态', key: 'destination', width: 160 },
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

async function open(id: string) {
  busy.value = true;
  try {
    selected.value = await getReview(id);
    manualNote.value = '';
    detailOpen.value = true;
  } finally {
    busy.value = false;
  }
}

async function decide(decision: 'APPROVE' | 'REJECT') {
  if (!selected.value || !manualNote.value.trim()) {
    message.warning('请填写人工复核意见。');
    return;
  }
  busy.value = true;
  try {
    await manualReview(
      selected.value.report.id,
      decision,
      manualNote.value.trim(),
    );
    message.success(
      decision === 'APPROVE'
        ? '人工复核通过，内容已发布并写入检索索引。'
        : '内容已驳回，未进入知识库。',
    );
    detailOpen.value = false;
    await refresh();
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  await refresh();
  if (route.query.id) await open(String(route.query.id));
});
</script>

<template>
  <main class="cms-glass-page review-page">
    <header class="cms-glass-hero">
      <h1>审查复核</h1>
      <p>
        审查记录与结构化报告合并展示；AI 只提供建议，由管理员最终发布或驳回。
      </p>
    </header>
    <Alert v-if="error" type="error" :message="error" show-icon />
    <Card title="待复核与历史记录">
      <template #extra
        ><Button :disabled="busy" @click="refresh">刷新</Button></template
      >
      <Spin :spinning="busy">
        <Table
          :columns="columns"
          :data-source="records"
          row-key="id"
          :pagination="{ pageSize: 10, showSizeChanger: true }"
          :locale="{ emptyText: '暂无审查记录' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">{{
              record.metadata.title
            }}</template>
            <template v-else-if="column.key === 'decision'">
              <Tag
                :color="
                  record.decision === 'FAIL'
                    ? 'red'
                    : record.decision === 'WARNING'
                      ? 'orange'
                      : 'green'
                "
              >
                {{
                  decisionNames[
                    record.decision as keyof typeof decisionNames
                  ] || record.decision
                }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'destination'">
              <Tag
                :color="
                  record.workflowStatus === 'PENDING_MANUAL'
                    ? 'orange'
                    : record.workflowStatus === 'APPROVED'
                      ? 'green'
                      : record.workflowStatus === 'REJECTED'
                        ? 'red'
                        : 'blue'
                "
              >
                {{
                  workflowNames[
                    record.workflowStatus as keyof typeof workflowNames
                  ] || record.workflowStatus
                }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Button type="link" @click="open(record.id)">查看并复核</Button>
            </template>
          </template>
        </Table>
      </Spin>
    </Card>

    <Modal
      v-model:open="detailOpen"
      title="AI 合规报告与人工复核"
      :width="920"
      :footer="null"
    >
      <Spin :spinning="busy">
        <template v-if="selected">
          <h2>{{ selected.report.metadata.title }}</h2>
          <div class="tags">
            <Tag>{{
              decisionNames[
                selected.report.decision as keyof typeof decisionNames
              ]
            }}</Tag>
            <Tag color="blue">{{
              workflowNames[
                selected.report.workflowStatus as keyof typeof workflowNames
              ]
            }}</Tag>
            <Tag>AI 分数 {{ selected.report.score }} / 100</Tag>
          </div>
          <div class="meta">
            <span>文号：{{ selected.report.metadata.documentNo || '-' }}</span>
            <span>发布机关：{{ selected.report.metadata.issuer }}</span>
            <span>效力：{{ selected.report.metadata.validity }}</span>
            <a
              :href="selected.report.metadata.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              >打开官方来源</a
            >
          </div>
          <h3>结构化问题</h3>
          <ul class="issues">
            <li
              v-for="issue in selected.report.issues"
              :key="`${issue.code}-${issue.location}`"
            >
              <Tag :color="issue.severity === 'ERROR' ? 'red' : 'orange'">{{
                issue.dimension
              }}</Tag>
              <strong>{{ issue.location }} · {{ issue.message }}</strong>
              <p>{{ issue.suggestion }}</p>
            </li>
          </ul>
          <Alert
            v-if="!selected.report.issues.length"
            message="AI 初审未发现结构化问题，仍需人工核对。"
            show-icon
            type="success"
          />
          <details>
            <summary>查看提交正文</summary>
            <pre>{{ selected.text }}</pre>
          </details>
          <template v-if="selected.report.workflowStatus === 'PENDING_MANUAL'">
            <Input.TextArea
              v-model:value="manualNote"
              :rows="3"
              :maxlength="4000"
              placeholder="填写人工复核依据与决定理由（必填）"
            />
            <div class="manual-actions">
              <Button danger :loading="busy" @click="decide('REJECT')"
                >人工驳回</Button
              >
              <Button type="primary" :loading="busy" @click="decide('APPROVE')"
                >人工通过并发布</Button
              >
            </div>
          </template>
          <Alert
            v-else-if="selected.report.workflowStatus === 'AI_REVIEWING'"
            message="AI 初审正在异步执行，请稍后刷新。"
            show-icon
            type="info"
          />
          <Alert
            v-else
            :message="selected.report.manualNote || '该记录已经完成终审。'"
            show-icon
            type="info"
          />
        </template>
      </Spin>
    </Modal>
  </main>
</template>

<style scoped>
.review-page {
  padding: 6px;
}

.tags,
.meta,
.manual-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 13px 0;
}

.meta {
  padding: 12px 14px;
  color: #52647b;
  background: rgb(243 248 253 / 58%);
  border: 1px solid rgb(118 146 183 / 16%);
  border-radius: 12px;
}

.issues {
  display: grid;
  gap: 9px;
  padding: 0;
  list-style: none;
}

.issues li {
  padding: 13px;
  background: rgb(255 255 255 / 48%);
  border: 1px solid rgb(118 146 183 / 17%);
  border-radius: 12px;
}

details {
  padding: 12px 14px;
  margin: 15px 0;
  background: rgb(247 250 253 / 62%);
  border: 1px solid rgb(118 146 183 / 16%);
  border-radius: 12px;
}

details summary {
  font-weight: 600;
  color: #52647b;
  cursor: pointer;
}

pre {
  max-height: 280px;
  padding: 14px;
  overflow: auto;
  white-space: pre-wrap;
  background: rgb(236 243 250 / 68%);
  border-radius: 10px;
}

.manual-actions {
  justify-content: flex-end;
}
</style>
