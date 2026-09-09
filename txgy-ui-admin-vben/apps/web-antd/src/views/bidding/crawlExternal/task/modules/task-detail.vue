<!--
  采集任务详情外壳（task 独立 Grid 页配套）：点「详情」打开，内部直接挂 纵向数据链 TaskChainView
        （①请求 URL → ②获取方式 CRAWLER/API → ③原始载荷 payload_json → ④解析 → ⑤执行/结果 → ⑥产出 + 全屏/复制/同批切换）。
  理由：调试视图统一复用 console/modules/task-chain.vue（无外壳纯内容组件），本文件只提供 Drawer 容器，避免重复维护静态三层。
  后端契约：同 task-chain.vue 文件头注释（task get=04-全链路 §5.8；源反查 fetchType 见外部信息获取设计 v1.0 §4.2）。
-->
<script lang="ts" setup>
import type { CrawlExternalApi } from '#/api/bidding/crawlExternal';

import { ref } from 'vue';

import { Drawer } from 'ant-design-vue';

import TaskChainView from '../../console/modules/task-chain.vue';

const open = ref(false);
const cur = ref<CrawlExternalApi.TaskItem | null>(null);

function openTask(t: CrawlExternalApi.TaskItem) {
  cur.value = t;
  open.value = true;
}

function pick(t: CrawlExternalApi.TaskItem) {
  cur.value = t;
}

defineExpose({ openTask });
</script>

<template>
  <Drawer v-model:open="open" :width="940" :title="cur ? '任务 #' + String(cur.id) + ' · 纵向数据链' : '任务详情'">
    <TaskChainView v-if="cur" :task="cur" @pick="pick" />
    <div v-else class="tdh">（未选择任务）</div>
  </Drawer>
</template>

<style scoped>
.tdh { color: var(--ind-weak-text, #6b7280); font-size: 13px; padding: 10px 2px; }
</style>