<script lang="ts" setup>
import { useRouter } from 'vue-router';

const props = defineProps<{ active?: string }>();

const router = useRouter();

/** 招投标前台模块导航（公告大厅由顶部「招标投标」入口直达，不再占导航按钮位；本平台发布晚点移管理端） */
const MODULES = [
  { key: 'bid-document', icon: '✎', name: '标书制作', sub: '编制 · 上传投标文件', path: '/portal/bidding/bid-document' },
  { key: 'compliance', icon: '✓', name: '合规审查', sub: '公告 · 标书合规核验', path: '/portal/bidding/compliance' },
  { key: 'process', icon: '▤', name: '投标流程管理', sub: '意向 · 待投标 · 已投标', path: '/portal/bidding/process' },
];

function go(path: string): void {
  router.push(path).catch(() => {
    /* 导航被取消/重复导航：忽略 */
  });
}
</script>

<template>
  <div class="main-nav">
    <div
      v-for="m in MODULES"
      :key="m.key"
      class="nav-item kbd-btn"
      role="button"
      tabindex="0"
      :class="{ on: props.active === m.key }"
      @click="go(m.path)"
      @keydown.enter.prevent="go(m.path)"
    >
      <span class="ic">{{ m.icon }}</span>
      <div class="n-name">{{ m.name }}</div>
      <div class="n-sub">{{ m.sub }}</div>
    </div>
  </div>
</template>

<style scoped>
.kbd-btn:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}
.main-nav {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  background: hsl(var(--border));
  gap: 1px;
}
.nav-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 12px;
  background: hsl(var(--muted) / 40%);
  cursor: pointer;
  text-align: center;
}
.nav-item:hover {
  background: hsl(var(--primary) / 8%);
}
.nav-item.on {
  background: hsl(var(--card));
  box-shadow: inset 0 -3px 0 hsl(var(--primary));
}
.nav-item .ic {
  font-size: 20px;
  color: hsl(var(--primary));
  line-height: 1;
}
.nav-item .n-name {
  font-size: 13.5px;
  font-weight: 700;
  color: hsl(var(--foreground));
}
.nav-item.on .n-name {
  color: hsl(var(--primary));
}
.nav-item .n-sub {
  font-size: 11px;
  color: hsl(var(--muted-foreground));
}
</style>
