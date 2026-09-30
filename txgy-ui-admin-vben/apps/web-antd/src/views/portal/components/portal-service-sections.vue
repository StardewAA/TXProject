<script lang="ts" setup>
import type { PortalOverview } from '#/api/cms/portal';

import { computed, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { getPortalOverview } from '#/api/cms/portal';

/** 门户首页（CMS 系统页）搜索区下方内容：统计 / 功能入口 / 法规库 / 政策库 / AI 助手 */
defineOptions({ name: 'PortalServiceSections' });

/**
 * part 控制本组件渲染哪些区块（首页据此调整模块顺序）：
 * - all：全部（默认）
 * - stats：只渲染统计条
 * - rest：渲染功能入口 / 法规库 / 政策库 / AI 助手
 */
const props = withDefaults(defineProps<{ part?: 'all' | 'rest' | 'stats' }>(), {
  part: 'all',
});
const showStats = computed(() => props.part !== 'rest');
const showRest = computed(() => props.part !== 'stats');

const overview = ref<PortalOverview>({
  experts: 0,
  financeChunks: 0,
  financeDocuments: 0,
  lawChunks: 0,
  lawDocuments: 0,
  standards: 0,
});

const stats = computed(() => [
  { icon: 'solar:document-text-linear', label: '法规原文（份）', value: overview.value.lawDocuments },
  { icon: 'solar:layers-linear', label: '法规切片（条）', value: overview.value.lawChunks },
  { icon: 'solar:banknote-linear', label: '金融政策（份）', value: overview.value.financeDocuments },
  { icon: 'solar:user-speak-linear', label: '可咨询专家（位）', value: overview.value.experts },
]);

/** 首页会把本组件渲染两次（统计条 / 其余区块），用模块级缓存避免重复请求 */
let overviewPromise: null | Promise<PortalOverview> = null;

function loadOverview() {
  if (!overviewPromise) {
    overviewPromise = getPortalOverview().catch((error) => {
      overviewPromise = null;
      throw error;
    });
  }
  return overviewPromise;
}

onMounted(async () => {
  try {
    overview.value = await loadOverview();
  } catch {
    // 统计失败不影响页面
  }
});
</script>

<template>
  <div class="svc">
    <!-- 统计 -->
    <section v-if="showStats" class="svc-stats">
      <div v-for="item in stats" :key="item.label" class="svc-stat">
        <span class="svc-stat-ic"><IconifyIcon :icon="item.icon" /></span>
        <div>
          <div class="svc-stat-val">{{ item.value }}</div>
          <div class="svc-stat-label">{{ item.label }}</div>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.svc {
  --svc-border: #e3e8ef;
  --svc-border-light: #eef1f6;
  --svc-navy: #0a2540;
  --svc-primary: #1a4480;
  --svc-text: #1f2937;
  --svc-text2: #5b6675;
  --svc-text3: #8a93a3;

  display: grid;
  gap: 22px;
  padding-bottom: 26px;
}

/* 统计 */
.svc-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: #fff;
  border: 1px solid var(--svc-border);
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(10 37 64 / 6%);
}

.svc-stat {
  min-width: 0;
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 20px 24px;
}

.svc-stat:not(:last-child) {
  border-right: 1px solid var(--svc-border-light);
}

.svc-stat-ic {
  display: grid;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  font-size: 22px;
  color: var(--svc-primary);
  background: #eef3fa;
  border-radius: 10px;
  place-items: center;
}

.svc-stat-val {
  font-size: 24px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--svc-navy);
}

.svc-stat-label {
  margin-top: 2px;
  font-size: 13px;
  color: var(--svc-text2);
}

/* 四大功能入口 */
.svc-features {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.svc-card {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 20px 20px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--svc-border);
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(10 37 64 / 6%);
  transition: all 0.22s ease;
}

.svc-card:hover {
  border-color: #dbe6f5;
  box-shadow: 0 6px 20px rgb(10 37 64 / 8%);
  transform: translateY(-2px);
}

.svc-card-ic {
  display: grid;
  width: 44px;
  height: 44px;
  font-size: 22px;
  color: var(--svc-primary);
  background: #eef3fa;
  border-radius: 10px;
  place-items: center;
}

.svc-card-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.svc-card-copy strong {
  font-size: 16px;
  font-weight: 700;
  color: var(--svc-navy);
}

.svc-card-copy small {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--svc-text2);
}

.svc-card-arrow {
  font-size: 16px;
  color: var(--svc-text3);
  transition: transform 0.22s ease;
}

.svc-card:hover .svc-card-arrow {
  color: var(--svc-primary);
  transform: translateX(3px);
}

/* 法规库 / 政策库 */
.svc-lib {
  padding: 22px 26px 24px;
  background: #fff;
  border: 1px solid var(--svc-border);
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(10 37 64 / 6%);
}

.svc-head {
  display: flex;
  gap: 14px;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--svc-border-light);
}

.svc-head h2 {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--svc-navy);
}

.svc-head h2 i {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: var(--svc-primary);
  border-radius: 2px;
}

.svc-count {
  flex: 1;
  font-size: 13px;
  color: var(--svc-text3);
}

.svc-more {
  font-size: 13px;
  color: var(--svc-primary);
  cursor: pointer;
}

.svc-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 34px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.svc-list li {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 11px 0;
  font-size: 14px;
  border-bottom: 1px dashed var(--svc-border-light);
}

.svc-list li a {
  flex: 1;
  overflow: hidden;
  color: var(--svc-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.svc-list li a::before {
  padding-right: 8px;
  color: #9fb0cc;
  content: '○';
}

.svc-list li a:hover {
  color: var(--svc-primary);
}

.svc-meta {
  flex-shrink: 0;
  font-size: 12.5px;
  color: var(--svc-text3);
}

/* AI 助手 CTA */
.svc-cta {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 28px 36px;
  color: #fff;
  background: linear-gradient(120deg, #0b2f57, #143664);
  border-radius: 12px;
  box-shadow: 0 6px 20px rgb(10 37 64 / 12%);
}

.svc-cta h2 {
  margin-bottom: 8px;
  font-size: 20px;
  font-weight: 700;
}

.svc-cta p {
  font-size: 13.5px;
  color: #c3d2e6;
}

.svc-cta-btn {
  flex-shrink: 0;
  height: 44px;
  padding: 0 28px;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  cursor: pointer;
  background: #b8860b;
  border: none;
  border-radius: 8px;
  transition: filter 0.2s ease;
}

.svc-cta-btn:hover {
  filter: brightness(1.1);
}

@media (max-width: 1200px) {
  .svc-stats,
  .svc-features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .svc-stat:nth-child(2) {
    border-right: none;
  }
}

@media (max-width: 720px) {
  .svc-features,
  .svc-list {
    grid-template-columns: 1fr;
  }

  .svc-cta {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

