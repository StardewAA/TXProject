<script lang="ts" setup>
import { useRouter } from 'vue-router';

const router = useRouter();

const groups = [
  {
    items: [
      { label: '标准规范库', path: '/cms-preview/standard' },
      { label: '材料参数库', path: '/cms-preview/material' },
      { label: '工艺案例库', path: '/cms-preview/process' },
      { label: '定额资料库', path: '/cms-preview/quota' },
      { label: '政策资讯', path: '/cms-preview/policy' },
    ],
    label: '内容管理',
  },
  {
    items: [
      { label: '待审核内容', path: '/cms-preview/audit-pending' },
      { label: '审核记录', path: '/cms-preview/audit-record' },
      { label: '发布/下架', path: '/cms-preview/audit-publish' },
    ],
    label: '审核管理',
  },
  {
    items: [
      { label: '专家入驻审核', path: '/cms-preview/expert-review' },
      { label: '专家信息维护', path: '/cms-preview/expert-info' },
      { label: '咨询订单管理', path: '/cms-preview/expert-order' },
      { label: '结算管理', path: '/cms-preview/expert-settlement' },
    ],
    label: '专家管理',
  },
  {
    items: [
      { label: 'AI 知识库', path: '/cms-preview/ai-knowledge' },
      { label: 'AI 对话记录', path: '/cms-preview/ai-chat' },
      { label: '定制咨询项目', path: '/cms-preview/custom-project' },
    ],
    label: '咨询服务',
  },
  {
    items: [
      { label: '采集源配置', path: '/cms-preview/crawler-source' },
      { label: '采集任务', path: '/cms-preview/crawler-task' },
      { label: '采集结果审核', path: '/cms-preview/crawler-result' },
      { label: '推送记录', path: '/cms-preview/crawler-push' },
    ],
    label: '政策采集',
  },
  {
    items: [
      { label: '内容数据统计', path: '/cms-preview/stat-content' },
      { label: '检索热词分析', path: '/cms-preview/stat-hotword' },
      { label: '咨询数据统计', path: '/cms-preview/stat-consult' },
    ],
    label: '统计分析',
  },
];
</script>

<template>
  <div class="cms-preview-shell">
    <header class="cms-preview-header">
      <button
        class="cms-preview-brand"
        type="button"
        @click="router.push('/cms-preview/standard')"
      >
        CMS 前端页面预览
      </button>
      <nav class="cms-preview-nav">
        <div v-for="group in groups" :key="group.label" class="cms-preview-group">
          <span class="cms-preview-group-label">{{ group.label }}</span>
          <button
            v-for="item in group.items"
            :key="item.path"
            type="button"
            @click="router.push(item.path)"
          >
            {{ item.label }}
          </button>
        </div>
      </nav>
    </header>
    <main class="cms-preview-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.cms-preview-shell {
  min-height: 100vh;
  background: #f3f5f7;
  color: #1f2937;
}

.cms-preview-header {
  position: sticky;
  z-index: 20;
  top: 0;
  padding: 12px 20px 8px;
  border-bottom: 1px solid #d9dee5;
  background: #fff;
  box-shadow: 0 1px 6px rgb(15 23 42 / 6%);
}

.cms-preview-brand {
  padding: 0;
  border: 0;
  background: transparent;
  color: #111827;
  cursor: pointer;
  font-size: 17px;
  font-weight: 700;
}

.cms-preview-nav {
  display: flex;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 10px 18px;
}

.cms-preview-group {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.cms-preview-group-label {
  margin-right: 4px;
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
}

.cms-preview-group button {
  padding: 3px 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #374151;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.cms-preview-group button:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.cms-preview-main {
  min-height: calc(100vh - 118px);
  padding: 18px 20px 40px;
}

@media (max-width: 900px) {
  .cms-preview-main {
    padding: 12px 10px 32px;
  }
}
</style>
