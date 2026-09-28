<!--
  招投标门户 · 右侧悬浮快捷导航（公告大厅 / 标书制作 / 合规审查 / 投标流程管理 四模块共用）
  视觉（2026-09-17 改版）：毛玻璃（半透明 + 背景模糊）替代不透明白底；按钮统一圆角矩形；
    「立即投标」由高饱和橙改为暖金品牌辅助色（形状与其它按钮一致，不再突兀）。
  结构自下而上：毛玻璃标题卡 → 四个模块按钮（可高亮当前模块）→「立即投标」（暖金强调色）。
  用法：`<FloatBar active="process" />`（active 可省略；用于高亮当前所在模块）。

  ⚠ 2026-09-21 现状：**本组件当前没有被任何地方引用**，页面上不会出现。
    当天一度把它改成页面右栏里的侧边栏（fixed → sticky、宽度 100%），
    随后用户要求「去掉右侧服务侧边栏、下面的内容恢复居中」，四个招标页面都退回单栏，
    于是引用全部移除、本组件还原为原来的固定悬浮形态（保持文件原样，便于日后需要时直接复用）。
    若以后要恢复：在页面末尾放 `<FloatBar active="hall" />` 即可 —— 它会固定贴在窗口右缘，
    页面正文宽度按 `calc(100% - var(--bid-side-inset))` 居中时正好给它让出位置。

  注（2026-09-20 当天加了又撤）：这里曾在「立即投标」下方挂过一个「点击立即咨询」两行块
    （组件 layouts/portal-consult-button.vue），后来还试过把强调色与「立即投标」互换；
    用户看过实际效果后要求撤下该按钮、并把「立即投标」的暖金配色改回来 —— 现已全部还原。
    不要再往这一栏里塞第二类动作按钮：栏宽只有 112px，塞进来必然要和导航按钮抢视觉层级。
-->
<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import '../styles/bid-ui.css';

const props = defineProps<{ active?: string }>();

const router = useRouter();

/* 「合规审查」= 标书审查工作台（bidReview/*，真实接口），与公告大厅模块卡片口径一致。
 * 原同名的本地流程版页（/portal/bidding/compliance，数据存 localStorage）2026-09-20 已删除，
 * 门户不再存在两套「合规审查」。 */
const ITEMS = [
  { key: 'hall', icon: 'lucide:home', label: '公告大厅', path: '/portal/bidding' },
  { key: 'bid-document', icon: 'lucide:file-text', label: '标书制作', path: '/portal/bidding/bid-document' },
  { key: 'compliance', icon: 'lucide:shield-check', label: '合规审查', path: '/portal/bidding/bidReview/review' },
  { key: 'process', icon: 'lucide:workflow', label: '流程管理', path: '/portal/bidding/process' },
];

function go(path: string): void {
  router.push(path).catch(() => {
    /* 导航被取消/重复导航：忽略 */
  });
}
</script>

<template>
  <aside class="float-bar">
    <div class="fb-card">
      <div class="fb-card-title">投标服务</div>
      <div class="fb-card-desc">专业 · 高效 · 合规</div>
    </div>

    <button
      v-for="it in ITEMS"
      :key="it.key"
      class="fb-btn"
      :class="{ on: props.active === it.key }"
      type="button"
      @click="go(it.path)"
    >
      <IconifyIcon class="fb-ic" :icon="it.icon" />
      <span>{{ it.label }}</span>
    </button>

    <button class="fb-btn fb-btn--accent" type="button" @click="go('/portal/bidding/process')">
      <IconifyIcon class="fb-ic" icon="lucide:send" />
      <span>立即投标</span>
    </button>
  </aside>
</template>

<style scoped>
/* 变量（--bid-*）来自 styles/bid-ui.css 的 .float-bar 选择器 */

.float-bar {
  position: fixed;
  top: 50%;
  right: 14px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 112px;
  transform: translateY(-50%);
}

/* 毛玻璃面板：半透明 + 背景模糊 + 细边高光 */
.fb-card,
.fb-btn {
  border: 1px solid rgb(255 255 255 / 72%);
  background: rgb(255 255 255 / 68%);
  box-shadow:
    0 6px 18px rgb(16 24 40 / 10%),
    inset 0 1px 0 rgb(255 255 255 / 85%);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
}

.fb-card {
  padding: 10px 8px;
  border-radius: var(--bid-radius);
  text-align: center;
}

.fb-card-title {
  color: var(--bid-text-1);
  font-size: 13px;
  font-weight: 700;
}

.fb-card-desc {
  margin-top: 3px;
  color: var(--bid-text-3);
  font-size: 11px;
}

/* 按钮统一为圆角矩形 + 同一形状，不再有"异形"按钮 */
.fb-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 8px;
  border-radius: var(--bid-radius);
  color: var(--bid-text-2);
  font-family: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.fb-btn:hover {
  border-color: rgb(26 86 184 / 35%);
  background: rgb(234 241 250 / 88%);
  color: var(--bid-primary);
}

/* 当前所在模块高亮 */
.fb-btn.on {
  border-color: rgb(26 86 184 / 40%);
  background: rgb(234 241 250 / 92%);
  color: var(--bid-primary);
  font-weight: 600;
}

.fb-ic {
  font-size: 15px;
}

/* 强调按钮（只有「立即投标」用）：暖金 + 深棕文字。
 * 深棕而不是白字是**硬要求**：金底白字对比度不足（bid-ui.css 里对此有明确注释，不可用）。 */
.fb-btn--accent {
  border-color: rgb(200 154 60 / 45%);
  background: linear-gradient(135deg, rgb(227 180 92 / 96%), rgb(207 154 53 / 96%));
  color: var(--bid-accent-ink);
  font-weight: 700;
}

.fb-btn--accent:hover {
  border-color: rgb(200 154 60 / 70%);
  background: linear-gradient(135deg, #dfae52, #c08f2e);
  box-shadow: 0 6px 16px rgb(185 136 42 / 30%);
  color: var(--bid-accent-ink);
}

@media (max-width: 720px) {
  .float-bar {
    display: none;
  }
}
</style>
