<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

defineOptions({ name: 'PortalTraining' });

const router = useRouter();
const route = useRoute();

/** 学习工作台（极客时间风首页）为默认页面，其余二级页面显示返回条；
 * 考试页改为使用页内横幅右上角的「返回课程中心」按钮，避免顶部出现重复返回入口 */
const onHomePage = computed(() => route.path.startsWith('/portal/training/workbench'));
const onExamPage = computed(() => route.path.startsWith('/portal/training/exam'));
const showBackBar = computed(() => !onHomePage.value && !onExamPage.value);

function backToHome() {
  router.push('/portal/training/workbench');
}

/** 右侧悬浮工具条：仅保留证书查询入口 */
const floatTools = [
  {
    label: '证书查询',
    icon: 'lucide:badge-check',
    path: '/portal/training/exam',
  },
];

function go(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="portal">
    <div v-if="showBackBar" class="back-bar">
      <button type="button" @click="backToHome">‹ 返回课程中心</button>
    </div>

    <main class="container">
      <router-view />
    </main>

    <footer class="footer">
      <div class="footer-inner">
        <div class="links">
          <span>友情链接：</span>
          <a href="https://www.miit.gov.cn/" target="_blank" rel="noreferrer">
            中华人民共和国工业和信息化部
          </a>
          <a href="http://www.mohrss.gov.cn/" target="_blank" rel="noreferrer">
            中华人民共和国人力资源和社会保障部
          </a>
          <a href="http://www.moe.gov.cn/" target="_blank" rel="noreferrer">
            中华人民共和国教育部
          </a>
        </div>
        <p class="org">承办单位：企业数字化人才培养中心 技术支持：平台研发部</p>
      </div>
      <div class="copyright">
        <span>Copyright © 2026 云培训学院考试系统 版权所有</span>
        <em>|</em>
        <span>平台管理入口</span>
      </div>
    </footer>

    <!-- 右侧悬浮工具条 -->
    <aside class="float-tools">
      <button
        v-for="tool in floatTools"
        :key="tool.label"
        type="button"
        @click="go(tool.path)"
      >
        <IconifyIcon :icon="tool.icon" class="size-5" />
        <span>{{ tool.label }}</span>
      </button>
    </aside>
  </div>
</template>

<style scoped>
/* 品牌色沿用原门户配色：
 * 横幅蓝 #007CB4 · 主色深蓝 #005C9F · 强调橙 #E03B00 · 正文 #333 */
.portal {
  --brand: #005c9f;
  --brand-banner: #007cb4;
  --brand-accent: #e03b00;
  --text: #333;
  --muted: rgb(0 0 0 / 65%);
  --line: #e5e7eb;

  min-height: 100%;
  color: var(--text);
  background: #f7f8fa;
}

.banner {
  background: linear-gradient(
    120deg,
    #0089c4 0%,
    var(--brand-banner) 46%,
    var(--brand) 100%
  );
}

.banner-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1200px, calc(100% - 48px));
  height: 120px;
  margin: 0 auto;
}

.brand {
  display: flex;
  gap: 14px;
  align-items: center;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #fff;
  background: rgb(255 255 255 / 18%);
  border: 1px solid rgb(255 255 255 / 35%);
  border-radius: 12px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand-text b {
  color: #fff;
  font-size: 26px;
  letter-spacing: 0.04em;
}

.brand-text small {
  color: rgb(255 255 255 / 72%);
  font-size: 12px;
  letter-spacing: 0.14em;
}

.banner-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 顶部导航栏右侧「返回课程系统」按钮 */
.back-bar {
  display: flex;
  justify-content: flex-start;

  button {
    padding: 8px 18px;
    border: none;
    border-radius: 0 0 10px 10px;
    background: rgb(0 124 180 / 8%);
    color: #005c9f;
    font-size: 14px;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: rgb(0 124 180 / 16%);
    }
  }
}

.banner-actions .banner-switch {
  padding: 9px 20px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  background: #1f6feb;
  border: 0;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 18%);
  transition: background 0.18s;
}

.banner-actions .banner-switch:hover {
  background: #3b82f6;
}

.container {
  width: min(1200px, calc(100% - 48px));
  margin: 0 auto;
  padding: 24px 0 56px;
}

.footer {
  border-top: 3px solid var(--brand);
  background: #fff;
}

.footer-inner {
  width: min(1200px, calc(100% - 48px));
  margin: 0 auto;
  padding: 22px 0 14px;
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.links a {
  color: #4b5b70;
  text-decoration: none;
}

.links a:hover {
  color: var(--brand);
}

.org {
  margin: 12px 0 0;
  color: #98a2b3;
  font-size: 12px;
}

.copyright {
  display: flex;
  gap: 10px;
  justify-content: center;
  padding: 12px 0;
  color: #98a2b3;
  font-size: 12px;
  border-top: 1px solid var(--line);
}

.copyright em {
  font-style: normal;
}

.float-tools {
  position: fixed;
  z-index: 80;
  top: 50%;
  right: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dce5f1;
  border-radius: 8px;
  box-shadow: 0 10px 26px rgb(20 42 82 / 16%);
  translate: 0 -50%;
}

.float-tools button {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  width: 62px;
  padding: 10px 4px;
  color: #5a6b82;
  font-size: 12px;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.float-tools button:hover {
  color: var(--brand);
  background: #f2f7ff;
}

@media (max-width: 900px) {
  .banner-inner {
    height: auto;
    padding: 18px 0;
    flex-wrap: wrap;
    gap: 14px;
  }

  .float-tools {
    display: none;
  }
}
</style>
